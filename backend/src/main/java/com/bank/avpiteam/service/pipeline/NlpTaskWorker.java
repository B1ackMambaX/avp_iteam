package com.bank.avpiteam.service.pipeline;

import com.bank.avpiteam.config.PipelineProperties;
import com.bank.avpiteam.domain.Operation;
import com.bank.avpiteam.repository.OperationRepository;
import com.bank.avpiteam.service.parsing.beneficiary.BeneficiaryDetectionResult;
import com.bank.avpiteam.service.parsing.beneficiary.BeneficiaryDetectionService;
import com.bank.avpiteam.service.pipeline.ParseResultWriter.ParseRecord;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Воркер очереди nlp_task: забирает задачи через FOR UPDATE SKIP LOCKED,
 * разбирает назначение платежа через BeneficiaryDetectionService (Python-сервис)
 * и переводит операцию в PARSED.
 *
 * HTTP-вызовы идут вне транзакции: захват задач и запись результатов -
 * две короткие транзакции, чтобы не держать соединение с БД, пока отвечает NLP.
 */
@Slf4j
@Service
public class NlpTaskWorker {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final OperationRepository operationRepository;
    private final BeneficiaryDetectionService detectionService;
    private final ParseResultWriter parseResultWriter;
    private final PipelineProperties.Nlp properties;
    private final ExecutorService executor;

    public NlpTaskWorker(NamedParameterJdbcTemplate jdbc,
                         PlatformTransactionManager transactionManager,
                         OperationRepository operationRepository,
                         BeneficiaryDetectionService detectionService,
                         ParseResultWriter parseResultWriter,
                         PipelineProperties properties) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.operationRepository = operationRepository;
        this.detectionService = detectionService;
        this.parseResultWriter = parseResultWriter;
        this.properties = properties.getNlp();
        this.executor = Executors.newFixedThreadPool(this.properties.getParallelism());
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public record BatchResult(int claimed, int done, int retried, int failed) {

        static final BatchResult EMPTY = new BatchResult(0, 0, 0, 0);
    }

    private record Claim(long taskId, long operationId, int attempts) {
    }

    private record Failure(Claim claim, String error) {
    }

    public BatchResult processBatch() {
        List<Claim> claims = tx.execute(status -> claim());
        if (claims == null || claims.isEmpty()) {
            return BatchResult.EMPTY;
        }

        Map<Long, Operation> operations = operationRepository
                .findAllById(claims.stream().map(Claim::operationId).toList())
                .stream()
                .collect(Collectors.toMap(Operation::getId, Function.identity()));

        // Запросы к NLP-сервису параллельно: время пачки - не сумма задержек, а примерно максимум
        List<Future<ParseRecord>> futures = new ArrayList<>(claims.size());
        for (Claim claim : claims) {
            Operation operation = operations.get(claim.operationId());
            futures.add(operation == null ? null : executor.submit(() -> detect(operation)));
        }

        List<Claim> succeeded = new ArrayList<>();
        List<ParseRecord> records = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        for (int i = 0; i < claims.size(); i++) {
            Claim claim = claims.get(i);
            Future<ParseRecord> future = futures.get(i);
            if (future == null) {
                failures.add(new Failure(claim, "Операция не найдена"));
                continue;
            }
            try {
                records.add(future.get());
                succeeded.add(claim);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.add(new Failure(claim, "Прервано"));
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                failures.add(new Failure(claim, cause.getClass().getSimpleName() + ": " + cause.getMessage()));
            }
        }

        int[] retriedAndFailed = tx.execute(status -> {
            saveSuccesses(succeeded, records);
            return saveFailures(failures);
        });
        BatchResult result = new BatchResult(claims.size(), succeeded.size(),
                retriedAndFailed[0], retriedAndFailed[1]);
        if (!failures.isEmpty()) {
            log.warn("NLP: {} задач с ошибкой (повтор {}, окончательно {}), первая: {}",
                    failures.size(), result.retried(), result.failed(), failures.get(0).error());
        }
        log.debug("NLP: {}", result);
        return result;
    }

