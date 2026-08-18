package com.bloom.app.service;

import com.bloom.app.api.dto.request.cashsession.CloseCashSessionRequest;
import com.bloom.app.api.dto.request.cashsession.OpenCashSessionRequest;
import com.bloom.app.api.dto.response.cashsession.CashReconciliationResponse;
import com.bloom.app.api.dto.response.cashsession.CashSessionResponse;

public interface CashSessionService {
    CashSessionResponse openSession(OpenCashSessionRequest request);

    CashSessionResponse getCurrentSession();

    CashSessionResponse getSessionDetails(Long sessionId);

    CashReconciliationResponse calculateExpectedCash(Long sessionId);

    CashSessionResponse closeSession(Long sessionId, CloseCashSessionRequest request);
}
