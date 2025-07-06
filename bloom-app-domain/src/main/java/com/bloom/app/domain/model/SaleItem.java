package com.bloom.app.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "sale_items")
public class SaleItem {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Sale sale;

    @ManyToOne
    private Item item;

    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
