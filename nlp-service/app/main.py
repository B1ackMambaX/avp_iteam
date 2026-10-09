"""
FastAPI-сервис для разбора назначения платежа.
Java-приложение будет обращаться сюда по HTTP в реальном пайплайне.
На этапе проверки гипотез мы НЕ гоняем нагрузочный тест через HTTP -
это добавляет сетевые накладные расходы и смажет чистое сравнение
алгоритмов. HTTP-слой понадобится позже, при сборке MVP.
"""

from fastapi import FastAPI
from pydantic import BaseModel

from app.parser import parse_payment_purpose

app = FastAPI(title="AVP iTeam NLP Service")


class ParseRequest(BaseModel):
    text: str


class ParseResponse(BaseModel):
    third_party: bool
    beneficiary_name: str | None
    confidence: float


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/parse", response_model=ParseResponse)
def parse(request: ParseRequest):
    result = parse_payment_purpose(request.text)
    return ParseResponse(**result)