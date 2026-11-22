---
title: Статусы операций и корзины бизнес-дашборда
status: актуально
---
# Статусы `operations` и корзины дашборда `/api/stats/summary` (задача 1, шаг 0)

Снято с базы после миграций V1–V11 (`information_schema.columns`, `pg_constraint`).

## Колонки `operations`

| Колонка | Тип | NULL | По умолчанию | Для дашборда |
|---|---|:-:|---|---|
| `id` | bigint | нет | `nextval('operation_seq')` | |
| `reference` | varchar | нет | | |
| `operation_date` | timestamp | нет | | дата операции из АБС, **не** дата загрузки |
| `sender_pin`, `sender_branch`, `sender_inn`, `sender_account`, `sender_name` | varchar | да | | |
| `receiver_pin`, `receiver_branch`, `receiver_inn`, `receiver_account`, `receiver_name` | varchar | да | | |
| `currency_code` | varchar | нет | `'643'` | |
| `amount`, `amount_rub` | numeric | нет | | |
| `payment_purpose` | text | да | | |
| `status` | varchar | нет | `'PENDING'` | **корзина** |
| `contract_id` | bigint | да | | |
| `created_at` | timestamptz | нет | `now()` | **день загрузки** (уже есть, миграция не нужна) |
| `updated_at` | timestamptz | да | | |
| `mapped_by` | varchar | да | | кто привязал |
| `mapped_at` | timestamptz | да | | когда привязал |
| `mapping_reason` | varchar | да | | причина решения маппинга |
| `mapping_source` | varchar | да | | **новая (V10)**: `AUTO` / `MANUAL` |

`status` — CHECK (V12): `PENDING`, `WAITING_NLP`, `PARSED`, `AUTO_MAPPED`, `MANUAL_MAPPED`, `MANUAL_REQUIRED`, `AMBIGUOUS`, `NOT_FOUND`. Других значений в базе быть не может. До V12 статусов было 7; `MANUAL_MAPPED` добавлен для ручного подтверждения (BE-4).

## Как отличить «вручную» от «автоматически»

Автопривязка **пишет** `mapped_by`: `MappingJdbcRepository.updateOperations` ставит `mapped_by = 'AUTO'` при найденном договоре. Ручное решение по `access-matrix.md` (п. 4.1) пишет в `mapped_by` логин оператора. Логин допускает `[A-Za-z0-9._-]{3,64}` (`UserAdminService.USERNAME`), то есть пользователь может называться `AUTO`, — **по `mapped_by` различить нельзя**.

Решение — явные признаки:

1. **Колонка `mapping_source`** (V10, CHECK `AUTO` / `MANUAL`) — источник последнего решения по привязке. Маппинг пишет `AUTO` вместе с `contract_id` и `NULL`, если договора нет; ручное решение — `MANUAL`. Существующие строки: `AUTO` там, где `contract_id IS NOT NULL AND mapped_by = 'AUTO'`. Почему колонка, а не значение `SYSTEM` в `mapped_by`: зарезервированное значение пришлось бы запрещать в логинах, а `mapped_by` по матрице доступа — автор действия.
2. **Статус `MANUAL_MAPPED`** (V12, согласовано в BE-4) — ручное подтверждение договора. Ручное отклонение даёт `NOT_FOUND` с `mapping_source = 'MANUAL'`.

Поэтому корзины дашборда определяются **только статусом**: каждый из 8 статусов попадает ровно в одну корзину, сумма корзин всегда равна `loaded`. `mapping_source` дашборду не нужен — он для карточки и аудита (вместе с историей `operation_manual_decision`).

## Корзины

| Поле ответа | Условие (среди операций дня) |
|---|---|
| `loaded` | все операции с `created_at` в `[date, date + 1)` в поясе `stats.time-zone` |
| `inProgress` | `PENDING`, `WAITING_NLP`, `PARSED` |
| `autoMapped` | `AUTO_MAPPED` |
| `manuallyMapped` | `MANUAL_MAPPED` |
| `awaitingManual.ambiguous` | `AMBIGUOUS` |
| `awaitingManual.manualRequired` | `MANUAL_REQUIRED` |
| `notFound` | `NOT_FOUND` — и автоматический, и ручное отклонение «договор не найден» |
| `autoShare` | `autoMapped / (loaded − inProgress)`, 4 знака; 0, если знаменатель 0 |
| `backlogAwaitingManual` | `AMBIGUOUS` + `MANUAL_REQUIRED` **за все дни** |

- `NOT_FOUND` — не «ожидает ручной привязки»: договор не найден (системой или оператором), разбирать нечего.
- День загрузки — `created_at` (момент записи в нашу БД), а не `operation_date` (дата операции в АБС): дашборд показывает, что система сделала с поступившим за день.
- Часовой пояс — `stats.time-zone` (по умолчанию `Europe/Moscow`, переменная `STATS_TIME_ZONE`), а не пояс машины или сессии БД: границы дня считаются в Java и передаются в запрос как моменты времени.
- Кеш — 30 с по ключу даты; сбрасывается после завершения `pipeline/run`, `/api/demo/reset` (после коммита) и ручного решения.

## Производительность (120 000 операций за день, x10)

Ответ эндпоинта: первый вызов без кеша 188 мс (включая прогрев), повторные 87–95 мс, из них ≈80 мс — проверка пароля BCrypt при Basic-аутентификации на каждом запросе. Сам запрос — 24 мс, обе части — index-only scan по индексам V11:

```
Nested Loop (actual time=23.822..23.898 rows=1 loops=1)
  Buffers: shared hit=4882
  ->  Finalize Aggregate (actual time=20.610..20.684 rows=1 loops=1)
        ->  Gather (Workers Launched: 2)
              ->  Partial Aggregate (actual time=19.380..19.380 rows=1 loops=3)
                    ->  Parallel Index Only Scan using idx_operation_created_at on operations
                          (actual time=0.012..14.293 rows=40000 loops=3)
                          Index Cond: ((created_at >= '2026-11-21 21:00:00+00') AND (created_at < ('2026-11-21 21:00:00+00' + '1 day')))
                          Heap Fetches: 12931
                          Buffers: shared hit=4053
  ->  Aggregate (actual time=3.210..3.210 rows=1 loops=1)
        ->  Index Only Scan using idx_operation_awaiting_manual on operations operations_1
              (actual time=0.024..2.429 rows=27933 loops=1)
              Heap Fetches: 3974
              Buffers: shared hit=823
Planning Time: 0.548 ms
Execution Time: 23.939 ms
```

Реальный ответ после прогона x10 (профиль `demo`, все операции загружены сегодня):

```json
{"date":"2026-11-22","demo":true,"loaded":120000,"inProgress":0,"autoMapped":77067,"manuallyMapped":0,"awaitingManual":{"ambiguous":24979,"manualRequired":2954},"notFound":15000,"autoShare":0.6422,"backlogAwaitingManual":27933}
```
