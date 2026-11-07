import csv, re
TRIG = re.compile(r"треть\w*|лиц\w*|интерес\w*|счет\w*|правопреемник\w*|довереннос\w*|цессионар\w*|контрагент\w*|польз\w*|выгодоприобретател\w*")
NEG = re.compile(r"\b(не|нет|отсутствует|без)\b")
with open("eval/eval_set_v2.csv", encoding="utf-8-sig", newline="") as f:
    rows = list(csv.reader(f))
bad = [(i, len(r)) for i, r in enumerate(rows, 1) if len(r) != 10]
print("bad columns:", bad, "rows:", len(rows) - 1)
for r in rows[1:]:
    if r[0] in {"10", "11", "12", "22"}:
        t = r[1].lower()
        hit = any(NEG.search(t[max(0, m.start() - 20): m.end() + 20]) for m in TRIG.finditer(t))
        print(r[0], "negation in window:", hit)