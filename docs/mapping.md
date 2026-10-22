# Модуль маппинга операций на договоры

## Конвейер

```
PENDING ──► WAITING_NLP ──► PARSED ──► AUTO_MAPPED | AMBIGUOUS | NOT_FOUND | MANUAL_REQUIRED
   │         (очередь nlp_task,  ▲
   │          BeneficiaryDetectionService)
   └──── режим marker: regex-парсер в процессе ────┘
```

| Шаг | Класс | Что делает |
|---|---|---|
| Приём | `IntakeService` | `nlp`: ставит PENDING-операции в `nlp_task`, статус WAITING_NLP. `marker`: сразу разбирает назначение `RegexPaymentPurposeParser`, статус PARSED |
| Разбор (NLP) | `NlpTaskWorker` | Забирает задачи `FOR UPDATE SKIP LOCKED`, вызывает `BeneficiaryDetectionService.detect` (параллельно, `pipeline.nlp.parallelism` запросов), пишет `operation_parse_result`, статус PARSED. Ошибка - повтор с паузой `retry-delay-seconds × попытка`; после `max-attempts` задача FAILED, операция всё равно идёт в маппинг (без автопривязки). Отложенные на повтор задачи подбирает следующий запуск конвейера |
| Маппинг | `MappingService` | Пачка PARSED-операций: кандидаты, скоринг, решение, запись `operation_mapping_candidate` и итогового статуса |
| Запуск | `PipelineRunner` | Крутит шаги, пока есть работа. REST `POST /api/pipeline/run` или фоном (`pipeline.scheduler.enabled=true`) |

Все шаги забирают строки через `FOR UPDATE SKIP LOCKED`, поэтому безопасны при параллельном запуске (REST + планировщик, несколько инстансов).

## Как ищутся кандидаты

Правил сопоставления от заказчика пока нет, ниже - наше допущение. Веса и пороги - в `application.yml` (`mapping.*`).

На пачку из 500 операций - фиксированное число запросов, все по индексам:

1. **Стороны операции**: клиенты по `sender_inn` / `receiver_inn` → их договоры через `contract_parties` (любая роль). Клиент с договорами > `max-contracts-per-party` (200) не перебирается целиком - его договоры попадают в кандидаты только по номеру или ФИО.
2. **Номер договора** из назначения платежа (`ContractNumberExtractor`): ищется только после слов «договор» / «дог.», нормализуется как `contracts.contract_number_norm` (`№ ДГ 00000124` → `ДГ00000124`).
3. **Выгодоприобретатель**: если разбор назначения подтвердил платёж за третье лицо (`is_genuine_third_party`), `extracted_name_norm` сравнивается с `clients.full_name_norm` среди выгодоприобретателей договоров. ФИО с тёзками > `max-clients-per-name` (50) не используется.

## Скоринг

| Правило | Вес | Когда срабатывает |
|---|---|---|
| `PARTY_SENDER` / `PARTY_RECEIVER` | 0.4 | отправитель / получатель - сторона договора |
| `BOTH_SIDES` | +0.2 | обе стороны операции - стороны договора |
| `CONTRACT_NUMBER` | 0.4 | номер договора упомянут в назначении |
| `BENEFICIARY_NAME` | 0.3 | ФИО третьего лица = выгодоприобретатель договора |

Score = сумма, не больше 1. Сработавшие правила с весами сохраняются в `matched_rules` - оператор видит, почему договор предложен.

## Решение

| Условие (по порядку) | Статус | `mapping_reason` |
|---|---|---|
| кандидатов нет | NOT_FOUND | `NO_CANDIDATES` |
| score лидера ≥ 0.6 и отрыв от второго ≥ 0.2 | AUTO_MAPPED | `CONFIDENT_MATCH` |
| кандидат один, и сторона операции в нём участвует | AUTO_MAPPED | `SINGLE_CANDIDATE` |
| отрыв лидера < 0.2 | AMBIGUOUS | `CLOSE_CANDIDATES` |
| иначе (например, только номер без участия сторон) | MANUAL_REQUIRED | `WEAK_EVIDENCE` |
| автопривязка, но платёж за третье лицо, а выгодоприобретатель не совпал | MANUAL_REQUIRED | `THIRD_PARTY_UNCONFIRMED` |
| автопривязка, но назначение не разобрано (NLP недоступен) | MANUAL_REQUIRED | `PARSE_UNAVAILABLE` |

