package com.bank.avpiteam.web;

import com.bank.avpiteam.domain.Operation.OperationStatus;
import com.bank.avpiteam.review.ContractRow;
import com.bank.avpiteam.review.ContractSearchService;
import com.bank.avpiteam.review.ManualDecisionService;
import com.bank.avpiteam.review.OperationFilter;
import com.bank.avpiteam.review.OperationQueryService;
import com.bank.avpiteam.review.OperationRow;
import com.bank.avpiteam.review.PageResult;
import com.bank.avpiteam.review.ResolveCommand;
import com.bank.avpiteam.service.mapping.MappingCard;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * REST-обёртка сервисов экранов «Операции» и «Ручной разбор» (Vaadin вызывает сервисы напрямую).
 * Права проверяются и в SecurityConfig, и в самих сервисах.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final OperationQueryService operations;
    private final ContractSearchService contracts;
    private final ManualDecisionService decisions;

    @GetMapping("/operations")
    public PageResult<OperationRow> operations(
            @RequestParam(required = false) List<OperationStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return operations.find(new OperationFilter(toSet(status), from, to, q, page, size));
    }

    @GetMapping("/review-queue")
    public PageResult<OperationRow> reviewQueue(
            @RequestParam(required = false) List<OperationStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return operations.reviewQueue(toSet(status), from, to, q, page, size);
    }

    @GetMapping("/contracts")
    public PageResult<ContractRow> contracts(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return contracts.search(q, page, size);
    }

    /** contractId = null - отклонение («договор не найден»); version - из прочитанной карточки. */
    public record ResolveRequest(Long contractId, String comment, Long version) {
    }

    @PostMapping("/operations/{id}/mapping/resolve")
    public MappingCard resolve(@PathVariable long id, @RequestBody ResolveRequest body) {
        return decisions.resolve(new ResolveCommand(id, body.contractId(), body.comment(), body.version()));
    }

    private static Set<OperationStatus> toSet(List<OperationStatus> statuses) {
        return statuses == null || statuses.isEmpty() ? Set.of() : EnumSet.copyOf(statuses);
    }
}
