package com.bank.avpiteam.demo;

import com.bank.avpiteam.domain.Operation.OperationStatus;

/**
 * Сценарии демо-операций. Для каждого известен правильный ответ маппинга -
 * он зашит в reference операции: DEMO-{сценарий}-{ожидаемый статус}-{ожидаемый договор}-{номер}.
 */
enum DemoScenario {

    // Сторона операции - участник договора, номер договора есть в назначении
    NUMBER_AND_PARTY(35, OperationStatus.AUTO_MAPPED),
    // Номера нет, но у стороны операции единственный договор
    SINGLE_CONTRACT(15, OperationStatus.AUTO_MAPPED),
    // Номера нет, у стороны несколько договоров - выбрать нельзя
    AMBIGUOUS(10, OperationStatus.AMBIGUOUS),
    // Платёж за третье лицо: номер договора + ФИО выгодоприобретателя
    THIRD_PARTY_NUMBER(10, OperationStatus.AUTO_MAPPED),
    // Платёж за третье лицо без номера: договор определяется по ФИО выгодоприобретателя
    THIRD_PARTY_NAME(10, OperationStatus.AUTO_MAPPED),
    // В назначении "выгодоприобретатель" - сам плательщик: не третье лицо (вопрос 6)
    FALSE_THIRD_PARTY(5, OperationStatus.AUTO_MAPPED),
    // Обе стороны не клиенты банка, номера договора в базе нет
    NOT_FOUND(15, OperationStatus.NOT_FOUND);

    final int percent;
    final OperationStatus expected;

    DemoScenario(int percent, OperationStatus expected) {
        this.percent = percent;
        this.expected = expected;
    }
}
