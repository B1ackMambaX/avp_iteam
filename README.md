# AVP ITeam — автоматическая привязка банковских операций к договорам

MVP системы привязки операций к договорам в рамках требований 115-ФЗ:
по назначению платежа конвейер определяет, проводится ли платёж за третье
лицо, извлекает ФИО выгодоприобретателя и связывает операцию с договором —
автоматически или через очередь ручного разбора.

## Проблема

Легаси-система заказчика распознаёт платежи за третье лицо по списку маркерных
слов («за», «в пользу» + имя в тексте). Такой список принципиально конечен:
на формулировках вне словаря recall падает до 30–40% — операции не
распознаются и оседают в ручном разборе (подтверждено заказчиком и
воспроизведено на holdout-выборке, см. [docs/nlp-hypothesis-result.md](docs/nlp-hypothesis-result.md)).

## Что умеет система

- **Семантическая классификация** «третье лицо / нет» — сравнение смысла
  фразы с эталонами (embedding), а не точных словоформ: ловит «в интересах»,
  «за счёт», «правопреемнику», «по доверенности» и т.д.
- **Извлечение ФИО** (NER, Natasha) с нормализацией падежей и женских фамилий
- **Сверка с данными операции**: извлечённое имя = плательщик или получатель →
  ложное срабатывание, платёж не считается «за третье лицо»
- **Скоринг кандидатов-договоров** по ИНН сторон, номеру договора из текста
  и выгодоприобретателю — с прозрачными причинами (`matched_rules`,
  `mapping_reason`): оператор видит, почему предложен договор
- **Очередь ручного разбора** для неоднозначных случаев (AMBIGUOUS /
  MANUAL_REQUIRED), ошибочных автопривязок нет
- **Роли** READER / EDITOR / ADMIN, админка пользователей и настройки
  NLP-классификатора без деплоя (порог, словари, версии, откат)
- **Мониторинг**: Prometheus + готовый дашборд Grafana, метрики стадий конвейера

## Результаты

| Метрика | Значение |
|---|---|
| Точность маппинга на демо-данных (7 сценариев + хаос-тексты) | ~95% |
| Ошибочные автопривязки (не к тому договору) | 0 |
| 120 000 операций (≈ сутки банка) на стенде из одной машины | ~14 минут |
| Скорость самого маппинга (линейна на объёме) | 1300–1450 оп/с |
| Классификация «третье лицо»: гибрид (правила + семантика) против маркерного подхода | F1 **94,1%** против 61,5% |

Детали и ограничения измерений: [docs/load-testing.md](docs/load-testing.md),
[docs/nlp-hybrid-results.md](docs/nlp-hybrid-results.md).

## Демо за 4 команды

Все `/api`-эндпоинты требуют HTTP Basic (кроме `/actuator/health`).
Перед стартом задайте `AVP_ADMIN_PASSWORD` — иначе пароль `admin`
сгенерируется и один раз напечатается в лог.

```bash
docker compose up -d                                                    # 1. PostgreSQL
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=demo \
    -Dspring-boot.run.arguments=--pipeline.parser=marker               # 2. бэкенд + демо-данные (без Python)
curl -u admin:$AVP_ADMIN_PASSWORD -X POST localhost:8080/api/pipeline/run   # 3. конвейер (только ADMIN)
curl -u admin:$AVP_ADMIN_PASSWORD localhost:8080/api/demo/report            # 4. отчёт точности
```

С Python-NLP (полный режим): поднять `nlp-service` (порт 8001) и убрать
`--pipeline.parser=marker`. Сценарии демо и расшифровка отчёта —
[docs/mapping.md](docs/mapping.md).

## Архитектура

Три компонента: **backend** (Java 21, Spring Boot 3.3.5, Vaadin 24 LTS —
конвейер, маппинг, REST, роли), **nlp-service** (Python 3.14+, FastAPI,
Natasha — классификация и извлечение ФИО) и **инфраструктура**
(PostgreSQL 16, Flyway, Prometheus, Grafana).

