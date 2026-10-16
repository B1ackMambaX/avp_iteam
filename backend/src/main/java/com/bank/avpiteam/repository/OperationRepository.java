package com.bank.avpiteam.repository;

import com.bank.avpiteam.domain.Operation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OperationRepository extends JpaRepository<Operation, Long> {

    /**
     * Постраничная выборка необработанных операций -
     * так будем гонять батчи через парсер/маппер, не вытаскивая  всё разом в память.
     */
    Page<Operation> findByStatus(Operation.OperationStatus status, Pageable pageable);

    /**
     * Батчевое обновление статуса - быстрее, чем сохранять
     * каждую сущность через save() по отдельности.
     */
    @Modifying
    @Query("""
        UPDATE Operation o
        SET o.status = :status, o.updatedAt = CURRENT_TIMESTAMP
        WHERE o.id IN :ids
        """)
    int updateStatusBatch(@Param("ids") List<Long> ids,
                          @Param("status") Operation.OperationStatus status);

    long countByStatus(Operation.OperationStatus status);
}