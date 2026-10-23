import csv
import sys
import os

# Добавляем корень nlp-service в пути поиска, чтобы видеть папку app
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from app.semantic_classifier import classify_by_similarity

def evaluate_holdout(path="holdout.csv", threshold=0.5):
    with open(path, encoding="utf-8") as f:
        rows = list(csv.DictReader(f))

    tp = fp = tn = fn = 0
    for row in rows:
        expected = row["expected_is_third_party"].strip().lower() == "true"
        result = classify_by_similarity(row["payment_purpose"], threshold=threshold)
        predicted = result["third_party"]

        if predicted and expected: tp += 1
        elif predicted and not expected: fp += 1
        elif not predicted and not expected: tn += 1
        else: fn += 1

        status = "OK" if predicted == expected else "MISS"
        print(f"[{status}] id={row['id']} expected={expected} got={predicted} "
              f"pos={result['positive_similarity']} neg={result['negative_similarity']} "
              f"negated={result['negated']} no_trigger={result.get('no_trigger')}")

    precision = tp / (tp + fp) if (tp + fp) else 0
    recall = tp / (tp + fn) if (tp + fn) else 0
    accuracy = (tp + tn) / len(rows)
    print(f"\naccuracy={accuracy:.3f} precision={precision:.3f} recall={recall:.3f} "
          f"tp={tp} fp={fp} tn={tn} fn={fn}")

if __name__ == "__main__":
    evaluate_holdout(path="../../data/holdout-dataset.csv")