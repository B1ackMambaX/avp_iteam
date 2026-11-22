-- =========================================================
-- V12: ручное решение по маппингу (BE-4)
-- Подтверждение договора оператором -> MANUAL_MAPPED,
-- отклонение ("договор не найден") -> NOT_FOUND; в обоих случаях mapping_source = 'MANUAL'.
-- Решение принимается только из очереди (AMBIGUOUS, MANUAL_REQUIRED), исправлять принятое нельзя.
-- =========================================================
ALTER TABLE operations DROP CONSTRAINT chk_operation_status;
ALTER TABLE operations ADD CONSTRAINT chk_operation_status CHECK (status IN (
    'PENDING', 'WAITING_NLP', 'PARSED',
    'AUTO_MAPPED', 'MANUAL_MAPPED', 'MANUAL_REQUIRED', 'AMBIGUOUS', 'NOT_FOUND'
));

-- История ручных решений: кто, когда, что выбрал и почему
CREATE TABLE operation_manual_decision (
    id               BIGSERIAL    PRIMARY KEY,
    operation_id     BIGINT       NOT NULL REFERENCES operations(id) ON DELETE CASCADE,
    action           VARCHAR(10)  NOT NULL CHECK (action IN ('CONFIRM', 'REJECT')),
    contract_id      BIGINT       REFERENCES contracts(id),
    -- выбран ли договор из сохранённых кандидатов; вне кандидатов - только с комментарием
    from_candidates  BOOLEAN      NOT NULL,
    previous_status  VARCHAR(30)  NOT NULL,
    comment          VARCHAR(1000),
    decided_by       VARCHAR(100) NOT NULL,
    decided_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_manual_decision_contract CHECK ((action = 'CONFIRM') = (contract_id IS NOT NULL))
);

CREATE INDEX idx_manual_decision_operation ON operation_manual_decision (operation_id);
