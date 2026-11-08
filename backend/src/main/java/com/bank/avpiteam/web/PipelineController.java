package com.bank.avpiteam.web;

import com.bank.avpiteam.service.mapping.MappingQueryService;
import com.bank.avpiteam.service.pipeline.PipelineRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineRunner runner;
    private final MappingQueryService mappingQueryService;

    /** Прогнать конвейер по всем новым операциям: разбор назначения + маппинг. */
    @PostMapping("/pipeline/run")
    public PipelineRunner.RunReport run(@RequestParam(defaultValue = "600") int maxSeconds) {
        return runner.runUntilDrained(Duration.ofSeconds(maxSeconds));
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
