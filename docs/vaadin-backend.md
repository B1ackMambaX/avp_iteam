---
title: Бэкенд для экранов Vaadin «Операции» и «Ручной разбор»
status: актуально
---
# Бэкенд для экранов Vaadin «Операции» и «Ручной разбор» (BE-1 … BE-4)

Vaadin Views вызывают Spring-сервисы напрямую. Права проверяются в самих сервисах (`@PreAuthorize`, `@EnableMethodSecurity`), поэтому View должна работать под аутентифицированным пользователем Spring Security. Для Postman и тестов те же операции доступны по REST (раздел «REST»).

| Сервис | Метод | Право |
|---|---|---|
| `review.OperationQueryService` | `PageResult<OperationRow> find(OperationFilter filter)` | `VIEW_DATA` |
| `review.OperationQueryService` | `PageResult<OperationRow> reviewQueue(Set<OperationStatus> statuses, LocalDate from, LocalDate to, String q, Integer page, Integer size)` | `VIEW_DATA` |
| `service.mapping.MappingQueryService` | `Optional<MappingCard> findCard(long operationId)` | `VIEW_DATA` |
| `review.ContractSearchService` | `PageResult<ContractRow> search(String q, Integer page, Integer size)` | `VIEW_DATA` |
| `review.ManualDecisionService` | `MappingCard resolve(ResolveCommand cmd)` | `EDIT_MAPPING` |

Без аутентификации — `AuthenticationCredentialsNotFoundException`, без права — `AccessDeniedException` (Spring Security).

## BE-1. Список операций и очередь ручного разбора

`PageResult<T>(List<T> content, int page, int size, long totalElements)` — страницы с 0, `totalElements` считается с теми же фильтрами.

`OperationFilter(Set<OperationStatus> statuses, LocalDate from, LocalDate to, String q, Integer page, Integer size)`; `OperationFilter.all()` — без фильтров.

| Параметр | Правило |
|---|---|
| `statuses` | один или несколько; пусто / `null` — все |
| `from`, `to` | дата операции (`operation_date`), **обе включительно**; `null` — без границы |
| `q` | подстрока без учёта регистра в назначении платежа, имени отправителя, имени получателя **или** точное совпадение с ИНН отправителя, ИНН получателя, `reference`; `%` и `_` ищутся как символы; до 200 символов |
| `page` | ≥ 0, по умолчанию 0 |
| `size` | 1…200, по умолчанию 50 |

Сортировка: дата операции по убыванию, затем `operationId` по убыванию — порядок стабилен при одинаковой дате, страницы не пересекаются на неизменных данных.

Очередь: `reviewQueue(...)` — та же выборка с `statuses` из `AMBIGUOUS`, `MANUAL_REQUIRED` (по умолчанию обе в одной выборке). Другой статус — ошибка валидации поля `status`.

`OperationRow(long operationId, LocalDate date, LocalDateTime operationDate, BigDecimal amount, String currencyCode, String senderName, String receiverName, String paymentPurpose, OperationStatus status, BigDecimal bestScore, int candidatesCount)` — для операции без кандидатов `bestScore = null`, `candidatesCount = 0`. Оценки считаются тем же SQL для всей страницы, без запроса на строку.

Ошибки параметров — `ValidationFailedException`, `errors()`: поле → сообщение (`from` позже `to`, `page < 0`, `size` вне 1…200, длинный `q`).

## BE-2. Карточка маппинга

`MappingQueryService.findCard(operationId)` → `Optional<MappingCard>`; `Optional.empty()` — операции нет. Старый `find(operationId)` на `Map<String, Object>` не менялся (его использует REST `GET /api/operations/{id}/mapping`).

`MappingCard(OperationInfo operation, ParseInfo parse, List<CandidateInfo> candidates)`:

- `OperationInfo` — `id`, `reference`, `operationDate` (`LocalDateTime`), ИНН и имена сторон, `amount` (`BigDecimal`), `currencyCode`, `paymentPurpose`, `status` (`OperationStatus`), `mappingReason`, `contractId` (`Long`), `contractNumber`, `mappedBy`, `mappedAt` (`OffsetDateTime`), `mappingSource` (`AUTO` / `MANUAL` / `null`), **`version`** (`long`) — передаётся обратно в ручное решение.
- `ParseInfo` — `parser`, `parserVersion`, `classificationMethod`, `classificationScore` (`Double`), `thirdParty`, `genuineThirdParty` (`Boolean`), `extractedName`, `extractedNameNorm`, `partyRole`, `parsedAt`. **`parse = null`** — назначение ещё не разобрано.
- `CandidateInfo` — `contractId`, `contractNumber`, `contractType`, `score` (`BigDecimal`), **`matchedRules`: `Map<MatchRule, Double>`** (правило скоринга → вес; `MatchRule` — `PARTY_SENDER`, `PARTY_RECEIVER`, `BOTH_SIDES`, `CONTRACT_NUMBER`, `BENEFICIARY_NAME`), `parties` — `List<PartyInfo(role, clientId, name, inn)>`. **Пустой список** — кандидатов нет.

