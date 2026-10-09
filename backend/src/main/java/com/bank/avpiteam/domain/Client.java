package com.bank.avpiteam.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Клиент банка. Одна таблица для ФЛ, ИП и ЮЛ (MVP-вариант).
 * Поля, не относящиеся к типу клиента, остаются пустыми (null).
 */
@Entity
@Table(name = "clients")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "client_seq")
    @SequenceGenerator(name = "client_seq", sequenceName = "client_seq", allocationSize = 50)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 20)
    private ClientType clientType;

    @Column(name = "inn", nullable = false, length = 12)
    private String inn;

    @Column(name = "pin", length = 6)
    private String pin;

    // Поля физлица / ИП

    @Column(name = "last_name", length = 200)
    private String lastName;

    @Column(name = "first_name", length = 200)
    private String firstName;

    @Column(name = "middle_name", length = 200)
    private String middleName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "birth_place", length = 500)
    private String birthPlace;

    @Column(name = "citizenship", length = 100)
    private String citizenship;

    // Документ, удостоверяющий личность

    @Column(name = "document_type", length = 100)
    private String documentType;

    @Column(name = "document_series", length = 20)
    private String documentSeries;

    @Column(name = "document_number", length = 20)
    private String documentNumber;

    @Column(name = "document_issue_date")
    private LocalDate documentIssueDate;

    @Column(name = "document_issued_by", length = 500)
    private String documentIssuedBy;

    @Column(name = "document_department_code", length = 20)
    private String documentDepartmentCode;

    @Column(name = "registration_address", length = 1000)
    private String registrationAddress;

    // Поля юрлица

    @Column(name = "organization_name", length = 500)
    private String organizationName;

    @Column(name = "legal_form", length = 200)
    private String legalForm;

    @Column(name = "bik", length = 9)
    private String bik;

    @Column(name = "ogrn", length = 15)
    private String ogrn;

    @Column(name = "ogrn_date")
    private LocalDate ogrnDate;

    // Флаги

    @Column(name = "is_extremist", nullable = false)
    @Builder.Default
    private Boolean isExtremist = false;

    @Column(name = "is_beneficiary", nullable = false)
    @Builder.Default
    private Boolean isBeneficiary = false;

    @Column(name = "is_bank_client", nullable = false)
    @Builder.Default
    private Boolean isBankClient = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum ClientType {
        INDIVIDUAL,     // фл
        ENTREPRENEUR,   // ИП
        LEGAL_ENTITY    // юл
    }
}