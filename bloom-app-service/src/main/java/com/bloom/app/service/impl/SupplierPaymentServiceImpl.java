package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.supplierpayment.CreateSupplierPaymentRequest;
import com.bloom.app.api.dto.request.supplierpayment.VoidSupplierPaymentRequest;
import com.bloom.app.api.dto.response.supplierpayment.SupplierPaymentResponse;
import com.bloom.app.domain.enums.CashMovementType;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.enums.GoodsReceiptStatus;
import com.bloom.app.domain.enums.SupplierPaymentMethod;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.exception.SupplierPaymentConflictException;
import com.bloom.app.domain.exception.SupplierPaymentIdempotencyConflictException;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.domain.model.GoodsReceipt;
import com.bloom.app.domain.model.SupplierPayment;
import com.bloom.app.persistence.repository.CashSessionRepository;
import com.bloom.app.persistence.repository.GoodsReceiptRepository;
import com.bloom.app.persistence.repository.SupplierPaymentRepository;
import com.bloom.app.service.CashMovementService;
import com.bloom.app.service.SupplierPaymentService;
import com.bloom.app.service.command.RecordCashMovementCommand;
import com.bloom.app.service.mapper.SupplierPaymentMapper;
import com.bloom.app.service.util.CashMoneyUtil;
import com.bloom.app.service.util.CurrentActorProvider;
import com.bloom.app.service.util.SupplierDebtCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class SupplierPaymentServiceImpl implements SupplierPaymentService {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final int MAX_TEXT_LENGTH = 255;
    private static final int MAX_RECEIPT_CODE_LENGTH = 100;

    private final SupplierPaymentRepository supplierPaymentRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final CashSessionRepository cashSessionRepository;
    private final CashMovementService cashMovementService;
    private final SupplierPaymentMapper supplierPaymentMapper;
    private final SupplierDebtCalculator supplierDebtCalculator;
    private final CurrentActorProvider currentActorProvider;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockIdempotencyKey(String idempotencyKey) {
        supplierPaymentRepository.lockIdempotencyKey(
            normalizeIdempotencyKey(idempotencyKey));
    }

    @Override
    @Transactional
    public SupplierPaymentResponse createPayment(
            String receiptCode, String idempotencyKey, CreateSupplierPaymentRequest request) {
        PreparedPayment prepared = prepare(requireReceiptCode(receiptCode), idempotencyKey, request);
        SupplierPayment existing = findIdempotentReplay(prepared);
        if (existing != null) {
            return supplierPaymentMapper.toResponse(existing);
        }

        goodsReceiptRepository.findHeaderByCode(prepared.receiptCode())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Goods receipt not found: " + prepared.receiptCode()));
        CashSession cashSession = lockOpenCashSessionIfRequired(prepared);
        GoodsReceipt receipt = goodsReceiptRepository
            .findPaymentHeaderByCodeForUpdate(prepared.receiptCode())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Goods receipt not found: " + prepared.receiptCode()));
        return persistAgainstLockedReceipt(receipt, prepared, cashSession);
    }

    @Override
    @Transactional
    public SupplierPaymentResponse voidPayment(
            Long paymentId, VoidSupplierPaymentRequest request) {
        validatePaymentId(paymentId);
        if (request == null) {
            throw new IllegalArgumentException("Void supplier payment request is required");
        }
        String reason = normalizeRequired(request.getReason(), "Void reason");
        SupplierPayment payment = supplierPaymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Supplier payment not found: " + paymentId));

        if (payment.isVoided()) {
            return supplierPaymentMapper.toResponse(payment);
        }

        if (payment.getPaymentMethod() == SupplierPaymentMethod.CASH) {
            CashSession session = payment.getCashSession();
            if (session == null || session.getStatus() != CashSessionStatus.OPEN) {
                throw new CashSessionConflictException(
                    "The original cash session is closed and rejects supplier payment voids");
            }
            cashMovementService.recordMovement(new RecordCashMovementCommand(
                session.getId(),
                CashMovementType.SUPPLIER_PAYMENT_REVERSAL,
                payment.getId(),
                paymentReference(payment.getId()) + "-VOID",
                payment.getAmount()
            ));
        }

        payment.voidWith(
            reason,
            Instant.now().truncatedTo(ChronoUnit.MICROS),
            currentActorProvider.username()
        );
        return supplierPaymentMapper.toResponse(
            supplierPaymentRepository.saveAndFlush(payment));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierPaymentResponse> getReceiptPaymentHistory(
            String receiptCode, Pageable pageable) {
        String normalizedCode = requireReceiptCode(receiptCode);
        GoodsReceipt receipt = goodsReceiptRepository.findHeaderByCode(normalizedCode)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Goods receipt not found: " + normalizedCode));
        Pageable effectivePageable = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Order.desc("paidAt"), Sort.Order.desc("id"))
        );
        return supplierPaymentRepository
            .findHistoryByReceiptId(receipt.getId(), effectivePageable)
            .map(supplierPaymentMapper::toResponse);
    }

    private SupplierPayment findIdempotentReplay(PreparedPayment prepared) {
        lockIdempotencyKey(prepared.idempotencyKey());
        SupplierPayment existing = supplierPaymentRepository
            .findByIdempotencyKey(prepared.idempotencyKey())
            .orElse(null);
        if (existing != null && !existing.getRequestHash().equals(prepared.requestHash())) {
            throw new SupplierPaymentIdempotencyConflictException();
        }
        return existing;
    }

    private SupplierPaymentResponse persistAgainstLockedReceipt(
            GoodsReceipt receipt, PreparedPayment prepared, CashSession cashSession) {
        if (receipt.getStatus() != GoodsReceiptStatus.POSTED) {
            throw new SupplierPaymentConflictException(
                "Only a posted goods receipt can accept supplier payments: " + receipt.getCode());
        }

        BigDecimal alreadyPaid = supplierDebtCalculator.validPaidAmount(receipt.getId());
        BigDecimal outstanding = receipt.getTotalAmount().subtract(alreadyPaid);
        if (prepared.amount().compareTo(outstanding) > 0) {
            throw new SupplierPaymentConflictException(
                "Supplier payment exceeds receipt outstanding amount of "
                    + supplierDebtCalculator.money(outstanding).toPlainString());
        }

        SupplierPayment saved = supplierPaymentRepository.saveAndFlush(SupplierPayment.builder()
            .receipt(receipt)
            .supplier(receipt.getSupplier())
            .cashSession(cashSession)
            .amount(prepared.amount())
            .paymentMethod(prepared.paymentMethod())
            .paidAt(prepared.paidAt())
            .reference(prepared.reference())
            .note(prepared.note())
            .actor(currentActorProvider.username())
            .idempotencyKey(prepared.idempotencyKey())
            .requestHash(prepared.requestHash())
            .build());

        if (prepared.paymentMethod() == SupplierPaymentMethod.CASH) {
            cashMovementService.recordMovement(new RecordCashMovementCommand(
                cashSession.getId(),
                CashMovementType.SUPPLIER_PAYMENT,
                saved.getId(),
                paymentReference(saved.getId()),
                saved.getAmount()
            ));
        }
        return supplierPaymentMapper.toResponse(saved);
    }

    private PreparedPayment prepare(
            String receiptCode, String idempotencyKey, CreateSupplierPaymentRequest request) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (request == null) {
            throw new IllegalArgumentException("Create supplier payment request is required");
        }
        BigDecimal amount = CashMoneyUtil.requirePositive(
            request.getAmount(), "Supplier payment amount");
        if (request.getPaymentMethod() == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        if (request.getPaidAt() == null) {
            throw new IllegalArgumentException("Paid at is required");
        }
        if (request.getPaidAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Paid at must not be in the future");
        }
        String reference = normalizeOptional(request.getReference(), "Payment reference");
        String note = normalizeOptional(request.getNote(), "Payment note");
        String requestHash = requestHash(
            receiptCode,
            amount,
            request.getPaymentMethod(),
            request.getPaidAt(),
            reference,
            note
        );
        return new PreparedPayment(
            receiptCode,
            normalizedKey,
            requestHash,
            amount,
            request.getPaymentMethod(),
            request.getPaidAt(),
            reference,
            note
        );
    }

    private CashSession lockOpenCashSessionIfRequired(PreparedPayment prepared) {
        if (prepared.paymentMethod() != SupplierPaymentMethod.CASH) {
            return null;
        }
        CashSession session = cashSessionRepository
            .findFirstByStatusForUpdate(CashSessionStatus.OPEN)
            .orElseThrow(() -> new CashSessionConflictException(
                "An open cash session is required for a CASH supplier payment"));
        if (prepared.paidAt().isBefore(session.getOpenedAt())) {
            throw new CashSessionConflictException(
                "A CASH supplier payment cannot predate its open cash session");
        }
        return session;
    }

    private String requestHash(
            String receiptCode,
            BigDecimal amount,
            SupplierPaymentMethod method,
            Instant paidAt,
            String reference,
            String note) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateHashField(digest, receiptCode);
            updateHashField(digest, amount.stripTrailingZeros().toPlainString());
            updateHashField(digest, method.name());
            updateHashField(digest, paidAt.toString());
            updateHashField(digest, reference == null ? "" : reference);
            updateHashField(digest, note == null ? "" : note);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void updateHashField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        String normalized = key.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(
                "Idempotency-Key must not exceed 100 characters");
        }
        return normalized;
    }

    private String requireReceiptCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Goods receipt code is required");
        }
        String normalized = code.trim();
        if (normalized.length() > MAX_RECEIPT_CODE_LENGTH) {
            throw new IllegalArgumentException(
                "Goods receipt code must not exceed 100 characters");
        }
        return normalized;
    }

    private String normalizeOptional(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return normalizeLength(value.trim(), fieldName);
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalizeLength(value.trim(), fieldName);
    }

    private String normalizeLength(String value, String fieldName) {
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must not exceed 255 characters");
        }
        return value;
    }

    private void validatePaymentId(Long paymentId) {
        if (paymentId == null || paymentId <= 0) {
            throw new IllegalArgumentException("Supplier payment ID must be positive");
        }
    }

    private String paymentReference(Long paymentId) {
        return "SUPPLIER-PAYMENT-" + paymentId;
    }

    private record PreparedPayment(
        String receiptCode,
        String idempotencyKey,
        String requestHash,
        BigDecimal amount,
        SupplierPaymentMethod paymentMethod,
        Instant paidAt,
        String reference,
        String note
    ) {
    }
}
