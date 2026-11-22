"""Снимки БД и стадий конвейера раз в минуту во время нагрузочного прогона (задача 2a).

Пишет в eval/load/<label>_snap.txt:
  - pg_stat_user_tables по operations, nlp_task, operation_mapping_candidate
  - pg_stat_activity (долгие транзакции, backend_xmin)
  - docker stats --no-stream
  - на минуте --explain-at: EXPLAIN (ANALYZE, BUFFERS) запроса intake (в транзакции с ROLLBACK)
и в eval/load/<label>_stages.csv - мс на вызов каждой стадии за минуту
(приращение avp_pipeline_stage_seconds из /actuator/prometheus, Prometheus не нужен).

Запуск (из nlp-service, параллельно с run_load.py):
  python eval/db_monitor.py x10_diag --container avp_iteam_postgres --minutes 40
Останавливается сам, когда run_load.py записал eval/load/<label>.json, или через --minutes.
Консоль - только ASCII.
"""
import argparse
import os
import re
import subprocess
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
LOAD = os.path.join(HERE, "load")
BASE = os.environ.get("AVP_BASE", "http://localhost:8080")
STAGES = ["intake", "enqueue", "nlp_batch", "claim", "load_ops", "nlp_calls", "save", "map"]

TABLES_SQL = """SELECT now()::time(0) AS at, relname, n_live_tup, n_dead_tup, last_autovacuum, autovacuum_count
FROM pg_stat_user_tables
WHERE relname IN ('operations','nlp_task','operation_mapping_candidate') ORDER BY relname;"""

ACTIVITY_SQL = """SELECT pid, state, now()-xact_start AS xact_age, backend_xmin, left(query, 60) AS query
FROM pg_stat_activity
WHERE datname = current_database() AND state <> 'idle' AND pid <> pg_backend_pid();"""

EXPLAIN_SQL = """BEGIN;
EXPLAIN (ANALYZE, BUFFERS)
SELECT id FROM operations WHERE status='PENDING' ORDER BY id LIMIT 1000 FOR UPDATE SKIP LOCKED;
ROLLBACK;"""

METRIC = re.compile(r'^avp_pipeline_stage_seconds_(sum|count)\{[^}]*stage="([^"]+)"[^}]*\}\s+([0-9.eE+-]+)')


def psql(args, sql):
    cmd = ["docker", "exec", "-i", args.container, "psql", "-U", args.user, "-d", args.db, "-X", "-q"]
    r = subprocess.run(cmd, input=sql, capture_output=True, text=True, encoding="utf-8")
    return r.stdout + r.stderr


def docker_stats(name_filter):
    """Только контейнеры проекта: в снимки не попадают чужие контейнеры машины."""
    r = subprocess.run(["docker", "stats", "--no-stream", "--format",
                        "{{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}} io={{.BlockIO}}"],
                       capture_output=True, text=True, encoding="utf-8")
    return "".join(line + "\n" for line in r.stdout.splitlines() if name_filter in line.split(" ", 1)[0])


def stage_counters():
    try:
        with urllib.request.urlopen(BASE + "/actuator/prometheus", timeout=10) as r:
            text = r.read().decode("utf-8")
    except Exception:
        return None
    out = {}
    for line in text.splitlines():
        m = METRIC.match(line)
        if m:
            kind, stage, value = m.groups()
            out.setdefault(stage, {})[kind] = float(value)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("label")
    ap.add_argument("--container", default="avp_iteam_postgres")
    ap.add_argument("--user", default="avp_iteam_user")
    ap.add_argument("--db", default="avp_iteam")
    ap.add_argument("--minutes", type=int, default=40)
    ap.add_argument("--interval", type=int, default=60)
    ap.add_argument("--explain-at", type=int, default=9, help="minute for EXPLAIN of intake query")
    ap.add_argument("--stats-filter", default="avp", help="docker stats only for containers with this in name")
    args = ap.parse_args()

    os.makedirs(LOAD, exist_ok=True)
    snap_path = os.path.join(LOAD, f"{args.label}_snap.txt")
    csv_path = os.path.join(LOAD, f"{args.label}_stages.csv")
    done_path = os.path.join(LOAD, f"{args.label}.json")
    started = time.time()

    with open(csv_path, "w", encoding="utf-8") as csv:
        csv.write("minute," + ",".join(f"{s}_ms,{s}_calls" for s in STAGES) + "\n")
    prev = stage_counters()
    print("minute " + " ".join(f"{s:>9}" for s in STAGES) + "  (ms per call)")

    minute = 0
    while minute < args.minutes:
        time.sleep(args.interval)
        minute += 1
        with open(snap_path, "a", encoding="utf-8") as snap:
            snap.write(f"\n===== minute {minute} =====\n")
            snap.write(psql(args, TABLES_SQL))
            snap.write(psql(args, ACTIVITY_SQL))
            snap.write(docker_stats(args.stats_filter))
            if minute == args.explain_at:
                snap.write("----- EXPLAIN intake query -----\n")
                snap.write(psql(args, EXPLAIN_SQL))

        cur = stage_counters()
        row, cells = [], []
        for s in STAGES:
            if prev and cur and s in cur:
                dc = cur[s].get("count", 0) - prev.get(s, {}).get("count", 0)
                ds = cur[s].get("sum", 0) - prev.get(s, {}).get("sum", 0)
                ms = 1000 * ds / dc if dc > 0 else None
            else:
                dc, ms = 0, None
            row += ["" if ms is None else f"{ms:.2f}", str(int(dc))]
            cells.append(f"{'-':>9}" if ms is None else f"{ms:9.1f}")
        with open(csv_path, "a", encoding="utf-8") as csv:
            csv.write(f"{minute}," + ",".join(row) + "\n")
        print(f"{minute:6d} " + " ".join(cells), flush=True)
        prev = cur or prev

        if os.path.exists(done_path) and os.path.getmtime(done_path) > started:
            print("run finished, monitor stops")
            break

    print("written:", os.path.relpath(snap_path, HERE), os.path.relpath(csv_path, HERE))


if __name__ == "__main__":
    main()
