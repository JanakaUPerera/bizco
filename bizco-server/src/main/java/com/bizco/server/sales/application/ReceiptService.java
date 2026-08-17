package com.bizco.server.sales.application;

import com.bizco.common.api.ApiErrorCode;
import com.bizco.server.audit.service.AuditService;
import com.bizco.server.catalog.application.BarcodeImageService;
import com.bizco.server.identity.repository.UserRepository;
import com.bizco.server.identity.service.IdentityException;
import com.bizco.server.sales.domain.Invoice;
import com.bizco.server.sales.domain.InvoiceLine;
import com.bizco.server.sales.domain.InvoiceStatus;
import com.bizco.server.sales.infrastructure.InvoiceBalanceRepository;
import com.bizco.server.sales.infrastructure.InvoiceRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receipt / tax invoice PDF output (DevelopmentPlan.md Week 8 tasks 8.7-8.9, SRS.md &sect;6.11.6.1
 * for the QR content). One A4 layout serves both the receipt print at checkout and a later
 * reprint - MVP.md's POS hardware section covers thermal receipt printers as a physical output
 * device, not a distinct document layout, so this doesn't maintain two templates.
 *
 * <p>The QR code is an <b>internal Bizco verification code</b> (invoice number, TIN, total), not a
 * LankaQR/e-Invoice compliant code with a digital signature - full e-Invoicing (SRS.md &sect;6.11.6.1's
 * complete field set) is explicitly out of MVP scope (MVP.md &sect;1.3).
 */
