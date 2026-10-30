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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfitMarginView {

    private static final String SEVEN_DAY_REPORT = "7 Day Report";
    private static final String MONTHLY_REPORT = "Monthly Report";
    private static final String YEARLY_REPORT = "Yearly Report";
    private static final String LINE_CHART = "Line Chart";
    private static final String BAR_CHART = "Bar Chart";
    private static final String PIE_CHART = "Pie Chart";
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final ReportingService reportService = new ReportingService();
    private ComboBox<String> periodFilter;
    private ComboBox<YearMonth> monthFilter;
    private ComboBox<String> chartFilter;
    private TableView<ProfitBucket> table;
    private StackPane chartPane;
    private Label chartTitle;
    private Label rangeLabel;
    private Label netSalesValue;
    private Label costValue;
    private Label profitValue;
    private Label marginValue;
    private Label statusLabel;
    private LocalDate reportFrom;
    private LocalDate reportTo;
    private List<ProfitBucket> reportData = List.of();

    public Node getView() {
        VBox page = new VBox(18);
        page.setPadding(new Insets(24));
        page.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + ";");
        page.getChildren().addAll(buildHeader(), buildFilters(), buildSummary(), buildChartCard(), buildTableCard());

        periodFilter.setValue(SEVEN_DAY_REPORT);
        updateMonthFilterVisibility();
        loadReport();

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG
                + "; -fx-background: " + AppUIManager.COLOR_BG + ";");
        return scroll;
    }

    private HBox buildHeader() {
        Label title = AppUIManager.pageTitle("Profit & Loss Analysis");
        Label subtitle = new Label("Review sales excluding tax, cost of goods, realized profit or loss, and margins.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        VBox heading = new VBox(4, title, subtitle);
        Region space = spacer();
        Button refresh = AppUIManager.primaryButton("Refresh Report");
        refresh.setOnAction(event -> loadReport());
        HBox header = new HBox(12, heading, space, refresh);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private HBox buildFilters() {
        periodFilter = new ComboBox<>(FXCollections.observableArrayList(
                SEVEN_DAY_REPORT, MONTHLY_REPORT, YEARLY_REPORT
        ));
        periodFilter.setPrefWidth(175);
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

        javafx.scene.control.Button apply = AppUIManager.primaryButton("Apply");
        apply.setOnAction(event -> loadReport());
        statusLabel = new Label("Choose a report period.");
        statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");

        HBox filters = new HBox(10,
                label("Report period", AppUIManager.COLOR_TEXT_DARK),
                periodFilter, monthFilter, apply, spacer(), statusLabel);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.setPadding(new Insets(14, 16, 14, 16));
        filters.setStyle(cardStyle());
        return filters;
    }

    private VBox buildSummary() {
        rangeLabel = label("Selected period", AppUIManager.COLOR_TEXT_MEDIUM);
        netSalesValue = metricValue(AppUIManager.COLOR_PRIMARY);
        costValue = metricValue(AppUIManager.COLOR_WARNING);
        profitValue = metricValue(AppUIManager.COLOR_SUCCESS);
        marginValue = metricValue(AppUIManager.COLOR_ACCENT);
        HBox cards = new HBox(14,
                metricCard("NET SALES (EXCL. TAX)", netSalesValue),
                metricCard("COST OF GOODS SOLD", costValue),
                metricCard("GROSS PROFIT / LOSS", profitValue),
                metricCard("PROFIT / LOSS MARGIN", marginValue)
        );
        cards.setAlignment(Pos.CENTER_LEFT);
        return new VBox(8, rangeLabel, cards);
    }

    private VBox buildChartCard() {
        chartFilter = new ComboBox<>(FXCollections.observableArrayList(LINE_CHART, BAR_CHART, PIE_CHART));
        chartFilter.setValue(LINE_CHART);
        chartFilter.setPrefWidth(145);
        chartFilter.valueProperty().addListener((observable, oldValue, newValue) -> renderChart());

        chartTitle = new Label("Profit and loss over time");
        chartTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        HBox chartHeader = new HBox(10, chartTitle, spacer(),
                label("Chart type", AppUIManager.COLOR_TEXT_MEDIUM), chartFilter);
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

    private VBox buildTableCard() {
        Label heading = new Label("Period Breakdown");
        heading.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");

        table = new TableView<>();
        AppUIManager.styleTable(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No completed sales in this period."));
        table.setPrefHeight(380);
        table.getColumns().setAll(
                column("Period", ProfitBucket::label),
                column("Transactions", row -> String.valueOf(row.transactions())),
                column("Net Sales", row -> money(row.netSales())),
                column("COGS", row -> money(row.cogs())),
                column("Profit / Loss", row -> money(row.profit())),
                column("Margin %", row -> percent(row.marginPercent())),
                column("COGS %", row -> percent(row.cogsPercent())),
                column("Tax", row -> money(row.tax())),
                column("Discounts", row -> money(row.discounts()))
        );

        VBox card = new VBox(12, heading, table);
        card.setPadding(new Insets(18));
        card.setStyle(cardStyle());
        return card;
    }

    private void loadReport() {
        if (periodFilter == null || periodFilter.getValue() == null) {
            return;
        }
        if (MONTHLY_REPORT.equals(periodFilter.getValue()) && monthFilter.getValue() == null) {
            AlertUtil.showError("Profit & Loss Report", "Select a month for the monthly report.");
            return;
        }

        LocalDate today = LocalDate.now();
        if (SEVEN_DAY_REPORT.equals(periodFilter.getValue())) {
            reportTo = today;
            reportFrom = today.minusDays(6);
        } else if (MONTHLY_REPORT.equals(periodFilter.getValue())) {
            YearMonth month = monthFilter.getValue();
            reportFrom = month.atDay(1);
            reportTo = month.atEndOfMonth();
        } else {
            reportFrom = LocalDate.of(today.getYear(), 1, 1);
            reportTo = LocalDate.of(today.getYear(), 12, 31);
        }

        try {
            List<Map<String, Object>> dailyRows = reportService.getProfitReport(reportFrom, reportTo);
            reportData = toBuckets(dailyRows, YEARLY_REPORT.equals(periodFilter.getValue()));
            table.setItems(FXCollections.observableArrayList(reportData));
            updateSummary();
            statusLabel.setText(reportData.size() + " period(s) · " + formatRange(reportFrom, reportTo));
            renderChart();
        } catch (Exception e) {
            reportData = List.of();
            table.getItems().clear();
            netSalesValue.setText("—");
            costValue.setText("—");
            profitValue.setText("—");
            marginValue.setText("—");
            statusLabel.setText("Report could not be loaded.");
            renderChart();
            AlertUtil.showError("Profit & Loss Report", "Could not load report: " + e.getMessage());
        }
    }

    private List<ProfitBucket> toBuckets(List<Map<String, Object>> rows, boolean groupByMonth) {
        Map<String, ProfitBucket> buckets = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object dateValue = row.get("sale_date");
            LocalDate date;
            if (dateValue instanceof java.sql.Date sqlDate) {
                date = sqlDate.toLocalDate();
            } else if (dateValue instanceof LocalDate localDate) {
                date = localDate;
            } else {
                throw new IllegalStateException("Profit report returned an invalid sale date.");
            }
            String key = groupByMonth
                    ? YearMonth.from(date).toString()
                    : date.toString();
            ProfitBucket daily = new ProfitBucket(
                    key,
                    groupByMonth
                            ? YearMonth.from(date).format(DateTimeFormatter.ofPattern("MMM yyyy"))
                            : date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    number(row, "transactions").intValue(),
                    number(row, "net_sales").doubleValue(),
                    number(row, "cogs").doubleValue(),
                    number(row, "gross_profit").doubleValue(),
                    number(row, "tax_collected").doubleValue(),
                    number(row, "total_discounts").doubleValue()
            );
            ProfitBucket current = buckets.get(key);
            buckets.put(key, current == null ? daily : current.plus(daily));
        }

        List<ProfitBucket> result = new ArrayList<>();
        if (groupByMonth) {
            YearMonth month = YearMonth.from(reportFrom);
            YearMonth lastMonth = YearMonth.from(reportTo);
            for (; !month.isAfter(lastMonth); month = month.plusMonths(1)) {
                result.add(buckets.getOrDefault(month.toString(), ProfitBucket.empty(
                        month.toString(), month.format(DateTimeFormatter.ofPattern("MMM yyyy")))));
            }
        } else if (SEVEN_DAY_REPORT.equals(periodFilter.getValue())) {
            for (LocalDate day = reportFrom; !day.isAfter(reportTo); day = day.plusDays(1)) {
                result.add(buckets.getOrDefault(day.toString(), ProfitBucket.empty(
                        day.toString(), day.format(DateTimeFormatter.ofPattern("dd MMM yyyy")))));
            }
        } else {
            result.addAll(buckets.values());
            result.sort((left, right) -> left.key().compareTo(right.key()));
        }
        return result;
    }

    private void updateSummary() {
        double netSales = reportData.stream().mapToDouble(ProfitBucket::netSales).sum();
        double cogs = reportData.stream().mapToDouble(ProfitBucket::cogs).sum();
        double profit = netSales - cogs;
        double margin = netSales == 0 ? 0 : profit / netSales * 100;
        netSalesValue.setText(money(netSales));
        costValue.setText(money(cogs));
        profitValue.setText((profit < 0 ? "LOSS " : "PROFIT ") + money(Math.abs(profit)));
        profitValue.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: "
                + (profit < 0 ? AppUIManager.COLOR_DANGER : AppUIManager.COLOR_SUCCESS) + ";");
        marginValue.setText(percent(margin));
        marginValue.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: "
                + (margin < 0 ? AppUIManager.COLOR_DANGER : AppUIManager.COLOR_ACCENT) + ";");
        rangeLabel.setText(formatRange(reportFrom, reportTo));
    }

    private void renderChart() {
        if (chartPane == null || chartFilter == null) {
            return;
        }
        chartPane.getChildren().clear();
        if (PIE_CHART.equals(chartFilter.getValue())) {
            renderAllocationPie();
            return;
        }
        chartTitle.setText("Profit and loss over time");
        if (reportData.isEmpty()) {
            chartPane.getChildren().add(emptyMessage());
            return;
        }

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel(YEARLY_REPORT.equals(periodFilter.getValue()) ? "Month" : "Date");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Amount (Rs)");
        XYChart<String, Number> chart = BAR_CHART.equals(chartFilter.getValue())
                ? new BarChart<>(xAxis, yAxis)
                : new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setAnimated(true);

        XYChart.Series<String, Number> profitSeries = new XYChart.Series<>();
        profitSeries.setName("Profit / Loss");
        XYChart.Series<String, Number> netSalesSeries = new XYChart.Series<>();
        netSalesSeries.setName("Net Sales");
        for (ProfitBucket bucket : reportData) {
            profitSeries.getData().add(new XYChart.Data<>(bucket.label(), bucket.profit()));
            netSalesSeries.getData().add(new XYChart.Data<>(bucket.label(), bucket.netSales()));
        }
        chart.getData().addAll(profitSeries, netSalesSeries);
        chartPane.getChildren().add(chart);
    }

    private void renderAllocationPie() {
        double netSales = reportData.stream().mapToDouble(ProfitBucket::netSales).sum();
        double cogs = reportData.stream().mapToDouble(ProfitBucket::cogs).sum();
        double profit = netSales - cogs;
        if (netSales <= 0 && cogs <= 0) {
            chartTitle.setText("Revenue and cost allocation");
            chartPane.getChildren().add(emptyMessage());
            return;
        }

        List<PieChart.Data> slices = new ArrayList<>();
        slices.add(new PieChart.Data("Cost of goods sold · " + money(cogs), cogs));
        if (profit >= 0) {
            slices.add(new PieChart.Data("Gross profit · " + money(profit), profit));
            chartTitle.setText("Net sales allocation");
        } else {
            slices.add(new PieChart.Data("Gross loss · " + money(Math.abs(profit)), Math.abs(profit)));
            chartTitle.setText("Cost and loss comparison");
        }
        slices.removeIf(slice -> slice.getPieValue() <= 0);
        PieChart chart = new PieChart(FXCollections.observableArrayList(slices));
        chart.setLabelsVisible(true);
        chart.setLegendVisible(true);
        chart.setAnimated(false);
        chartPane.getChildren().add(chart);
    }

    private TableColumn<ProfitBucket, String> column(String title,
                                                       java.util.function.Function<ProfitBucket, String> value) {
        TableColumn<ProfitBucket, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private void updateMonthFilterVisibility() {
        if (monthFilter == null || periodFilter == null) {
            return;
        }
        boolean monthly = MONTHLY_REPORT.equals(periodFilter.getValue());
        monthFilter.setVisible(monthly);
        monthFilter.setManaged(monthly);
    }

    private VBox metricCard(String title, Label value) {
        Label heading = label(title, AppUIManager.COLOR_TEXT_MEDIUM);
        heading.setStyle(heading.getStyle() + "-fx-font-size: 10px; -fx-font-weight: bold;");
        VBox card = new VBox(9, heading, value);
        card.setPadding(new Insets(17));
        card.setMinWidth(180);
        card.setStyle(cardStyle());
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Label metricValue(String color) {
        Label value = new Label("—");
        value.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        return value;
    }

    private Label emptyMessage() {
        Label empty = new Label("No completed sales for this period.");
        empty.setStyle("-fx-font-size: 14px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        return empty;
    }

    private static Number number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number : 0;
    }

    private static String money(double amount) {
        return String.format("Rs %.2f", amount);
    }

    private static String percent(double value) {
        return String.format("%.2f%%", value);
    }

    private static String formatRange(LocalDate from, LocalDate to) {
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

    private record ProfitBucket(String key, String label, int transactions, double netSales,
                                double cogs, double profit, double tax, double discounts) {
        private double marginPercent() {
            return netSales == 0 ? 0 : profit / netSales * 100;
        }

        private double cogsPercent() {
            return netSales == 0 ? 0 : cogs / netSales * 100;
        }

        private ProfitBucket plus(ProfitBucket other) {
            double combinedSales = netSales + other.netSales;
            double combinedCogs = cogs + other.cogs;
            return new ProfitBucket(key, label, transactions + other.transactions,
                    combinedSales, combinedCogs, combinedSales - combinedCogs,
                    tax + other.tax, discounts + other.discounts);
        }

        private static ProfitBucket empty(String key, String label) {
            return new ProfitBucket(key, label, 0, 0, 0, 0, 0, 0);
        }
    }
}
