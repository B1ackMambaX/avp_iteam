"""ФИО -> именительный падеж с согласованием по роду. None, если не уверены."""
import re
from app.name_completeness import _INITIAL, _analyzer

_PATR_FEM = re.compile(r"(?:овн|евн|ичн)(?:а|ы|е|у|ой|ою)$")
_PATR_MASC = re.compile(r"(?:ович|евич|ьич)(?:а|у|ем|е)?$")
_ROLES = {"Surn", "Name", "Patr"}

def _gender(tokens: list[str]) -> str | None:
    last = tokens[-1].lower()
    if _PATR_FEM.search(last): return "femn"
    if _PATR_MASC.search(last): return "masc"
    for tok in tokens:
        if _INITIAL.match(tok): continue
        for parse in _analyzer().parse(tok)[:3]:
            if "Name" in parse.tag and parse.tag.gender in ("masc", "femn"):
                return parse.tag.gender
    return None

def _nomn(token: str, role: str, gender: str) -> str | None:
    """role: 'Surn' | 'Name' | 'Patr'; gender: 'masc' | 'femn'.
    Только единственное число, только нужная роль и род.
    Обычные разборы предпочитаем неизменяемым (Fixd)."""
    if "-" in token:
        return None
    cands = [p for p in _analyzer().parse(token)
             if role in p.tag and gender in p.tag and "sing" in p.tag]
    # стабильная сортировка: не-Fixd первыми
    cands.sort(key=lambda p: "Fixd" in p.tag)
    for p in cands:
        form = p.inflect({"nomn"})
        if form is not None:
            return form.word.capitalize()
    return None

def _role_variants(tokens: list[str]) -> list[list[str]]:
    if len(tokens) == 3:
        return [["Surn", "Name", "Patr"]]
    last = tokens[-1].lower()
    if _PATR_FEM.search(last) or _PATR_MASC.search(last):
        return [["Surn", "Patr"], ["Name", "Patr"]]
    return [["Surn", "Name"], ["Name", "Surn"]]


def _apply_roles(tokens: list[str], roles: list[str], gender: str) -> str | None:
    out = []
    for tok, role in zip(tokens, roles):
        if _INITIAL.match(tok):
            out.append(tok)
            continue
        form = _nomn(tok, role, gender)
        if form is None:
            return None
        out.append(form)
    return " ".join(out)


def normalize_fio_nominative(text: str) -> str | None:
    tokens = text.split()
    if not 2 <= len(tokens) <= 3:
        return None
    gender = _gender(tokens)
    if gender is None:
        return None
    results = {
        r for r in (_apply_roles(tokens, roles, gender)
                    for roles in _role_variants(tokens))
        if r is not None
    }
    return results.pop() if len(results) == 1 else None