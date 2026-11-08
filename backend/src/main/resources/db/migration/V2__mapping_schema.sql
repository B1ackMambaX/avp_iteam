-- =========================================================
-- V2: схема под модуль маппинга и очередь NLP
--  - роли сторон договора вынесены в contract_parties
--  - нормализованные ФИО клиента и номер договора для поиска
--  - результаты парсинга, кандидаты маппинга, очередь задач NLP
--  - точечные исправления V1
-- =========================================================

-- =========================================================
-- СТОРОНЫ ДОГОВОРА
-- Вместо трёх FK в contracts: "все договоры клиента" - один
-- индексный поиск по client_id, а не OR по трём колонкам.
-- Заодно допускает несколько сторон одной роли (вопрос 13 в вики).
-- =========================================================
CREATE TABLE contract_parties (
    contract_id     BIGINT      NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    client_id       BIGINT      NOT NULL REFERENCES clients(id),
    role            VARCHAR(20) NOT NULL
                    CHECK (role IN ('COUNTERPARTY', 'AGENT', 'BENEFICIARY')),

    PRIMARY KEY (contract_id, role, client_id)
);

-- Главный индекс маппинга: клиент -> его договоры (index-only scan)
CREATE INDEX idx_contract_party_client ON contract_parties(client_id, role, contract_id);

INSERT INTO contract_parties (contract_id, client_id, role)
SELECT id, counterparty_id, 'COUNTERPARTY' FROM contracts WHERE counterparty_id IS NOT NULL
UNION ALL
SELECT id, agent_id, 'AGENT' FROM contracts WHERE agent_id IS NOT NULL
UNION ALL
SELECT id, beneficiary_id, 'BENEFICIARY' FROM contracts WHERE beneficiary_id IS NOT NULL;

-- Индексы idx_contract_agent/counterparty/beneficiary удаляются вместе с колонками
ALTER TABLE contracts
    DROP COLUMN agent_id,
    DROP COLUMN counterparty_id,
    DROP COLUMN beneficiary_id;

-- =========================================================
-- КЛИЕНТЫ
-- =========================================================

-- Выгодоприобретатель-физлицо может прийти без ИНН
ALTER TABLE clients ALTER COLUMN inn DROP NOT NULL;

-- Производный флаг: вычисляется по contract_parties, иначе разъезжается с договорами
ALTER TABLE clients DROP COLUMN is_beneficiary;

-- Нормализованное ФИО: нижний регистр, ё -> е, одиночные пробелы.
-- Сравнивается с operation_parse_result.extracted_name_norm (то же правило).
ALTER TABLE clients ADD COLUMN full_name_norm TEXT GENERATED ALWAYS AS (
    NULLIF(btrim(regexp_replace(
        replace(lower(coalesce(last_name, '') || ' ' || coalesce(first_name, '') || ' ' || coalesce(middle_name, '')), 'ё', 'е'),
        '\s+', ' ', 'g')), '')
) STORED;

CREATE INDEX idx_client_full_name_norm ON clients(full_name_norm);

-- Низкая селективность (3 значения) - индекс не используется
DROP INDEX idx_client_type;

-- =========================================================
-- ДОГОВОРЫ
-- =========================================================

-- Уникальность номера не подтверждена (вопрос 12 в вики):
-- номер может повторяться в разных типах договоров
ALTER TABLE contracts DROP CONSTRAINT contracts_contract_number_key;
DROP INDEX idx_contract_number;

-- Нормализованный номер: только буквы и цифры, верхний регистр, Ё -> Е.
-- "№ 12-КР" и "12кр" дают одно значение "12КР".
ALTER TABLE contracts ADD COLUMN contract_number_norm VARCHAR(100) GENERATED ALWAYS AS (
    replace(upper(regexp_replace(contract_number, '[^0-9A-Za-zА-Яа-яЁё]', '', 'g')), 'Ё', 'Е')
) STORED;

CREATE INDEX idx_contract_number_norm ON contracts(contract_number_norm);

-- =========================================================
-- ОПЕРАЦИИ
-- =========================================================

-- Код рубля по ISO 4217 и ОКВ - 643; 810 встречается только в номерах счетов
ALTER TABLE operations ALTER COLUMN currency_code SET DEFAULT '643';
UPDATE operations SET currency_code = '643' WHERE currency_code = '810';

ALTER TABLE operations ADD CONSTRAINT chk_operation_status CHECK (status IN (
    'PENDING', 'WAITING_NLP', 'AUTO_MAPPED', 'MANUAL_REQUIRED', 'AMBIGUOUS', 'NOT_FOUND'
));

