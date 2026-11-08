"""Полнота извлечённого ФИО. Это эвристика, а не факт."""
import re
from enum import Enum

try:
    import pymorphy3 as _pymorphy
except ImportError:
    import pymorphy2 as _pymorphy


class NameCompleteness(str, Enum):
    FULL = "FULL"                        # фамилия + имя + отчество
    NO_PATRONYMIC = "NO_PATRONYMIC"      # фамилия + имя
    NO_FIRST_NAME = "NO_FIRST_NAME"      # фамилия + отчество
    SURNAME_INITIALS = "SURNAME_INITIALS"  # фамилия + инициалы
    NO_SURNAME = "NO_SURNAME"            # имя + отчество
    SINGLE = "SINGLE"                    # одно слово
    UNKNOWN = "UNKNOWN"


# Фамилия несёт основную различающую силу, поэтому без неё сигнал слабый.
STRENGTH = {
    NameCompleteness.FULL: "STRONG",
    NameCompleteness.NO_PATRONYMIC: "MEDIUM",
    NameCompleteness.NO_FIRST_NAME: "MEDIUM",
    NameCompleteness.SURNAME_INITIALS: "MEDIUM",
    NameCompleteness.NO_SURNAME: "WEAK",
    NameCompleteness.SINGLE: "WEAK",
    NameCompleteness.UNKNOWN: "WEAK",
}

# И | И. | И.П | И.П.   (ИП без точек инициалами не считаем)
_INITIAL = re.compile(r"^[А-ЯЁA-Z](?:\.(?:[А-ЯЁA-Z]\.?)?)?$")
# Отчество по окончанию, с возможным падежным хвостом
_PATRONYMIC = re.compile(
    r"(?:ович|евич|ьич|ич|овна|евна|ична|инична)(?:а|у|ем|е|ы|ой)?$")

_morph = None


def _analyzer():
    global _morph
    if _morph is None:
        _morph = _pymorphy.MorphAnalyzer()
    return _morph


def _role(token: str) -> str:
    """I инициал, P отчество, S фамилия, N имя, ? неизвестно."""
    if _INITIAL.match(token):
        return "I"
    if _PATRONYMIC.search(token.lower()):
        return "P"
    for parse in _analyzer().parse(token)[:3]:
        if "Surn" in parse.tag:
            return "S"
        if "Name" in parse.tag:
            return "N"
        if "Patr" in parse.tag:
            return "P"
    return "?"


def classify_name(name: str) -> NameCompleteness:
    tokens = name.split()
    if not tokens:
        return NameCompleteness.UNKNOWN
    if len(tokens) == 1:
        return NameCompleteness.SINGLE
    roles = [_role(t) for t in tokens]
    # Единственное незнакомое слово рядом с известными ролями считаем фамилией
    if roles.count("?") == 1 and "S" not in roles:
        roles[roles.index("?")] = "S"
    has = set(roles)
    if {"S", "N", "P"} <= has:
        return NameCompleteness.FULL
    if {"S", "N"} <= has:
        return NameCompleteness.NO_PATRONYMIC
    if {"S", "P"} <= has:
        return NameCompleteness.NO_FIRST_NAME
    if {"S", "I"} <= has:
        return NameCompleteness.SURNAME_INITIALS
    if {"N", "P"} <= has:
        return NameCompleteness.NO_SURNAME
    return NameCompleteness.UNKNOWN