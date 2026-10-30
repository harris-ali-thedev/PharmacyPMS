package com.pharmacy.view.inventory;

import com.pharmacy.dao.ProductDAO;
import com.pharmacy.model.Medicine;
import com.pharmacy.model.Product;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.SessionManager;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Shared catalog browser; medicine editing and receiving retain their clinical workflows. */
public class RetailInventoryView {
    private final ProductDAO dao = new ProductDAO();
    private final TableView<Product> table = new TableView<>();
    private final TextField search = new TextField();
    private final TextField brand = new TextField();
    private final ComboBox<ProductDAO.CategoryOption> category = new ComboBox<>();
    private final ComboBox<String> stock = new ComboBox<>();
    private final ComboBox<String> expiry = new ComboBox<>();
    private final Label status = new Label();

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F3F4F6;");
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        Label title = new Label("Store Products");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #111827;");
        search.setPromptText("Search name, barcode, brand, flavor...");
        brand.setPromptText("Brand");
        category.setPrefWidth(170);
        category.setConverter(new StringConverter<>() {
            @Override public String toString(ProductDAO.CategoryOption option) {
                return option == null ? "" : option.name();
            }
            @Override public ProductDAO.CategoryOption fromString(String value) { return null; }
        });
        stock.getItems().addAll("Any stock", "In stock", "Low stock", "Out of stock");
        stock.setValue("Any stock");
        expiry.getItems().addAll("Any expiry", "Expiring in 90 days", "Expired");
        expiry.setValue("Any expiry");

        Button filterButton = new Button("Filter");
        Button addButton = new Button("+ Add Product");
        Button receiveButton = new Button("Receive Stock");
        Button refreshButton = new Button("Refresh");
        addButton.setStyle("-fx-background-color: #059669; -fx-text-fill: white; -fx-font-weight: bold;");
        HBox filters = new HBox(8, search, brand, category, stock, expiry, filterButton,
                new Region(), addButton, receiveButton, refreshButton);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox.setHgrow(filters.getChildren().get(6), Priority.ALWAYS);

        configureTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        content.getChildren().addAll(title, filters, table, status);
        root.setCenter(content);

        try {
            List<ProductDAO.CategoryOption> options = new ArrayList<>();
            options.add(new ProductDAO.CategoryOption("ALL", "All Categories", false, false));
            options.addAll(dao.getCategories());
//            options.removeIf(c-> c.toString());
            category.getItems().setAll(options);
            category.setValue(options.get(0));
            load();
        } catch (SQLException e) {
            showError("Could not load product categories: " + e.getMessage());
        }

