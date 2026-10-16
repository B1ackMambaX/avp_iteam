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

app = FastAPI(title="AVP iTeam NLP Service")


class ParseRequest(BaseModel):
    text: str


class ParseResponse(BaseModel):
    third_party: bool
    beneficiary_name: str | None
    confidence: float

class TextRequest(BaseModel):
    text: str

class ClassifyResponse(BaseModel):
    isThirdParty: bool
    score: float

class ExtractResponse(BaseModel):
    fullName: str | None

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/classify", response_model=ClassifyResponse)
def classify(request: TextRequest):
    result = classify_by_similarity(request.text)
    return ClassifyResponse(
        isThirdParty=result["third_party"],
        score=float(result["positive_similarity"] or 0.0),
    )

@app.post("/extract-name", response_model=ExtractResponse)
def extract_name(request: TextRequest):
    name = extract_beneficiary_name(request.text, 0)
    return ExtractResponse(fullName=name)

@app.post("/parse")
def parse(request: TextRequest):
    return parse_payment_purpose(request.text)

