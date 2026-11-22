package com.bank.avpiteam.web;

import com.bank.avpiteam.review.ManualDecisionException;
import com.bank.avpiteam.review.ValidationFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/** Ошибки экранов операций и ручного разбора в формате RFC 7807 (как в api-contract.md). */
@RestControllerAdvice(assignableTypes = ReviewController.class)
class ReviewExceptionHandler {

    @ExceptionHandler(ValidationFailedException.class)
    ProblemDetail validation(ValidationFailedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", e.errors().entrySet().stream()
                .map(err -> Map.of("field", err.getKey(), "message", err.getValue()))
                .toList());
        return problem;
    }

    @ExceptionHandler(ManualDecisionException.class)
    ProblemDetail decision(ManualDecisionException e) {
        HttpStatus status = switch (e.code()) {
            case OPERATION_NOT_FOUND, CONTRACT_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN_TRANSITION, STALE_CARD -> HttpStatus.CONFLICT;
            case CONTRACT_NOT_ALLOWED -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setProperty("code", e.code().name());
        if (e.code() == ManualDecisionException.Code.CONTRACT_NOT_ALLOWED) {
            problem.setProperty("errors", List.of(Map.of("field", "comment", "message", e.getMessage())));
        }
        return problem;
    }
}
