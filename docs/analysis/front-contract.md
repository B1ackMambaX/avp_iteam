---

# API avp_iteam — контракт для фронтенда

## Базовая информация

- **Base URL:** `http://localhost:8080`
- **Формат:** JSON, `Content-Type: application/json`
- **Кодировка:** UTF-8
- **Аутентификация:** HTTP Basic (логин/пароль в заголовке `Authorization`)
- **Сессии:** stateless, cookie не используются

---

## 1. Роли и права

| Право | Что даёт | READER | EDITOR | ADMIN |
|---|---|:-:|:-:|:-:|
| `VIEW_DATA` | Смотреть операции, договоры, результаты маппинга, отчёты | ✓ | ✓ | ✓ |
| `EDIT_MAPPING` | Ручная привязка и исправление | – | ✓ | ✓ |
| `MANAGE_NLP` | Настройки NLP (порог, слова, тесты) | – | – | ✓ |
| `MANAGE_USERS` | Управление пользователями | – | – | ✓ |

- Кнопка «Админка» видна, если `canSeeAdminPanel = true` (только у `ADMIN`).
- Настоящая проверка прав — на сервере (403).

---

## 2. Текущий пользователь

**`GET /api/me`** — любой авторизованный

```json
{
  "username": "admin",
  "role": "ADMIN",
  "permissions": ["EDIT_MAPPING", "MANAGE_NLP", "MANAGE_USERS", "VIEW_DATA"],
  "canSeeAdminPanel": true
}
```

На старте приложения вызови этот эндпоинт — он даёт всё, чтобы построить UI под роль.

---

## 3. Общие правила

- **Идентификатор договора:** всегда `contractId` (Long). Номер договора не уникален, в ссылках не использовать.
- **Пагинация:** `?page=0&size=50` → ответ `{ "content": [...], "page": 0, "size": 50, "totalElements": 1234 }`
- **Ошибки — RFC 7807 (`ProblemDetail`):**
    - `400` — невалидные данные (тело содержит `errors: [{ "field": "...", "message": "..." }]`)
    - `401` — не авторизован (в ответе `WWW-Authenticate: Basic`)
    - `403` — нет права
    - `404` — не найдено
    - `409` — конфликт (например, дубликат логина или версия настроек устарела)
    - `503` — NLP-сервис недоступен

Пример ошибки:

```json
{ "type":"about:blank", "title":"Forbidden", "status":403, "detail":"Insufficient permissions" }
```

- **Время:** ISO-8601 UTC (`2025-01-15T10:20:30Z`).

---

## 4. Операции и маппинг (`VIEW_DATA`)

| Метод | Путь | Назначение |
|---|---|---|
| GET | `/api/operations?status=&from=&to=&q=&page=&size=` | Список операций с фильтрами |
| GET | `/api/operations/{id}/mapping` | Детали маппинга одной операции |
| GET | `/api/review-queue?status=AMBIGUOUS\|MANUAL_REQUIRED&page=&size=` | Очередь на ручной разбор |
| GET | `/api/contracts?q=&page=&size=` | Список договоров |

**Статусы операции:** `PENDING`, `WAITING_NLP`, `PARSED`, `AUTO_MAPPED`, `AMBIGUOUS`, `NOT_FOUND`, `MANUAL_REQUIRED`.

**Элемент списка операций:**

```json
{
  "operationId": 600001,
  "date": "2025-01-15",
  "amount": 15000.00,
  "senderName": "ООО Ромашка",
  "receiverName": "ИП Иванов",
  "paymentPurpose": "Оплата по договору №123...",
  "status": "AMBIGUOUS",
  "bestScore": 0.45,
  "candidatesCount": 3
}
```

**Детали маппинга одной операции** (`/api/operations/{id}/mapping`):

```json
{
  "operation": {
    "id": 600001,
    "payment_purpose": "Оплата по договору №123, выгодоприобретатель...",
    "status": "AUTO_MAPPED",
    "mapping_reason": "CONFIDENT_MATCH",
    "contract_id": 123456,
    "contract_number": "ДГ-00012345"
  },
  "parse": {
    "is_third_party": true,
    "is_genuine_third_party": true,
    "extracted_name": "Иванов Иван Иванович",
    "classification_method": "NLP_NER",
    "classification_score": 0.85
  },
  "candidates": [
    {
      "contract_id": 123456,
      "contract_number": "ДГ-00012345",
      "score": 0.8,
      "matched_rules": {"PARTY_RECEIVER": 0.4, "CONTRACT_NUMBER": 0.4},
      "parties": "BENEFICIARY: Иванов И.И. (ИНН 5000...); COUNTERPARTY: ООО Ромашка (ИНН 7700...)"
    }
  ]
}
```

---

## 5. Ручное подтверждение (`EDIT_MAPPING`)

**`POST /api/operations/{id}/mapping/resolve`**

```json
{ "contractId": 123456, "comment": "Подтверждено по ИНН" }
```

- `contractId: null` = «договор не найден, отклонить».
- Возвращает обновлённые детали маппинга.
- Бэкенд сохраняет `decidedBy`, `decidedAt`, `source=MANUAL`.

---

## 6. Админка: пользователи (`MANAGE_USERS`)

| Метод | Путь | Назначение |
|---|---|---|
| GET | `/api/admin/users` | Список пользователей |
| POST | `/api/admin/users` | Создать пользователя |
| PATCH | `/api/admin/users/{id}` | Сменить роль / блокировать |
| POST | `/api/admin/users/{id}/reset-password` | Сбросить пароль |

**Создание** — тело:

```json
{ "username": "anna", "role": "EDITOR", "password": null }
```

