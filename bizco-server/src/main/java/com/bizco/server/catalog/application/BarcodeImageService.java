package com.bizco.server.catalog.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.oned.Code128Writer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.stereotype.Service;

@Service
public class BarcodeImageService {

    public byte[] code128Png(final String value, final int width, final int height) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Barcode value is required.");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(new Code128Writer().encode(value, BarcodeFormat.CODE_128,
                    Math.max(width, 160), Math.max(height, 60)), "PNG", output);
            return output.toByteArray();
        } catch (final IOException exception) {
            throw new IllegalStateException("Barcode image could not be generated.", exception);
        }
    }
}
