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

SIMPLE_MARKERS = [
    "выгодоприобретатель",
    "в пользу",
    "по поручению",
    "алимент",
    "исполнительн",
]

# Паттерн по корню - ловит "третье лицо", "третьего лица",
# "третьему лицу", "третьих лиц" и т.д. Идентичен Java-версии.
THIRD_PARTY_PHRASE = re.compile(r"треть\S*\s+лиц\S*")

STOP_WORDS = {
    "выгодоприобретатель", "оплата", "перевод", "платеж", "платёж",
    "перечисление", "возврат", "погашение", "заработная", "алименты",
}


def find_marker_position(lower_text: str) -> int:
    """Возвращает позицию самого раннего маркера, либо -1."""
    earliest = -1

    for marker in SIMPLE_MARKERS:
        idx = lower_text.find(marker)
        if idx >= 0 and (earliest == -1 or idx < earliest):
            earliest = idx

    match = THIRD_PARTY_PHRASE.search(lower_text)
    if match:
        idx = match.start()
        if earliest == -1 or idx < earliest:
            earliest = idx

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