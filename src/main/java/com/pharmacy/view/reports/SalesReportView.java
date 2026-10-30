package com.pharmacy.view.reports;

import com.pharmacy.dao.SaleDAO;
import com.pharmacy.model.Sale;
import com.pharmacy.service.ReportingService;
import com.pharmacy.service.ReportExportService;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.view.AppUIManager;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SalesReportView {

    private static final String SEVEN_DAY_REPORT = "7 Day Report";
    private static final String MONTHLY_REPORT = "Monthly Report";
    private static final String YEARLY_REPORT = "Yearly Report";
    private static final String LINE_CHART = "Line Chart";
    private static final String BAR_CHART = "Bar Chart";
    private static final String PIE_CHART = "Pie Chart";
    private static final DateTimeFormatter SALE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final SaleDAO saleDAO = new SaleDAO();
    private final ReportingService reportingService = new ReportingService();
    private TableView<Sale> salesTable;
    private TableView<Map<String, Object>> categoryTable;
    private ComboBox<String> periodFilter;
    private ComboBox<YearMonth> monthFilter;
    private ComboBox<String> chartFilter;
    private StackPane chartPane;
    private Label chartTitle;
    private Label rangeLabel;
    private Label revenueValue;
    private Label transactionValue;
    private Label averageValue;
    private Label statusLabel;
    private LocalDate reportFrom;
    private LocalDate reportTo;
    private List<SaleDAO.SalesBucket> trendData = List.of();
    private List<SaleDAO.PaymentBreakdown> paymentData = List.of();

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + ";");

        VBox page = new VBox(18);
        page.setPadding(new Insets(24));
        page.getChildren().addAll(buildHeader(), buildFilters(), buildSummary(), buildChartCard(),
                buildCategoryCard(), buildSalesCard());
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG
                + "; -fx-background: " + AppUIManager.COLOR_BG + ";");
        root.setCenter(scroll);

        periodFilter.setValue(SEVEN_DAY_REPORT);
        updateMonthFilterVisibility();
        loadReport();
        return root;
    }

    private HBox buildHeader() {
        Label title = AppUIManager.pageTitle("Sales Performance");
        Label subtitle = new Label("Track revenue trends, compare periods, and review completed transactions.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        VBox heading = new VBox(4, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button refreshButton = AppUIManager.primaryButton("Refresh Report");
        refreshButton.setOnAction(event -> loadReport());
        HBox header = new HBox(12, heading, spacer, refreshButton);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private HBox buildFilters() {
        periodFilter = new ComboBox<>(FXCollections.observableArrayList(
                SEVEN_DAY_REPORT, MONTHLY_REPORT, YEARLY_REPORT
        ));
        periodFilter.setPrefWidth(180);
        periodFilter.valueProperty().addListener((observable, oldValue, newValue) -> updateMonthFilterVisibility());

        List<YearMonth> months = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        for (int ago = 11; ago >= 0; ago--) {
            months.add(currentMonth.minusMonths(ago));
        }
        monthFilter = new ComboBox<>(FXCollections.observableArrayList(months));
        monthFilter.setPrefWidth(170);
        monthFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(YearMonth month) {
                return month == null ? "" : MONTH_FORMAT.format(month);
            }

            @Override
            public YearMonth fromString(String value) {
                return null;
            }
        });
        monthFilter.setValue(currentMonth);

        Button applyButton = AppUIManager.primaryButton("Apply");
        applyButton.setOnAction(event -> loadReport());
        statusLabel = new Label("Choose a report period.");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");

        HBox filters = new HBox(10,
                label("Report period", AppUIManager.COLOR_TEXT_DARK), periodFilter, monthFilter, applyButton,
                spacer(), statusLabel
        );
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.setPadding(new Insets(14, 16, 14, 16));
        filters.setStyle(cardStyle());
        return filters;
    }

    private VBox buildSummary() {
        revenueValue = metricValue();
        transactionValue = metricValue();
        averageValue = metricValue();
        rangeLabel = new Label("Selected period");
        rangeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        HBox cards = new HBox(14,
                metricCard("TOTAL REVENUE", revenueValue, AppUIManager.COLOR_PRIMARY),
                metricCard("COMPLETED SALES", transactionValue, AppUIManager.COLOR_SUCCESS),
                metricCard("AVERAGE SALE", averageValue, AppUIManager.COLOR_ACCENT)
        );
        cards.setAlignment(Pos.CENTER_LEFT);
        return new VBox(8, rangeLabel, cards);
    }

    private VBox buildChartCard() {
        chartFilter = new ComboBox<>(FXCollections.observableArrayList(LINE_CHART, BAR_CHART, PIE_CHART));
        chartFilter.setValue(LINE_CHART);
        chartFilter.setPrefWidth(145);
        chartFilter.valueProperty().addListener((observable, oldValue, newValue) -> renderChart());

        chartTitle = new Label("Revenue over time");
        chartTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        Region space = spacer();
        HBox chartHeader = new HBox(10, chartTitle, space, label("Chart type", AppUIManager.COLOR_TEXT_MEDIUM), chartFilter);
        chartHeader.setAlignment(Pos.CENTER_LEFT);

        chartPane = new StackPane();
        chartPane.setMinHeight(340);
        chartPane.setPrefHeight(360);

        VBox card = new VBox(14, chartHeader, chartPane);
        card.setPadding(new Insets(18));
        card.setStyle(cardStyle());
        VBox.setVgrow(chartPane, Priority.ALWAYS);
        return card;
    }

    private VBox buildSalesCard() {
        Label title = new Label("Completed Transactions");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        Region space = spacer();
//        Button exportExcel = AppUIManager.outlineButton("Export Excel");
//        Button exportPdf = AppUIManager.outlineButton("Export PDF");
        Button exportExcel = new Button("Export Excel");
        exportExcel.setStyle("-fx-background-color: #0ED118; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
        Button exportPdf = new Button("Export PDF");
        exportPdf.setStyle("-fx-background-color: #F21000; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");

        exportExcel.setOnAction(event -> exportExcel());
        exportPdf.setOnAction(event -> exportPdf());
        HBox header = new HBox(10, title, space, exportExcel, exportPdf);
        header.setAlignment(Pos.CENTER_LEFT);

        salesTable = buildSalesTable();
        salesTable.setPrefHeight(360);
        VBox card = new VBox(12, header, salesTable);
        card.setPadding(new Insets(18));
        card.setStyle(cardStyle());
        return card;
    }

    private VBox buildCategoryCard() {
        Label title = new Label("Sales and Profit by Product Category");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        categoryTable = new TableView<>();
        AppUIManager.styleTable(categoryTable);
        categoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        categoryTable.setPlaceholder(new Label("No category sales in this period."));
        categoryTable.getColumns().setAll(
                mapTextColumn("Category", "category"),
                mapNumberColumn("Units Sold", "units_sold", "%.0f"),
                mapNumberColumn("Net Sales", "net_sales", "Rs %.2f"),
                mapNumberColumn("COGS", "cogs", "Rs %.2f"),
                mapNumberColumn("Gross Profit", "gross_profit", "Rs %.2f"),
                mapNumberColumn("Margin", "margin_percent", "%.2f%%"),
                mapNumberColumn("Tax", "tax_collected", "Rs %.2f"),
                mapNumberColumn("Sales incl. Tax", "sales_including_tax", "Rs %.2f")
        );
        categoryTable.setPrefHeight(210);
        VBox card = new VBox(12, title, categoryTable);
        card.setPadding(new Insets(18));
        card.setStyle(cardStyle());
        return card;
    }

    private TableColumn<Map<String, Object>, String> mapTextColumn(String title, String key) {
        TableColumn<Map<String, Object>, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().get(key))));
        return column;
    }

    private TableColumn<Map<String, Object>, Number> mapNumberColumn(String title, String key, String format) {
        TableColumn<Map<String, Object>, Number> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleDoubleProperty(
                ((Number) cell.getValue().get(key)).doubleValue()));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.format(format, value.doubleValue()));
            }
        });
        return column;
    }

    private TableView<Sale> buildSalesTable() {
        TableView<Sale> table = new TableView<>();
        AppUIManager.styleTable(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No completed sales in this period."));

        TableColumn<Sale, Number> id = new TableColumn<>("Sale ID");
        id.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getSaleId()));

        TableColumn<Sale, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getSaleDate() == null ? "—" : SALE_DATE_FORMAT.format(cell.getValue().getSaleDate())));

        TableColumn<Sale, String> cashier = new TableColumn<>("Cashier");
        cashier.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getCashierName() == null ? "—" : cell.getValue().getCashierName()));

        TableColumn<Sale, Number> total = new TableColumn<>("Total");
        total.setCellValueFactory(cell -> new SimpleDoubleProperty(cell.getValue().getGrandTotal()));
        total.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.format("Rs %.2f", value.doubleValue()));
            }
        });

        TableColumn<Sale, String> payment = new TableColumn<>("Payment Method");
        payment.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getPaymentMethod() == null ? "—" : cell.getValue().getPaymentMethod()));

        table.getColumns().setAll(id, date, cashier, total, payment);
        return table;
    }

    private void loadReport() {
        if (periodFilter == null || periodFilter.getValue() == null) {
            return;
        }
        if (MONTHLY_REPORT.equals(periodFilter.getValue()) && monthFilter.getValue() == null) {
            AlertUtil.showError("Sales Report", "Select a month for the monthly report.");
            return;
        }

        LocalDate today = LocalDate.now();
        if (SEVEN_DAY_REPORT.equals(periodFilter.getValue())) {
            reportTo = today;
            reportFrom = today.minusDays(6);
        } else if (MONTHLY_REPORT.equals(periodFilter.getValue())) {
            YearMonth selectedMonth = monthFilter.getValue();
            reportFrom = selectedMonth.atDay(1);
            reportTo = selectedMonth.atEndOfMonth();
        } else {
            reportFrom = LocalDate.of(today.getYear(), 1, 1);
            reportTo = LocalDate.of(today.getYear(), 12, 31);
        }

        boolean groupByMonth = YEARLY_REPORT.equals(periodFilter.getValue());
        try {
            trendData = saleDAO.getSalesTrend(reportFrom, reportTo, groupByMonth);
            paymentData = saleDAO.getPaymentBreakdown(reportFrom, reportTo);
            categoryTable.setItems(FXCollections.observableArrayList(
                    reportingService.getSalesByCategory(reportFrom, reportTo)));
            List<Sale> sales = saleDAO.getSalesBetween(reportFrom, reportTo);
            salesTable.setItems(FXCollections.observableArrayList(sales));

            double revenue = trendData.stream().mapToDouble(SaleDAO.SalesBucket::revenue).sum();
            int transactions = trendData.stream().mapToInt(SaleDAO.SalesBucket::transactions).sum();
            revenueValue.setText(String.format("Rs %.2f", revenue));
            transactionValue.setText(String.valueOf(transactions));
            averageValue.setText(String.format("Rs %.2f", transactions == 0 ? 0 : revenue / transactions));
            rangeLabel.setText(formatRange(reportFrom, reportTo));
            statusLabel.setText(transactions + " completed sale(s) · " + formatRange(reportFrom, reportTo));
            renderChart();
        } catch (Exception e) {
            trendData = List.of();
            paymentData = List.of();
            categoryTable.getItems().clear();
            salesTable.getItems().clear();
            revenueValue.setText("—");
            transactionValue.setText("—");
            averageValue.setText("—");
            statusLabel.setText("Report could not be loaded.");
            renderChart();
            AlertUtil.showError("Sales Report", "Could not load sales report: " + e.getMessage());
        }
    }

    private void renderChart() {
        if (chartPane == null || chartFilter == null) {
            return;
        }
        chartPane.getChildren().clear();
        if (PIE_CHART.equals(chartFilter.getValue())) {
            chartTitle.setText("Revenue by payment method");
            if (paymentData.isEmpty()) {
                chartPane.getChildren().add(emptyChartMessage());
                return;
            }
            PieChart chart = new PieChart(FXCollections.observableArrayList(
                    paymentData.stream()
                            .map(row -> new PieChart.Data(row.paymentMethod(), row.revenue()))
                            .toList()
            ));
            chart.setLabelsVisible(true);
            chart.setLegendVisible(true);
            chart.setAnimated(false);
            chartPane.getChildren().add(chart);
            return;
        }

        chartTitle.setText("Revenue over time");
        if (trendData.isEmpty()) {
            chartPane.getChildren().add(emptyChartMessage());
            return;
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel(YEARLY_REPORT.equals(periodFilter.getValue()) ? "Month" : "Date");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Revenue (Rs)");
        yAxis.setForceZeroInRange(true);
        yAxis.setTickLabelFormatter(new NumberAxis.DefaultFormatter(yAxis) {
            @Override
            public String toString(Number value) {
                return String.format("%.0f", value.doubleValue());
            }
        });

        XYChart<String, Number> chart;
        if (BAR_CHART.equals(chartFilter.getValue())) {
            chart = new BarChart<>(xAxis, yAxis);
        } else {
            chart = new LineChart<>(xAxis, yAxis);
        }
        chart.setLegendVisible(false);
        chart.setAnimated(false);
//        chart.setCreateSymbols(true);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Revenue");
        Map<String, SaleDAO.SalesBucket> bucketsByPeriod = new HashMap<>();
        for (SaleDAO.SalesBucket bucket : trendData) {
            bucketsByPeriod.put(bucket.period(), bucket);
        }
        if (YEARLY_REPORT.equals(periodFilter.getValue())) {
            YearMonth month = YearMonth.from(reportFrom);
            YearMonth lastMonth = YearMonth.from(reportTo);
            for (; !month.isAfter(lastMonth); month = month.plusMonths(1)) {
                SaleDAO.SalesBucket bucket = bucketsByPeriod.get(month.toString());
                series.getData().add(new XYChart.Data<>(
                        month.format(DateTimeFormatter.ofPattern("MMM")),
                        bucket == null ? 0 : bucket.revenue()
                ));
            }
        } else {
            for (LocalDate day = reportFrom; !day.isAfter(reportTo); day = day.plusDays(1)) {
                SaleDAO.SalesBucket bucket = bucketsByPeriod.get(day.toString());
                series.getData().add(new XYChart.Data<>(
                        day.format(DateTimeFormatter.ofPattern("dd MMM")),
                        bucket == null ? 0 : bucket.revenue()
                ));
            }
        }
        chart.getData().add(series);
        chartPane.getChildren().add(chart);
    }

    private void exportExcel() {
        export(true);
    }

    private void exportPdf() {
        export(false);
    }

    private void export(boolean excel) {
        if (salesTable == null || salesTable.getScene() == null) {
            return;
        }
        try {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(excel ? "Save Excel Report To..." : "Save PDF Report To...");
            File directory = chooser.showDialog(salesTable.getScene().getWindow());
            if (directory == null) {
                return;
            }
            ReportExportService exportService = new ReportExportService();
            String path = excel
                    ? exportService.exportSalesToExcel(salesTable.getItems(), directory.getAbsolutePath())
                    : exportService.exportSalesToPdf(salesTable.getItems(), "Sales Report", directory.getAbsolutePath());
            AlertUtil.showInfo("Export Complete", "Saved to:\n" + path);
        } catch (Exception e) {
            AlertUtil.showError("Export Failed", e.getMessage());
        }
    }

    private void updateMonthFilterVisibility() {
        if (monthFilter == null || periodFilter == null) {
            return;
        }
        boolean monthly = MONTHLY_REPORT.equals(periodFilter.getValue());
        monthFilter.setVisible(monthly);
        monthFilter.setManaged(monthly);
    }

    private VBox metricCard(String title, Label value, String accent) {
        Label heading = label(title, AppUIManager.COLOR_TEXT_MEDIUM);
        heading.setStyle(heading.getStyle() + "-fx-font-size: 10px; -fx-font-weight: bold;");
        value.setStyle("-fx-font-size: 23px; -fx-font-weight: bold; -fx-text-fill: " + accent + ";");
        VBox card = new VBox(9, heading, value);
        card.setPadding(new Insets(17));
        card.setMinWidth(180);
        card.setStyle(cardStyle());
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Label metricValue() {
        Label value = new Label("—");
        value.setStyle("-fx-font-size: 23px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        return value;
    }

    private Label emptyChartMessage() {
        Label empty = new Label("No completed sales for this period.");
        empty.setStyle("-fx-font-size: 14px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        return empty;
    }

    private String formatRange(LocalDate from, LocalDate to) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy");
        return from.equals(to) ? formatter.format(from) : formatter.format(from) + " – " + formatter.format(to);
    }

    private Label label(String text, String color) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 12px; -fx-text-fill: " + color + ";");
        return label;
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private String cardStyle() {
        return "-fx-background-color: white; -fx-background-radius: 12; "
                + "-fx-border-color: " + AppUIManager.COLOR_BORDER + "; -fx-border-width: 1; -fx-border-radius: 12;";
    }
}
