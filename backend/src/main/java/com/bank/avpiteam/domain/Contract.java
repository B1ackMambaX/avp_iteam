package com.bank.avpiteam.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Договор. Стороны договора и их роли - в {@link ContractParty}.
 */
@Entity
@Table(name = "contracts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "contract_seq")
    @SequenceGenerator(name = "contract_seq", sequenceName = "contract_seq", allocationSize = 100)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 50)
    private ContractType contractType;

    // Уникальность номера не подтверждена заказчиком (вопрос 12 в вики)
    @Column(name = "contract_number", nullable = false, length = 100)
    private String contractNumber;

    // Вычисляется в БД (generated column) - только для чтения.
    // После insert в памяти остаётся null, заполняется при следующей загрузке.
    @Column(name = "contract_number_norm", length = 100, insertable = false, updatable = false)
    private String contractNumberNorm;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContractParty> parties = new ArrayList<>();

    // "Запрос" - задел на будущее по ТЗ заказчика. Пока простое текстовое поле.
    @Column(name = "request_ref", length = 200)
    private String requestRef;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public void addParty(Client client, ContractParty.Role role) {
        parties.add(new ContractParty(this, client, role));
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * точный список типов договоров нужно уточнить у заказчика.
     * Сейчас заложена логика на основе того, какие роли заполнены.
     */
    public enum ContractType {
        STANDARD,                     // только counterparty
        WITH_AGENT,                   // counterparty + agent
        WITH_BENEFICIARY,             // counterparty + beneficiary
        WITH_AGENT_AND_BENEFICIARY    // все три роли
    }
}