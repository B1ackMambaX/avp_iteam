"""
FastAPI-сервис для разбора назначения платежа.
Java-приложение будет обращаться сюда по HTTP в реальном пайплайне.
На этапе проверки гипотез мы НЕ гоняем нагрузочный тест через HTTP -
это добавляет сетевые накладные расходы и смажет чистое сравнение
алгоритмов. HTTP-слой понадобится позже, при сборке MVP.
"""
from fastapi import FastAPI
from pydantic import BaseModel
from app.parser import parse_payment_purpose, extract_beneficiary_name
from app.semantic_classifier import classify_by_similarity
from prometheus_fastapi_instrumentator import Instrumentator
from app.metrics import CLASSIFY_TOTAL, EXTRACT_NAME_TOTAL
from app.gate_patterns import get_gate_patterns
from pydantic import BaseModel, Field
from fastapi import FastAPI, HTTPException
from app.semantic_classifier import POSITIVE_EXAMPLES, NEGATIVE_EXAMPLES

app = FastAPI(title="AVP iTeam NLP Service")

Instrumentator(excluded_handlers=["/metrics"]).instrument(app).expose(
    app, endpoint="/metrics", include_in_schema=False
)

class ParseRequest(BaseModel):
    text: str


class ParseResponse(BaseModel):
    third_party: bool
    beneficiary_name: str | None
    confidence: float

class TextRequest(BaseModel):
    text: str
    threshold: float = 0.5
    triggerWords: list[str] | None = Field(default=None, min_length=1, max_length=100)
    negationWords: list[str] | None = Field(default=None, min_length=1, max_length=100)

class ClassifyResponse(BaseModel):
    isThirdParty: bool
    score: float
    decision: str

class ExtractResponse(BaseModel):
    fullName: str | None

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/classify", response_model=ClassifyResponse)
def classify(request: TextRequest):
    try:
        trigger_re, negation_re = get_gate_patterns(request.triggerWords, request.negationWords)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))

    result = classify_by_similarity(
        request.text,
        threshold=request.threshold,
        trigger_re=trigger_re,
        negation_re=negation_re
    )

    CLASSIFY_TOTAL.labels(
        decision=result["decision"],
        is_third_party=str(result["third_party"]).lower(),
    ).inc()

    return ClassifyResponse(
        isThirdParty=result["third_party"],
        score=float(result["positive_similarity"] or 0.0),
        decision=result["decision"]
    )

@app.post("/extract-name", response_model=ExtractResponse)
def extract_name(request: TextRequest):
    name = extract_beneficiary_name(request.text, 0)

    EXTRACT_NAME_TOTAL.labels(found="true" if name else "false").inc()

    return ExtractResponse(fullName=name)

@app.post("/parse")
def parse(request: TextRequest):
    return parse_payment_purpose(request.text)

@app.get("/examples")
def examples():
    return {"positive": list(POSITIVE_EXAMPLES), "negative": list(NEGATIVE_EXAMPLES)}