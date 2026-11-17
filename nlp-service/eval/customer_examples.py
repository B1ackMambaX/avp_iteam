"""Примеры заказчика -> /api/admin/nlp/test.
В консоль только ASCII, полный отчёт в results_customer/report.md.
"""
import json
import os
import re
import urllib.request

from avp_auth import basic_auth_header  # ADAPT, как в show_op.py

BASE = "http://localhost:8080"
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "data", "customer_examples.txt")
OUT = os.path.join(HERE, "results_customer")

FIELD = re.compile(r"^\s*\d+\.\s*(Назначение|Бенефициар|Тип договора)\s*:\s*(.*)$")
KEYS = {"Назначение": "text", "Бенефициар": "beneficiary", "Тип договора": "type"}

# Простая legacy-эвристика для сравнения (не для настройки)
LEGACY = re.compile(r"\b(за|в пользу)\b", re.IGNORECASE)


def parse(path):
    items, cur, last = [], None, None
    with open(path, encoding="utf-8") as f:
        for line in f.read().splitlines():
            m = FIELD.match(line)
            if m:
                key = KEYS[m.group(1)]
                if key == "text":
                    cur = {"text": "", "beneficiary": "", "type": ""}
                    items.append(cur)
                if cur is None:
                    continue
                cur[key] = m.group(2).strip()
                last = key
            elif line.strip() and cur is not None and last:
                cur[last] = (cur[last] + " " + line.strip()).strip()
    return items

def _auth_headers():
    h = basic_auth_header("admin")
    if isinstance(h, str):
        return {"Authorization": h}
    return dict(h)

def call(text):
    headers = {"Content-Type": "application/json; charset=utf-8"}
    headers.update(_auth_headers())
    req = urllib.request.Request(
        BASE + "/api/admin/nlp/test",
        data=json.dumps({"text": text}).encode("utf-8"),
        headers=headers,
        method="POST")
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def pick(d, *names):
    for n in names:
        if n in d:
            return d[n]
    return None


def kind(ben):
    if not ben:
        return "none"
    if re.match(r"\s*(ООО|ОАО|ЗАО|ПАО|АО)\b", ben):
        return "org"
    if re.match(r"\s*ИП\b", ben):
        return "ip"
    return "person"


def tokens(s):
    return re.sub(r"[^\w\s]", " ", (s or "").lower().replace("ё", "е")).split()


def name_hit(expected, got):
    exp = [t for t in tokens(expected) if t != "ип" and len(t) > 1]
    return bool(exp) and any(t[:4] == exp[0][:4] for t in tokens(got))


def legacy_baseline(items):
    """Простейшая эвристика \b(за|в пользу)\b — сравнение, не настройка."""
    tp = fp = fn = tn = 0
    rows = []
    for i, it in enumerate(items, 1):
        pred = bool(LEGACY.search(it["text"]))
        exp = bool(it["beneficiary"])
        tag = "OK " if pred == exp else "ERR"
        tp += pred and exp
        fp += pred and (not exp)
        fn += (not pred) and exp
        tn += (not pred) and (not exp)
        rows.append(f"{i:2d} {tag} exp={int(exp)} pred={int(pred)}")
    return rows, (tp, fp, fn, tn)


def main():
    items = parse(SRC)
    os.makedirs(OUT, exist_ok=True)
    tp = fp = fn = tn = 0
    hits = {"person": [0, 0], "ip": [0, 0], "org": [0, 0]}
    md = ["# Примеры заказчика: результат прогона\n"]

    for i, it in enumerate(items, 1):
        res = call(it["text"])
        if i == 1:
            print("response keys:", sorted(res.keys()))
        got = bool(pick(res, "thirdParty", "isThirdParty"))
        name = pick(res, "extractedName", "name", "beneficiaryName")
        decision = pick(res, "decision") or "?"
        score = pick(res, "score") or 0.0
        threshold = pick(res, "threshold")
        overridden = pick(res, "overridden")
        exp = bool(it["beneficiary"])
        k = kind(it["beneficiary"])
        hit = None
        if exp and k in hits:
            hit = name_hit(it["beneficiary"], name)
            hits[k][1] += 1
            hits[k][0] += int(hit)
        tp += exp and got
        fp += (not exp) and got
        fn += exp and (not got)
        tn += (not exp) and (not got)
        tag = "OK " if exp == got else "ERR"
        thr_s = f"{threshold:.3f}" if isinstance(threshold, (int, float)) else "?"
        neg = pick(res, "negativeSimilarity")
        neg_s = f"{neg:.3f}" if isinstance(neg, (int, float)) else "?"
        print(f"{i:2d} {tag} exp={int(exp)} got={int(got)} "
              f"dec={decision:8s} score={score:.3f} neg={neg_s} thr={thr_s} ovr={overridden} "
              f"kind={k:6s} name_hit={hit}")
        md.append(f"## {i}. {tag.strip()} (ожидали {exp}, получили {got}, вид бенефициара: {k})\n")
        md.append(f"Текст: {it['text']}\n")
        md.append(f"Бенефициар: {it['beneficiary'] or '-'} | Тип договора: {it['type'] or '-'}\n")
        md.append(f"Решение: {decision}, score={score:.3f}, threshold={thr_s}, overridden={overridden}\n")
        md.append(f"Извлечённое имя: {name} | попадание: {hit}\n")
        md.append("```json\n" + json.dumps(res, ensure_ascii=False, indent=1) + "\n```\n")

    n = len(items)
    print(f"total={n} tp={tp} fp={fp} fn={fn} tn={tn} correct={tp + tn}")
    print("name hits (hit/total):", {k: tuple(v) for k, v in hits.items()})

    # Legacy baseline
    rows, (ltp, lfp, lfn, ltn) = legacy_baseline(items)
    print("\n=== legacy baseline \\b(за|в пользу)\\b ===")
    for r in rows:
        print(r)
    print(f"legacy total={n} tp={ltp} fp={lfp} fn={lfn} tn={ltn} correct={ltp + ltn}")

    md.append("\n# Legacy baseline \\b(за|в пользу)\\b\n")
    for r in rows:
        md.append(r + "\n")
    md.append(f"\nlegacy total={n} tp={ltp} fp={lfp} fn={lfn} tn={ltn} correct={ltp + ltn}\n")

    with open(os.path.join(OUT, "report.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(md))


if __name__ == "__main__":
    main()