@Service
public class ReceiptService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final float MARGIN = 40f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();

    private final InvoiceRepository invoiceRepository;
    private final InvoiceBalanceRepository invoiceBalanceRepository;
    private final BarcodeImageService barcodeImageService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ReceiptService(final InvoiceRepository invoiceRepository, final InvoiceBalanceRepository invoiceBalanceRepository,
                          final BarcodeImageService barcodeImageService, final UserRepository userRepository,
                          final AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceBalanceRepository = invoiceBalanceRepository;
        this.barcodeImageService = barcodeImageService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public byte[] receipt(final UUID invoiceId) {
        return render(load(invoiceId));
    }

    /** Reprint is a distinct, audited action (task 8.9) - gated by invoice.reprint at the controller. */
    @Transactional
    public byte[] reprint(final UUID invoiceId, final Authentication authentication) {
        final Invoice invoice = load(invoiceId);
        final byte[] pdf = render(invoice);
        auditService.record("INVOICE", invoice.getId().toString(), "INVOICE_REPRINTED", actor(authentication),
                Map.of("invoiceNumber", invoice.getInvoiceNumber()));
        return pdf;
    }

    private Invoice load(final UUID invoiceId) {
        final Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new IdentityException(
                ApiErrorCode.INVOICE_NOT_FOUND, HttpStatus.NOT_FOUND, "Invoice was not found"));
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new IdentityException(ApiErrorCode.INVOICE_NOT_POSTED, HttpStatus.CONFLICT,
                    "A receipt is only available once the invoice is posted");
        }
        return invoice;
    }

    private byte[] render(final Invoice invoice) {
        try (PDDocument document = new PDDocument()) {
            final PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = PDRectangle.A4.getHeight() - MARGIN;
                y = writeCentered(content, bold, 16, y, invoice.getBusinessNameSnapshot() == null
                        ? "Bizco" : invoice.getBusinessNameSnapshot());
                if (invoice.getBusinessAddressSnapshot() != null) {
                    y = writeCentered(content, regular, 9, y - 4, invoice.getBusinessAddressSnapshot());
                }
                if (invoice.getBusinessTinSnapshot() != null) {
                    y = writeCentered(content, regular, 9, y - 2, "TIN: " + invoice.getBusinessTinSnapshot());
                }
                y -= 18;
                y = writeLine(content, bold, 12, y, invoice.getInvoiceType() + " INVOICE");
                y = writeLine(content, regular, 10, y - 4, "Invoice No: " + invoice.getInvoiceNumber());
                y = writeLine(content, regular, 10, y - 2, "Date: " + invoice.getInvoiceDate());
                if (invoice.getPostedAt() != null) {
                    y = writeLine(content, regular, 10, y - 2, "Posted: " + TIMESTAMP_FORMAT.format(invoice.getPostedAt()));
                }
                if (invoice.getStatus() == InvoiceStatus.VOIDED) {
                    y = writeLine(content, bold, 11, y - 6, "*** VOIDED - " + nullToEmpty(invoice.getVoidReason()) + " ***");
                }
                if (invoice.getCustomerNameSnapshot() != null) {
                    y = writeLine(content, regular, 10, y - 8, "Customer: " + invoice.getCustomerNameSnapshot());
                    if (invoice.getCustomerTinSnapshot() != null) {
                        y = writeLine(content, regular, 10, y - 2, "Customer TIN: " + invoice.getCustomerTinSnapshot());
                    }
                }

                y -= 16;
                y = writeLine(content, bold, 10, y, "Description");
                content.beginText();
                content.setFont(bold, 10);
                content.newLineAtOffset(PAGE_WIDTH - MARGIN - 180, y + 12);
                content.showText("Qty      Price      Total");
                content.endText();
                y -= 6;

                final List<InvoiceLine> lines = invoice.getLines();
                for (final InvoiceLine line : lines) {
                    y = writeLine(content, regular, 9, y - 12, truncate(line.getDescriptionSnapshot(), 55));
                    content.beginText();
                    content.setFont(regular, 9);
                    content.newLineAtOffset(PAGE_WIDTH - MARGIN - 180, y + 12);
                    content.showText(line.getQuantity().stripTrailingZeros().toPlainString() + "   "
                            + line.getUnitPrice().toPlainString() + "   " + line.getLineTotalInclVat().toPlainString());
                    content.endText();
                }

                y -= 20;
                y = writeRight(content, regular, 10, y, "Subtotal: " + invoice.getSubtotal().toPlainString());
                if (invoice.getDiscountAmount() != null && invoice.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                    y = writeRight(content, regular, 10, y - 2, "Discount: -" + invoice.getDiscountAmount().toPlainString());
                }
                y = writeRight(content, regular, 10, y - 2, "VAT: " + invoice.getVatAmount().toPlainString());
                y = writeRight(content, bold, 12, y - 4, "Total: " + invoice.getTotalAmount().toPlainString());
                final BigDecimal balance = invoiceBalanceRepository.balanceDue(invoice.getId());
                if (balance.compareTo(BigDecimal.ZERO) > 0) {
                    y = writeRight(content, regular, 10, y - 4, "Balance Due: " + balance.toPlainString());
                }

                y -= 30;
                final byte[] qrPng = barcodeImageService.qrPng(qrPayload(invoice), 110);
                final PDImageXObject qrImage = PDImageXObject.createFromByteArray(document, qrPng, "qr");
                content.drawImage(qrImage, MARGIN, y - 110, 110, 110);

                final byte[] barcodePng = barcodeImageService.code128Png(invoice.getInvoiceNumber(), 220, 50);
                final PDImageXObject barcodeImage = PDImageXObject.createFromByteArray(document, barcodePng, "barcode");
                content.drawImage(barcodeImage, PAGE_WIDTH - MARGIN - 220, y - 60, 220, 50);
            }

            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (final IOException exception) {
            throw new IllegalStateException("Receipt PDF could not be generated", exception);
        }
    }

    /** Internal verification payload (see class Javadoc) - not a signed e-Invoice QR. */
    private String qrPayload(final Invoice invoice) {
        return "BIZCO-INV|" + invoice.getInvoiceNumber() + "|TIN:" + nullToEmpty(invoice.getBusinessTinSnapshot())
                + "|TOTAL:" + invoice.getTotalAmount().toPlainString();
    }

    private float writeCentered(final PDPageContentStream content, final PDFont font, final float size, final float y,
                                final String text) throws IOException {
        final float width = font.getStringWidth(text) / 1000 * size;
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset((PAGE_WIDTH - width) / 2, y);
        content.showText(text);
        content.endText();
        return y;
    }

    private float writeLine(final PDPageContentStream content, final PDFont font, final float size, final float y,
                            final String text) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(MARGIN, y);
        content.showText(text);
        content.endText();
        return y;
    }

    private float writeRight(final PDPageContentStream content, final PDFont font, final float size, final float y,
                             final String text) throws IOException {
        final float width = font.getStringWidth(text) / 1000 * size;
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(PAGE_WIDTH - MARGIN - width, y);
        content.showText(text);
        content.endText();
        return y;
    }

    private String truncate(final String value, final int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }

    private UUID actor(final Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).map(user -> user.getId()).orElse(null);
    }
}
