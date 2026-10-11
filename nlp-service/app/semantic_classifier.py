import re
import numpy as np
from natasha import NewsEmbedding

_emb = NewsEmbedding()

POSITIVE_EXAMPLES = [
    "платёж совершён в интересах другого лица",
    "оплата произведена за счёт третьего лица",
    "денежные средства перечислены правопреемнику",
    "платёж по доверенности от имени другого человека",
    "исполнение обязательства вместо контрагента третьей стороной",
    "перевод в пользу выгодоприобретателя по договору",
]

NEGATIVE_EXAMPLES = [
    "оплата по счету за товары и услуги",
    "заработная плата сотруднику",
    "погашение кредита по собственному договору",
    "оплата коммунальных услуг",
    "перевод между своими счетами",
    "компенсация морального ущерба по решению суда",
    "выплата неустойки за нарушение обязательств",
    "возмещение ущерба по страховому случаю",
]

TRIGGER_WORDS_PATTERN = re.compile(
    r"\b(треть\w*|лиц\w*|интерес\w*|счет\w*|правопреемник\w*|"
    r"довереннос\w*|цессионар\w*|контрагент\w*)\b"
)

# ИСПРАВЛЕНО: границы слова через \b — "не" внутри "несовершеннолетнего"
# или "исполнение" больше не матчится, т.к. там нет границы слова
# непосредственно перед/после этих двух букв.
NEGATION_PATTERN = re.compile(r"\b(не|нет|отсутств\w*|без)\b")

NEGATION_WINDOW = 20


def _sentence_vector(text: str) -> np.ndarray:
    words = text.lower().split()
    vectors = [_emb[w] for w in words if w in _emb.vocab]
    if not vectors:
        return np.zeros(_emb.vocab.dim)
    return np.mean(vectors, axis=0)


def _cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
    denom = (np.linalg.norm(a) * np.linalg.norm(b))
    if denom == 0:
        return 0.0
    return float(np.dot(a, b) / denom)


def _has_negation_near_trigger(lower_text: str, window: int = NEGATION_WINDOW) -> bool:
    """
    ИСПРАВЛЕНО: теперь проверяем окно с ОБЕИХ сторон от триггера
    (а не только слева) — закрывает случай "третьи лица НЕ
    привлекались", где отрицание идёт после ключевой фразы.
    Плюс используем regex с границами слова вместо `in`-поиска
    подстроки — закрывает ложные срабатывания на "несовершеннолетнего"
    и "исполнение", которые содержат "не" как часть другого слова.
    """
    for match in TRIGGER_WORDS_PATTERN.finditer(lower_text):
        start = max(0, match.start() - window)
        end = min(len(lower_text), match.end() + window)
        surrounding = lower_text[start:end]
        if NEGATION_PATTERN.search(surrounding):
            return True
    return False


_positive_vectors = [_sentence_vector(p) for p in POSITIVE_EXAMPLES]
_negative_vectors = [_sentence_vector(n) for n in NEGATIVE_EXAMPLES]


def classify_by_similarity(text: str, threshold: float = 0.5) -> dict:
    lower = text.lower()

    if _has_negation_near_trigger(lower):
        return {
            "third_party": False,
            "positive_similarity": None,
            "negative_similarity": None,
            "negated": True,
        }

    vec = _sentence_vector(text)
    max_positive_sim = max(_cosine_similarity(vec, p) for p in _positive_vectors)
    max_negative_sim = max(_cosine_similarity(vec, n) for n in _negative_vectors)

    is_third_party = max_positive_sim > threshold and max_positive_sim > max_negative_sim

    return {
        "third_party": is_third_party,
        "positive_similarity": round(max_positive_sim, 3),
        "negative_similarity": round(max_negative_sim, 3),
        "negated": False,
    }