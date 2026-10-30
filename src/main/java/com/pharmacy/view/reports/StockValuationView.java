package com.pharmacy.view.reports;

import com.pharmacy.service.ReportingService;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.view.AppUIManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Current on-hand inventory valuation by product category. */
public class StockValuationView {

    private static final String COST_VALUE = "Cost Value";
    private static final String RETAIL_VALUE = "Retail Value";
    private static final String POTENTIAL_PROFIT = "Potential Profit";
    private static final String BAR_CHART = "Bar Chart";
    private static final String LINE_CHART = "Line Chart";
    private static final String PIE_CHART = "Pie Chart";

    private final ReportingService reportingService = new ReportingService();
    private final List<CategoryValuation> valuations = new ArrayList<>();
    private ComboBox<String> chartType;
    private ComboBox<String> metric;
    private StackPane chartPane;
    private TableView<CategoryValuation> table;
    private Label costValue;
    private Label retailValue;
    private Label potentialProfit;
    private Label stockSummary;
    private Label status;

    public Node getView() {
        VBox page = new VBox(18);
        page.setPadding(new Insets(24));
        page.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + ";");
        page.getChildren().addAll(buildHeader(), buildSummary(), buildChartCard(), buildTableCard());

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG
                + "; -fx-background: " + AppUIManager.COLOR_BG + ";");
        refresh();
        return scroll;
    }

    private HBox buildHeader() {
        Label title = AppUIManager.pageTitle("Stock Valuation");
        Label subtitle = new Label("On-hand, unexpired inventory valued at batch cost and current retail prices.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        VBox heading = new VBox(4, title, subtitle);
        status = new Label("Loading valuation…");
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        Button refresh = AppUIManager.primaryButton("Refresh");
        refresh.setOnAction(event -> refresh());
        HBox header = new HBox(14, heading, spacer(), status, refresh);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private HBox buildSummary() {
        costValue = metricValue(AppUIManager.COLOR_PRIMARY);
        retailValue = metricValue(AppUIManager.COLOR_ACCENT);
        potentialProfit = metricValue(AppUIManager.COLOR_SUCCESS);
        stockSummary = metricValue(AppUIManager.COLOR_TEXT_DARK);
        HBox cards = new HBox(14,
                metricCard("INVENTORY COST", costValue, "Remaining batch cost"),
                metricCard("RETAIL VALUE", retailValue, "Potential sales at current prices"),
                metricCard("POTENTIAL GAIN", potentialProfit, "Retail value less inventory cost"),
                metricCard("STOCK ON HAND", stockSummary, "Products · units")
        );
        cards.setAlignment(Pos.CENTER_LEFT);
        return cards;
    }

    private VBox buildChartCard() {
        chartType = new ComboBox<>(FXCollections.observableArrayList(BAR_CHART, LINE_CHART, PIE_CHART));
        chartType.setValue(BAR_CHART);
        chartType.setPrefWidth(140);
        chartType.valueProperty().addListener((observable, oldValue, newValue) -> renderChart());

        metric = new ComboBox<>(FXCollections.observableArrayList(COST_VALUE, RETAIL_VALUE, POTENTIAL_PROFIT));
        metric.setValue(COST_VALUE);
        metric.setPrefWidth(155);
        metric.valueProperty().addListener((observable, oldValue, newValue) -> renderChart());

        Label title = new Label("Valuation by category");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        HBox header = new HBox(10, title, spacer(),
                label("Chart", AppUIManager.COLOR_TEXT_MEDIUM), chartType,
                label("Measure", AppUIManager.COLOR_TEXT_MEDIUM), metric);
        header.setAlignment(Pos.CENTER_LEFT);

        chartPane = new StackPane();
        chartPane.setMinHeight(340);
        chartPane.setPrefHeight(360);
        VBox card = new VBox(14, header, chartPane);
        card.setPadding(new Insets(18));
        card.setStyle(cardStyle());
        VBox.setVgrow(chartPane, Priority.ALWAYS);
        return card;
    }

    private VBox buildTableCard() {
        Label heading = new Label("Category Breakdown");
        heading.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        table = new TableView<>();
        AppUIManager.styleTable(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No eligible stock to value."));
        table.setPrefHeight(360);
        table.getColumns().setAll(
                column("Category", row -> row.category()),
                column("Products", row -> String.valueOf(row.productCount())),
                column("Units", row -> String.format("%,d", row.units())),
                column("Cost Value", row -> money(row.costValue())),
                column("Retail Value", row -> money(row.retailValue())),
                column("Potential Gain", row -> money(row.potentialProfit())),
                column("Return on Cost", row -> percent(row.returnOnCost()))
        );

        VBox card = new VBox(12, heading, table);
        card.setPadding(new Insets(18));
        card.setStyle(cardStyle());
        return card;
    }

    private void refresh() {
        if (table == null) {
            return;
        }
        try {
            List<Map<String, Object>> rows = reportingService.getInventoryValueByCategory();
            valuations.clear();
            for (Map<String, Object> row : rows) {
                valuations.add(new CategoryValuation(
                        row.get("category") == null ? "Uncategorized" : row.get("category").toString(),
                        number(row, "product_count").intValue(),
                        number(row, "total_units").longValue(),
                        number(row, "cost_value").doubleValue(),
                        number(row, "retail_value").doubleValue()
                ));
            }
            valuations.sort(Comparator.comparingDouble(CategoryValuation::costValue).reversed());
            table.setItems(FXCollections.observableArrayList(valuations));

            int products = valuations.stream().mapToInt(CategoryValuation::productCount).sum();
            long units = valuations.stream().mapToLong(CategoryValuation::units).sum();
            double cost = valuations.stream().mapToDouble(CategoryValuation::costValue).sum();
            double retail = valuations.stream().mapToDouble(CategoryValuation::retailValue).sum();
            costValue.setText(money(cost));
            retailValue.setText(money(retail));
            potentialProfit.setText(money(retail - cost));
            stockSummary.setText(String.format("%,d · %,d", products, units));
            status.setText(valuations.size() + " categories · refreshed just now");
            renderChart();
        } catch (Exception e) {
            valuations.clear();
            table.getItems().clear();
            costValue.setText("—");
            retailValue.setText("—");
            potentialProfit.setText("—");
            stockSummary.setText("—");
            status.setText("Valuation could not be loaded.");
            renderChart();
            AlertUtil.showError("Stock Valuation", "Could not load stock valuation: " + e.getMessage());
        }
    }

    private void renderChart() {
        if (chartPane == null || metric == null || chartType == null) {
            return;
        }
        chartPane.getChildren().clear();
        List<CategoryValuation> chartRows = valuations.stream()
                .filter(row -> valueFor(row) != 0)
                .toList();
        if (chartRows.isEmpty()) {
            chartPane.getChildren().add(emptyMessage());
            return;
        }
        if (PIE_CHART.equals(chartType.getValue())) {
            List<PieChart.Data> slices = chartRows.stream()
                    .map(row -> {
                        double value = valueFor(row);
                        String category = row.category() + (value < 0 ? " (loss)" : "");
                        return new PieChart.Data(category, Math.abs(value));
                    })
                    .toList();
            if (slices.isEmpty()) {
                chartPane.getChildren().add(emptyMessage());
                return;
            }
            PieChart pie = new PieChart(FXCollections.observableArrayList(slices));
            pie.setTitle(metric.getValue() + " magnitude by category");
            pie.setLabelsVisible(true);
            pie.setLegendVisible(true);
            pie.setAnimated(false);
            chartPane.getChildren().add(pie);
            return;
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Category");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(metric.getValue() + " (Rs)");
        XYChart<String, Number> chart = LINE_CHART.equals(chartType.getValue())
                ? new LineChart<>(xAxis, yAxis)
                : new BarChart<>(xAxis, yAxis);
        chart.setTitle(metric.getValue() + " by category");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (CategoryValuation row : chartRows) {
            series.getData().add(new XYChart.Data<>(row.category(), valueFor(row)));
        }
        chart.getData().add(series);
        chartPane.getChildren().add(chart);
    }

    private double valueFor(CategoryValuation row) {
        if (RETAIL_VALUE.equals(metric.getValue())) {
            return row.retailValue();
        }
        if (POTENTIAL_PROFIT.equals(metric.getValue())) {
            return row.potentialProfit();
        }
        return row.costValue();
    }

    private TableColumn<CategoryValuation, String> column(
            String title, java.util.function.Function<CategoryValuation, String> getter) {
        TableColumn<CategoryValuation, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(getter.apply(cell.getValue())));
        return column;
    }

    private VBox metricCard(String heading, Label value, String note) {
        Label title = label(heading, AppUIManager.COLOR_TEXT_MEDIUM);
        title.setStyle(title.getStyle() + "-fx-font-size: 10px; -fx-font-weight: bold;");
        VBox card = new VBox(8, title, value, label(note, AppUIManager.COLOR_TEXT_MEDIUM));
        card.setPadding(new Insets(17));
        card.setMinWidth(190);
        card.setStyle(cardStyle());
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Label metricValue(String color) {
        Label value = new Label("—");
        value.setStyle("-fx-font-size: 21px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        return value;
    }

    private Label emptyMessage() {
        Label message = new Label("No eligible stock is available for valuation.");
        message.setStyle("-fx-font-size: 14px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        return message;
    }

    private static Number number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number : 0;
    }

    private static String money(double amount) {
        return String.format("Rs %,.2f", amount);
    }

    private static String percent(double amount) {
        return String.format("%.2f%%", amount);
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

    private record CategoryValuation(String category, int productCount, long units,
                                     double costValue, double retailValue) {
        private double potentialProfit() {
            return retailValue - costValue;
        }

        private double returnOnCost() {
            return costValue == 0 ? 0 : potentialProfit() / costValue * 100;
        }
    }
}
