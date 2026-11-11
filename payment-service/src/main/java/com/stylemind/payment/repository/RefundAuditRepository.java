package com.stylemind.payment.repository;

import com.stylemind.payment.entity.RefundAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundAuditRepository extends JpaRepository<RefundAuditEntity, Long> {
}
