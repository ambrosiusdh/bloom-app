package com.bloom.app.service;

import com.bloom.app.api.dto.request.cashsession.CloseCashSessionRequest;
import com.bloom.app.api.dto.request.cashsession.OpenCashSessionRequest;
import com.bloom.app.api.dto.response.cashsession.CashReconciliationResponse;
import com.bloom.app.api.dto.response.cashsession.CashMovementResponse;
import com.bloom.app.api.dto.response.cashsession.CashSessionResponse;
import com.bloom.app.domain.enums.CashSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CashSessionService {
    CashSessionResponse openSession(OpenCashSessionRequest request);

    Optional<CashSessionResponse> getCurrentSession();

    Page<CashSessionResponse> getSessionHistory(
        CashSessionStatus status, Pageable pageable);

    CashSessionResponse getSessionDetails(Long sessionId);

    Page<CashMovementResponse> getSessionMovements(Long sessionId, Pageable pageable);

    CashReconciliationResponse calculateExpectedCash(Long sessionId);

    CashSessionResponse closeSession(Long sessionId, CloseCashSessionRequest request);
}
