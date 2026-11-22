import json
import sys
import urllib.request
from avp_auth import basic_auth_header

BASE = "http://localhost:8080"

def show(op_id):
    req = urllib.request.Request(
        f"{BASE}/api/operations/{op_id}/mapping",
        headers={"Authorization": basic_auth_header("admin")},
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        d = json.load(r)
    op, parse, cands = d["operation"], d["parse"], d["candidates"]
    expected = op["reference"].split("-")[2]
    print("=" * 70)
    print("ID операции:        ", op["id"])
    print("Назначение платежа: ", op["payment_purpose"])
    print("Получатель:         ", op["receiver_name"])
    print("Статус:             ", op["status"], "| причина:", op["mapping_reason"])
    print("NLP: score          ", parse["classification_score"],
          "| третье лицо:", parse["is_third_party"],
          "| имя:", parse["extracted_name"])
    print(f"Кандидатов: {len(cands)}")
    for i, c in enumerate(cands, 1):
        mark = "  <-- договор из метки" if str(c["contract_id"]) == expected else ""
        print(f"  [{i:2d}] {c['contract_number']:<18} "
              f"score={c['score']:<5} rules={c['matched_rules']}{mark}")

for arg in sys.argv[1:]:
    show(arg)