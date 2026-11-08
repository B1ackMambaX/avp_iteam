import json
import os
import sys

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "load")


def load(name):
    with open(os.path.join(D, name + ".json"), encoding="utf-8") as f:
        return json.load(f)["report"]


for n in (sys.argv[1:] or ("x1", "x2", "x3")):
    r = load(n)
    print(n, {k: (v["correct"], v["total"]) for k, v in r["byScenario"].items()})
    for k, v in r["chaos"]["byScenario"].items():
        print("  chaos", k, v["correct"], v["total"],
              "inCand", v.get("expectedInCandidates"), "avgCand", v.get("avgCandidates"))

refs = [x for s in load("x3")["chaos"]["byScenario"].values() for x in s.get("missingExamples", [])]
if refs:
    print("\nSELECT o.reference, (SELECT count(*) FROM operation_mapping_candidate c "
          "WHERE c.operation_id = o.id) AS stored FROM operations o WHERE o.reference IN ("
          + ",".join("'%s'" % x for x in refs) + ");")
else:
    print("no missing examples in x3")