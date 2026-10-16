"""
Гипотеза Б: извлечение сущностей из назначения платежа
с использованием Natasha.

Обновление: логика выбора кандидата приведена в соответствие
с Java-реализацией ("ближайший к маркеру", а не "первый найденный") -
иначе сравнение гипотез было бы нечестным из-за разной логики выбора.
Также добавлен фильтр стоп-слов (аналог Java) и regex-паттерн
для маркера "третье лицо" в любой словоформе.
"""

import re

from natasha import (
    Segmenter,
    MorphVocab,
    NewsEmbedding,
    NewsMorphTagger,
    NewsNERTagger,
    PER,
    Doc,
)

_segmenter = Segmenter()
_morph_vocab = MorphVocab()
_emb = NewsEmbedding()
_morph_tagger = NewsMorphTagger(_emb)
_ner_tagger = NewsNERTagger(_emb)

MARKER_PATTERNS = [
    re.compile(r"\bвыгодоприобретател\w*\b"),
    re.compile(r"\bв\s+пользу\b"),
    re.compile(r"\bпо\s+поручени\w*\b"),
    re.compile(r"\bалимент\w*\b"),
    re.compile(r"\bисполнительн\w*\b"),
    re.compile(r"треть\S*\s+лиц\S*"),
]

NEGATION_WORDS = {"не", "нет", "отсутств", "без"}
NEGATION_WINDOW = 20

STOP_WORDS = {
    "выгодоприобретатель", "оплата", "перевод", "платеж", "платёж",
    "перечисление", "возврат", "погашение", "заработная", "алименты",
}

NEGATION_PATTERN = re.compile(r"\b(не|нет|отсутств\w*|без)\b", re.UNICODE)
NEGATION_WINDOW = 20

def is_negated(lower_text: str, marker_start: int, marker_end: int) -> bool:
    window_start = max(0, marker_start - NEGATION_WINDOW)
    window_end = min(len(lower_text), marker_end + NEGATION_WINDOW)
    window = lower_text[window_start:window_end]
    return bool(NEGATION_PATTERN.search(window))


def find_marker_position(lower_text: str) -> int:
    earliest = -1
    for pattern in MARKER_PATTERNS:
        for match in pattern.finditer(lower_text):
            idx = match.start()
            if is_negated(lower_text, idx, match.end()):
                continue
            if earliest == -1 or idx < earliest:
                earliest = idx
            break
    return earliest


def is_third_party(text: str) -> bool:
    return find_marker_position(text.lower()) >= 0


def strip_leading_stop_word(candidate: str) -> str | None:
    """Аналог Java-версии: если кандидат начинается со служебного
    слова, случайно попавшего в NER-спан, отбрасываем его."""
    words = candidate.split()
    if not words:
        return None

    first_lower = words[0].lower()
    if not any(first_lower.startswith(sw) for sw in STOP_WORDS):
        return candidate

    remaining = words[1:]
    if len(remaining) < 2:
        return None
    return " ".join(remaining)


def extract_beneficiary_name(text: str, marker_position: int) -> str | None:
    """
    Находит ВСЕ персоны (PER) в тексте и выбирает ближайшую
    к позиции маркера - та же логика, что и в Java-парсере,
    для честного сравнения.
    """
    doc = Doc(text)
    doc.segment(_segmenter)
    doc.tag_morph(_morph_tagger)
    doc.tag_ner(_ner_tagger)

    best_candidate = None
    best_distance = float("inf")

    for span in doc.spans:
        if span.type != PER:
            continue

        span.normalize(_morph_vocab)
        normalized = span.normal

        cleaned = strip_leading_stop_word(normalized) if normalized else None
        if cleaned is None:
            continue

        distance = abs(span.start - marker_position)
        if distance < best_distance:
            best_distance = distance
            best_candidate = cleaned

    return best_candidate


def parse_payment_purpose(text: str) -> dict:
    if not text or not text.strip():
        return {"third_party": False, "beneficiary_name": None, "confidence": 0.9}

    lower = text.lower()
    marker_position = find_marker_position(lower)
    third_party = marker_position >= 0

    if not third_party:
        return {"third_party": False, "beneficiary_name": None, "confidence": 0.9}

    name = extract_beneficiary_name(text, marker_position)
    confidence = 0.8 if name else 0.3

    return {"third_party": True, "beneficiary_name": name, "confidence": confidence}