```
PENDING ──► WAITING_NLP ──► PARSED ──► AUTO_MAPPED | AMBIGUOUS | NOT_FOUND | MANUAL_REQUIRED
              очередь nlp_task        скоринг кандидатов, решение с причиной
```

Подробно: [docs/architecture.md](docs/architecture.md).

## Документация

Вся документация — в папке `docs/`, правится вместе с кодом.
Полный индекс со статусами документов — [docs/README.md](docs/README.md).

| Документ | О чём |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Архитектура: компоненты, конвейер обработки, ключевые решения, API nlp-service |
| [docs/database.md](docs/database.md) | Схема БД: таблицы, типы, индексы, история миграций V1–V9 |
| [docs/mapping.md](docs/mapping.md) | Модуль маппинга: кандидаты, скоринг, правила решений, REST конвейера |
| [docs/api-contract.md](docs/api-contract.md) | REST-контракт **для фронтенда**: роли, операции, админка NLP и пользователей (разделы 4–5 — ТЗ, не реализовано) |
| [docs/access-matrix.md](docs/access-matrix.md) | Матрица прав и ролей READER / EDITOR / ADMIN |
| [docs/load-testing.md](docs/load-testing.md) | Нагрузочное тестирование x1–x10: результаты, узкие места, открытые вопросы |
| [docs/benchmarks.md](docs/benchmarks.md) | Как запускать бенчмарки гипотез А (regex) и Б (NLP) |
| [docs/nlp-hypothesis-result.md](docs/nlp-hypothesis-result.md) | Исследования NLP: сравнение гипотез, гибрид, eval-сеты |

---

# Для разработчиков

## Быстрый старт

Требуется: JDK 21, Maven, Docker (Python 3.14+ — только для NLP-режима).

1. Поднять БД и мониторинг: `docker compose up -d` (Postgres 5432,
   Prometheus 9090, Grafana 3000)
2. Перед первым стартом задать пароль администратора `AVP_ADMIN_PASSWORD`
   (иначе сгенерируется и один раз напечатается в лог). При пустой таблице
   `app_user` создаётся `admin`; `editor` и `reader` — дополнительно,
   только в профиле `demo` (`AVP_EDITOR_PASSWORD`, `AVP_READER_PASSWORD`)
3. Запустить приложение:
   - IntelliJ: класс `com.bank.avpiteam.AvpIteamApplication` → Run
   - терминал: `cd backend && mvn spring-boot:run`

После обычного старта таблицы пустые — тестовые данные генерируются
профилем `demo` или `datagen` (следующий раздел).

