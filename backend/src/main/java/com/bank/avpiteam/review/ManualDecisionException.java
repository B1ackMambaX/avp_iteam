package com.bank.avpiteam.review;

/**
 * Отказ в ручном решении. По коду Vaadin различает ситуации без разбора текста сообщения.
 * Отсутствие права EDIT_MAPPING - стандартный AccessDeniedException Spring Security.
 */
public class ManualDecisionException extends RuntimeException {

    public enum Code {
        OPERATION_NOT_FOUND,    // операции с таким id нет
        CONTRACT_NOT_FOUND,     // договора с таким id нет
        FORBIDDEN_TRANSITION,   // операция не в очереди ручного разбора (уже решена или ещё в обработке)
        CONTRACT_NOT_ALLOWED,   // договор не из кандидатов, а комментарий не указан
        STALE_CARD              // карточку изменили после того, как её прочитали
    }

    private final Code code;

    public ManualDecisionException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