        filterButton.setOnAction(event -> load());
        search.setOnAction(event -> load());
        brand.setOnAction(event -> load());
        category.setOnAction(event -> load());
        stock.setOnAction(event -> load());
        expiry.setOnAction(event -> load());
        refreshButton.setOnAction(event -> load());
        addButton.setOnAction(event -> addProduct());
        receiveButton.setOnAction(event -> receiveSelected());
        return root;
    }

    private void configureTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No products match these filters."));
        TableColumn<Product, String> name = textColumn("Product", Product::getName, 180);
        TableColumn<Product, String> categoryCol = textColumn("Category", Product::getCategoryName, 100);
        TableColumn<Product, String> brandCol = textColumn("Brand", Product::getBrand, 100);
        TableColumn<Product, String> details = textColumn("Size / Flavor", p ->
                String.join(" · ", List.of(p.getSizeOrWeight() == null ? "" : p.getSizeOrWeight(),
                        p.getFlavor() == null ? "" : p.getFlavor()).stream().filter(s -> !s.isBlank()).toList()), 120);
        TableColumn<Product, String> barcode = textColumn("Barcode", Product::getBarcode, 100);
        TableColumn<Product, Number> quantity = new TableColumn<>("Stock");
        quantity.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getStockQty()));
        TableColumn<Product, Number> price = new TableColumn<>("Price");
        price.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().getUnitPrice()));
        TableColumn<Product, Number> tax = new TableColumn<>("Tax %");
        tax.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().getTaxPercent()));
        TableColumn<Product, String> expiryFlag = textColumn("Expiry", p -> p.isTracksExpiry() ? "Tracked" : "Not tracked", 90);
        TableColumn<Product, Void> actions = new TableColumn<>("Actions");
        actions.setPrefWidth(120);
        actions.setCellFactory(column -> new TableCell<>() {
            private final Button edit = new Button("Edit");
            {
                edit.setOnAction(event -> editProduct(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty ? null : edit);
            }
        });
        table.getColumns().setAll(name, categoryCol, brandCol, details, barcode, quantity, price, tax, expiryFlag, actions);
    }

    private TableColumn<Product, String> textColumn(String title,
            java.util.function.Function<Product, String> getter, double width) {
        TableColumn<Product, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(
                getter.apply(cell.getValue()) == null ? "" : getter.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private void load() {
        try {
            ProductDAO.CategoryOption selected = category.getValue();
            String stockFilter = switch (stock.getValue()) {
                case "In stock" -> "IN_STOCK";
                case "Low stock" -> "LOW";
                case "Out of stock" -> "OUT";
                default -> "ALL";
            };
            String expiryFilter = switch (expiry.getValue()) {
                case "Expiring in 90 days" -> "EXPIRING";
                case "Expired" -> "EXPIRED";
                default -> "ALL";
            };
            List<Product> products = dao.getFiltered(search.getText(),
                    selected == null ? "ALL" : selected.code(), brand.getText(), stockFilter, expiryFilter);
            table.getItems().setAll(products);
            status.setText(products.size() + " products");
        } catch (SQLException | NullPointerException e) {
            showError("Could not load inventory: " + e.getMessage());
        }
    }

    private void addProduct() {
        if (!requireInventoryPermission()) return;
        Product product = productDialog(null);
        if (product == null) return;
        try {
            product.setStockQty(0);
            dao.insert(product);
            load();
            status.setText("Product added. Use Receive Stock to record its opening quantity.");
        } catch (SQLException e) {
            showError("Could not add product: " + e.getMessage());
        }
    }

    private void editProduct(Product existing) {
        if (!requireInventoryPermission()) return;
        if (existing.isMedicine()) {
            showError("Use Medicine Inventory to edit clinical fields for medicines.");
            return;
        }
        Product changed = productDialog(existing);
        if (changed == null) return;
        try {
            if (existing.getStockQty() > 0 && changed.isTracksExpiry() != existing.isTracksExpiry()) {
                showError("Expiry tracking cannot be changed while the product has stock.");
                return;
            }
            dao.update(changed);
            load();
        } catch (SQLException e) {
            showError("Could not update product: " + e.getMessage());
        }
    }

    private Product productDialog(Product existing) {
        try {
            List<ProductDAO.CategoryOption> categories = dao.getCategories().stream()
                    .filter(item -> !"MEDICINE".equals(item.code())).toList();
            if (categories.isEmpty()) {
                showError("No retail product categories are configured.");
                return null;
            }
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(existing == null ? "Add Store Product" : "Edit Store Product");
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            TextField name = new TextField(existing == null ? "" : existing.getName());
            TextField productBrand = new TextField(existing == null ? "" : empty(existing.getBrand()));
            TextField size = new TextField(existing == null ? "" : empty(existing.getSizeOrWeight()));
            TextField flavor = new TextField(existing == null ? "" : empty(existing.getFlavor()));
            TextField barcode = new TextField(existing == null ? "" : empty(existing.getBarcode()));
            TextField price = new TextField(existing == null ? "" : String.valueOf(existing.getUnitPrice()));
            TextField cost = new TextField(existing == null ? "" : String.valueOf(existing.getUnitCost()));
            TextField reorder = new TextField(existing == null ? "20" : String.valueOf(existing.getReorderLevel()));
            TextField tax = new TextField(existing == null ? "0" : String.valueOf(existing.getTaxPercent()));
            TextField discount = new TextField(existing == null ? "0" : String.valueOf(existing.getDiscountPercent()));
            ComboBox<ProductDAO.CategoryOption> cat = new ComboBox<>();
            cat.getItems().setAll(categories);
            cat.setConverter(category.getConverter());
            cat.setValue(existing == null ? categories.get(0) : categories.stream()
                    .filter(option -> option.code().equals(existing.getCategoryCode())).findFirst().orElse(categories.get(0)));
            CheckBox tracksExpiry = new CheckBox("Track expiry dates (perishable)");
            tracksExpiry.setSelected(existing != null && existing.isTracksExpiry());
            tracksExpiry.setDisable(existing != null && existing.getStockQty() > 0);
            cat.valueProperty().addListener((observable, previous, selectedCategory) -> {
                if (selectedCategory != null) {
                    tracksExpiry.setSelected((existing != null && existing.isTracksExpiry())
                            || selectedCategory.requiresExpiry());
                }
            });
            if (cat.getValue() != null && cat.getValue().requiresExpiry()) tracksExpiry.setSelected(true);
            GridPane form = new GridPane();
            form.setHgap(10);
            form.setVgap(8);
            form.setPadding(new Insets(12));
            addRow(form, 0, "Name", name);
            addRow(form, 1, "Category", cat);
            addRow(form, 2, "Brand", productBrand);
            addRow(form, 3, "Size / weight", size);
            addRow(form, 4, "Flavor", flavor);
            addRow(form, 5, "Barcode", barcode);
            addRow(form, 6, "Unit price", price);
            addRow(form, 7, "Unit cost", cost);
            addRow(form, 8, "Reorder level", reorder);
            addRow(form, 9, "Tax percent", tax);
            addRow(form, 10, "Discount percent", discount);
            form.add(tracksExpiry, 1, 11);
            dialog.getDialogPane().setContent(form);
            if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return null;
            if (name.getText().isBlank() || cat.getValue() == null) {
                showError("Product name and category are required.");
                return null;
            }
            Product product = existing == null ? new Product() : existing;
            product.setName(name.getText().trim());
            product.setCategoryCode(cat.getValue().code());
            product.setBrand(nullable(productBrand.getText()));
            product.setSizeOrWeight(nullable(size.getText()));
            product.setFlavor(nullable(flavor.getText()));
            product.setBarcode(nullable(barcode.getText()));
            product.setUnitPrice(parse(price.getText(), "unit price"));
            product.setUnitCost(parse(cost.getText(), "unit cost"));
            product.setReorderLevel(Integer.parseInt(reorder.getText().trim()));
            product.setTaxPercent(parse(tax.getText(), "tax"));
            product.setDiscountPercent(parse(discount.getText(), "discount"));
            product.setTracksExpiry(tracksExpiry.isSelected());
            product.setRequiresPrescription(false);
            product.setActive(true);
            return product;
        } catch (IllegalArgumentException e) {
            showError("Invalid product details: " + e.getMessage());
            return null;
        } catch (SQLException e) {
            showError("Could not load product categories: " + e.getMessage());
            return null;
        }
    }

    private void receiveSelected() {
        if (!requireInventoryPermission()) return;
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a product first.");
            return;
        }
        if (selected.isMedicine()) {
            showError("Use Add Stock to receive medicine batches.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Receive Stock: " + selected.getName());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField quantity = new TextField();
        TextField cost = new TextField(String.valueOf(selected.getUnitCost()));
        TextField batchNumber = new TextField();
        DatePicker expiryDate = new DatePicker();
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.setPadding(new Insets(12));
        addRow(form, 0, "Quantity", quantity);
        addRow(form, 1, "Unit cost", cost);
        if (selected.isTracksExpiry()) {
            addRow(form, 2, "Batch number", batchNumber);
            addRow(form, 3, "Expiry date", expiryDate);
        }
        dialog.getDialogPane().setContent(form);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            dao.receiveStock(selected.getProductId(), Integer.parseInt(quantity.getText().trim()),
                    parse(cost.getText(), "unit cost"), batchNumber.getText().trim(), expiryDate.getValue());
            load();
        } catch (SQLException | IllegalArgumentException e) {
            showError("Could not receive stock: " + e.getMessage());
        }
    }

    private boolean requireInventoryPermission() {
        if (SessionManager.getInstance().getCurrentUser() != null
                && SessionManager.getInstance().getCurrentUser().canManageInventory()) return true;
        showError("You do not have permission to manage inventory.");
        return false;
    }

    private static void addRow(GridPane form, int row, String label, Control control) {
        form.add(new Label(label), 0, row);
        control.setPrefWidth(260);
        form.add(control, 1, row);
    }

    private static String empty(String value) { return value == null ? "" : value; }
    private static String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static double parse(String text, String label) {
        double value = Double.parseDouble(text.trim());
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(label + " must be zero or greater.");
        return value;
    }

    private void showError(String message) {
        status.setText(message);
        status.setStyle("-fx-text-fill: #DC2626;");
        AlertUtil.showError("Inventory", message);
    }
}
