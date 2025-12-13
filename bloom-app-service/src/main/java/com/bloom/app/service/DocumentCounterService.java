package com.bloom.app.service;

public interface DocumentCounterService {
    String generateNextCode(String documentType, String prefix);
}
