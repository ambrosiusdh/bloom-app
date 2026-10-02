package com.bloom.app.service.impl;

import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.properties.PrinterProperties;
import com.bloom.app.persistence.repository.SaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.print.PrintService;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import javax.print.attribute.standard.PrinterState;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class PrintServiceImplTest {
    private static final String PRINTER_NAME = "Receipt Printer";

    private final SaleRepository saleRepository = mock(SaleRepository.class);
    private final PrinterProperties printerProperties = PrinterProperties.builder()
        .printerName(PRINTER_NAME)
        .build();

    @Test
    void rejectsPrinterThatIsOfflineAtOperatingSystemLevel() {
        PrintService printer = printer(PRINTER_NAME);
        PrintServiceImpl service = spy(new PrintServiceImpl(saleRepository, printerProperties));
        doReturn(new PrintService[]{printer}).when(service).lookupPrintServices();
        doReturn(false).when(service).isOperatingSystemPrinterOnline(PRINTER_NAME);

        assertThatThrownBy(service::getPrinterService)
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(ErrorCode.PRINTER_UNAVAILABLE.getStatus());
                assertThat(exception.getReason()).isEqualTo(ErrorCode.PRINTER_UNAVAILABLE.getMessage());
            });
    }

    @Test
    void rejectsPrinterThatIsNotAcceptingJobs() {
        PrintService printer = printer(PRINTER_NAME);
        when(printer.getAttribute(PrinterIsAcceptingJobs.class))
            .thenReturn(PrinterIsAcceptingJobs.NOT_ACCEPTING_JOBS);
        PrintServiceImpl service = spy(new PrintServiceImpl(saleRepository, printerProperties));
        doReturn(new PrintService[]{printer}).when(service).lookupPrintServices();

        assertThatThrownBy(service::getPrinterService)
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(ErrorCode.PRINTER_UNAVAILABLE.getStatus()));
    }

    @Test
    void rejectsStoppedPrinter() {
        PrintService printer = printer(PRINTER_NAME);
        when(printer.getAttribute(PrinterState.class)).thenReturn(PrinterState.STOPPED);
        PrintServiceImpl service = spy(new PrintServiceImpl(saleRepository, printerProperties));
        doReturn(new PrintService[]{printer}).when(service).lookupPrintServices();

        assertThatThrownBy(service::getPrinterService)
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(ErrorCode.PRINTER_UNAVAILABLE.getStatus()));
    }

    @Test
    void returnsPrinterWhenAllAvailabilityChecksPass() {
        PrintService printer = printer(PRINTER_NAME);
        PrintServiceImpl service = spy(new PrintServiceImpl(saleRepository, printerProperties));
        doReturn(new PrintService[]{printer}).when(service).lookupPrintServices();
        doReturn(true).when(service).isOperatingSystemPrinterOnline(PRINTER_NAME);

        assertThat(service.getPrinterService()).isSameAs(printer);
    }

    @Test
    void formatsWholeAndFractionalQuantitiesWithoutPaddingZeros() {
        PrintServiceImpl service = new PrintServiceImpl(saleRepository, printerProperties);

        assertThat(service.formatQuantity(new BigDecimal("1.0000"))).isEqualTo("1");
        assertThat(service.formatQuantity(new BigDecimal("0.2500"))).isEqualTo("0.25");
        assertThat(service.formatQuantity(new BigDecimal("12.3450"))).isEqualTo("12.345");
    }

    @Test
    void formatsRupiahWithoutDecimalPlaces() {
        PrintServiceImpl service = new PrintServiceImpl(saleRepository, printerProperties);

        assertThat(service.formatRupiah(new BigDecimal("12500.0000"))).isEqualTo("12500");
        assertThat(service.formatRupiah(new BigDecimal("12500.5000"))).isEqualTo("12501");
    }

    private PrintService printer(String name) {
        PrintService printer = mock(PrintService.class);
        when(printer.getName()).thenReturn(name);
        when(printer.getAttribute(PrinterIsAcceptingJobs.class))
            .thenReturn(PrinterIsAcceptingJobs.ACCEPTING_JOBS);
        return printer;
    }
}
