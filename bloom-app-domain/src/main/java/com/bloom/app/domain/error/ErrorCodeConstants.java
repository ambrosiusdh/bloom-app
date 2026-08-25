package com.bloom.app.domain.error;

public class ErrorCodeConstants {
    public static final String GOODS_RECEIPT_NOT_FOUND_CODE = "goods_receipt_not_found";
    public static final String GOODS_RECEIPT_NOT_FOUND_MESSAGE = "Goods receipt not found for: %s";

    public static final String SUPPLIER_NOT_FOUND_CODE = "supplier_not_found";
    public static final String SUPPLIER_NOT_FOUND_MESSAGE = "Supplier not found for code: %s";
    public static final String SUPPLIER_CODE_ALREADY_EXISTS_CODE = "supplier_code_already_exists";
    public static final String SUPPLIER_CODE_ALREADY_EXISTS_MESSAGE = "Supplier code already exists: %s";
    public static final String SUPPLIER_HAS_FINANCIAL_HISTORY_CODE = "supplier_has_financial_history";
    public static final String SUPPLIER_HAS_FINANCIAL_HISTORY_MESSAGE =
        "Supplier %s cannot be deleted because financial history exists";

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
    public static final String SALE_INSUFFICIENT_STOCK_CODE = "sale_insufficient_stock";
    public static final String SALE_INSUFFICIENT_STOCK_MESSAGE = "Jumlah stok item %s pada %s tidak mencukupi untuk transaksi";
    public static final String SALE_PAID_LESS_THAN_TOTAL_CODE = "sale_paid_less_than_total";
    public static final String SALE_PAID_LESS_THAN_TOTAL_MESSAGE = "Jumlah pembayaran lebih kecil dari total transaksi";
    public static final String SALE_QRIS_PAYMENT_MISMATCH_CODE = "sale_qris_payment_mismatch";
    public static final String SALE_QRIS_PAYMENT_MISMATCH_MESSAGE = "Jumlah pembayaran QRIS harus sama dengan total transaksi";
}