- Если `password` пустой — сервер сгенерирует и вернёт **один раз** в ответе:

```json
{
  "user": { "id": 7, "username": "anna", "role": "EDITOR", "enabled": true },
  "generatedPassword": "Tp-xYz123..."
}
```

**Изменение** — тело:

```json
{ "role": "READER", "enabled": false }
```

**Сброс пароля** — тело (можно `null`, тогда сервер сгенерирует):

```json
{ "password": "newStrongPass123" }
```

**Правила:**
- Нельзя менять свою роль и блокировать себя (409).
- Нельзя лишить прав последнего активного админа (409).
- Пароль: минимум 10 символов, максимум 72 байта.
- После блокировки — следующий запрос пользователя вернёт 401.

---

## 7. Админка: настройки NLP (`MANAGE_NLP`)

| Метод | Путь | Назначение |
|---|---|---|
| GET | `/api/admin/nlp/settings` | Текущие настройки |
| PUT | `/api/admin/nlp/settings` | Сохранить (новая версия) |
| POST | `/api/admin/nlp/settings/reset` | Сбросить к дефолту |
| GET | `/api/admin/nlp/settings/history` | История изменений |
| POST | `/api/admin/nlp/settings/rollback/{version}` | Откатить к версии |
| POST | `/api/admin/nlp/test` | Тестовый прогон текста (не сохраняется) |
| GET | `/api/admin/nlp/examples` | Эталонные примеры (read-only) |

**Текущие настройки:**

```json
{
  "version": 3,
  "threshold": 0.5,
  "triggerWords": ["треть*", "лиц*", "польз*", ...],
  "negationWords": ["не", "нет", "без"],
  "changedBy": "admin",
  "changedAt": "2025-01-15T10:20:30Z"
}
```

**PUT** — обязательно передать `version`, которую получил через GET. Если на сервере уже новая версия — вернётся **409** с `currentVersion`, обнови и попроси повторить:

```json
{ "version": 3, "threshold": 0.55, "triggerWords": [...], "negationWords": [...], "comment": "Поднял порог" }
```

**Тестовый прогон** (`/test`) — админ проверяет, что даст текст, не сохраняя:

```json
// запрос
{ "text": "Оплата в пользу Иванова Ивана Ивановича" }

// ответ
{
  "settingsVersion": 3,
  "overridden": false,
  "threshold": 0.5,
  "decision": "SEMANTIC",
  "score": 0.81,
  "thirdParty": true,
  "extractedName": "Иванов Иван Иванович"
}
```

**Если админ передаёт черновые настройки** (до сохранения):

```json
{ "text": "...", "threshold": 0.9, "triggerWords": ["фидуциар*"] }
```

Тогда в ответе `"overridden": true`. Можно делать превью «что будет, если сохранить».

**Примеры** (`/examples`) — read-only, для UI-подсказок админу:

```json
{ "positive": ["..."], "negative": ["..."] }
```

---

## 8. Служебные

| Метод | Путь | Назначение |
|---|---|---|
| POST | `/api/pipeline/run` | Запуск конвейера (**только ADMIN**) |
| POST | `/api/demo/reset` | Сброс демо-операций (**только ADMIN**) |
| GET | `/api/demo/report` | Отчёт по точности (`VIEW_DATA`) |
| GET | `/actuator/health` | Проверка живости (без авторизации) |

---

## 9. Рекомендованный порядок экранов

1. **Логин** → `GET /api/me`, сохраняем `username`, `role`, `permissions`, `canSeeAdminPanel`.
2. **Главный экран** → `GET /api/operations?status=PENDING&page=0&size=50` (для роли `VIEW_DATA`).
3. **Очередь на ручной разбор** → `GET /api/review-queue?status=AMBIGUOUS` + `MANUAL_REQUIRED`.
4. **Карточка операции** → `GET /api/operations/{id}/mapping`, кнопка «Подтвердить» (только `EDIT_MAPPING`).
5. **Настройки NLP** (только `canSeeAdminPanel`) → `GET /api/admin/nlp/settings`, слайдер порога, редактор слов, кнопка «Тест» (`POST /api/admin/nlp/test`), «Сохранить» (`PUT`).
6. **Пользователи** (только `canSeeAdminPanel`) → `GET /api/admin/users`, создание, изменение роли, блокировка, сброс пароля.

---

## 10. Тестовые учётные данные

На стенде `demo` при старте создаются три пользователя (пароли из env-переменных `AVP_ADMIN_PASSWORD`, `AVP_EDITOR_PASSWORD`, `AVP_READER_PASSWORD`):

| Логин | Роль | Что видит |
|---|---|---|
| `admin` | ADMIN | Всё, включая админку |
| `editor` | EDITOR | Данные + ручное подтверждение маппинга |
| `reader` | READER | Только просмотр |

---

## 11. Что пока не реализовано (следующие этапы)

- REST для экспорта отчётов (CSV/Excel) — в плане.
- WebSocket для обновления статуса операций в реальном времени — в плане.
- Vaadin-views будут ходить в эти сервисы напрямую (без REST), но DTO те же — их можно использовать как контракт.

---

## Вопросы для фронтендера

1. **Vaadin Flow или отдельный React SPA?** Если Vaadin — REST-контракт используется для Postman/тестов, а UI-компоненты вызывают сервисы напрямую. Если React — REST полностью боевой.
2. **Как часто дергать `/api/me`?** Сейчас Basic Auth на каждый запрос. Если фронт будет активно ходить — переключим на сессию или JWT.
3. **Нужен ли `/api/operations/{id}` (детали операции без маппинга)?** Сейчас детали отдаются вместе с маппингом через `/mapping`.