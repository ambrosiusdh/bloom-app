package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.sale.SaleResponse;
import com.bloom.app.domain.enums.PaymentType;
import com.bloom.app.domain.enums.SaleCorrectionStatus;
import com.bloom.app.domain.enums.SalePaymentStatus;
import com.bloom.app.domain.enums.SaleStatus;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.SaleItem;
import com.bloom.app.domain.model.UnitOfMeasure;
import com.bloom.app.service.SaleItemMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SaleMapperTest {
    @Test
    void mapsCashSaleWithAuthoritativeStatusesAndPersistedFractionalLineFacts() {
        SaleResponse response = mapSale(PaymentType.CASH, "20.0000", "7.5000");

        assertThat(response.getSaleStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(response.getPaymentStatus()).isEqualTo(SalePaymentStatus.PAID);
        assertThat(response.getCorrectionStatus()).isEqualTo(SaleCorrectionStatus.NONE);
        assertThat(response.getSessionId()).isEqualTo(7L);
        assertThat(response.getCode()).isEqualTo("SALE-READ-1");
        assertThat(response.getPaymentType()).isEqualTo(PaymentType.CASH);
        assertThat(response.getSubtotalAmount()).isEqualByComparingTo("12.5000");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("0.0000");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("12.5000");
        assertThat(response.getPaidAmount()).isEqualByComparingTo("20.0000");
        assertThat(response.getChangeAmount()).isEqualByComparingTo("7.5000");

        assertThat(response.getSaleItems()).singleElement().satisfies(line -> {
            assertThat(line.getQuantity()).isEqualByComparingTo("1.2500");
            assertThat(line.getUnitPrice()).isEqualByComparingTo("10.0000");
            assertThat(line.getSubtotal()).isEqualByComparingTo("12.5000");
            assertThat(line.getStockLocation()).isEqualTo(StockLocation.WAREHOUSE);
            assertThat(line.getItem().getSku()).isEqualTo("ITEM-1");
            assertThat(line.getItem().getName()).isEqualTo("Kain meteran");
            assertThat(line.getItem().getBaseUnitOfMeasure()).isEqualTo(UnitOfMeasure.METER);
            assertThat(line.getItem().getPrice()).isEqualByComparingTo("99.0000");
        });
    }

    @Test
    void mapsQrisSaleAsPaidAndCompletedWithBackendConfirmedPaymentAndZeroChange() {
        SaleResponse response = mapSale(PaymentType.QRIS, "12.5000", "0.0000");

        assertThat(response.getSaleStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(response.getPaymentStatus()).isEqualTo(SalePaymentStatus.PAID);
        assertThat(response.getCorrectionStatus()).isEqualTo(SaleCorrectionStatus.NONE);
        assertThat(response.getPaymentType()).isEqualTo(PaymentType.QRIS);
        assertThat(response.getPaidAmount()).isEqualByComparingTo("12.5000");
        assertThat(response.getChangeAmount()).isEqualByComparingTo("0.0000");
    }

    private SaleResponse mapSale(PaymentType paymentType, String paidAmount, String changeAmount) {
        Item liveItem = Item.builder()
            .id(3L)
            .sku("ITEM-1")
            .name("Kain meteran")
            .price(new BigDecimal("99.0000"))
            .baseUnitOfMeasure(UnitOfMeasure.METER)
            .build();
        Sale sale = Sale.builder()
            .id(11L)
            .code("SALE-READ-1")
            .cashSession(CashSession.builder().id(7L).build())
            .paymentType(paymentType)
            .subtotalAmount(new BigDecimal("12.5000"))
            .discountAmount(new BigDecimal("0.0000"))
            .totalAmount(new BigDecimal("12.5000"))
            .paidAmount(new BigDecimal(paidAmount))
            .changeAmount(new BigDecimal(changeAmount))
            .build();
        sale.setItems(List.of(SaleItem.builder()
            .sale(sale)
            .item(liveItem)
            .stockLocation(StockLocation.WAREHOUSE)
            .quantity(new BigDecimal("1.2500"))
            .unitPrice(new BigDecimal("10.0000"))
            .subtotal(new BigDecimal("12.5000"))
            .build()));

        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext()) {
            context.register(SaleMapperImpl.class, SaleItemMapperImpl.class);
            context.refresh();
            return context.getBean(SaleMapper.class).saleToResponse(sale);
        }
    }
}
