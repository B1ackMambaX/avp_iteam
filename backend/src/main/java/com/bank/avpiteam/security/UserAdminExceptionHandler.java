package com.bank.avpiteam.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UserAdminController.class)
class UserAdminExceptionHandler {

    @ExceptionHandler(UserErrors.Validation.class)
    ProblemDetail validation(UserErrors.Validation e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        pd.setProperty("errors", e.errors());
        return pd;
    }

    @ExceptionHandler(UserErrors.NotFound.class)
    ProblemDetail notFound(UserErrors.NotFound e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UserErrors.RuleViolation.class)
    ProblemDetail conflict(UserErrors.RuleViolation e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}