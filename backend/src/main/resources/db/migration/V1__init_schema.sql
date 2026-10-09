-- =========================================================
-- Последовательности для генерации ID
-- =========================================================
CREATE SEQUENCE client_seq    START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE contract_seq  START WITH 1 INCREMENT BY 100;
CREATE SEQUENCE operation_seq START WITH 1 INCREMENT BY 100;

-- =========================================================
-- КЛИЕНТЫ
-- Вариант 1 (плоская таблица): храним поля и для ФЛ/ИП, и для ЮЛ
-- в одной таблице, заполняем только релевантные по client_type.
-- =========================================================
CREATE TABLE clients (
    id                      BIGINT PRIMARY KEY DEFAULT nextval('client_seq'),

    client_type             VARCHAR(20)  NOT NULL, -- INDIVIDUAL / ENTREPRENEUR / LEGAL_ENTITY
    inn                     VARCHAR(12)  NOT NULL,
    pin                     VARCHAR(6),             -- ПИН клиента

    -- Поля физлица / ИП
    last_name               VARCHAR(200),
    first_name              VARCHAR(200),
    middle_name             VARCHAR(200),
    birth_date              DATE,
    birth_place             VARCHAR(500),
    citizenship             VARCHAR(100),

    -- Документ, удостоверяющий личность (ДУЛ)
    document_type           VARCHAR(100),           -- паспорт РФ, загранпаспорт и т.д.
    document_series         VARCHAR(20),
    document_number         VARCHAR(20),
    document_issue_date     DATE,
    document_issued_by      VARCHAR(500),
    document_department_code VARCHAR(20),            -- код подразделения

    registration_address    VARCHAR(1000),

    -- Поля юрлица
    organization_name       VARCHAR(500),            -- Наименование
    legal_form              VARCHAR(200),            -- Организационно-правовая форма
    bik                     VARCHAR(9),
    ogrn                    VARCHAR(15),
    ogrn_date               DATE,

    -- Флаги
    is_extremist            BOOLEAN NOT NULL DEFAULT FALSE, -- внесён в перечень экстремистов
    is_beneficiary          BOOLEAN NOT NULL DEFAULT FALSE, -- фигурирует как выгодоприобретатель хоть в одном договоре
    is_bank_client          BOOLEAN NOT NULL DEFAULT FALSE, -- клиент банка (может быть контрагентом не-клиентом)

    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP
);

CREATE INDEX idx_client_inn  ON clients(inn);
CREATE INDEX idx_client_type ON clients(client_type);

-- =========================================================
-- ДОГОВОРЫ
-- Роли разведены по смыслу (не "плательщик/получатель", а
-- агент/контрагент/выгодоприобретатель), как уточнил заказчик
-- =========================================================
CREATE TABLE contracts (
    id                  BIGINT PRIMARY KEY DEFAULT nextval('contract_seq'),

    contract_type       VARCHAR(50)  NOT NULL, -- см. enum ContractType ниже
    contract_number     VARCHAR(100) NOT NULL UNIQUE,

    agent_id            BIGINT REFERENCES clients(id),        -- посредник, nullable
    counterparty_id     BIGINT REFERENCES clients(id),        -- вторая сторона договора
    beneficiary_id      BIGINT REFERENCES clients(id),        -- выгодоприобретатель, nullable

    request_ref         VARCHAR(200), -- "Запрос" — задел на будущее, пока просто строка

    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP
);

CREATE INDEX idx_contract_number       ON contracts(contract_number);
CREATE INDEX idx_contract_type         ON contracts(contract_type);
CREATE INDEX idx_contract_agent        ON contracts(agent_id);
CREATE INDEX idx_contract_counterparty ON contracts(counterparty_id);
CREATE INDEX idx_contract_beneficiary  ON contracts(beneficiary_id);

-- =========================================================
-- ОПЕРАЦИИ
-- =========================================================
CREATE TABLE operations (
    id                      BIGINT PRIMARY KEY DEFAULT nextval('operation_seq'),

    reference               VARCHAR(100) NOT NULL UNIQUE, -- референс проводки
    operation_date          TIMESTAMP NOT NULL,

    -- Отправитель
    sender_pin              VARCHAR(6),
    sender_branch           VARCHAR(4),
    sender_inn              VARCHAR(12),
    sender_account          VARCHAR(20),
    sender_name             VARCHAR(500),

    -- Получатель
    receiver_pin            VARCHAR(6),
    receiver_branch         VARCHAR(4),
    receiver_inn            VARCHAR(12),
    receiver_account        VARCHAR(20),
    receiver_name           VARCHAR(500),

    currency_code           VARCHAR(3) NOT NULL DEFAULT '810', -- 810 = RUB по ОКВ
    amount                  NUMERIC(19,2) NOT NULL,
    amount_rub              NUMERIC(19,2) NOT NULL,

    payment_purpose         TEXT,

    status                  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    contract_id             BIGINT REFERENCES contracts(id),

    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP -- дата последней модификации записи
);

CREATE INDEX idx_operation_reference    ON operations(reference);
CREATE INDEX idx_operation_sender_inn   ON operations(sender_inn);
CREATE INDEX idx_operation_receiver_inn ON operations(receiver_inn);
CREATE INDEX idx_operation_status       ON operations(status);
CREATE INDEX idx_operation_date         ON operations(operation_date);
CREATE INDEX idx_operation_contract     ON operations(contract_id);

-- Составной индекс — главный "рабочий" запрос маппинга для обычных договоров
CREATE INDEX idx_operation_mapping ON operations(sender_inn, receiver_inn, status);