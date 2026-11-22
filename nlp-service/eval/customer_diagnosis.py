"""Диагностика детектора третьего лица на 24 примерах заказчика (задача 4, только чтение).

Что делает (ничего не настраивает и не меняет):
  4a  /api/admin/nlp/test для каждого примера + проверка правила
      third_party = decision == SEMANTIC and score > threshold and score > negativeSimilarity
  4b  какие слова гейта есть в тексте (те же паттерны, что в app/gate_patterns.py,
      слова берутся из действующих настроек /api/admin/nlp/settings)
  4c  /extract-name напрямую, в обход флага третьего лица
  4d  регэксп \\b(за|в пользу)\\b с фрагментом +-20 символов

Запуск (из nlp-service):
  python eval/customer_diagnosis.py
Переменные: AVP_BASE (по умолчанию http://localhost:8080), AVP_NLP (http://localhost:8001),
            AVP_ADMIN_PASSWORD.
Консоль - только ASCII. Полные данные: eval/results_customer/diagnosis.json
"""
import json
import os
import re
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))  # для app.gate_patterns

from app.gate_patterns import get_gate_patterns  # noqa: E402
from avp_auth import basic_auth_header  # noqa: E402
from customer_examples import SRC, kind, parse  # noqa: E402

BASE = os.environ.get("AVP_BASE", "http://localhost:8080")
NLP = os.environ.get("AVP_NLP", "http://localhost:8001")
OUT = os.path.join(HERE, "results_customer", "diagnosis.json")

REGEX_BASELINE = re.compile(r"\b(за|в пользу)\b", re.IGNORECASE)
CONTEXT = 20


def http(url, payload=None, auth=True):
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if auth:
        headers.update(basic_auth_header("admin"))
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method="GET" if data is None else "POST")
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def fragments(pattern, text):
    """Все совпадения с контекстом +-CONTEXT символов."""
    out = []
    for m in pattern.finditer(text):
        start, end = max(0, m.start() - CONTEXT), min(len(text), m.end() + CONTEXT)
        out.append({"match": m.group(0), "pos": m.start(), "context": text[start:end]})
    return out


def gate_details(text, trigger_words, negation_words):
    """Повторяет гейт classify_by_similarity: какие слова-триггеры есть в тексте и где."""
    lower = text.lower()
    checked = []
    for word in trigger_words:
        single, _ = get_gate_patterns([word], negation_words)
        hits = fragments(single, lower)
        checked.append({"word": word, "matched": [h["match"] for h in hits],
                        "context": [h["context"] for h in hits]})
    trigger_re, negation_re = get_gate_patterns(trigger_words, negation_words)
    return {
        "has_trigger": trigger_re.search(lower) is not None,
        "matched_words": [c["word"] for c in checked if c["matched"]],
        "checked": checked,
        "za_fragments": fragments(re.compile(r"\bза\b"), lower),
    }


def semantic_rule(res):
    """Правило Python-сервиса (semantic_classifier.classify_by_similarity, строка 114)."""
    if res.get("decision") != "SEMANTIC":
        return False
    score, neg, thr = res.get("score"), res.get("negativeSimilarity"), res.get("threshold")
    if neg is None or thr is None:
        return None
    return score > thr and score > neg


def main():
    settings = http(BASE + "/api/admin/nlp/settings")
    trigger_words, negation_words = settings["triggerWords"], settings["negationWords"]
    items = parse(SRC)
    print(f"examples={len(items)} settingsVersion={settings.get('version')} "
          f"threshold={settings.get('threshold')} triggers={len(trigger_words)} negations={len(negation_words)}")

    rows = []
    for i, it in enumerate(items, 1):
        res = http(BASE + "/api/admin/nlp/test", {"text": it["text"]})
        expected = bool(it["beneficiary"])
        rule = semantic_rule(res)
        extracted = None
        if expected:
            extracted = http(NLP + "/extract-name", {"text": it["text"]}, auth=False).get("fullName")
        row = {
            "id": i,
            "expected": expected,
            "beneficiary": it["beneficiary"],
            "kind": kind(it["beneficiary"]),
            "contract_type": it["type"],
            "text": it["text"],
            "api": res,
            "rule_third_party": rule,
            "api_matches_rule": None if rule is None else rule == res.get("thirdParty"),
            "gate": gate_details(it["text"], trigger_words, negation_words),
            "extract_name_direct": extracted,
            "regex_baseline": fragments(REGEX_BASELINE, it["text"]),
        }
        rows.append(row)
        neg = res.get("negativeSimilarity")
        print(f"{i:2d} exp={int(expected)} got={int(bool(res.get('thirdParty')))} "
              f"dec={res.get('decision'):8s} score={res.get('score', 0):.3f} "
              f"neg={'-' if neg is None else f'{neg:.3f}'} thr={res.get('threshold')} "
              f"rule={rule} gate_words={len(row['gate']['matched_words'])} "
              f"regex_hits={len(row['regex_baseline'])} extracted={'yes' if extracted else 'no'}")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"settings": settings, "rows": rows}, f, ensure_ascii=False, indent=1)

    tp = sum(r["expected"] and bool(r["api"].get("thirdParty")) for r in rows)
    fp = sum((not r["expected"]) and bool(r["api"].get("thirdParty")) for r in rows)
    fn = sum(r["expected"] and not r["api"].get("thirdParty") for r in rows)
    tn = sum((not r["expected"]) and not r["api"].get("thirdParty") for r in rows)
    mismatch = [r["id"] for r in rows if r["api_matches_rule"] is False]
    print(f"detector tp={tp} fp={fp} fn={fn} tn={tn}; api vs rule mismatches: {mismatch or 'none'}")
    print("written:", os.path.relpath(OUT, os.path.dirname(HERE)))


if __name__ == "__main__":
    main()
