package com.stylemind.payment.repository;

import com.stylemind.payment.entity.RefundTransferReceiptEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundTransferReceiptRepository extends JpaRepository<RefundTransferReceiptEntity, UUID> { }
