package com.freestone.pettycash.repository;

import com.freestone.pettycash.model.CashBox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CashBoxRepository extends JpaRepository<CashBox, Long> {
}
