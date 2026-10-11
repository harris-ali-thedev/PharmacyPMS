package com.pharmacy.view.billing;

import com.pharmacy.view.AppUIManager;
import com.pharmacy.view.dialogs.MessageDialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * InvoiceView — Generate, preview, and print invoices.
 */
public class InvoiceView {

    public Node getView() {
        VBox page = new VBox(0);
        page.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + ";");

        HBox header = new HBox(12);
        header.setPadding(new Insets(22, 24, 16, 24));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color:white; -fx-border-color:transparent transparent " + AppUIManager.COLOR_BORDER + " transparent; -fx-border-width:0 0 1 0;");
        Label title = AppUIManager.pageTitle("🧾  Invoice Management");
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button printBtn = AppUIManager.primaryButton("🖨  Print Invoice");
        printBtn.setOnAction(e -> MessageDialog.showInfo("Print", "Connect PrinterHelper.java to print invoices."));
        Button pdfBtn = AppUIManager.outlineButton("📄  Export PDF");
        pdfBtn.setOnAction(e -> MessageDialog.showInfo("PDF", "Connect PDFExporter.java to export invoices."));
        header.getChildren().addAll(title, sp, pdfBtn, printBtn);
        page.getChildren().add(header);

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));

        // Invoice search
        VBox searchCard = AppUIManager.card();
        searchCard.getChildren().add(AppUIManager.sectionTitle("Find Invoice"));
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        TextField invField = AppUIManager.styledField("Enter invoice number…");
        invField.setPrefWidth(280);
        Button findBtn = AppUIManager.primaryButton("Find");
        findBtn.setOnAction(e -> MessageDialog.showInfo("Search", "Connect InvoiceService.getByInvoiceNumber() here."));
        searchRow.getChildren().addAll(invField, findBtn);
        searchCard.getChildren().add(searchRow);

        // Preview area
        VBox previewCard = AppUIManager.card();
        previewCard.setMinHeight(400);
        previewCard.getChildren().add(AppUIManager.sectionTitle("Invoice Preview"));
        Label placeholder = new Label("Search for an invoice above to preview it here.\nThe invoice will show all line items, taxes, and payment details.");
        placeholder.setStyle("-fx-font-size:13px; -fx-text-fill:" + AppUIManager.COLOR_TEXT_MEDIUM + "; -fx-wrap-text:true;");
        previewCard.getChildren().add(placeholder);

        content.getChildren().addAll(searchCard, previewCard);
        page.getChildren().add(content);

        ScrollPane scrollPane = new ScrollPane(page);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color:" + AppUIManager.COLOR_BG + "; -fx-background:" + AppUIManager.COLOR_BG + ";");
        return scrollPane;
    }
}