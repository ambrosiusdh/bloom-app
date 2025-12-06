package com.bloom.app.repository;

import com.bloom.app.domain.model.ItemAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemAuditLogRepository
        extends JpaRepository<ItemAuditLog, Long>, JpaSpecificationExecutor<ItemAuditLog> {
    Page<ItemAuditLog> findByItemSku(String sku, Pageable pageable);
}
