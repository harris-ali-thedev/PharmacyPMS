package com.pharmacy.service;

import com.pharmacy.model.Sale;
import com.pharmacy.model.SaleItem;
import com.pharmacy.util.AlertUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class InvoicePreview {

    private final Sale sale;
    private final List<SaleItem> items;
    private final InvoiceService invoiceService;
    private byte[] pdfData;

    public InvoicePreview(Sale sale, List<SaleItem> items) {
        this.sale = sale;
        this.items = items;
        this.invoiceService = new InvoiceService();
    }

    public void showAndWait() {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("Invoice Preview - Sale #" + sale.getSaleId());

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: white;");

        // Scrollable preview area
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: white;");

        // Create preview content
        VBox previewContent = createPreviewContent();
        scrollPane.setContent(previewContent);

        root.setCenter(scrollPane);

        // Button panel
        VBox buttonPanel = new VBox(10);
        buttonPanel.setPadding(new Insets(15));
        buttonPanel.setStyle("-fx-background-color: #F3F4F6; -fx-border-color: #E5E7EB; -fx-border-width: 1 0 0 0;");

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER);

        Button saveBtn = new Button("💾 Save Invoice");
        saveBtn.setStyle("-fx-background-color: #185FA5; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-cursor: hand; -fx-padding: 8 20 8 20; -fx-background-radius: 6;");
        saveBtn.setOnAction(e -> saveInvoice(dialogStage));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #6B7280; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-cursor: hand; -fx-padding: 8 20 8 20; -fx-background-radius: 6;");
        cancelBtn.setOnAction(e -> dialogStage.close());

        buttons.getChildren().addAll(saveBtn, cancelBtn);

        Label infoLabel = new Label("Review the invoice above. Click 'Save Invoice' to save as PDF.");
        infoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");
        infoLabel.setAlignment(Pos.CENTER);

        buttonPanel.getChildren().addAll(infoLabel, buttons);
        root.setBottom(buttonPanel);

        Scene scene = new Scene(root, 700, 600);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    private VBox createPreviewContent() {
        VBox content = new VBox(8);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: white;");

        // Header
        Text pharmacyName = new Text("MEDICARE PHARMACY");
        pharmacyName.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        pharmacyName.setStyle("-fx-fill: #1f2bde;");

        Text address = new Text("Blue Area, Islamabad, Pakistan\nTel: 051-1234567");
        address.setFont(Font.font("Arial", 10));
        address.setStyle("-fx-fill: #6B7280;");

        Separator separator1 = new Separator();

        content.getChildren().addAll(pharmacyName, address, separator1);

        // Center align header
        pharmacyName.setTextAlignment(TextAlignment.CENTER);
        address.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        VBox.setMargin(pharmacyName, new Insets(0, 0, 5, 0));
        VBox.setMargin(address, new Insets(0, 0, 5, 0));

        // Invoice details
        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(20);
        detailsGrid.setVgap(8);
        detailsGrid.setPadding(new Insets(10, 0, 10, 0));

        addDetailRow(detailsGrid, "Invoice #:", String.valueOf(sale.getSaleId()), 0);
        addDetailRow(detailsGrid, "Date:", sale.getSaleDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")), 1);
        addDetailRow(detailsGrid, "Cashier:", sale.getCashierName() != null ? sale.getCashierName() : "—", 2);
        addDetailRow(detailsGrid, "Payment:", sale.getPaymentMethod(), 3);

        content.getChildren().add(detailsGrid);

        Separator separator2 = new Separator();
        content.getChildren().add(separator2);

        // Items table header
        GridPane headerGrid = new GridPane();
        headerGrid.setHgap(10);
        headerGrid.setPadding(new Insets(10, 0, 5, 0));
        headerGrid.setStyle("-fx-background-color: #E5E7EB;");

        String[] headers = {"Medicine", "Qty", "Unit Price", "Subtotal"};
        int[] widths = {250, 50, 100, 100};

        for (int i = 0; i < headers.length; i++) {
            Label header = new Label(headers[i]);
            header.setFont(Font.font("Arial", FontWeight.BOLD, 12));
            header.setPadding(new Insets(5));
            headerGrid.add(header, i, 0);
            ColumnConstraints col = new ColumnConstraints();
            col.setPrefWidth(widths[i]);
            headerGrid.getColumnConstraints().add(col);
        }

        content.getChildren().add(headerGrid);

        // Items rows
        for (SaleItem item : items) {
            GridPane rowGrid = new GridPane();
            rowGrid.setHgap(10);
            rowGrid.setPadding(new Insets(5, 0, 5, 0));

            Label nameLabel = new Label(item.getMedicineName());
            nameLabel.setWrapText(true);
            Label qtyLabel = new Label(String.valueOf(item.getQuantity()));
            Label priceLabel = new Label(String.format("Rs %.2f", item.getUnitPrice()));
            Label subtotalLabel = new Label(String.format("Rs %.2f", item.getSubtotal()));

            rowGrid.add(nameLabel, 0, 0);
            rowGrid.add(qtyLabel, 1, 0);
            rowGrid.add(priceLabel, 2, 0);
            rowGrid.add(subtotalLabel, 3, 0);

            content.getChildren().add(rowGrid);
        }

        Separator separator3 = new Separator();
        content.getChildren().add(separator3);

        // Totals section
        VBox totalsBox = new VBox(5);
        totalsBox.setAlignment(Pos.TOP_RIGHT);
        totalsBox.setPadding(new Insets(10, 0, 10, 0));

        addTotalRow(totalsBox, "Subtotal:", String.format("Rs %.2f", sale.getSubtotal()), false);

        if (sale.getDiscountAmount() > 0) {
            addTotalRow(totalsBox, "Discount:", String.format("- Rs %.2f", sale.getDiscountAmount()), false);
        }

        if (sale.getTaxAmount() > 0) {
            addTotalRow(totalsBox, "Tax:", String.format("Rs %.2f", sale.getTaxAmount()), false);
        }

        addTotalRow(totalsBox, "Grand Total:", String.format("Rs %.2f", sale.getGrandTotal()), true);
        addTotalRow(totalsBox, "Amount Paid:", String.format("Rs %.2f", sale.getAmountPaid()), false);
        addTotalRow(totalsBox, "Change:", String.format("Rs %.2f", sale.getChangeReturned()), false);

        content.getChildren().add(totalsBox);

        Separator separator4 = new Separator();
        content.getChildren().add(separator4);

        // Footer
        Text footer = new Text("Thank you for your business!\nPlease retain this receipt for your records.");
        footer.setFont(Font.font("Arial", 10));
        footer.setStyle("-fx-fill: #9CA3AF;");
        footer.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        content.getChildren().add(footer);

        return content;
    }

    private void addDetailRow(GridPane grid, String label, String value, int row) {
        Label labelLbl = new Label(label);
        labelLbl.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        labelLbl.setStyle("-fx-text-fill: #374151;");

        Label valueLbl = new Label(value);
        valueLbl.setFont(Font.font("Arial", 12));
        valueLbl.setStyle("-fx-text-fill: #6B7280;");

        grid.add(labelLbl, 0, row);
        grid.add(valueLbl, 1, row);
    }

    private void addTotalRow(VBox box, String label, String value, boolean bold) {
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER_RIGHT);

        Label labelLbl = new Label(label);
        if (bold) {
            labelLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
            labelLbl.setStyle("-fx-text-fill: #185FA5;");
        } else {
            labelLbl.setFont(Font.font("Arial", 12));
            labelLbl.setStyle("-fx-text-fill: #374151;");
        }

        Label valueLbl = new Label(value);
        if (bold) {
            valueLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
            valueLbl.setStyle("-fx-text-fill: #185FA5;");
        } else {
            valueLbl.setFont(Font.font("Arial", 12));
            valueLbl.setStyle("-fx-text-fill: #374151;");
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        row.getChildren().addAll(spacer, labelLbl, valueLbl);
        box.getChildren().add(row);
    }

    private void saveInvoice(Stage dialogStage) {
        try {
            // Show save dialog first
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Invoice");
            fileChooser.setInitialFileName("Invoice_" + sale.getSaleId() + ".pdf");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
            );
            fileChooser.setInitialDirectory(new File("D:/comsats/Programming/Projects/Pharmacy"));

            File destinationFile = fileChooser.showSaveDialog(dialogStage);
            if (destinationFile != null) {
                // Generate PDF directly to the selected location
                String parentDir = destinationFile.getParent();
                String path = invoiceService.generateInvoice(sale, items);

                // Rename the generated file to the user's chosen name
                File generatedFile = new File(path);
                if (generatedFile.exists() && !generatedFile.getAbsolutePath().equals(destinationFile.getAbsolutePath())) {
                    Files.move(generatedFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                AlertUtil.showInfo("Success", "Invoice saved to:\n" + destinationFile.getAbsolutePath());
                dialogStage.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
            AlertUtil.showError("Error", "Failed to save invoice: " + e.getMessage());
        }
    }
}