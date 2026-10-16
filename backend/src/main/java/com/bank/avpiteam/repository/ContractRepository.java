package com.bank.avpiteam.repository;

import com.bank.avpiteam.domain.Contract;
import com.bank.avpiteam.domain.ContractParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    /**
     * Поиск договоров, где заданный ИНН фигурирует в заданной роли.
     * COUNTERPARTY - самый частый случай, простая привязка без агента/выгодоприобретателя;
     * BENEFICIARY - когда парсер определил, что операция - платёж в пользу 3-го лица.
     */
    @Query("""
        SELECT p.contract FROM ContractParty p
        WHERE p.client.inn = :inn
          AND p.id.role = :role
        """)
    List<Contract> findByPartyInn(@Param("inn") String inn,
                                  @Param("role") ContractParty.Role role);

    /**
     * Универсальный поиск - ИНН встречается в любой роли.
     * Пригодится на этапе, когда мы ещё не знаем тип операции
     * и хотим получить всех кандидатов сразу.
     */
    @Query("""
        SELECT DISTINCT p.contract FROM ContractParty p
        WHERE p.client.inn = :inn
        """)
    List<Contract> findByAnyRoleInn(@Param("inn") String inn);
}
