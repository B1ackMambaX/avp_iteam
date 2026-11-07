package com.bank.avpiteam.settings;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {NlpSettingsController.class, NlpTestController.class})
class NlpSettingsExceptionHandler {

    @ExceptionHandler(NlpSettingsErrors.Validation.class)
    ProblemDetail validation(NlpSettingsErrors.Validation e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Некорректные настройки NLP");
        pd.setTitle("Validation failed");
        pd.setProperty("errors", e.fields());
        return pd;
    }

    @ExceptionHandler(NlpSettingsErrors.Conflict.class)
    ProblemDetail conflict(NlpSettingsErrors.Conflict e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Настройки изменены другим пользователем, обновите страницу");
        pd.setTitle("Version conflict");
        pd.setProperty("currentVersion", e.currentVersion());
        return pd;
    }

    @ExceptionHandler(NlpSettingsErrors.NotFound.class)
    ProblemDetail notFound(NlpSettingsErrors.NotFound e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        pd.setTitle("Version not found");
        return pd;
    }

    @ExceptionHandler(BadTestInput.class)
    ProblemDetail badInput(BadTestInput e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NlpAdminUnavailable.class)
    ProblemDetail unavailable(NlpAdminUnavailable e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}