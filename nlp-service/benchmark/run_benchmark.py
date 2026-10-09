"""
Бенчмарк Гипотезы Б - зеркало HypothesisABenchmarkRunner.java.
Считает точность на размеченном датасете, затем нагрузочный прогон с замером CPU/RAM через psutil.
"""

import csv
import os
import sys
import time

import psutil

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from app.parser import parse_payment_purpose

DATASET_PATH = os.path.join(os.path.dirname(__file__), "..", "..", "data", "test-dataset.csv")

# Natasha заметно медленнее regex (embedding + NER на каждый текст),
# поэтому итераций берём на порядки меньше, чем в Java-версии.
PERFORMANCE_ITERATIONS = 2000


def load_dataset(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append({
                "id": row["id"],
                "payment_purpose": row["payment_purpose"],
                "expected_third_party": row["expected_is_third_party"].lower() == "true",
                "expected_beneficiary_name": row["expected_beneficiary_name"] or None,
            })
    return rows


def normalize(s: str) -> str:
    return " ".join(s.lower().split())


def names_match(expected, actual):
    if expected is None and actual is None:
        return True
    if expected is None or actual is None:
        return False
    return normalize(expected) == normalize(actual)


def run_accuracy_phase(dataset):
    print("========== ФАЗА 1: ТОЧНОСТЬ (Гипотеза Б/Natasha) ==========")

    total = len(dataset)
    correct_classification = 0
    tp = fp = fn = tn = 0
    third_party_rows = 0
    correct_name_extraction = 0

    for row in dataset:
        result = parse_payment_purpose(row["payment_purpose"])

        expected = row["expected_third_party"]
        actual = result["third_party"]

        if expected == actual:
            correct_classification += 1

        if expected and actual:
            tp += 1
        if not expected and actual:
            fp += 1
        if expected and not actual:
            fn += 1
        if not expected and not actual:
            tn += 1

        if expected:
            third_party_rows += 1
            if names_match(row["expected_beneficiary_name"], result["beneficiary_name"]):
                correct_name_extraction += 1

        print(f"id={row['id']} | expected_tp={expected} actual_tp={actual} | "
              f"expected_name='{row['expected_beneficiary_name']}' "
              f"actual_name='{result['beneficiary_name']}'")

    classification_accuracy = 100.0 * correct_classification / total
    name_accuracy = (100.0 * correct_name_extraction / third_party_rows) if third_party_rows else 0.0

    print(f"\nВсего строк: {total}")
    print(f"Точность классификации: {classification_accuracy:.1f}%")
    print(f"  TP={tp}, FP={fp}, FN={fn}, TN={tn}")
    print(f"Строк с третьим лицом: {third_party_rows}")
    print(f"Точность извлечения ФИО: {name_accuracy:.1f}%")


def run_performance_phase(dataset):
    print("\n========== ФАЗА 2: НАГРУЗКА (Гипотеза Б/Natasha) ==========")
    print(f"Итераций: {PERFORMANCE_ITERATIONS}")
    print(f"PID процесса: {os.getpid()} - если необходимо - подключайтесь системным монитором/Диспетчером задач")
    time.sleep(10)

    process = psutil.Process(os.getpid())
    process.cpu_percent(interval=None)  # "прайминг" - первый вызов всегда возвращает 0.0
    mem_before = process.memory_info().rss

    dataset_size = len(dataset)
    start = time.perf_counter()

    for i in range(PERFORMANCE_ITERATIONS):
        row = dataset[i % dataset_size]
        parse_payment_purpose(row["payment_purpose"])

    elapsed = time.perf_counter() - start

    cpu_percent = process.cpu_percent(interval=None)  # % с момента прошлого вызова
    mem_after = process.memory_info().rss

    throughput = PERFORMANCE_ITERATIONS / elapsed
    mem_delta_mb = (mem_after - mem_before) / 1024 / 1024

    print(f"Время выполнения: {elapsed * 1000:.1f} мс")
    print(f"Throughput: {throughput:.0f} операций/сек")
    print(f"CPU за время теста: {cpu_percent:.1f}% "
          f"(psutil нормализует к одному ядру, может быть >100% при многопоточности)")
    print(f"Изменение RSS-памяти процесса: {mem_delta_mb:.2f} МБ")


if __name__ == "__main__":
    dataset = load_dataset(DATASET_PATH)
    print(f"Загружено {len(dataset)} строк датасета\n")

    run_accuracy_phase(dataset)
    run_performance_phase(dataset)