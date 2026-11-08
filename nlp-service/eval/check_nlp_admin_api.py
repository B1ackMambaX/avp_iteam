import json
import urllib.error
import urllib.request
from avp_auth import basic_auth_header

BASE = "http://localhost:8080/api/admin/nlp"


def call(path, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    headers.update(basic_auth_header())
    req = urllib.request.Request(BASE + path, data, headers,
                                 method="POST" if body is not None else "GET")
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, None


fidu = "Оплата по фидуциарному поручению"
third = "Платеж за третье лицо Иванов Иван Иванович"

s, ex = call("/examples")
print("examples:", s, len(ex["positive"]), len(ex["negative"]))

s, r = call("/test", {"text": fidu})
print("default words, fidu:", s, r["decision"], "overridden=%s" % r["overridden"])

s, r = call("/test", {"text": fidu, "triggerWords": ["фидуциар*"]})
print("custom words, fidu:", s, "GATED" if r["decision"] == "GATED" else "not GATED")

s, r = call("/test", {"text": third, "threshold": 1.0})
print("threshold 1.0:", s, "thirdParty=%s" % r["thirdParty"], "threshold=%s" % r["threshold"])

print("blank text:", call("/test", {"text": "  "})[0])
print("bad words:", call("/test", {"text": fidu, "triggerWords": ["*"]})[0])
print("bad threshold:", call("/test", {"text": fidu, "threshold": 1.5})[0])