Кандидаты — по убыванию `score`, затем по `contractId`. Их до 20 (`mapping.max-stored-candidates`, было 10) — список кандидатов на экране должен листаться.

## BE-3. Поиск договора

`ContractSearchService.search(q, page, size)` → `PageResult<ContractRow(long contractId, String contractNumber, String contractType, List<PartyInfo> parties)>`.

| Поле | Совпадение |
|---|---|
| Номер договора | точное после нормализации, как в маппинге: регистр, пробелы, дефисы, `№`, `/` не важны (`дг 00000124` = `ДГ-00000124`) |
| ИНН стороны | точное, если `q` — 10 или 12 цифр; любая роль (контрагент, агент, выгодоприобретатель) |

Подстрочного поиска нет (оба поиска идут по индексам на 1 млн+ договоров). Пустой `q` — ошибка валидации. Сортировка: номер, затем `contractId`. Договоры с одинаковым номером различаются по `parties`. В ручное решение передаётся `contractId`.

**Выбор договора вне списка кандидатов разрешён** (согласовано), но только с комментарием — поиск используется в сценарии ручного разбора.

## BE-4. Ручное решение

`ManualDecisionService.resolve(ResolveCommand(long operationId, Long contractId, String comment, Long expectedVersion))` → обновлённая `MappingCard`.

### Согласованные правила

| Действие | Из статусов | Итоговый статус | `contract_id` | `mapping_source` |
|---|---|---|---|---|
| Подтверждение (`contractId != null`) | `AMBIGUOUS`, `MANUAL_REQUIRED` | **`MANUAL_MAPPED`** (новый) | выбранный | `MANUAL` |
| Отклонение (`contractId = null`, «договор не найден») | `AMBIGUOUS`, `MANUAL_REQUIRED` | `NOT_FOUND` | `NULL` | `MANUAL` |

- Принятое решение (ручное или `AUTO_MAPPED`) не исправляется — это была бы отмена привязки, её в этой поставке нет.
- Комментарий: до 1000 символов; **обязателен** при отклонении и при договоре не из кандидатов; иначе необязателен.
- Автор — из Spring Security, время — момент сохранения; всё сохраняется в одной транзакции вместе с записью в `operation_manual_decision` (история: действие, договор, из кандидатов или нет, прежний статус, комментарий, автор, время).
- Конвейер решение не перезаписывает: он берёт только `PENDING` и `PARSED`.
- После решения операция уходит из очереди и видна в списке со своим статусом.

### Ошибки (различимы по типу и коду)

| Ситуация | Исключение | REST |
|---|---|---|
| Нет права `EDIT_MAPPING` | `AccessDeniedException` | 403 |
| Нет операции | `ManualDecisionException`, `code = OPERATION_NOT_FOUND` | 404 |
| Нет договора | `ManualDecisionException`, `CONTRACT_NOT_FOUND` | 404 |
| Операция не в очереди (уже решена, в обработке, `AUTO_MAPPED`) | `ManualDecisionException`, `FORBIDDEN_TRANSITION` | 409 |
| Карточку изменили после прочтения (в т.ч. второй редактор) | `ManualDecisionException`, `STALE_CARD` | 409 |
| Договор не из кандидатов без комментария | `ManualDecisionException`, `CONTRACT_NOT_ALLOWED` | 400 |
| Нет обязательного комментария, комментарий длиннее 1000 | `ValidationFailedException`, поле `comment` | 400 |

**Устаревшая карточка.** UI передаёт `expectedVersion = card.operation().version()` из прочитанной карточки. Строка операции блокируется на время решения, поэтому второй редактор с той же версией получает `STALE_CARD`, первое решение сохраняется; экрану нужно перечитать карточку.

## REST (для Postman и тестов)

| Метод | Путь | Сервис |
|---|---|---|
| GET | `/api/operations?status=AMBIGUOUS&status=NOT_FOUND&from=2026-11-01&to=2026-11-09&q=&page=0&size=50` | `find` |
| GET | `/api/review-queue?status=&from=&to=&q=&page=&size=` | `reviewQueue` |
| GET | `/api/contracts?q=&page=&size=` | `search` |
| POST | `/api/operations/{id}/mapping/resolve` с телом `{"contractId": 123, "comment": "…", "version": 1763...}` | `resolve`, ответ — карточка |

Ошибки — RFC 7807: `{"status":409, "detail":"…", "code":"STALE_CARD"}`, у ошибок валидации — `"errors":[{"field":"comment","message":"…"}]`.

## Демо-данные для сквозной проверки

1. Профиль `demo` создаёт пользователей `admin`, `editor`, `reader` (пароли — `AVP_ADMIN_PASSWORD`, `AVP_EDITOR_PASSWORD`, `AVP_READER_PASSWORD`).
2. `GET /api/demo/samples` — ID операций для сценариев: `withoutParse` (нет разбора — до запуска конвейера), `multipleCandidates`, `toConfirm` (`AMBIGUOUS` с кандидатами), `toReject` (`MANUAL_REQUIRED`).
3. `POST /api/pipeline/run` — разобрать и смаппить; `POST /api/demo/reset` — вернуть демо-операции к началу (удаляет и ручные решения по ним).
