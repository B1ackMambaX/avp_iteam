ALTER TABLE operation_parse_result
    ADD COLUMN classification_method VARCHAR(20) NOT NULL DEFAULT 'MARKER',
    ADD COLUMN classification_score DOUBLE PRECISION,
    ADD COLUMN extracted_beneficiary_name VARCHAR(255),
    ADD COLUMN is_genuine_third_party BOOLEAN;

ALTER TABLE operation_parse_result
    ADD CONSTRAINT chk_classification_method
    CHECK (classification_method IN ('MARKER', 'SEMANTIC', 'NLP_NER'));

COMMENT ON COLUMN operation_parse_result.classification_method IS
    'Метод классификации: MARKER (legacy), SEMANTIC (embedding similarity), NLP_NER (Natasha)';
COMMENT ON COLUMN operation_parse_result.classification_score IS
    'Score семантического классификатора (0..1), NULL для MARKER';
COMMENT ON COLUMN operation_parse_result.extracted_beneficiary_name IS
    'ФИО, извлечённое NLP-модулем; NULL если не третье лицо или извлечение не удалось';
COMMENT ON COLUMN operation_parse_result.is_genuine_third_party IS
    'Результат сверки имени с плательщиком/получателем (вопрос 6): false = ложное срабатывание';