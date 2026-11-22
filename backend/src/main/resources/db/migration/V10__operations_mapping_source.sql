-- =========================================================
-- V10: явный источник привязки договора
-- AUTO   - модуль маппинга (MappingJdbcRepository.updateOperations)
-- MANUAL - оператор через ручное подтверждение (resolve, EDIT_MAPPING)
--
-- mapped_by для этого не подходит: автопривязка пишет туда 'AUTO',
-- а логин пользователя ([A-Za-z0-9._-]{3,64}) тоже может быть "AUTO".
-- Подробно - docs/analysis/stats-status-mapping.md
-- =========================================================
ALTER TABLE operations ADD COLUMN mapping_source VARCHAR(10);

ALTER TABLE operations ADD CONSTRAINT chk_operation_mapping_source
    CHECK (mapping_source IN ('AUTO', 'MANUAL'));

-- Все существующие привязки сделаны маппингом: ручного подтверждения в коде ещё нет
UPDATE operations SET mapping_source = 'AUTO'
WHERE contract_id IS NOT NULL AND mapped_by = 'AUTO';
