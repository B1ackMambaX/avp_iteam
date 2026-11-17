package com.bank.avpiteam.web;

import com.bank.avpiteam.service.mapping.MappingQueryService;
import com.bank.avpiteam.service.pipeline.PipelineRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineRunner runner;
    private final MappingQueryService mappingQueryService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Прогнать конвейер по всем новым операциям: разбор назначения + маппинг. */
    @PostMapping("/pipeline/run")
    public ResponseEntity<?> run(@RequestParam(defaultValue = "600") int maxSeconds) {
        if (!running.compareAndSet(false, true)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ProblemDetail.forStatusAndDetail(
                            HttpStatus.CONFLICT, "Конвейер уже запущен"));
        }
        try {
            int limit = Math.max(1, Math.min(maxSeconds, 3600));
            return ResponseEntity.ok(runner.runUntilDrained(Duration.ofSeconds(limit)));
        } finally {
            running.set(false);
        }
    }

    /** Сколько операций в каком статусе, состояние очереди NLP. */
    @GetMapping("/pipeline/stats")
    public Map<String, Object> stats() {
        return runner.stats();
    }

    /** Разбор и кандидаты по одной операции. */
    @GetMapping("/operations/{id}/mapping")
    public ResponseEntity<MappingQueryService.OperationMapping> operationMapping(@PathVariable long id) {
        return ResponseEntity.of(mappingQueryService.find(id));
    }
}