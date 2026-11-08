-- =========================================================
-- V4: статусы конвейера маппинга
--  PENDING -> WAITING_NLP -> PARSED -> AUTO_MAPPED / AMBIGUOUS / NOT_FOUND / MANUAL_REQUIRED
-- PARSED - назначение платежа разобрано, операция ждёт маппинга
-- =========================================================
ALTER TABLE operations DROP CONSTRAINT chk_operation_status;
ALTER TABLE operations ADD CONSTRAINT chk_operation_status CHECK (status IN (
    'PENDING', 'WAITING_NLP', 'PARSED',
    'AUTO_MAPPED', 'MANUAL_REQUIRED', 'AMBIGUOUS', 'NOT_FOUND'
));

-- Почему маппинг принял такое решение: CONFIDENT_MATCH, SINGLE_CANDIDATE,
-- CLOSE_CANDIDATES, WEAK_EVIDENCE, THIRD_PARTY_UNCONFIRMED, PARSE_UNAVAILABLE, NO_CANDIDATES
ALTER TABLE operations ADD COLUMN mapping_reason VARCHAR(40);

-- Выборка разобранных операций пачками по id (keyset)
CREATE INDEX idx_operation_parsed ON operations(id) WHERE status = 'PARSED';
