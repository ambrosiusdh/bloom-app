package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptRequest;
import com.bloom.app.api.dto.request.goodsreceipt.GoodsReceiptItemRequest;
import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptItemResponse;
import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.model.GoodsReceipt;
import com.bloom.app.domain.model.GoodsReceiptItem;
import com.bloom.app.domain.model.Item;
import com.bloom.app.persistence.repository.GoodsReceiptRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.service.DocumentCounterService;
import com.bloom.app.service.GoodsReceiptService;
import com.bloom.app.service.StockMovementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsReceiptServiceImpl implements GoodsReceiptService {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final ItemRepository itemRepository;
    private final StockMovementService stockMovementService;
    private final DocumentCounterService documentCounterService;

    @Override
    @Transactional
    public GoodsReceiptResponse createGoodsReceipt(CreateGoodsReceiptRequest request) {
        log.debug("Creating Goods Receipt");

        GoodsReceipt goodsReceipt = GoodsReceipt.builder()
                .code(documentCounterService.generateNextCode("GOODS_RECEIPT", "GR"))
                .receivedDate(request.getReceivedDate() != null ? request.getReceivedDate() : Instant.now())
                .supplierName(request.getSupplierName())
                .description(request.getDescription())
                .createdAt(Instant.now())
                .build();

        List<GoodsReceiptItem> items = new ArrayList<>();

        for (GoodsReceiptItemRequest itemRequest : request.getItems()) {
            Item item = itemRepository.findItemBySku(itemRequest.getItemSku())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Item not found: " + itemRequest.getItemSku()));

            GoodsReceiptItem goodsReceiptItem = GoodsReceiptItem.builder()
                    .goodsReceipt(goodsReceipt)
                    .item(item)
                    .quantity(itemRequest.getQuantity())
                    .build();

            items.add(goodsReceiptItem);
        }

        goodsReceipt.setItems(items);
        GoodsReceipt savedReceipt = goodsReceiptRepository.save(goodsReceipt);

        for (GoodsReceiptItem savedItem : savedReceipt.getItems()) {
            stockMovementService.recordMovement(
                    MovementSourceType.GOODS_RECEIPT,
                    savedReceipt.getId(),
                    savedItem.getItem(),
                    savedItem.getQuantity(),
                    MovementType.IN,
                    savedReceipt.getCode()
            );
        }

        return toResponse(savedReceipt);
    }

    @Override
    public GoodsReceiptResponse getGoodsReceiptDetails(String code) {
        GoodsReceipt goodsReceipt = goodsReceiptRepository.findByCode(code)
                .orElseThrow(
                        () -> new ResponseStatusException(
                            ErrorCode.GOODS_RECEIPT_NOT_FOUND.getStatus(),
                            ErrorCode.GOODS_RECEIPT_NOT_FOUND.formatMessage(code)
                        )
                );
        return toResponse(goodsReceipt);
    }

    @Override
    public Page<GoodsReceiptResponse> getGoodsReceipts(Pageable pageable) {
        Page<GoodsReceipt> page = goodsReceiptRepository.findAll(pageable);
        List<GoodsReceiptResponse> responses = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, page.getTotalElements());
    }

    private GoodsReceiptResponse toResponse(GoodsReceipt goodsReceipt) {
        List<GoodsReceiptItemResponse> itemResponses = goodsReceipt.getItems().stream()
                .map(item -> GoodsReceiptItemResponse.builder()
                        .id(item.getId())
                        .itemId(item.getItem().getId())
                        .itemName(item.getItem().getName())
                        .itemSku(item.getItem().getSku())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        return GoodsReceiptResponse.builder()
                .id(goodsReceipt.getId())
                .code(goodsReceipt.getCode())
                .receivedDate(goodsReceipt.getReceivedDate())
                .supplierName(goodsReceipt.getSupplierName())
                .description(goodsReceipt.getDescription())
                .createdAt(goodsReceipt.getCreatedAt())
                .createdBy(goodsReceipt.getCreatedBy())
                .items(itemResponses)
                .build();
    }
}
