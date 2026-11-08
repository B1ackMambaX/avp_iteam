import re
import numpy as np
from natasha import NewsEmbedding
from app.metrics import EMBEDDING_SECONDS
from app.gate_patterns import get_gate_patterns
_emb = NewsEmbedding()

POSITIVE_EXAMPLES = [
    "платёж совершён в интересах другого лица",
    "оплата произведена за счёт третьего лица",
    "денежные средства перечислены правопреемнику",
    "платёж по доверенности от имени другого человека",
    "исполнение обязательства вместо контрагента третьей стороной",
    "перевод в пользу выгодоприобретателя по договору",
    "оплата произведена в пользу другого лица",
]

NEGATIVE_EXAMPLES = [
    # обычные расчётные платежи
    "оплата по счету за товары и услуги",
    "оплата по счету за консультационные услуги",
    "заработная плата сотруднику",
    "погашение кредита по собственному договору",
    "оплата коммунальных услуг",
    "перевод между своими счетами",
    # договоры поставки/подряда/аренды — отдельная категория
    "оплата по договору поставки товара",
    "оплата по договору подряда за выполненные работы",
    "оплата аренды помещения по договору",
    # компенсации/неустойки — не про третье лицо, про возмещение
    "компенсация морального ущерба по решению суда",
    "выплата неустойки за нарушение обязательств",
    "возмещение ущерба по страховому случаю",
    # взносы/налоги
    "уплата налога на доходы физических лиц",
    "взнос по договору негосударственного пенсионного обеспечения",
]

# ВАЖНО: добавлены "польз" и "выгодоприобретател" — это прямые маркеры,
# которые заказчик назвал как используемые в legacy (ответ на вопрос 6).
# Без них классификация пропускает канонические фразы "в пользу ...".


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


def _has_trigger_word(lower_text: str, trigger_re) -> bool:
    return trigger_re.search(lower_text) is not None


def _has_negation_near_trigger(lower_text: str, trigger_re, negation_re, window: int = NEGATION_WINDOW) -> bool:
    for match in trigger_re.finditer(lower_text):
        start = max(0, match.start() - window)
        end = min(len(lower_text), match.end() + window)
        surrounding = lower_text[start:end]
        if negation_re.search(surrounding):
            return True
    return False


_positive_vectors = [_sentence_vector(p) for p in POSITIVE_EXAMPLES]
_negative_vectors = [_sentence_vector(n) for n in NEGATIVE_EXAMPLES]


def classify_by_similarity(text: str, threshold: float = 0.5, trigger_re=None, negation_re=None) -> dict:
    if trigger_re is None or negation_re is None:
        default_trigger, default_negation = get_gate_patterns()
        if trigger_re is None:
            trigger_re = default_trigger
        if negation_re is None:
            negation_re = default_negation

    lower = text.lower()

    if not _has_trigger_word(lower, trigger_re):
        return {
            "third_party": False,
            "positive_similarity": None,
            "negative_similarity": None,
            "negated": False,
            "no_trigger": True,
            "decision": "GATED"
        }

    if _has_negation_near_trigger(lower, trigger_re, negation_re):
        return {
            "third_party": False,
            "positive_similarity": None,
            "negative_similarity": None,
            "negated": True,
            "no_trigger": False,
            "decision": "NEGATED"
        }

    with EMBEDDING_SECONDS.time():
        vec = _sentence_vector(text)
        max_positive_sim = max(_cosine_similarity(vec, p) for p in _positive_vectors)
        max_negative_sim = max(_cosine_similarity(vec, n) for n in _negative_vectors)
        is_third_party = max_positive_sim > threshold and max_positive_sim > max_negative_sim

    return {
        "third_party": is_third_party,
        "positive_similarity": round(max_positive_sim, 3),
        "negative_similarity": round(max_negative_sim, 3),
        "negated": False,
        "no_trigger": False,
        "decision": "SEMANTIC"
    }