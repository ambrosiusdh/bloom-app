package com.bloom.app.service;

import java.io.IOException;

public interface PrintService {
    Boolean printReceipt(String saleCode) throws IOException;
}
