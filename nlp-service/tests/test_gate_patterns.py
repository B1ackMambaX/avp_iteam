import pytest
from app.gate_patterns import (
    DEFAULT_NEGATION_WORDS, DEFAULT_TRIGGER_WORDS, get_gate_patterns,
)

LEGACY_TRIGGER = (r"\b(треть\w*|лиц\w*|интерес\w*|счет\w*|правопреемник\w*|"
                  r"довереннос\w*|цессионар\w*|контрагент\w*|польз\w*|выгодоприобретател\w*)\b")
LEGACY_NEGATION = r"\b(не|нет|отсутств\w*|без)\b"


def test_defaults_identical_to_legacy_patterns():
    t, n = get_gate_patterns()
    assert t.pattern == LEGACY_TRIGGER
    assert n.pattern == LEGACY_NEGATION

def test_explicit_defaults_equal_none():
    t1, n1 = get_gate_patterns()
    t2, n2 = get_gate_patterns(list(DEFAULT_TRIGGER_WORDS), list(DEFAULT_NEGATION_WORDS))
    assert t1 is t2 and n1 is n2

def test_custom_wildcard_and_exact():
    t, n = get_gate_patterns(["фидуциар*"], ["никак"])
    assert t.search("в фидуциарных интересах")
    assert not t.search("за третье лицо")
    assert n.search("никак не") is not None
    assert not n.search("никакой")

def test_negation_word_boundary():
    _, n = get_gate_patterns()
    assert n.search("ндс не облагается")
    assert not n.search("некий договор")

def test_special_chars_are_escaped():
    t, _ = get_gate_patterns(["ab-c"])
    assert t.search("ab-c")
    t2, _ = get_gate_patterns(["a.b"])
    assert not t2.search("axb")

@pytest.mark.parametrize("bad", [["*"], ["а"], ["а*"], ["x" * 51], [], ["  "]])
def test_rejects_bad_words(bad):
    with pytest.raises(ValueError):
        get_gate_patterns(bad)