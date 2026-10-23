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
    # договоры поставки/подряда/аренды — отдельная категория,
    # именно она давала ложные срабатывания на проде
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
TRIGGER_WORDS_PATTERN = re.compile(
    r"\b(треть\w*|лиц\w*|интерес\w*|счет\w*|правопреемник\w*|"
    r"довереннос\w*|цессионар\w*|контрагент\w*|польз\w*|выгодоприобретател\w*)\b"
)

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


def _has_trigger_word(lower_text: str) -> bool:
    return TRIGGER_WORDS_PATTERN.search(lower_text) is not None


def _has_negation_near_trigger(lower_text: str, window: int = NEGATION_WINDOW) -> bool:
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

    # НОВОЕ: gate по триггерным словам. Если в тексте вообще нет
    # ни одного маркера третьего лица — не считаем сходство, сразу False.
    # Это убирает ложные срабатывания на обычных финансовых текстах
    # ("оплата по договору поставки", "компенсация морального ущерба"),
    # которые раньше получали высокий score из-за усреднения частых слов.
    if not _has_trigger_word(lower):
        return {
            "third_party": False,
            "positive_similarity": None,
            "negative_similarity": None,
            "negated": False,
            "no_trigger": True,
        }

    if _has_negation_near_trigger(lower):
        return {
            "third_party": False,
            "positive_similarity": None,
            "negative_similarity": None,
            "negated": True,
            "no_trigger": False,
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
        "no_trigger": False,
    }