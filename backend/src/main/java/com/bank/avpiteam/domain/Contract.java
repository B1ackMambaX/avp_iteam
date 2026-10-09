package com.bank.avpiteam.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Договор. Роли сторон разведены по смыслу, а не по движению денег:
 *  - agent        - посредник, действует от чьего-то имени (может отсутствовать)
 *  - counterparty - вторая сторона договора (плательщик/получатель - не фиксировано)
 *  - beneficiary  - тот, в чью пользу заключён договор (может отсутствовать)
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

    @Column(name = "contract_number", nullable = false, unique = true, length = 100)
    private String contractNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Client agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_id")
    private Client counterparty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id")
    private Client beneficiary;

    // "Запрос" - задел на будущее по ТЗ заказчика. Пока простое текстовое поле.
    @Column(name = "request_ref", length = 200)
    private String requestRef;

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