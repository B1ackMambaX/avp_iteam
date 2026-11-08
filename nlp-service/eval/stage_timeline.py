"""Время стадий по минутам (мс на вызов). Окно берётся из load/<label>.json."""
import json, math, os, sys, urllib.parse, urllib.request

PROM = os.environ.get("PROM_URL", "http://localhost:9090")
STAGES = ["intake", "claim", "load_ops", "nlp_calls", "save", "map"]


def query(expr, start, end):
    url = PROM + "/api/v1/query_range?" + urllib.parse.urlencode(
        {"query": expr, "start": start, "end": end, "step": 60})
    with urllib.request.urlopen(url, timeout=30) as r:
        result = json.load(r)["data"]["result"]
    return {s["metric"]["stage"]: {int(float(t)): float(v) for t, v in s["values"]}
            for s in result}


def main(label, start=None, end=None):
    if start is None:
        with open(os.path.join(os.path.dirname(__file__), "load", label + ".json")) as f:
            meta = json.load(f)
        start, end = meta["started_at"], meta["finished_at"]
    end += 60
    ms = query("1000 * sum by (stage)(rate(avp_pipeline_stage_seconds_sum[1m]))"
               " / sum by (stage)(rate(avp_pipeline_stage_seconds_count[1m]))", start, end)
    cnt = query("sum by (stage)(increase(avp_pipeline_stage_seconds_count[1m]))", start, end)
    stamps = sorted({t for s in ms.values() for t in s})
    print("min iter/min " + " ".join(f"{s:>9}" for s in STAGES) + "   (ms per call)")
    for t in stamps:
        row = []
        for s in STAGES:
            v = ms.get(s, {}).get(t, float("nan"))
            row.append(f"{'-':>9}" if math.isnan(v) else f"{v:9.1f}")
        it = cnt.get("claim", {}).get(t, float("nan"))
        print(f"{(t - start) // 60:3d} {'-' if math.isnan(it) else int(it):>8} " + " ".join(row))


if __name__ == "__main__":
    a = sys.argv
    main(a[1], int(a[2]) if len(a) > 3 else None, int(a[3]) if len(a) > 3 else None)