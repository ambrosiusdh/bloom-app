package com.bloom.app.service.util;

import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.domain.enums.GoodsReceiptStatus;
import com.bloom.app.domain.enums.SupplierPaymentStatus;
import com.bloom.app.domain.model.GoodsReceipt;
import com.bloom.app.persistence.projection.ReceiptPaymentTotal;
import com.bloom.app.persistence.repository.SupplierPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SupplierDebtCalculator {
    private final SupplierPaymentRepository supplierPaymentRepository;

    public BigDecimal validPaidAmount(Long receiptId) {
        return money(supplierPaymentRepository.sumValidAmountByReceiptId(receiptId));
    }

    public Map<Long, BigDecimal> validPaidAmounts(Collection<Long> receiptIds) {
        if (receiptIds == null || receiptIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return supplierPaymentRepository.sumValidAmountsByReceiptIds(receiptIds).stream()
            .collect(Collectors.toMap(
                ReceiptPaymentTotal::getReceiptId,
                total -> money(total.getPaidAmount()),
                (left, right) -> left
            ));
    }

    public GoodsReceiptResponse apply(
            GoodsReceiptResponse response, GoodsReceipt receipt, BigDecimal paidAmount) {
        BigDecimal normalizedPaid = money(paidAmount);
        BigDecimal outstanding = receipt.getStatus() == GoodsReceiptStatus.CANCELLED
            ? money(BigDecimal.ZERO)
            : money(receipt.getTotalAmount().subtract(normalizedPaid));
        response.setPaidAmount(normalizedPaid);
        response.setOutstandingAmount(outstanding);
        response.setPaymentStatus(status(receipt.getTotalAmount(), normalizedPaid));
        return response;
    }

    public SupplierPaymentStatus status(BigDecimal totalAmount, BigDecimal paidAmount) {
        if (paidAmount == null || paidAmount.signum() == 0) {
            return SupplierPaymentStatus.UNPAID;
        }
        return paidAmount.compareTo(totalAmount) >= 0
            ? SupplierPaymentStatus.PAID
            : SupplierPaymentStatus.PARTIALLY_PAID;
    }

    public BigDecimal money(BigDecimal value) {
        return CashMoneyUtil.reconciliationBoundary(value == null ? BigDecimal.ZERO : value);
    }
}