В логах успешного старта: `Flyway ... Successfully applied 8 migrations` (V6 пропущена),
затем `Started AvpIteamApplication`. Приложение — [http://localhost:8080](http://localhost:8080).

## Переменные окружения

| Переменная | По умолчанию | Зачем |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/avp_iteam` | Подключение к PostgreSQL |
| `DB_USER` / `DB_PASSWORD` | `avp_iteam_user` / `avp_iteam_pass` | Учётная запись БД |
| `PIPELINE_PARSER` | `nlp` | Режим разбора: `nlp` (Python-сервис) или `marker` (regex, без Python) |
| `PIPELINE_SCHEDULER` | `false` | Фоновый прогон конвейера планировщиком (без ручного `POST /api/pipeline/run`) |
| `NLP_SERVICE_URL` | `http://localhost:8001` | Адрес nlp-service |
| `NLP_SEMANTIC_THRESHOLD` | `0.5` | Резервный порог семантики на случай недоступности `nlp_settings`; рабочее значение берётся из `nlp_settings` |
| `AVP_ADMIN_PASSWORD` | пусто → генерируется | Пароль `admin`; создаётся при пустой `app_user` |
| `AVP_EDITOR_PASSWORD`, `AVP_READER_PASSWORD` | пусто → генерируются | Пароли `editor` / `reader`; создаются только в профиле `demo` |
| `SECURITY_METRICS_ALLOWED_CIDRS` | loopback + приватные сети | CIDR, с которых доступен `/actuator/prometheus` (для прода нужен basic-auth, см. [load-testing.md](docs/load-testing.md)) |
| `DEMO_OPERATIONS` | `10000` | Число демо-операций в профиле `demo` (также `DEMO_CLIENTS`, `DEMO_CONTRACTS`, `DEMO_SEED`) |
| `AVP_RUN_QUERY` | пусто | Нагрузочные скрипты (`nlp-service/eval`): query к `/api/pipeline/run`, напр. `maxSeconds=1800` |
| `AVP_EXPECTED_CONTRACTS` | `1000000` | Нагрузочные скрипты: ожидаемый размер справочника для самопроверки замера |
| `AVP_BASE`, `AVP_PROM` | `:8080`, `:9090` | Нагрузочные скрипты: адреса бэкенда и Prometheus |
| `GRAFANA_ADMIN_PASSWORD` | `admin` | Пароль администратора Grafana (docker-compose) |

## Тестовые данные

Актуальный путь — профиль `demo` (как в демо выше): генерирует справочник
и 10 000 операций. Дефолты в `application-demo.yml`: 300 000 клиентов,
1 000 000 договоров; для объёма «как у заказчика» значения поднимают
(`demo.contracts: 10000000`, `demo.clients: 3000000` — генерация в Postgres
займёт несколько минут и ~5 ГБ). Повторный запуск безопасен: генератор
пропускает заполненные таблицы. Повторный прогон конвейера —
`POST /api/demo/reset`.

Альтернативный генератор — профиль `datagen` (10 000 клиентов / 100 000
договоров / 1 000 операций, настраивается в `application-datagen.yml`),
использовался на этапе сравнения гипотез; для демо не нужен:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=datagen
```

## NLP-сервис (полный режим)

```bash
cd nlp-service
python -m venv venv && venv\Scripts\activate    # Linux/macOS: source venv/bin/activate
pip install -r requirements.txt                 # Natasha скачает модели, это надолго
uvicorn app.main:app --host 0.0.0.0 --port 8001
```

Адрес настраивается переменной `NLP_SERVICE_URL` (по умолчанию
`http://localhost:8001`). Без Python работает режим
`--pipeline.parser=marker` (regex-парсер): дёшево и быстро, но не приводит
ФИО из родительного падежа к именительному — разница подходов видна на
сценарии `THIRD_PARTY_NAME`.

## Тесты

```bash
cd backend && mvn test       # Java: маппинг, извлечение номера, security, настройки NLP (11 классов)
cd nlp-service && pytest     # Python: gate-паттерны, нормализатор ФИО, полнота имени
```

Сценарные проверки REST (нужен поднятый стенд): `nlp-service/eval/check_users_api.py`,
`nlp-service/eval/nlp_settings_check.py` — см. [docs/load-testing.md](docs/load-testing.md).

## Бенчмарки гипотез А/Б

Точность и производительность regex- и NLP-парсеров на размеченных датасетах:
[docs/benchmarks.md](docs/benchmarks.md), результаты —
[docs/nlp-hypothesis-result.md](docs/nlp-hypothesis-result.md).

## Схема БД

PostgreSQL 16, Flyway-миграции V1–V9 (`backend/src/main/resources/db/migration/`, V6 не используется).
Таблицы, статусы, связи, индексы и ER-диаграммы — [docs/database.md](docs/database.md).

## Структура репозитория

```
avp_iteam/
├── backend/       # Java/Spring Boot/Vaadin: конвейер, маппинг, REST, роли
├── nlp-service/   # Python/FastAPI/Natasha: классификация, NER, eval-скрипты
├── data/          # размеченные датасеты (основной + holdout)
├── docs/          # вся документация проекта (индекс: docs/README.md)
├── monitoring/    # Prometheus + дашборд Grafana
└── docker-compose.yml
```
