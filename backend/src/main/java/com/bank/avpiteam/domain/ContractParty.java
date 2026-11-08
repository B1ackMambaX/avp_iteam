package com.bank.avpiteam.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * Сторона договора: клиент в определённой роли.
 * Вынесено из contracts, чтобы поиск "все договоры клиента"
 * шёл одним индексом по client_id, а не OR по трём колонкам.
 */
@Entity
@Table(name = "contract_parties")
@Getter
@Setter
@NoArgsConstructor
public class ContractParty {

    @EmbeddedId
    private Id id;

    @MapsId("contractId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private Contract contract;

    @MapsId("clientId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    public ContractParty(Contract contract, Client client, Role role) {
        this.id = new Id(null, null, role);
        this.contract = contract;
        this.client = client;
    }

    public Role getRole() {
        return id.getRole();
    }

    /**
     * Роли разведены по смыслу, а не по движению денег:
     *  - COUNTERPARTY - сторона договора (плательщик/получатель - не фиксировано)
     *  - AGENT        - посредник, действует от чьего-то имени
     *  - BENEFICIARY  - тот, в чью пользу заключён договор
     */
    public enum Role {
        COUNTERPARTY,
        AGENT,
        BENEFICIARY
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Column(name = "contract_id")
        private Long contractId;

        @Column(name = "client_id")
        private Long clientId;

        @Enumerated(EnumType.STRING)
        @Column(name = "role", length = 20)
        private Role role;
    }
}
