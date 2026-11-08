package com.bank.avpiteam.repository;

import com.bank.avpiteam.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientRepository extends JpaRepository<Client, Long> {

    // ИНН не уникален (дубли в источнике, ИП и ФЛ с одним ИНН) - возвращаем всех
    List<Client> findByInn(String inn);
}
