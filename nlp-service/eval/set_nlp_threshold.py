import json
import sys
import urllib.request
from avp_auth import basic_auth_header

URL = "http://localhost:8080/api/admin/nlp/settings"

# Получаем текущие настройки
cur = json.load(urllib.request.urlopen(urllib.request.Request(URL, headers=basic_auth_header())))
body = {k: cur[k] for k in ("version", "threshold", "triggerWords", "negationWords")}
body["threshold"] = float(sys.argv[1])

req = urllib.request.Request(
    URL,
    json.dumps(body).encode("utf-8"),
    {"Content-Type": "application/json", **basic_auth_header()},
    method="PUT"
)
res = json.load(urllib.request.urlopen(req))
print("version", res["version"], "threshold", res["threshold"])