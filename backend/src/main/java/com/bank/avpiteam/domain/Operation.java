package com.bank.avpiteam.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Операция (платёж). Атрибуты соответствуют структуре,
 * присланной заказчиком.
 */
@Entity
@Table(name = "operations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "operation_seq")
    @SequenceGenerator(name = "operation_seq", sequenceName = "operation_seq", allocationSize = 100)
    private Long id;

    // Референс проводки - уникальный бизнес-ключ операции (не наш internal id)
    @Column(name = "reference", nullable = false, unique = true, length = 100)
    private String reference;

    @Column(name = "operation_date", nullable = false)
    private LocalDateTime operationDate;

    //Отправитель

    @Column(name = "sender_pin", length = 6)
    private String senderPin;

    @Column(name = "sender_branch", length = 4)
    private String senderBranch;

    @Column(name = "sender_inn", length = 12)
    private String senderInn;

    @Column(name = "sender_account", length = 20)
    private String senderAccount;

    @Column(name = "sender_name", length = 500)
    private String senderName;

    // Получатель

    @Column(name = "receiver_pin", length = 6)
    private String receiverPin;

    @Column(name = "receiver_branch", length = 4)
    private String receiverBranch;

    @Column(name = "receiver_inn", length = 12)
    private String receiverInn;

    @Column(name = "receiver_account", length = 20)
    private String receiverAccount;

    @Column(name = "receiver_name", length = 500)
    private String receiverName;

    @Column(name = "currency_code", nullable = false, length = 3)
    @Builder.Default
    private String currencyCode = "643"; // RUB по ISO 4217 / ОКВ

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_rub", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountRub;

    @Column(name = "payment_purpose", columnDefinition = "TEXT")
    private String paymentPurpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private OperationStatus status = OperationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    // Кто привязал договор: AUTO или логин оператора
    @Column(name = "mapped_by", length = 100)
    private String mappedBy;

    @Column(name = "mapped_at")
    private OffsetDateTime mappedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public enum OperationStatus {
        PENDING,          // ещё не обработана
        WAITING_NLP,      // платёж через третье лицо, ждёт разбора в очереди nlp_task
        AUTO_MAPPED,      // успешно привязана автоматически
        MANUAL_REQUIRED,  // требует ручной привязки
        AMBIGUOUS,        // несколько кандидатов на договор
        NOT_FOUND         // договор не найден
    }
}