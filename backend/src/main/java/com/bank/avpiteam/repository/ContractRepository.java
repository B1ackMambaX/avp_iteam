package com.bank.avpiteam.repository;

import com.bank.avpiteam.domain.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    /**
     * Поиск договоров, где заданный ИНН фигурирует как контрагент.
     * Это самый частый случай - простая привязка без агента/выгодоприобретателя.
     */
    @Query("""
        SELECT c FROM Contract c
        WHERE c.counterparty.inn = :inn
        """)
    List<Contract> findByCounterpartyInn(@Param("inn") String inn);

    /**
     * Поиск договоров, где заданный ИНН фигурирует как выгодоприобретатель.
     * Нужен, когда парсер определил, что операция - платёж в пользу 3-го лица.
     */
    @Query("""
        SELECT c FROM Contract c
        WHERE c.beneficiary.inn = :inn
        """)
    List<Contract> findByBeneficiaryInn(@Param("inn") String inn);

    /**
     * Поиск договоров, где заданный ИНН фигурирует как агент.
     */
    @Query("""
        SELECT c FROM Contract c
        WHERE c.agent.inn = :inn
        """)
    List<Contract> findByAgentInn(@Param("inn") String inn);

    /**
     * Универсальный поиск - ИНН встречается в любой из трёх ролей.
     * Пригодится на этапе, когда мы ещё не знаем тип операции
     * и хотим получить всех кандидатов сразу.
     */
    @Query("""
        SELECT c FROM Contract c
        WHERE c.counterparty.inn = :inn
           OR c.beneficiary.inn = :inn
           OR c.agent.inn = :inn
        """)
    List<Contract> findByAnyRoleInn(@Param("inn") String inn);
}