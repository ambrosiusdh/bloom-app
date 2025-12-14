package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptRequest;
import com.bloom.app.api.dto.request.goodsreceipt.FilterGoodsReceiptRequest;
import com.bloom.app.api.dto.request.goodsreceipt.GoodsReceiptItemRequest;
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
import com.bloom.app.service.mapper.GoodsReceiptMapper;
import com.bloom.app.service.specification.GoodsReceiptSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final GoodsReceiptMapper goodsReceiptMapper;

    @Override
    @Transactional
    public GoodsReceiptResponse createGoodsReceipt(CreateGoodsReceiptRequest request) {
        log.debug("Creating Goods Receipt");

        GoodsReceipt goodsReceipt = goodsReceiptMapper.createRequestToEntity(request);
        goodsReceipt.setCode(documentCounterService.generateNextCode("GOODS_RECEIPT", "GR"));

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
                savedReceipt.getCode());
        }

        return goodsReceiptMapper.toResponse(savedReceipt);
    }

    @Override
    public GoodsReceiptResponse getGoodsReceiptDetails(String code) {
        GoodsReceipt goodsReceipt = goodsReceiptRepository.findByCode(code)
            .orElseThrow(
                () -> new ResponseStatusException(
                    ErrorCode.GOODS_RECEIPT_NOT_FOUND.getStatus(),
                    ErrorCode.GOODS_RECEIPT_NOT_FOUND.formatMessage(code)));
        return goodsReceiptMapper.toResponse(goodsReceipt);
    }

    @Override
    public Page<GoodsReceiptResponse> filterGoodsReceipts(FilterGoodsReceiptRequest request, Pageable pageable) {
        Specification<GoodsReceipt> spec = GoodsReceiptSpecification.filter(request);
        Page<GoodsReceipt> page = goodsReceiptRepository.findAll(spec, pageable);
        List<GoodsReceiptResponse> responses = page.getContent().stream()
            .map(goodsReceiptMapper::toResponse)
            .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, page.getTotalElements());
    }
}
