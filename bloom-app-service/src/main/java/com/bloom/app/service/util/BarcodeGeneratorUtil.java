package com.bloom.app.service.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import lombok.NoArgsConstructor;

import java.awt.image.BufferedImage;

@NoArgsConstructor
public class BarcodeGeneratorUtil {
    /**
     * Generates a Code128 barcode BufferedImage from a given text (SKU).
     *
     * @param text   The SKU or text to encode.
     * @param width  The desired width of the barcode.
     * @param height The desired height of the barcode.
     * @return BufferedImage containing the generated barcode.
     */
    public static BufferedImage generateCode128BarcodeImage(String text, int width, int height) {
        try {
            Code128Writer barcodeWriter = new Code128Writer();
            BitMatrix bitMatrix = barcodeWriter.encode(text, BarcodeFormat.CODE_128, width, height);
            // MatrixToImageWriter creates a BufferedImage using TYPE_INT_RGB or ARGB depending on config
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate barcode image for text: " + text, e);
        }
    }
}
