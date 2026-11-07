import json
import sys
import time
import urllib.parse
import urllib.request

PROM = "http://localhost:9090"
HOURS = float(sys.argv[1]) if len(sys.argv) > 1 else 12
STEP = 60
H = 'http_request_duration_seconds_%s{handler="/classify"}'
Q = {
    "rps": "sum(rate(%s[2m]))" % (H % "count"),
    "ms": "1000 * sum(rate(%s[2m])) / sum(rate(%s[2m]))" % (H % "sum", H % "count"),
    "rss": "max(process_resident_memory_bytes) / 1048576",
}


def series(query, start, end):
    url = PROM + "/api/v1/query_range?" + urllib.parse.urlencode(
        {"query": query, "start": start, "end": end, "step": STEP})
    with urllib.request.urlopen(url, timeout=60) as r:
        res = json.load(r)["data"]["result"]
    return {int(float(t)): float(v) for t, v in (res[0]["values"] if res else [])}


end = int(time.time())
start = int(end - HOURS * 3600)
data = {k: series(q, start, end) for k, q in Q.items()}
prev = None
for ts in sorted(data["rps"]):
    rps = data["rps"][ts]
    if not rps >= 1:
        continue
    if prev is not None and ts - prev > STEP * 1.5:
        print("--- gap ---")
    prev = ts
    ms, rss = data["ms"].get(ts), data["rss"].get(ts)
    print(time.strftime("%H:%M", time.localtime(ts)), "rps=%.1f" % rps,
          "classify_ms=%s" % ("%.1f" % ms if ms == ms and ms is not None else "-"),
          "py_rss_mb=%s" % ("%.0f" % rss if rss is not None else "-"))