    private List<Claim> claim() {
        return jdbc.query("""
                UPDATE nlp_task t
                SET status = 'IN_PROGRESS',
                    attempts = t.attempts + 1,
                    locked_until = now() + make_interval(secs => :lease),
                    updated_at = now()
                WHERE t.id IN (
                    SELECT id FROM nlp_task
                    WHERE (status = 'NEW' AND next_try_at <= now())
                       OR (status = 'IN_PROGRESS' AND locked_until < now())
                    ORDER BY id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING t.id, t.operation_id, t.attempts
                """,
                new MapSqlParameterSource("lease", properties.getLeaseSeconds())
                        .addValue("limit", properties.getBatchSize()),
                (rs, i) -> new Claim(rs.getLong("id"), rs.getLong("operation_id"), rs.getInt("attempts")));
    }

    private ParseRecord detect(Operation operation) {
        String purpose = operation.getPaymentPurpose();
        if (purpose == null || purpose.isBlank()) {
            // пустое назначение NLP-сервис не примет - разбирать нечего
            return new ParseRecord(operation.getId(), "NLP", properties.getParserVersion(),
                    false, false, null, null, "SEMANTIC", null);
        }
        BeneficiaryDetectionResult result = detectionService.detect(operation);
        return new ParseRecord(operation.getId(), "NLP", properties.getParserVersion(),
                result.isThirdParty(), result.isGenuineThirdParty(), result.extractedName(),
                result.classificationScore(), result.method().name(), result.classificationScore());
    }

    private void saveSuccesses(List<Claim> succeeded, List<ParseRecord> records) {
        if (succeeded.isEmpty()) {
            return;
        }
        parseResultWriter.insert(records);
        jdbc.update("""
                UPDATE nlp_task
                SET status = 'DONE', locked_until = NULL, last_error = NULL, updated_at = now()
                WHERE id IN (:ids)
                """, Map.of("ids", succeeded.stream().map(Claim::taskId).toList()));
        markParsed(succeeded.stream().map(Claim::operationId).toList());
    }

    /** @return [поставлено на повтор, окончательно FAILED] */
    private int[] saveFailures(List<Failure> failures) {
        int retried = 0;
        List<Long> failedOperations = new ArrayList<>();
        for (Failure failure : failures) {
            Claim claim = failure.claim();
            MapSqlParameterSource params = new MapSqlParameterSource("id", claim.taskId())
                    .addValue("error", truncate(failure.error()));
            if (claim.attempts() >= properties.getMaxAttempts()) {
                jdbc.update("""
                        UPDATE nlp_task
                        SET status = 'FAILED', locked_until = NULL, last_error = :error, updated_at = now()
                        WHERE id = :id
                        """, params);
                failedOperations.add(claim.operationId());
            } else {
                jdbc.update("""
                        UPDATE nlp_task
                        SET status = 'NEW', locked_until = NULL, last_error = :error,
                            next_try_at = now() + make_interval(secs => :delay), updated_at = now()
                        WHERE id = :id
                        """, params.addValue("delay", properties.getRetryDelaySeconds() * claim.attempts()));
                retried++;
            }
        }
        // Операция без разбора всё равно идёт в маппинг: автопривязки не будет (PARSE_UNAVAILABLE),
        // но кандидаты по ИНН и номеру договора оператор увидит
        markParsed(failedOperations);
        return new int[]{retried, failedOperations.size()};
    }

    private void markParsed(List<Long> operationIds) {
        if (operationIds.isEmpty()) {
            return;
        }
        jdbc.update("""
                UPDATE operations SET status = 'PARSED', updated_at = now()
                WHERE id IN (:ids) AND status = 'WAITING_NLP'
                """, Map.of("ids", operationIds));
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
