package com.bloom.app.service.impl;

import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.SaleItem;
import com.bloom.app.domain.properties.PrinterProperties;
import com.bloom.app.domain.utils.DateTimeUtils;
import com.bloom.app.repository.SaleRepository;
import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.Style;
import com.github.anastaciocintra.escpos.EscPosConst.Justification;
import com.github.anastaciocintra.output.PrinterOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.print.PrintServiceLookup;
import javax.print.PrintService;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintServiceImpl implements com.bloom.app.service.PrintService {
    private final SaleRepository saleRepository;
    private final PrinterProperties printerProperties;

    @Override
    public Boolean printReceipt(String saleCode) throws IOException {
        Sale sale = saleRepository.findByCode(saleCode).orElseThrow(
                () -> new ResponseStatusException(ErrorCode.SALE_NOT_FOUND.getStatus(), ErrorCode.SALE_NOT_FOUND.getMessage())
        );

        try {
            PrinterOutputStream outputStream = new PrinterOutputStream(getPrinterService());
            EscPos escpos = new EscPos(outputStream);

            Style centerAlignBold = new Style().setBold(true).setJustification(Justification.Center);
            Style centerAlign = new Style().setJustification(Justification.Center);
            Style leftAlign = new Style().setJustification(Justification.Left_Default);

            escpos.writeLF(centerAlignBold, "Tb Mekar");
            escpos.feed(1);
            escpos.writeLF(centerAlign, "Jl. Pangeran Walangsungsang, Kec. Ciledug, Kabupaten Cirebon, Jawa Barat 45188");
            escpos.writeLF(centerAlign, "Tel: (XXX) XXX-XXXXXXX");
            escpos.feed(1);

            escpos.writeLF(leftAlign, String.format("Tanggal     : %s", DateTimeUtils.formatInstant(sale.getCreatedAt())));
            escpos.writeLF(leftAlign, String.format("Kasir       : %s", sale.getCreatedBy()));
            escpos.writeLF(leftAlign, String.format("No Trx      : %s", sale.getCode()));
            escpos.writeLF(leftAlign, String.format("Pembayaran  : %s", sale.getPaymentType()));

            escpos.feed(1);
            escpos.writeLF("--------------------------------");
            for (SaleItem  item : sale.getItems()) {
                escpos.writeLF(leftAlign, String.format("%s", item.getItem().getName()));
                String itemText = String.format("  %s x %s", item.getQuantity(), item.getUnitPrice().toString());
                escpos.writeLF(leftAlign, formatItemLine(itemText, item.getSubtotal().toString(),32));
            }
            escpos.writeLF("--------------------------------");
            escpos.writeLF(leftAlign, formatItemLine("Subtotal", sale.getSubtotalAmount().toString(),32));
            escpos.writeLF(leftAlign, formatItemLine("Diskon", sale.getDiscountAmount().toString(),32));
            escpos.writeLF(leftAlign, formatItemLine("Total", sale.getTotalAmount().toString(),32));
            escpos.writeLF(leftAlign, formatItemLine("Bayar", sale.getPaidAmount().toString(),32));
            escpos.writeLF(leftAlign, formatItemLine(
                    "Kembali", sale.getPaidAmount().subtract(sale.getTotalAmount()).toString(),32)
            );

            escpos.feed(1);
            escpos.writeLF(centerAlign, "Thank you!");
            escpos.feed(3);
            escpos.cut(EscPos.CutMode.FULL);

            escpos.close();

            return Boolean.TRUE;
        } catch (IOException e) {
            throw new IOException("Error printing receipt");
        }
    }

    public PrintService getPrinterService () {
        String printerName = printerProperties.getPrinterName();

        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        PrintService selectedService = null;

        for (PrintService service : services) {
            if (service.getName().equalsIgnoreCase(printerName)) {
                selectedService = service;
                break;
            }
        }

        if (selectedService == null) {
            System.out.println("Printer not found: " + printerName);
            throw new ResponseStatusException(ErrorCode.PRINTER_NOT_FOUND.getStatus(), ErrorCode.PRINTER_NOT_FOUND.getMessage());
        }

        return selectedService;
    }

    public String formatItemLine(String itemText, String total, int lineWidth) {
        int totalLength = itemText.length() + total.length();
        int spaces = lineWidth - totalLength;
        if (spaces < 1) spaces = 1;

        return itemText + " ".repeat(spaces) + total;
    }
}
