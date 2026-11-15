---
title: Бенчмарки гипотез А и Б
status: актуально
---
# Бенчмарки гипотез парсинга

Перенесено из корневого README. Здесь — как запустить замеры точности и
производительности двух подходов к разбору назначения платежа:

- **Гипотеза А** — алгоритмический regex-парсер (Java, `RegexPaymentPurposeParser`);
- **Гипотеза Б** — NLP-парсер на Natasha (Python, `nlp-service`).

Итоги сравнения (точность, производительность, ограничения подходов) —
в [nlp-hypothesis-result.md](nlp-hypothesis-result.md). Прогоны на eval-сетах
с гибридом B — [nlp-eval-set-rules.md](nlp-eval-set-rules.md) и
[nlp-hybrid-results.md](nlp-hybrid-results.md).

Датасеты: `data/test-dataset.csv` (основной, 100 строк) и
`data/holdout-dataset.csv` (15 строк, лексика вне словаря маркеров).

## 1. Бенчмарк гипотезы А (Java)

Поднимите БД (`docker-compose up -d`), затем из `backend/`:

**Через IntelliJ:** `Run → Edit Configurations → Active profiles: benchmark-a` → Run

**Через терминал:**

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=benchmark-a
```

Результат печатается в консоль двумя блоками: `ТОЧНОСТЬ (Гипотеза А)` —
метрики на размеченном датасете и `ФАЗА 2: НАГРУЗОЧНАЯ ЧАСТЬ` —
производительность (throughput, CPU, память).

Путь к датасету и число итераций настраиваются в
`backend/src/main/resources/application-benchmark-a.yml`.

> Внимание: закоммиченный путь — абсолютный, на машине автора профиля
> (`C:/Users/darkh/...`). Перед запуском на другой машине подставьте свой
> путь к `data/test-dataset.csv` (рядом ищется и `holdout-dataset.csv`).

### Снятие метрик CPU/RAM

Перед нагрузочной частью есть пауза 10 секунд — за это время подключитесь
к процессу через **JConsole** (идёт с JDK: команда `jconsole` из терминала),
`jps` или VisualVM. PID процесса выводится в лог перед паузой.

## 2. Бенчмарк гипотезы Б (Python)

Окружение — то же, что для NLP-сервиса: venv и `pip install -r requirements.txt`,
см. README, раздел «NLP-сервис (полный режим)». Установка займёт несколько
минут — Natasha скачивает предобученные embedding-модели.

### Запуск бенчмарка

```bash
# venv должен быть активирован
python benchmark/run_benchmark.py
python benchmark/evaluate_holdout.py   # отдельно по holdout-датасету
```

Аналогично Java-версии печатает точность и метрики производительности.
PID процесса выводится перед 10-секундной паузой для подключения системного
монитора (Диспетчер задач / Activity Monitor / htop).

## 3. Прогон на eval-сетах (живой сервис)

Бенчмарки выше работают in-process. Против запущенного `nlp-service`
(порт 8001) гоняются eval-скрипты — см. шапку `nlp-service/eval/run_eval.py`
и раздел 2 [load-testing.md](load-testing.md).