-- Кто и когда привязал договор: AUTO или логин оператора
ALTER TABLE operations
    ADD COLUMN mapped_by VARCHAR(100),
    ADD COLUMN mapped_at TIMESTAMPTZ;

DROP INDEX idx_operation_reference; -- дублирует UNIQUE
DROP INDEX idx_operation_status;    -- низкая селективность
DROP INDEX idx_operation_mapping;   -- маппинг ищет в contracts, а не в operations

-- Выборка необработанных пачками по id (keyset)
CREATE INDEX idx_operation_pending ON operations(id) WHERE status = 'PENDING';

-- =========================================================
-- Технические даты - абсолютный момент времени
-- (operation_date не трогаем: формат источника не подтверждён, вопрос 9)
-- =========================================================
ALTER TABLE clients
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;
ALTER TABLE contracts
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;
ALTER TABLE operations
    ALTER COLUMN created_at TYPE TIMESTAMPTZ,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ;

-- =========================================================
-- РЕЗУЛЬТАТЫ ПАРСИНГА НАЗНАЧЕНИЯ ПЛАТЕЖА
-- Одна строка на прогон парсера: маркерный (Java) и NLP (Python)
-- хранятся рядом, чтобы видеть, кто что извлёк.
-- =========================================================
CREATE SEQUENCE operation_parse_result_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE operation_parse_result (
    id                      BIGINT PRIMARY KEY DEFAULT nextval('operation_parse_result_seq'),
    operation_id            BIGINT       NOT NULL REFERENCES operations(id),

    parser                  VARCHAR(20)  NOT NULL CHECK (parser IN ('MARKER', 'NLP')),
    parser_version          VARCHAR(50)  NOT NULL,

    is_third_party          BOOLEAN      NOT NULL,
    -- Роль извлечённого лица в договоре: выгодоприобретатель, или
    -- "по поручению" (доверитель - сторона договора, плательщик - агент)
    party_role              VARCHAR(20)  CHECK (party_role IN ('COUNTERPARTY', 'AGENT', 'BENEFICIARY')),
    extracted_name          TEXT,
    extracted_name_norm     TEXT GENERATED ALWAYS AS (
        NULLIF(btrim(regexp_replace(replace(lower(extracted_name), 'ё', 'е'), '\s+', ' ', 'g')), '')
    ) STORED,
    confidence              NUMERIC(4,3) CHECK (confidence BETWEEN 0 AND 1),

    parsed_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_parse_result_operation ON operation_parse_result(operation_id);

-- =========================================================
-- КАНДИДАТЫ МАППИНГА
-- Все договоры, которые рассматривались для операции, со скорингом.
-- Итоговый выбор - operations.contract_id; здесь - обоснование
-- для AMBIGUOUS/MANUAL_REQUIRED и для оператора в UI.
-- =========================================================
CREATE TABLE operation_mapping_candidate (
    operation_id            BIGINT       NOT NULL REFERENCES operations(id),
    contract_id             BIGINT       NOT NULL REFERENCES contracts(id),

    score                   NUMERIC(5,4) NOT NULL CHECK (score BETWEEN 0 AND 1),
    -- Сработавшие правила с весами, например {"CONTRACT_NUMBER": 0.5, "COUNTERPARTY_INN": 0.3}
    matched_rules           JSONB        NOT NULL DEFAULT '{}',

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    PRIMARY KEY (operation_id, contract_id)
);

-- =========================================================
-- ОЧЕРЕДЬ ЗАДАЧ NLP
-- Воркер забирает задачи через FOR UPDATE SKIP LOCKED.
-- Зависшие IN_PROGRESS (locked_until в прошлом) забираются повторно.
-- =========================================================
CREATE SEQUENCE nlp_task_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE nlp_task (
    id                      BIGINT PRIMARY KEY DEFAULT nextval('nlp_task_seq'),
    operation_id            BIGINT       NOT NULL UNIQUE REFERENCES operations(id),

    status                  VARCHAR(20)  NOT NULL DEFAULT 'NEW'
                            CHECK (status IN ('NEW', 'IN_PROGRESS', 'DONE', 'FAILED')),
    attempts                INT          NOT NULL DEFAULT 0,
    next_try_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_until            TIMESTAMPTZ,
    last_error              TEXT,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ
);

CREATE INDEX idx_nlp_task_ready ON nlp_task(next_try_at) WHERE status = 'NEW';
CREATE INDEX idx_nlp_task_locked ON nlp_task(locked_until) WHERE status = 'IN_PROGRESS';
