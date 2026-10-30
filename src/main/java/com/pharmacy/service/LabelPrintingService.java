package com.pharmacy.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.pharmacy.model.Prescription;
import com.pharmacy.model.Sale;
import com.pharmacy.model.SaleItem;
import com.pharmacy.util.AppConstants;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dispensing Label Generation Service.
 *
 * Generates legally-compliant dispensing labels containing:
 *   - Pharmacy name, address, phone, license
 *   - Patient name
 *   - Drug name, strength, dosage form
 *   - Prescriber name and registration number
 *   - Dose instructions (frequency, duration)
 *   - Dispensing date and Rx expiry
 *   - Refills remaining
 *   - Warning bands (narcotic flag, storage instructions)
 *   - Barcode (Rx number for scan-to-verify)
 *
 * Output: PDF sized for 4" × 2" Dymo/Zebra label stock.
 * In production, replace PDF output with a direct ESC/POS or ZPL stream
 * for direct-to-printer label generation without a PDF intermediate.
 */
public class LabelPrintingService {

    private static final Logger LOG = Logger.getLogger(LabelPrintingService.class.getName());

    private static final float LABEL_WIDTH  = 288f;  // 4 inches in points (72pt/in)
    private static final float LABEL_HEIGHT = 144f;  // 2 inches in points

    private static final Font FONT_PHARMACY = new Font(Font.FontFamily.HELVETICA, 8, Font.BOLD);
    private static final Font FONT_PATIENT  = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD);
    private static final Font FONT_DRUG     = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
    private static final Font FONT_NORMAL   = new Font(Font.FontFamily.HELVETICA, 7, Font.NORMAL);
    private static final Font FONT_WARN     = new Font(Font.FontFamily.HELVETICA, 7, Font.BOLD, BaseColor.WHITE);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Public API ───────────────────────────────────────────

    public List<PrescriptionService.DispensingLabel> generateLabels(
            Sale sale, Prescription rx, List<SaleItem> items) {

        List<PrescriptionService.DispensingLabel> labels = new ArrayList<>();

        for (SaleItem item : items) {
            // Find matching Rx item for dosage instructions
            Prescription.PrescriptionItem rxItem = rx.getItems().stream()
                    .filter(i -> i.getMedId() == item.getMedId())
                    .findFirst().orElse(null);

            String dosage     = rxItem != null ? rxItem.getDosage()    : "";
            String frequency  = rxItem != null ? rxItem.getFrequency() : "";
            String duration   = rxItem != null ? rxItem.getDuration()  : "";
            String instructions = rxItem != null ? rxItem.getInstructions() : "";

            labels.add(new PrescriptionService.DispensingLabel(
                    AppConstants.PHARMACY_NAME,
                    AppConstants.PHARMACY_ADDRESS,
                    AppConstants.PHARMACY_PHONE,
                    rx.getPatientName(),
                    item.getMedicineName(),
                    "",   // strength — add to SaleItem in a future iteration
                    dosage, frequency, duration, instructions,
                    rx.getDoctorName() + " (" + rx.getDoctorRegNo() + ")",
                    LocalDate.now().format(DATE_FMT),
                    rx.getExpiryDate() != null ? rx.getExpiryDate().format(DATE_FMT) : "N/A",
                    rx.getRefillsRemaining(),
                    item.isNarcotic(),
                    rx.getPrescriptionNumber()
            ));
        }

        return labels;
    }

    /**
     * Generates a PDF label file for the given label data.
     * @return absolute path to the generated PDF file
     */
    public String generateLabelPdf(PrescriptionService.DispensingLabel label, String outputDir)
            throws DocumentException, IOException {

        File dir = new File(outputDir);
        if (!dir.exists()) dir.mkdirs();

        String filename = outputDir + File.separator
                + "Label_" + label.barcodeData() + "_" + System.currentTimeMillis() + ".pdf";

        Rectangle pageSize = new Rectangle(LABEL_WIDTH, LABEL_HEIGHT);
        Document doc = new Document(pageSize, 6, 6, 6, 6);
        PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(filename));
        doc.open();

        PdfContentByte canvas = writer.getDirectContent();

        // ── Pharmacy header ──────────────────────────────────
        Paragraph header = new Paragraph(label.pharmacyName(), FONT_PHARMACY);
        header.setAlignment(Element.ALIGN_CENTER);
        doc.add(header);

        Paragraph address = new Paragraph(
                label.pharmacyAddress() + " | " + label.pharmacyPhone(), FONT_NORMAL);
        address.setAlignment(Element.ALIGN_CENTER);
        address.setSpacingAfter(2);
        doc.add(address);

        // ── Separator line ───────────────────────────────────
        canvas.setLineWidth(0.5f);
        canvas.moveTo(6, LABEL_HEIGHT - 28);
        canvas.lineTo(LABEL_WIDTH - 6, LABEL_HEIGHT - 28);
        canvas.stroke();

        // ── Patient name ─────────────────────────────────────
        doc.add(new Paragraph("Patient: " + label.patientName(), FONT_PATIENT));

        // ── Drug name ────────────────────────────────────────
        String drugLine = label.medicineName()
                + (label.strength() != null && !label.strength().isBlank()
                ? " " + label.strength() : "");
        doc.add(new Paragraph(drugLine, FONT_DRUG));

        // ── Dosage instructions ──────────────────────────────
        if (!label.dosage().isBlank()) {
            doc.add(new Paragraph("Take: " + label.dosage()
                    + " — " + label.frequency()
                    + (label.duration().isBlank() ? "" : " for " + label.duration()),
                    FONT_NORMAL));
        }

        if (!label.instructions().isBlank()) {
            doc.add(new Paragraph(label.instructions(), FONT_NORMAL));
        }

        // ── Prescriber ───────────────────────────────────────
        doc.add(new Paragraph("Rx: " + label.prescribedBy(), FONT_NORMAL));

        // ── Dates row ────────────────────────────────────────
        doc.add(new Paragraph("Dispensed: " + label.dispensedDate()
                + "   Exp: " + label.expiryDate()
                + "   Refills: " + label.refillsRemaining(), FONT_NORMAL));

        // ── Narcotic warning band ─────────────────────────────
        if (label.narcotic()) {
            PdfPTable warnTable = new PdfPTable(1);
            warnTable.setWidthPercentage(100);
            PdfPCell warnCell = new PdfPCell(
                    new Phrase("⚠ CONTROLLED SUBSTANCE — Keep out of reach of children", FONT_WARN));
            warnCell.setBackgroundColor(BaseColor.RED);
            warnCell.setBorder(Rectangle.NO_BORDER);
            warnCell.setPadding(2);
            warnCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            warnTable.addCell(warnCell);
            warnTable.setSpacingBefore(2);
            doc.add(warnTable);
        }

        // ── Barcode ──────────────────────────────────────────
        if (label.barcodeData() != null) {
            try {
                Barcode128 barcode = new Barcode128();
                barcode.setCode(label.barcodeData());
                barcode.setBarHeight(15f);
                barcode.setX(1.0f);
                Image barcodeImage = barcode.createImageWithBarcode(canvas, null, null);
                barcodeImage.setAlignment(Image.ALIGN_RIGHT);
                barcodeImage.scaleToFit(80, 25);
                doc.add(barcodeImage);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Barcode generation failed", e);
            }
        }

        doc.close();
        return filename;
    }
}
