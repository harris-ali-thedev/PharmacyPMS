package com.pharmacy.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import com.pharmacy.model.Sale;
import com.pharmacy.model.SaleItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generates a PDF invoice for a completed sale using iText 5.
 */
public class InvoiceService {

    private static final String PHARMACY_NAME    = "MEDICARE PHARMACY";
    private static final String PHARMACY_ADDRESS = "Blue Area, Islamabad";
    private static final String PHARMACY_PHONE   = "051-1234567";

    private static final Font FONT_TITLE   = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD,   BaseColor.BLUE);
    private static final Font FONT_HEADER  = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD,   BaseColor.BLACK);
    private static final Font FONT_NORMAL  = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.BLUE);
    private static final Font FONT_SMALL   = new Font(Font.FontFamily.HELVETICA,  9, Font.NORMAL, BaseColor.GRAY);
    private static final Font FONT_TOTAL   = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD,   new BaseColor(24, 95, 165));

    public String generateInvoice(Sale sale, java.util.List<SaleItem> items) throws DocumentException, IOException {
        if (sale == null) {
            throw new IllegalArgumentException("Sale cannot be null");
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items list cannot be null or empty");
        }

//        if (outputDir == null || outputDir.trim().isEmpty()) {
//            outputDir = ("G:\\Projects\\Pharmacy files");
//        }

        String outputDir = "G:\\Projects\\Pharmacy files";
        // Ensure directory exists
        File directory = new File(outputDir);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                throw new IOException("Cannot create directory: " + outputDir);
            }
        }

        // Ensure sale date is not null
        LocalDateTime saleDate = sale.getSaleDate();
        if (saleDate == null) {
            saleDate = LocalDateTime.now();
            sale.setSaleDate(saleDate);
        }


        String filename = outputDir + "/Invoice_" + sale.getSaleId() + ".pdf";
        Document doc = new Document(PageSize.A5, 28, 28, 28, 28);
        PdfWriter.getInstance(doc, new FileOutputStream(filename));
        doc.open();

        // ── Header ──────────────────────────────────────────
        Paragraph title = new Paragraph(PHARMACY_NAME, FONT_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        Paragraph addr = new Paragraph(PHARMACY_ADDRESS + "\n" + PHARMACY_PHONE, FONT_SMALL);
        addr.setAlignment(Element.ALIGN_CENTER);
        doc.add(addr);

        doc.add(new Chunk(new LineSeparator(0.5f, 100, BaseColor.LIGHT_GRAY, Element.ALIGN_CENTER, -2)));
        doc.add(Chunk.NEWLINE);

        // ── Invoice meta ─────────────────────────────────────
        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100);
        meta.setWidths(new float[]{50, 50});
        addMeta(meta, "Invoice #:", String.valueOf(sale.getSaleId()));
        addMeta(meta, "Date:", sale.getSaleDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")));
        addMeta(meta, "Cashier:", sale.getCashierName() != null ? sale.getCashierName() : "—");
        addMeta(meta, "Payment:", sale.getPaymentMethod());
        meta.setSpacingAfter(8);
        doc.add(meta);

        // ── Items table ──────────────────────────────────────
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{30, 15, 15, 20, 20});
        String[] headers = {"Medicine", "Qty", "Unit", "Subtotal", ""};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FONT_HEADER));
            cell.setBackgroundColor(new BaseColor(230, 241, 251));
            cell.setBorder(Rectangle.BOTTOM);
            cell.setPadding(5);
            table.addCell(cell);
        }
        for (SaleItem item : items) {
            addCell(table, item.getMedicineName());
            addCell(table, String.valueOf(item.getQuantity()));
            addCell(table, String.format("Rs %.0f", item.getUnitPrice()));
            addCell(table, String.format("Rs %.0f", item.getSubtotal()));
            addCell(table, "");
        }
        table.setSpacingAfter(8);
        doc.add(table);

        // ── Totals ───────────────────────────────────────────
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(60);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setWidths(new float[]{55, 45});
        addTotalRow(totals, "Subtotal:",        String.format("Rs %.2f", sale.getSubtotal()), false);
        if (sale.getDiscountAmount() > 0)
            addTotalRow(totals, "Discount:",    String.format("- Rs %.2f", sale.getDiscountAmount()), false);
        if (sale.getTaxAmount() > 0)
            addTotalRow(totals, "Tax:",         String.format("Rs %.2f", sale.getTaxAmount()), false);
        addTotalRow(totals, "Grand Total:",     String.format("Rs %.2f", sale.getGrandTotal()), true);
        addTotalRow(totals, "Amount Paid:",     String.format("Rs %.2f", sale.getAmountPaid()), false);
        addTotalRow(totals, "Change:",          String.format("Rs %.2f", sale.getChangeReturned()), false);
        doc.add(totals);

        // ── Footer ──────────────────────────────────────────
        doc.add(Chunk.NEWLINE);
        doc.add(new Chunk(new LineSeparator(0.5f, 100, BaseColor.LIGHT_GRAY, Element.ALIGN_CENTER, -2)));
        Paragraph footer = new Paragraph("Thank you for your business!\nPlease retain this receipt for your records.", FONT_SMALL);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(4);
        doc.add(footer);

        doc.close();
        return filename;
    }

    private void addMeta(PdfPTable t, String label, String value) {
        PdfPCell lc = noBorderCell(new Phrase(label, FONT_HEADER));
        PdfPCell vc = noBorderCell(new Phrase(value, FONT_NORMAL));
        vc.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(lc);
        t.addCell(vc);
    }

    private void addCell(PdfPTable t, String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, FONT_NORMAL));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(BaseColor.LIGHT_GRAY);
        c.setPadding(4);
        t.addCell(c);
    }

    private void addTotalRow(PdfPTable t, String label, String value, boolean bold) {
        Font f = bold ? FONT_TOTAL : FONT_NORMAL;
        PdfPCell lc = noBorderCell(new Phrase(label, bold ? FONT_TOTAL : FONT_HEADER));
        PdfPCell vc = noBorderCell(new Phrase(value, f));
        vc.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(lc);
        t.addCell(vc);
    }

    private PdfPCell noBorderCell(Phrase phrase) {
        PdfPCell c = new PdfPCell(phrase);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(3);
        return c;
    }
}