Платёж за третье лицо (115-ФЗ) автоматически привязывается только при подтверждённом выгодоприобретателе.

## REST

| Запрос | Что возвращает |
|---|---|
| `POST /api/pipeline/run?maxSeconds=600` | Прогон конвейера по всем новым операциям: сколько принято, разобрано, смаплено, скорость, статусы |
| `GET /api/pipeline/stats` | Операции по статусам и причинам, очередь NLP, объём справочника |
| `GET /api/operations/{id}/mapping` | Операция, результат разбора назначения, кандидаты со score, правилами и сторонами договора |
| `GET /api/demo/report` | (профиль `demo`) точность маппинга по сценариям |
| `POST /api/demo/reset` | (профиль `demo`) вернуть демо-операции в PENDING для повторного прогона |

## Как записать демо

1. Поднять БД: `docker-compose up -d`. Для чистого прогона - пустая база (`docker-compose down -v`, затем `up -d`).
2. Запустить NLP-сервис (`nlp-service`, порт 8001) - или пропустить и использовать режим `marker`.
3. Запустить бэкенд с профилем `demo`:
   ```bash
   cd backend
   mvn spring-boot:run -Dspring-boot.run.profiles=demo
   ```
   Без Python: добавить `-Dspring-boot.run.arguments=--pipeline.parser=marker`.
   При старте генерируется справочник и 10 000 операций (`application-demo.yml`). Объём меняется там же:
   `demo.contracts: 10000000`, `demo.clients: 3000000` - "как у заказчика" (генерация в Postgres займёт несколько минут и ~5 ГБ на диске).
4. Прогнать конвейер: `curl -X POST localhost:8080/api/pipeline/run`
5. Посмотреть результат:
   - `curl localhost:8080/api/demo/report` - точность по сценариям, `wrongAutoMapped` (привязки не к тому договору) должно быть 0;
   - `curl localhost:8080/api/pipeline/stats` - распределение по статусам;
   - `curl localhost:8080/api/operations/{id}/mapping` - разбор одной операции.
6. Повторить прогон: `curl -X POST localhost:8080/api/demo/reset`, затем снова шаг 4.

Сценарии демо-операций (`DemoScenario`), правильный ответ зашит в `reference` (`DEMO-{сценарий}-{ожидаемый статус}-{договор}-{номер}`):

| Сценарий | Доля | Ожидается |
|---|---|---|
| `NUMBER_AND_PARTY` - сторона операции участвует в договоре, номер в назначении | 35% | AUTO_MAPPED |
| `SINGLE_CONTRACT` - номера нет, у стороны один договор | 15% | AUTO_MAPPED |
| `AMBIGUOUS` - номера нет, у стороны несколько договоров | 10% | AMBIGUOUS |
| `THIRD_PARTY_NUMBER` - за третье лицо, номер + ФИО выгодоприобретателя | 10% | AUTO_MAPPED |
| `THIRD_PARTY_NAME` - за третье лицо, только ФИО (в т.ч. в родительном падеже) | 10% | AUTO_MAPPED |
| `FALSE_THIRD_PARTY` - «выгодоприобретатель» = сам плательщик (вопрос 6) | 5% | AUTO_MAPPED |
| `NOT_FOUND` - стороны не клиенты банка, номера нет в базе | 15% | NOT_FOUND |

В режиме `marker` часть сценария `THIRD_PARTY_NAME` уходит в MANUAL_REQUIRED: regex не приводит ФИО в родительном падеже к именительному. Это ожидаемо и наглядно показывает разницу с NLP.
