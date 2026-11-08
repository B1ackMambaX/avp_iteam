import pytest
from app.name_completeness import classify_name, NameCompleteness as C

@pytest.mark.parametrize("name, expected", [
    ("Иванов Иван Иванович", C.FULL),
    ("Иван Петрович", C.NO_SURNAME),
    ("Петровна Анна", C.NO_SURNAME),
    ("Петрович Сидоров", C.NO_FIRST_NAME),
    ("Сидоров И.П.", C.SURNAME_INITIALS),
    ("Иван", C.SINGLE),
    ("Сидоров Иван", C.NO_PATRONYMIC),
])
def test_classify(name, expected):
    assert classify_name(name) == expected