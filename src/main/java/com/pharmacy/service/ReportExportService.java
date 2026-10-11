package com.pharmacy.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.pharmacy.model.Medicine;
import com.pharmacy.model.Sale;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReportExportService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // ─── Excel Export ────────────────────────────────────────
    public String exportSalesToExcel(List<Sale> sales, String outputDir) throws IOException {
        String filename = outputDir + "/SalesReport_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sales");

            // Header row
            CellStyle hStyle = wb.createCellStyle();
            hStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font hFont = wb.createFont();
            hFont.setBold(true);
            hFont.setColor(IndexedColors.WHITE.getIndex());
            hStyle.setFont(hFont);

            String[] cols = {"Sale ID", "Date", "Cashier", "Subtotal", "Discount", "Tax", "Grand Total", "Payment", "Status"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(hStyle);
            }

            // Data rows
            CellStyle numStyle = wb.createCellStyle();
            numStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            int rowIdx = 1;
            for (Sale s : sales) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(s.getSaleId());
                row.createCell(1).setCellValue(s.getSaleDate() != null ? s.getSaleDate().format(DT_FMT) : "");
                row.createCell(2).setCellValue(s.getCashierName() != null ? s.getCashierName() : "");

                Cell subtotalCell = row.createCell(3);
                subtotalCell.setCellValue(s.getSubtotal());
                subtotalCell.setCellStyle(numStyle);

                Cell discCell = row.createCell(4);
                discCell.setCellValue(s.getDiscountAmount());
                discCell.setCellStyle(numStyle);

                Cell taxCell = row.createCell(5);
                taxCell.setCellValue(s.getTaxAmount());
                taxCell.setCellStyle(numStyle);

                Cell totalCell = row.createCell(6);
                totalCell.setCellValue(s.getGrandTotal());
                totalCell.setCellStyle(numStyle);

                row.createCell(7).setCellValue(s.getPaymentMethod());
                row.createCell(8).setCellValue(s.getStatus());
            }

            // Auto-size columns
            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream fos = new FileOutputStream(filename)) {
                wb.write(fos);
            }
        }
        return filename;
    }

    // ─── PDF Export ──────────────────────────────────────────
    public String exportSalesToPdf(List<Sale> sales, String title, String outputDir) throws DocumentException, IOException {
        String filename = outputDir + "/SalesReport_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf";

        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        PdfWriter.getInstance(doc, new FileOutputStream(filename));
        doc.open();

        com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(
                com.itextpdf.text.Font.FontFamily.HELVETICA, 16, com.itextpdf.text.Font.BOLD, BaseColor.DARK_GRAY);
        Paragraph heading = new Paragraph(title, titleFont);
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingAfter(12);
        doc.add(heading);

        com.itextpdf.text.Font hFont = new com.itextpdf.text.Font(
                com.itextpdf.text.Font.FontFamily.HELVETICA, 9, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font dFont = new com.itextpdf.text.Font(
                com.itextpdf.text.Font.FontFamily.HELVETICA, 9);

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{8, 18, 18, 12, 12, 12, 14, 10});

        String[] headers = {"ID", "Date", "Cashier", "Subtotal", "Discount", "Tax", "Grand Total", "Payment"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
            cell.setBackgroundColor(new BaseColor(56, 128, 200));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }

        boolean alt = false;
        for (Sale s : sales) {
            BaseColor bg = alt ? new BaseColor(245, 28, 122) : BaseColor.WHITE;
            addPdfCell(table, String.valueOf(s.getSaleId()), dFont, bg, Element.ALIGN_CENTER);
            addPdfCell(table, s.getSaleDate() != null ? s.getSaleDate().format(DT_FMT) : "", dFont, bg, Element.ALIGN_LEFT);
            addPdfCell(table, s.getCashierName() != null ? s.getCashierName() : "", dFont, bg, Element.ALIGN_LEFT);
            addPdfCell(table, String.format("%.2f", s.getSubtotal()),      dFont, bg, Element.ALIGN_RIGHT);
            addPdfCell(table, String.format("%.2f", s.getDiscountAmount()), dFont, bg, Element.ALIGN_RIGHT);
            addPdfCell(table, String.format("%.2f", s.getTaxAmount()),      dFont, bg, Element.ALIGN_RIGHT);
            addPdfCell(table, String.format("%.2f", s.getGrandTotal()),     dFont, bg, Element.ALIGN_RIGHT);
            addPdfCell(table, s.getPaymentMethod(), dFont, bg, Element.ALIGN_CENTER);
            alt = !alt;
        }
        doc.add(table);
        doc.close();
        return filename;
    }

    private void addPdfCell(PdfPTable t, String text, com.itextpdf.text.Font font,
                             BaseColor bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        t.addCell(c);
    }
    public String exportInventoryToExcel(List<Medicine> medicines, String directoryPath) throws Exception {
            // Create a unique filename with a timestamp so it doesn't overwrite old reports
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filePath = directoryPath + File.separator + "Inventory_Report_" + timestamp + ".xlsx";

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Inventory Data");

            // Create a bold font for the Header row
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);


            org.apache.poi.ss.usermodel.Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            // 1. Create the Header Row
            Row headerRow = sheet.createRow(0);
            String[] columns = {"Name", "Generic Name", "Category", "Barcode", "Price", "Stock", "Reorder Level", "Supplier"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // 2. Populate the Data Rows
            int rowNum = 1;
            for (Medicine med : medicines) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(med.getName() != null ? med.getName() : "");
                row.createCell(1).setCellValue(med.getGenericName() != null ? med.getGenericName() : "");
                row.createCell(2).setCellValue(med.getCategory() != null ? med.getCategory() : "");
                row.createCell(3).setCellValue(med.getBarcode() != null ? med.getBarcode() : "");
                row.createCell(4).setCellValue(med.getUnitPrice());
                row.createCell(5).setCellValue(med.getStockQty());
                row.createCell(6).setCellValue(med.getReorderLevel());
                row.createCell(7).setCellValue(med.getSupplierName() != null ? med.getSupplierName() : "");
            }

            // Auto-size columns to fit the text
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 3. Write to file
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
        return filePath; // Return the path so the UI can show the success popup
    }

    public String exportInventoryToPdf(List<Medicine> medicines, String title, String directoryPath) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filePath = directoryPath + File.separator + "Inventory_Report_" + timestamp + ".pdf";

        Document document = new Document(PageSize.A4.rotate()); // Landscape mode so columns fit!
        PdfWriter.getInstance(document, new FileOutputStream(filePath));

        document.open();

        // 1. Add Report Title
        com.itextpdf.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
        Paragraph reportTitle = new Paragraph(title, titleFont);
        reportTitle.setAlignment(Element.ALIGN_CENTER);
        reportTitle.setSpacingAfter(20);
        document.add(reportTitle);

        // 2. Create the Table (8 columns)
        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);

        // Define Headers
        String[] headers = {"Name", "Generic Name", "Category", "Barcode", "Price", "Stock", "Reorder Lvl", "Supplier"};
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBackgroundColor(BaseColor.BLUE);
            table.addCell(cell);
        }

        // 3. Populate Data
        for (Medicine med : medicines) {
            table.addCell(med.getName() != null ? med.getName() : "");
            table.addCell(med.getGenericName() != null ? med.getGenericName() : "");
            table.addCell(med.getCategory() != null ? med.getCategory() : "");
            table.addCell(med.getBarcode() != null ? med.getBarcode() : "");
            table.addCell(String.format("Rs %.2f", med.getUnitPrice()));
            table.addCell(String.valueOf(med.getStockQty()));
            table.addCell(String.valueOf(med.getReorderLevel()));
            table.addCell(med.getSupplierName() != null ? med.getSupplierName() : "");
        }

        document.add(table);
        document.close();

        return filePath;
    }
}
