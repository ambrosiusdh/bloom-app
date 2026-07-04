package com.bloom.app.service.util;

import com.bloom.app.domain.model.Item;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import com.bloom.app.domain.properties.PdfProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PdfGeneratorUtil {
    private final PdfProperties pdfProperties;

    /**
     * Generates a printable A4 PDF containing barcode labels for the provided items.
     * 3 columns per row.
     *
     * @param items List of items to generate labels for
     * @return byte array of the PDF
     */
    public byte[] generateBarcodeLayoutPdf(List<Item> items) {
        int columns = pdfProperties.getColumns();
        float margin = pdfProperties.getMargin();
        Document document = null;
        ByteArrayOutputStream baos = null;

        try {
            baos = new ByteArrayOutputStream();
            document = new Document(PageSize.A4, margin, margin, margin, margin);
            PdfWriter.getInstance(document, baos);
            document.open();

            PdfPTable table = new PdfPTable(columns);
            table.setWidthPercentage(100f);
            
            // Give a little space between cells
            table.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            Font skuFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font nameFont = new Font(Font.HELVETICA, 8, Font.NORMAL);

            for (Item item : items) {
                PdfPCell cell = new PdfPCell();
                // Padding for easy cutting and spacing
                cell.setPadding(15f);
                cell.setMinimumHeight(110f); // Ensures uniform row height

                // Add a light border as a cutting guide
                cell.setBorder(Rectangle.BOX);
                cell.setBorderWidth(0.5f);
                cell.setBorderColor(new Color(200, 200, 200));

                // 1. Barcode Image
                // Using 150x40 logical pixels for image generation
                BufferedImage bcImage = BarcodeGeneratorUtil.generateCode128BarcodeImage(item.getSku(), 150, 40);
                Image pdfImage = Image.getInstance(bcImage, null);
                pdfImage.setAlignment(Element.ALIGN_CENTER);
                pdfImage.scaleToFit(140f, 40f);

                Paragraph imgPara = new Paragraph();
                imgPara.add(new Chunk(pdfImage, 0, 0, true));
                imgPara.setAlignment(Element.ALIGN_CENTER);

                // 2. Text Content (SKU, Name)
                Paragraph skuPara = new Paragraph(item.getSku(), skuFont);
                skuPara.setAlignment(Element.ALIGN_CENTER);
                skuPara.setSpacingBefore(8f); // Gap between barcode and text

                String itemName = item.getName() != null ? item.getName() : "Item";
                Paragraph namePara = new Paragraph(itemName, nameFont);
                namePara.setAlignment(Element.ALIGN_CENTER);
                namePara.setSpacingBefore(4f);

                cell.addElement(imgPara);
                cell.addElement(skuPara);
                cell.addElement(namePara);

                table.addCell(cell);
            }

            // Pad the last row with empty cells if it doesn't divide evenly
            int remainder = items.size() % columns;
            if (remainder != 0) {
                for (int i = 0; i < columns - remainder; i++) {
                    PdfPCell emptyCell = new PdfPCell();
                    emptyCell.setBorder(Rectangle.NO_BORDER);
                    table.addCell(emptyCell);
                }
            }

            document.add(table);
            document.close();

            return baos.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new RuntimeException("Failed to generate PDF for barcode labels.", e);
        } finally {
            // Ensure the document is closed to free resources
            if (document != null && document.isOpen()) {
                document.close();
            }
            
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException e) {
                    log.error("Failed to close ByteArrayOutputStream", e);
                }
            }
        }
    }
}
