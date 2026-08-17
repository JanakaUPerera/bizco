package com.bizco.server.catalog.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.stereotype.Service;

/** DevelopmentPlan.md Week 8 task 8.8: CODE-128/QR image generation, shared by Catalog (product barcodes) and Sales (receipt output). */
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

    /** An internal Bizco invoice QR (verification reference, not a LankaQR/e-Invoice compliant code - MVP.md's e-Invoicing/QR-payment scope is out of MVP). */
    public byte[] qrPng(final String value, final int size) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("QR value is required.");
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE,
                    Math.max(size, 120), Math.max(size, 120)), "PNG", output);
            return output.toByteArray();
        } catch (final Exception exception) {
            throw new IllegalStateException("QR image could not be generated.", exception);
        }
    }
}
