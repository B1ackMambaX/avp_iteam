import json
import math
import os
import sys
import time
import urllib.parse
import urllib.request

from avp_auth import basic_auth_header

BASE = os.environ.get("AVP_BASE", "http://localhost:8080")
PROM = os.environ.get("AVP_PROM", "http://localhost:9090")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "load")
RUN_QUERY = os.environ.get("AVP_RUN_QUERY", "")   # например maxSeconds=1800

def call(path, method="GET"):
    req = urllib.request.Request(BASE + path, b"" if method == "POST" else None,
                                 basic_auth_header("admin"), method=method)
    with urllib.request.urlopen(req, timeout=3600) as r:
        raw = r.read()
    return json.loads(raw) if raw[:1] in (b"{", b"[") else None


def prom_max(query):
    url = PROM + "/api/v1/query?" + urllib.parse.urlencode({"query": query})
    try:
        with urllib.request.urlopen(url, timeout=30) as r:
            result = json.load(r)["data"]["result"]
        return max(float(x["value"][1]) for x in result) if result else None
    except Exception:
        return None


def scalars(d):
    return {k: v for k, v in (d or {}).items() if isinstance(v, (int, float, str, bool))}


def main():
    label, total = sys.argv[1], int(sys.argv[2])
    os.makedirs(OUT, exist_ok=True)

    t0 = time.time()
    call("/api/demo/reset", "POST")
    reset_s = time.time() - t0

    t1 = time.time()
    run = call("/api/pipeline/run" + ("?" + RUN_QUERY if RUN_QUERY else ""), "POST")
    run_s = time.time() - t1

    report = call("/api/demo/report")
    w = math.ceil(reset_s + run_s) + 30
    prom = {
        "hikari_pending_max": prom_max(f"max_over_time(hikaricp_connections_pending[{w}s])"),
        "hikari_active_max": prom_max(f"max_over_time(hikaricp_connections_active[{w}s])"),
        "heap_mb_max": prom_max(
            f'max_over_time(sum(jvm_memory_used_bytes{{area="heap"}})[{w}s:15s]) / 1048576'),
        "cpu_max": prom_max(f"max_over_time(process_cpu_usage[{w}s])"),
        "classify_avg_ms": prom_max(
            f'1000 * sum(increase(http_request_duration_seconds_sum{{handler="/classify"}}[{w}s]))'
            f' / sum(increase(http_request_duration_seconds_count{{handler="/classify"}}[{w}s]))'),
    }

    # --- ИЗМЕНЕНИЯ ЗДЕСЬ ---
    stats = (run or {}).get("stats", {})
    intake = (run or {}).get("intake", 0)

    summary = {
        "label": label,
        "total_ops": total,
        "reset_s": round(reset_s, 1),
        "run_s": round(run_s, 1),
        "ops_per_s": round(intake / run_s, 1) if run_s > 0 else 0, # Считаем по intake
        "contracts": stats.get("contracts"),                        # Добавили contracts
        "started_at": int(t0),
        "finished_at": int(time.time()),
        **prom
    }
    # ----------------------

    with open(os.path.join(OUT, f"{label}.json"), "w", encoding="utf-8") as f:
        json.dump({"summary": summary, "run": run, "report": report}, f,
                  ensure_ascii=False, indent=2)

    print(json.dumps(summary))
    print("run:", json.dumps(scalars(run)))
    print("report:", json.dumps(scalars(report)))
    print("chaos:", json.dumps(scalars((report or {}).get("chaos"))))

    # --- ПРОВЕРКИ ---
    checks = []
    expected_contracts = int(os.environ.get("AVP_EXPECTED_CONTRACTS", "1000000"))

    if stats.get("contracts") != expected_contracts:
        checks.append(f"BASE CORRUPTED: contracts={stats.get('contracts')} (expected {expected_contracts})")

    if intake != total:
        checks.append(f"INTAKE {intake} != expected {total}")

    if (run or {}).get("timedOut"):
        checks.append("TIMED OUT")

    for c in checks:
        print("CHECK FAILED:", c)
    # ---------------

    if run_s < 5 and total >= 10000:
        print("WARNING: run returned in <5s - pipeline may be asynchronous")


main()