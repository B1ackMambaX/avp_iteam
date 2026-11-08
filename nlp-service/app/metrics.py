from prometheus_client import Counter, Histogram

CLASSIFY_TOTAL = Counter(
    "nlp_classify",
    "Результаты классификации по пути решения",
    ["decision", "is_third_party"],
)

EMBEDDING_SECONDS = Histogram(
    "nlp_embedding_seconds",
    "Время расчёта эмбеддингов и сравнения с эталонами",
    buckets=(0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0),
)

EXTRACT_NAME_TOTAL = Counter(
    "nlp_extract_name",
    "Результаты извлечения имени",
    ["found"],
)

NAME_NORMALIZE_TOTAL = Counter(
    "nlp_name_normalize", "Каким путём нормализовано имя", ["path"])