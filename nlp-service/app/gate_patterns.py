import re
from functools import lru_cache

MIN_STEM, MAX_STEM = 2, 50

DEFAULT_TRIGGER_WORDS = (
    "треть*", "лиц*", "интерес*", "счет*", "правопреемник*",
    "довереннос*", "цессионар*", "контрагент*", "польз*", "выгодоприобретател*",
)
DEFAULT_NEGATION_WORDS = ("не", "нет", "отсутств*", "без")

def _alternation(words) -> str:
    parts = []
    for raw in words:
        w = raw.strip().lower()
        wildcard = w.endswith("*")
        stem = w[:-1] if wildcard else w
        if not (MIN_STEM <= len(stem) <= MAX_STEM):
            raise ValueError(f"word stem must be {MIN_STEM}-{MAX_STEM} chars: {raw!r}")
        parts.append(re.escape(stem) + (r"\w*" if wildcard else ""))
    if not parts:
        raise ValueError("empty word list")
    return "|".join(parts)

@lru_cache(maxsize=32)
def _trigger(words: tuple) -> re.Pattern:
    return re.compile(r"\b(" + _alternation(words) + r")\b")

@lru_cache(maxsize=32)
def _negation(words: tuple) -> re.Pattern:
    return re.compile(r"\b(" + _alternation(words) + r")\b")

def get_gate_patterns(trigger_words=None, negation_words=None):
    """None -> слова по умолчанию. Паттерны кешируются по кортежу слов."""
    t = DEFAULT_TRIGGER_WORDS if trigger_words is None else tuple(trigger_words)
    n = DEFAULT_NEGATION_WORDS if negation_words is None else tuple(negation_words)
    return _trigger(t), _negation(n)