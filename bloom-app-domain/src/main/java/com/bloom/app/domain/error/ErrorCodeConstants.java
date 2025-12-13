package com.bloom.app.domain.error;

public class ErrorCodeConstants {
    public static final String GOODS_RECEIPT_NOT_FOUND_CODE = "goods_receipt_not_found";
    public static final String GOODS_RECEIPT_NOT_FOUND_MESSAGE = "Goods receipt not found for: %s";

    public static final String ITEM_NOT_FOUND_CODE = "item_not_found";
    public static final String ITEM_NOT_FOUND_MESSAGE = "Item not found for: %s";
    public static final String ITEM_QUANTITY_MUST_BE_POSITIVE_CODE = "item_quantity_must_be_positive";
    public static final String ITEM_QUANTITY_MUST_BE_POSITIVE_MESSAGE = "Item quantity must be positive";

    public static final String ITEM_CATEGORY_ALREADY_EXISTS_CODE = "item_category_already_exists";
    public static final String ITEM_CATEGORY_ALREADY_EXISTS_MESSAGE = "Item Category already exists";
    public static final String ITEM_CATEGORY_NOT_FOUND_CODE = "item_category_not_found";
    public static final String ITEM_CATEGORY_NOT_FOUND_MESSAGE = "Item category not found";

    public static final String PRINTER_NOT_FOUND_CODE = "printer_not_found";
    public static final String PRINTER_NOT_FOUND_MESSAGE = "Printer tidak ditemukan";

    public static final String SALE_NOT_FOUND_CODE = "sale_not_found";
    public static final String SALE_NOT_FOUND_MESSAGE = "Transaksi tidak ditemukan";
    public static final String SALE_PAID_LESS_THAN_TOTAL_CODE = "sale_paid_less_than_total";
    public static final String SALE_PAID_LESS_THAN_TOTAL_MESSAGE = "Jumlah pembayaran lebih kecil dari total transaksi";
}
