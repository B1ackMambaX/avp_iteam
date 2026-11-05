"""Прогон eval_set_v1 против запущенного nlp-service. Сервис не изменяет.

Запуск из nlp-service:  python eval/run_eval.py --service-rev <hash>
"""
import argparse
import csv
import json
import re
import subprocess
import urllib.request
from pathlib import Path

from natasha import Doc, NewsEmbedding, NewsNERTagger, Segmenter

ROOT = Path(__file__).parent
BASE = "http://localhost:8001"

_seg = Segmenter()
_ner = NewsNERTagger(NewsEmbedding())

STRONG = re.compile(r"выгодоприобретател\w*|треть\w*\s+лиц\w*")
PREPS = [("за",), ("за", "счет"), ("в", "пользу"), ("в", "интересах")]
MAX_GAP = 2


def post(path, body):
    req = urllib.request.Request(
        BASE + path,
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json; charset=utf-8"},
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def is_tp(resp):
    for k in ("isThirdParty", "is_third_party", "thirdParty"):
        if k in resp:
            return bool(resp[k])
    raise KeyError(f"no third-party field in /classify response, keys: {list(resp)}")


def norm(s):
    return re.sub(r"\s+", " ", (s or "").lower().replace("ё", "е")).strip()


def per_spans(text):
    doc = Doc(text)
    doc.segment(_seg)
    doc.tag_ner(_ner)
    return [s for s in doc.spans if s.type == "PER"]


def prep_before(text, start):
    tokens = re.findall(r"[а-яa-z0-9]+", norm(text[:start]))
    for gap in range(MAX_GAP + 1):
        end = len(tokens) - gap
        for p in PREPS:
            if end - len(p) >= 0 and tuple(tokens[end - len(p):end]) == p:
                return True
    return False


def hybrid(text, resp):
    if resp.get("decision") == "NEGATED":
        return False, "rule1_negation"
    if STRONG.search(norm(text)):
        return True, "rule2_strong"
    for s in per_spans(text):
        if prep_before(text, s.start):
            return True, "rule3_prep_per"
    return is_tp(resp), "rule4_semantic"


def predict(variant, text):
    thr = 0.75 if variant == "A_0.75" else 0.5
    resp = post("/classify", {"text": text, "threshold": thr})
    if variant.startswith("A"):
        pred, how = is_tp(resp), "classifier"
    else:
        pred, how = hybrid(text, resp)
    return pred, how, resp.get("decision"), resp.get("score")


def git(*args):
    try:
        return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()
    except Exception:
        return "unknown"


def tally(rows):
    c = {"n": len(rows), "tp": 0, "fp": 0, "fn": 0, "tn": 0}
    for r in rows:
        y, p = r["label"] == "yes", r["pred"]
        c["tp" if y and p else "fp" if p else "fn" if y else "tn"] += 1
    tp, fp, fn = c["tp"], c["fp"], c["fn"]
    c["precision"] = round(tp / (tp + fp), 3) if tp + fp else None
    c["recall"] = round(tp / (tp + fn), 3) if tp + fn else None
    c["f1"] = round(2 * tp / (2 * tp + fp + fn), 3) if tp + fp + fn else None
    return c


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--service-rev", default="unknown",
                    help="git hash кода, из которого запущен nlp-service")
    args = ap.parse_args()

    with open(ROOT / "eval_set_v1.csv", encoding="utf-8-sig", newline="") as f:
        data = list(csv.DictReader(f))

    out_dir = ROOT / "results"
    out_dir.mkdir(exist_ok=True)
    summary = {
        "eval_set_commit": git("log", "-1", "--format=%h", "--", "eval_set_v1.csv"),
        "script_head": git("rev-parse", "--short", "HEAD"),
        "service_rev": args.service_rev,
        "variants": {},
    }

    for variant in ("A_0.5", "A_0.75", "B_0.5"):
        rows = []
        for d in data:
            pred, how, dec, score = predict(variant, d["text"])
            ext = post("/extract-name", {"text": d["text"]}).get("fullName") if pred else None
            exp = d["expected_name"]
            name_ok = None
            if d["label_final"] == "yes" and exp and pred:
                name_ok = norm(ext) == norm(exp)
            rows.append({
                "id": d["id"], "category": d["category"], "label": d["label_final"],
                "note": d["note"], "pred": pred, "how": how, "decision": dec,
                "score": score, "extracted": ext, "expected": exp, "name_ok": name_ok,
            })

        with open(out_dir / f"{variant}.csv", "w", encoding="utf-8", newline="") as f:
            w = csv.DictWriter(f, fieldnames=list(rows[0]))
            w.writeheader()
            w.writerows(rows)

        ev = [r for r in rows if r["label"] in ("yes", "no") and r["category"] != "self_name"]
        groups = {"all": ev}
        for r in ev:
            groups.setdefault("cat:" + r["category"], []).append(r)
        groups["near"] = [r for r in ev if "near_" in r["note"]]
        groups["clean"] = [r for r in ev if "near_" not in r["note"]]
        groups["gate_passed"] = [r for r in ev if r["decision"] != "GATED"]
        groups["gate_blocked"] = [r for r in ev if r["decision"] == "GATED"]

        yes_named = [r for r in ev if r["label"] == "yes" and r["expected"]]
        interp = [r for r in yes_named if "интерпретация" in r["note"]]
        regular = [r for r in yes_named if r not in interp]
        summary["variants"][variant] = {
            "groups": {k: tally(v) for k, v in groups.items()},
            "name_match": {
                "regular_ok": sum(1 for r in regular if r["name_ok"]),
                "regular_total": len(regular),
                "interp_ok": sum(1 for r in interp if r["name_ok"]),
                "interp_total": len(interp),
            },
            "unclear_pred": {r["id"]: r["pred"] for r in rows if r["label"] == "unclear"},
            "self_name_pred": {r["id"]: r["pred"] for r in rows if r["category"] == "self_name"},
        }
        a = summary["variants"][variant]["groups"]["all"]
        print(variant, "all:", {k: a[k] for k in ("n", "tp", "fp", "fn", "tn", "precision", "recall", "f1")})

    (out_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print("written:", out_dir)


if __name__ == "__main__":
    main()