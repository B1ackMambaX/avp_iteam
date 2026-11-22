-- =========================================================
-- V11: индексы бизнес-дашборда GET /api/stats/summary
-- Обе части запроса читаются index-only scan (при актуальной visibility map)
-- =========================================================

-- Счётчики за день: диапазон по created_at, статус и источник - из INCLUDE
CREATE INDEX idx_operation_created_at ON operations (created_at)
    INCLUDE (status, mapping_source);

-- Общий бэклог ручного разбора вне зависимости от дня
CREATE INDEX idx_operation_awaiting_manual ON operations (id)
    INCLUDE (mapping_source)
    WHERE status IN ('AMBIGUOUS', 'MANUAL_REQUIRED');
