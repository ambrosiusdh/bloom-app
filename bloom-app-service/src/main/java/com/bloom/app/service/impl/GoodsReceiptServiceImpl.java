package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptRequest;
import com.bloom.app.api.dto.request.goodsreceipt.FilterGoodsReceiptRequest;
import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptItemRequest;
import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.GoodsReceipt;
import com.bloom.app.domain.model.GoodsReceiptItem;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Supplier;
import com.bloom.app.persistence.repository.GoodsReceiptRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.SupplierRepository;
import com.bloom.app.service.DocumentCounterService;
import com.bloom.app.service.GoodsReceiptService;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.mapper.GoodsReceiptMapper;
import com.bloom.app.service.specification.GoodsReceiptSpecification;
import com.bloom.app.domain.validation.InventoryQuantityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SupplierRepository supplierRepository;

    @Override
    @Transactional
    public GoodsReceiptResponse createGoodsReceipt(CreateGoodsReceiptRequest request) {
        log.debug("GoodsReceiptService createGoodsReceipt with request: {}", request);
        Supplier supplier = supplierRepository.findByCode(request.getSupplierCode())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Supplier not found: " + request.getSupplierCode()
            ));

        GoodsReceipt goodsReceipt = goodsReceiptMapper.createRequestToEntity(request);
        goodsReceipt.setCode(documentCounterService.generateNextCode(DocumentType.GOODS_RECEIPT));
        goodsReceipt.setSupplier(supplier);

        List<GoodsReceiptItem> items = new ArrayList<>();

        for (CreateGoodsReceiptItemRequest itemRequest : request.getItems()) {
            Item item = itemRepository.findItemBySku(itemRequest.getItemSku())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Item not found: " + itemRequest.getItemSku()
                ));
            InventoryQuantityValidator.validateIncoming(
                itemRequest.getQuantity(), item.isFractionalQuantityAllowed());

            GoodsReceiptItem goodsReceiptItem = GoodsReceiptItem.builder()
                .goodsReceipt(goodsReceipt)
                .item(item)
                .quantity(itemRequest.getQuantity())
                .stockLocation(itemRequest.getStockLocation())
                .build();

            items.add(goodsReceiptItem);
        }

        goodsReceipt.setItems(items);
        goodsReceipt.setSupplier(supplier);
        GoodsReceipt savedReceipt = goodsReceiptRepository.save(goodsReceipt);

        for (GoodsReceiptItem savedItem : savedReceipt.getItems()) {
            stockMovementService.recordMovement(
                MovementSourceType.GOODS_RECEIPT,
                savedReceipt.getId(),
                savedItem.getItem(),
                savedItem.getQuantity(),
                MovementType.IN,
                savedReceipt.getCode(),
                savedItem.getStockLocation()
            );
        }

        return goodsReceiptMapper.toResponse(savedReceipt);
    }

    @Override
    public GoodsReceiptResponse getGoodsReceiptDetails(String code) {
        GoodsReceipt goodsReceipt = goodsReceiptRepository.findByCode(code)
            .orElseThrow(
                () -> new ResourceNotFoundException(ErrorCode.GOODS_RECEIPT_NOT_FOUND.formatMessage(code)));
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
