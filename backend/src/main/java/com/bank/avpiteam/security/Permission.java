package com.bank.avpiteam.security;

public enum Permission {
    VIEW_DATA,          // операции, договоры, клиенты, стороны, кандидаты, результаты парсинга
    VIEW_NLP_TASKS,     // nlp_task и last_error
    RUN_PIPELINE,
    EDIT_MAPPING,       // подтверждение, изменение, отмена привязки
    IMPORT_OPERATIONS,
    EDIT_REFERENCE,     // клиенты, договоры, стороны договора
    DELETE_REFERENCE,   // только без ссылок (матрица п. 4.2, 4.3)
    RECOVER_STATUS,     // служебные переходы статусов, только ADMIN
    RESET_NLP_TASK,
    MANAGE_NLP,
    MANAGE_USERS,
    EXPORT_DATA         // только то, что доступно для просмотра
}