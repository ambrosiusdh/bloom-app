package com.bloom.app.service.impl;

import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.SaleItem;
import com.bloom.app.domain.properties.PrinterProperties;
import com.bloom.app.domain.utils.DateTimeUtils;
import com.bloom.app.persistence.repository.SaleRepository;
import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.Style;
import com.github.anastaciocintra.escpos.EscPosConst.Justification;
import com.github.anastaciocintra.output.PrinterOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import javax.print.attribute.standard.PrinterState;
import javax.print.attribute.standard.PrinterStateReasons;
import javax.print.attribute.standard.Severity;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintServiceImpl implements com.bloom.app.service.PrintService {
    private static final String PRINTER_NAME_ENVIRONMENT_VARIABLE = "BLOOM_PRINTER_NAME";
    private static final long WINDOWS_STATUS_TIMEOUT_SECONDS = 10;
    private static final String WINDOWS_PRINTER_STATUS_SCRIPT = """
        try {
            $printer = Get-CimInstance -ClassName Win32_Printer -ErrorAction Stop |
                Where-Object { $_.Name -eq $env:BLOOM_PRINTER_NAME } |
                Select-Object -First 1

            if ($null -eq $printer) { exit 2 }
            if ($printer.WorkOffline -eq $true) { exit 3 }
            if (@(6, 7) -contains [int] $printer.PrinterStatus) { exit 4 }
            if (@(5, 7, 8, 9, 10, 11, 12) -contains [int] $printer.DetectedErrorState) { exit 5 }

            exit 0
        } catch {
            exit 10
        }
        """;

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
                String itemText = String.format(
                    "  %s x %s",
                    formatQuantity(item.getQuantity()),
                    formatRupiah(item.getUnitPrice())
                );
                escpos.writeLF(leftAlign, formatItemLine(itemText, formatRupiah(item.getSubtotal()),32));
            }
            escpos.writeLF("--------------------------------");
            escpos.writeLF(leftAlign, formatItemLine("Subtotal", formatRupiah(sale.getSubtotalAmount()),32));
            escpos.writeLF(leftAlign, formatItemLine("Diskon", formatRupiah(sale.getDiscountAmount()),32));
            escpos.writeLF(leftAlign, formatItemLine("Total", formatRupiah(sale.getTotalAmount()),32));
            escpos.writeLF(leftAlign, formatItemLine("Bayar", formatRupiah(sale.getPaidAmount()),32));
            escpos.writeLF(leftAlign, formatItemLine(
                    "Kembali", formatRupiah(sale.getPaidAmount().subtract(sale.getTotalAmount())),32)
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

    public PrintService getPrinterService() {
        String printerName = printerProperties.getPrinterName();

        PrintService[] services = lookupPrintServices();
        PrintService selectedService = null;

        for (PrintService service : services) {
            if (service.getName().equalsIgnoreCase(printerName)) {
                selectedService = service;
                break;
            }
        }

        if (selectedService == null) {
            log.warn("Configured printer was not found: {}", printerName);
            throw new ResponseStatusException(ErrorCode.PRINTER_NOT_FOUND.getStatus(), ErrorCode.PRINTER_NOT_FOUND.getMessage());
        }

        if (!isPrinterAvailable(selectedService) || !isOperatingSystemPrinterOnline(printerName)) {
            log.warn("Configured printer is unavailable or offline: {}", printerName);
            throw new ResponseStatusException(
                ErrorCode.PRINTER_UNAVAILABLE.getStatus(),
                ErrorCode.PRINTER_UNAVAILABLE.getMessage()
            );
        }

        return selectedService;
    }

    PrintService[] lookupPrintServices() {
        return PrintServiceLookup.lookupPrintServices(null, null);
    }

    boolean isPrinterAvailable(PrintService printer) {
        PrinterIsAcceptingJobs acceptingJobs = printer.getAttribute(PrinterIsAcceptingJobs.class);
        if (PrinterIsAcceptingJobs.NOT_ACCEPTING_JOBS.equals(acceptingJobs)) {
            return false;
        }

        PrinterState state = printer.getAttribute(PrinterState.class);
        if (PrinterState.STOPPED.equals(state)) {
            return false;
        }

        PrinterStateReasons reasons = printer.getAttribute(PrinterStateReasons.class);
        return reasons == null || reasons.entrySet().stream()
            .noneMatch(reason -> Severity.ERROR.equals(reason.getValue()));
    }

    boolean isOperatingSystemPrinterOnline(String printerName) {
        if (!isWindows()) {
            return true;
        }

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                getPowerShellExecutable(),
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                WINDOWS_PRINTER_STATUS_SCRIPT
            );
            processBuilder.environment().put(PRINTER_NAME_ENVIRONMENT_VARIABLE, printerName);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

            process = processBuilder.start();
            if (!process.waitFor(WINDOWS_STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("Timed out while checking Windows status for printer: {}", printerName);
                return false;
            }

            return process.exitValue() == 0;
        } catch (IOException e) {
            log.warn("Could not check Windows status for printer: {}", printerName, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while checking Windows status for printer: {}", printerName);
            return false;
        }
    }

    String formatQuantity(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }

    String formatRupiah(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getIntegerInstance(Locale.US);
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        return "Rp. " + formatter.format(amount);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private String getPowerShellExecutable() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null && !systemRoot.isBlank()) {
            Path executable = Path.of(
                systemRoot,
                "System32",
                "WindowsPowerShell",
                "v1.0",
                "powershell.exe"
            );
            if (Files.isRegularFile(executable)) {
                return executable.toString();
            }
        }

        return "powershell.exe";
    }

    public String formatItemLine(String itemText, String total, int lineWidth) {
        int totalLength = itemText.length() + total.length();
        int spaces = lineWidth - totalLength;
        if (spaces < 1) spaces = 1;

        return itemText + " ".repeat(spaces) + total;
    }
}
