---
title: Архитектура системы
status: актуально
---
# Архитектура системы

MVP автоматической привязки банковских операций к договорам (требования 115-ФЗ):
по назначению платежа система определяет, является ли он оплатой за третье лицо,
извлекает ФИО выгодоприобретателя и привязывает операцию к договору —
автоматически или через очередь ручного разбора.

## Компоненты

| Компонент | Технологии | Роль |
|---|---|---|
| `backend/` | Java 21, Spring Boot 3.3.5, Vaadin 24, Spring Security (Basic), Flyway | Конвейер обработки, маппинг на договоры, REST API, роли, админка |
| `nlp-service/` | Python, FastAPI, Natasha, NewsEmbedding | Классификация «третье лицо / нет» (семантика) и извлечение ФИО (NER) |
| Инфраструктура | PostgreSQL 16, Prometheus, Grafana (`docker-compose.yml`) | БД, метрики, дашборд `monitoring/grafana/dashboards/avp_iteam.json` |

## Конвейер обработки

```
PENDING ──► WAITING_NLP ──► PARSED ──► AUTO_MAPPED | AMBIGUOUS | NOT_FOUND | MANUAL_REQUIRED
   │         очередь nlp_task   ▲
   │         (NlpTaskWorker)    │
   └──── режим marker: regex ───┘
         (RegexPaymentPurposeParser, без Python)
```

| Шаг | Кто | Что делает |
|---|---|---|
| Приём | `IntakeService` | Режим `nlp`: PENDING-операции ставятся в очередь `nlp_task` → `WAITING_NLP`. Режим `marker`: назначение разбирается regex-парсером сразу → `PARSED` |
| Разбор | `NlpTaskWorker` | Захват задач `FOR UPDATE SKIP LOCKED`, параллельные вызовы `/classify` + `/extract-name` Python-сервиса (вне транзакции БД), запись `operation_parse_result` → `PARSED`. Ошибки — повтор с backoff (`retry-delay-seconds × попытка`); после `max-attempts` задача FAILED, операция всё равно идёт в маппинг без автопривязки. Отложенные на повтор задачи подбирает следующий запуск конвейера |
| Маппинг | `MappingService` | Пачка `PARSED`-операций: кандидаты по ИНН сторон, номеру договора из текста и ФИО выгодоприобретателя; скоринг правил с весами; решение (`AUTO_MAPPED` / `AMBIGUOUS` / `NOT_FOUND` / `MANUAL_REQUIRED`) с причиной в `mapping_reason`, запись `operation_mapping_candidate` |
| Запуск | `PipelineRunner` | Крутит шаги, пока есть работа: `POST /api/pipeline/run` или фоновый планировщик (`pipeline.scheduler.enabled=true`) |

Все шаги забирают строки через `FOR UPDATE SKIP LOCKED` — безопасны при
параллельном запуске (REST + планировщик, несколько инстансов).

Платёж за третье лицо автоматически привязывается только при подтверждённом
выгодоприобретателе (`is_genuine_third_party`): извлечённое ФИО сверяется
с плательщиком и получателем — совпадение означает ложное срабатывание
классификатора (ответ заказчика на «вопрос 6»).

## Ключевые решения

- **Очередь в Postgres** вместо отдельного брокера: `nlp_task` с lease
  (`locked_until`), ретраи (`next_try_at`, backoff), `FOR UPDATE SKIP LOCKED` —
  безопасно при параллельных инстансах и рестартах.
- **HTTP-вызовы NLP вне транзакций**: захват задач и запись результатов — две
  короткие транзакции, соединение с БД не висит, пока отвечает Python.
- **Нормализация в БД** (generated columns): `full_name_norm`, `contract_number_norm`,
  `extracted_name_norm` — ФИО из парсера сравнивается с клиентом простым равенством.
- **Настраиваемый классификатор**: порог и списки слов лежат в `nlp_settings`,
  меняются через админку с версионированием и откатом, без деплоя.
- **Роли и права**: READER / EDITOR / ADMIN → `VIEW_DATA` / `EDIT_MAPPING` / `MANAGE_NLP` / `MANAGE_USERS`; всё stateless на HTTP Basic.
- **Наблюдаемость**: Micrometer → Prometheus; метрики стадий конвейера
  `avp_pipeline_stage_seconds{stage}` и метрики NLP-сервиса; готовый дашборд Grafana.

## API nlp-service

Python-сервис (порт 8001). Бэкенд ходит в него через `NlpServiceClient`,
но вызывать можно и напрямую. Формат — JSON.

| Запрос | Назначение |
|---|---|
| `POST /classify` | Классификация «третье лицо»: gate по триггер-словам → проверка отрицания → косинусное сходство с эталонами. Ответ: `isThirdParty`, `score`, `decision` |
| `POST /extract-name` | Извлечение ФИО (NER Natasha): PER-спаны, склейка соседних, ближайший к маркеру, нормализация в именительный падеж. Ответ: `fullName` |
| `POST /parse` | Разбор назначения целиком: `third_party`, `beneficiary_name`, `confidence` |
| `GET /examples` | Эталонные positive / negative примеры классификатора |
| `GET /health` | Проверка живости |

`decision` в `/classify`: `GATED` — триггер-слово не найдено; `NEGATED` — рядом
с триггером обнаружено отрицание; `SEMANTIC` — решено по сходству. В запросе
можно передать свои `threshold` / `triggerWords` / `negationWords` — бэкенд
подставляет их из настроек `nlp_settings` (меняются через админку без деплоя).
Метрики — `GET /metrics` (Prometheus).

## Где что смотреть

| Тема | Документ |
|---|---|
| Правила маппинга, скоринг, решения | [mapping.md](mapping.md) |
| Схема БД (V1–V9) | [database.md](database.md) |
| REST-контракт, роли | [api-contract.md](api-contract.md) |
| Матрица прав и статус реализации | [access-matrix.md](access-matrix.md) |
| Нагрузочное тестирование | [load-testing.md](load-testing.md) |
