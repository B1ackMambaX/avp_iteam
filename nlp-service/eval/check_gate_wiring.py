import json
import sys
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8001"


def classify(**body):
    req = urllib.request.Request(
        f"{BASE}/classify", json.dumps(body).encode("utf-8"),
        {"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, None


fidu = "Оплата по фидуциарному поручению"
third = "Платеж за третье лицо Иванова"

print("default words, fidu text:", classify(text=fidu, threshold=0.5)[1]["decision"])
d = classify(text=fidu, threshold=0.5, triggerWords=["фидуциар*"])[1]["decision"]
print("custom words, fidu text:", "GATED" if d == "GATED" else "not GATED")
print("custom words, third-party text:",
      classify(text=third, threshold=0.5, triggerWords=["фидуциар*"])[1]["decision"])
print("bad words:", classify(text=fidu, threshold=0.5, triggerWords=["*"])[0])