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


def _is_whitespace_gap(text: str, start: int, stop: int) -> bool:
    return text[start:stop].strip() == ""


def _merge_adjacent_per_spans(per_spans, text):
    """
    Natasha иногда распознаёт одно ФИО как несколько отдельных PER-спанов
    по токену вместо одного (особенно в падежах, отличных от именительного) -
    например, "Михайлова" / "Евгения" / "Евгеньевича" как три сущности вместо
    одной. Склеиваем соседние PER-спаны, если между ними только пробел -
    это признак того, что это одно имя, а не разные упоминания.
    """
    per_spans = sorted(per_spans, key=lambda s: s.start)
    merged = []
    for span in per_spans:
        if merged and _is_whitespace_gap(text, merged[-1][1], span.start):
            merged[-1] = (merged[-1][0], span.stop)
        else:
            merged.append((span.start, span.stop))
    return merged


def _normalize_name_slice(text_slice: str) -> str | None:
    """
    Прогоняет вырезанный фрагмент ФИО как отдельный мини-документ через
    полный NER-пайплайн Natasha заново. Без окружающего контекста (остального
    текста назначения платежа) модель, как правило, корректно распознаёт
    весь фрагмент как ОДИН PER-спан и .normalize() даёт согласованную
    по роду/падежу форму - тот же метод, что в оригинальной реализации,
    просто применённый к уже склеенному диапазону, а не к сырым спанам
    из полного текста.
    """
    mini_doc = Doc(text_slice)
    mini_doc.segment(_segmenter)
    mini_doc.tag_morph(_morph_tagger)
    mini_doc.tag_ner(_ner_tagger)

    mini_per_spans = [s for s in mini_doc.spans if s.type == PER]

    if not mini_per_spans:
        # NER не увидела сущность даже в изолированном фрагменте -
        # не должно происходить часто, но на всякий случай fallback:
        # возвращаем исходный фрагмент как есть, без нормализации,
        # лучше отдать что-то, чем ничего.
        return text_slice.strip()

    if len(mini_per_spans) == 1:
        mini_per_spans[0].normalize(_morph_vocab)
        return mini_per_spans[0].normal

    # Редкий случай: даже на изолированном фрагменте распалось на
    # несколько спанов. Нормализуем каждый отдельно и склеиваем -
    # не идеально, но не теряем часть имени, как было в баге раньше.
    parts = []
    for span in mini_per_spans:
        span.normalize(_morph_vocab)
        parts.append(span.normal)
    return " ".join(parts)


def extract_beneficiary_name(text: str, marker_position: int) -> str | None:
    """
    Находит ВСЕ персоны (PER) в тексте, склеивает соседние спаны одного
    имени (см. _merge_adjacent_per_spans) и выбирает ближайшее к позиции
    маркера - та же логика, что и в Java-парсере, для честного сравнения.
    """
    doc = Doc(text)
    doc.segment(_segmenter)
    doc.tag_morph(_morph_tagger)
    doc.tag_ner(_ner_tagger)

    per_spans = [span for span in doc.spans if span.type == PER]
    if not per_spans:
        return None

    merged_ranges = _merge_adjacent_per_spans(per_spans, text)

    best_candidate = None
    best_distance = float("inf")

    for start, stop in merged_ranges:
        normalized = _normalize_name_slice(text[start:stop])

        cleaned = strip_leading_stop_word(normalized) if normalized else None
        if cleaned is None:
            continue

        distance = abs(start - marker_position)
        if distance < best_distance:
            best_distance = distance
            best_candidate = cleaned

    return best_candidate.title() if best_candidate else None


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