import json
import urllib.request
from urllib.error import HTTPError

BASE = "http://localhost:8080/api/admin/nlp/settings"


def call(method, url, body=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body else None
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Content-Type": "application/json; charset=utf-8"})
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8"))


# 1. Получить текущие настройки
status, s = call("GET", BASE)
print(f"start: v{s['version']} t={s['threshold']} trig={len(s['triggerWords'])} neg={len(s['negationWords'])}")

# 2. Обновить порог (правильный запрос)
body = {
    "version": s["version"],
    "threshold": 0.55,
    "triggerWords": s["triggerWords"],
    "negationWords": s["negationWords"],
    "comment": "test from python",
}
status, r = call("PUT", BASE, body)
print(f"put ok: v{r['version']} t={r['threshold']}")

# 3. Устаревшая версия → 409
body_stale = dict(body, version=s["version"], threshold=0.6)
status, err = call("PUT", BASE, body_stale)
print(f"stale version: {status}")

# 4. Невалидные данные → 400
body_bad = {"version": r["version"], "threshold": 1.5,
            "triggerWords": ["a"], "negationWords": []}
status, err = call("PUT", BASE, body_bad)
print(f"bad input: {status}")

# 5. История
status, hist = call("GET", BASE + "/history")
print(f"history: {len(hist)}")

# 6. Откат к версии 1 → 200
status, z = call("POST", BASE + "/rollback/1")
print(f"rollback: v{z['version']} t={z['threshold']}")

# 7. Несуществующая версия → 404
status, err = call("POST", BASE + "/rollback/99")
print(f"no such version: {status}")