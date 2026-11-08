# nlp-service/benchmark/run_semantic_check.py
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from app.semantic_classifier import classify_by_similarity
from run_benchmark import load_dataset, HOLDOUT_PATH

holdout = load_dataset(HOLDOUT_PATH)
correct = 0
for row in holdout:
    result = classify_by_similarity(row["payment_purpose"])
    match = result["third_party"] == row["expected_third_party"]
    correct += match
    print(f"id={row['id']} expected={row['expected_third_party']} "
          f"actual={result['third_party']} "
          f"pos_sim={result['positive_similarity']} neg_sim={result['negative_similarity']} "
          f"{'OK' if match else 'MISS'}")

print(f"\nAccuracy: {100*correct/len(holdout):.1f}%")