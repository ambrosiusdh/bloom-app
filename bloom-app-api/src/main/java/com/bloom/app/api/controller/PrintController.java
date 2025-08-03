package com.bloom.app.api.controller;

import com.bloom.app.api.helper.ResponseHelper;
import com.bloom.app.domain.dto.request.print.PrintReceiptRequest;
import com.bloom.app.domain.dto.response.ApiResponse;
import com.bloom.app.service.PrintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping(path = "/api/print")
@RequiredArgsConstructor
public class PrintController {
    private final PrintService printService;

    @PostMapping
    public ResponseEntity<ApiResponse<Boolean>> printReceipt(@RequestBody @Valid PrintReceiptRequest request) throws IOException {
        Boolean response = printService.printReceipt(request.getSaleCode());
        return ResponseHelper.ok(response);
    }
}
