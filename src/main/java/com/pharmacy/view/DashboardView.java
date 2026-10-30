package com.pharmacy.view;

import com.pharmacy.service.DashboardService;
import com.pharmacy.service.DashboardService.DayPoint;
import com.pharmacy.service.DashboardService.Insight;
import com.pharmacy.service.DashboardService.Kpis;
import com.pharmacy.service.DashboardService.Snapshot;
import com.pharmacy.util.UiTasks;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Overview dashboard: KPI cards, 7-day sales chart, and data-driven insights. */
public class DashboardView {

    private final DashboardService service = new DashboardService();
    private final com.pharmacy.service.ShiftSessionService shifts = new com.pharmacy.service.ShiftSessionService();
    private final Button openShift = AppUIManager.primaryButton("Open Shift");
    private final Button closeShift = AppUIManager.outlineButton("Close Shift");

    private final Label salesValue = value(), salesSub = sub();
    private final Label marginValue = value(), marginSub = sub();
    private final Label inventoryValue = value(), inventorySub = sub();
    private final Label discountValue = value(), discountSub = sub();
    private final StackPane chartHost = new StackPane();
    private final VBox insightsBox = new VBox(12);
    private final Label banner = new Label("Loading…");
    private final Label status = new Label();

    public Node getView() {
        VBox page = new VBox(18);
        page.setPadding(new Insets(24));
        page.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + ";");

        HBox kpis = new HBox(14,
                kpiCard("Sales Today", salesValue, salesSub),
                kpiCard("Gross Margin (7 days)", marginValue, marginSub),
                kpiCard("Inventory Value", inventoryValue, inventorySub),
                kpiCard("Discounts Given Today", discountValue, discountSub));

        VBox chartCard = card();
        Label chartTitle = new Label("Sales – Last 7 Days");
        chartTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        chartHost.setMinHeight(320);
        VBox.setVgrow(chartHost, Priority.ALWAYS);
        chartCard.getChildren().addAll(chartHeader(chartTitle), chartHost);

        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setStyle("-fx-background-color: #ECFDF5; -fx-text-fill: #047857; -fx-font-size: 12px; "
                + "-fx-padding: 11 16; -fx-background-radius: 8; -fx-border-color: #A7F3D0; -fx-border-radius: 8;");

        VBox left = new VBox(14, chartCard, banner);
        HBox.setHgrow(left, Priority.ALWAYS);

        VBox right = card();
        right.setPrefWidth(310);
        right.setMinWidth(270);
        right.setMaxWidth(340);
        Label insightsTitle = new Label("Insights");
        insightsTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        right.getChildren().addAll(insightsTitle, insightsBox);

        HBox body = new HBox(16, left, right);
        body.setAlignment(Pos.TOP_LEFT);

        page.getChildren().addAll(header(), kpis, body);

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: " + AppUIManager.COLOR_BG + "; -fx-background: " + AppUIManager.COLOR_BG + ";");

        refresh();
        return scroll;
    }

    // ── Data loading ─────────────────────────────────────────

    private void refresh() {
        refreshShift();
        status.setText("Refreshing…");
        UiTasks.run(service::load, this::render, err -> {
            status.setText("Could not load dashboard.");
            chartHost.getChildren().setAll(message("Dashboard data could not be loaded: " + err.getMessage()));
            insightsBox.getChildren().setAll(message("Insights unavailable."));
            banner.setText("Check the database connection and press Refresh.");
        });
    }

    private void render(Snapshot s) {
        Kpis k = s.kpis();
        salesValue.setText(pkr(k.salesToday()));
        salesSub.setText(change(k.salesToday(), k.salesYesterday(), "vs yesterday"));
        marginValue.setText(k.hasMarginData() || k.margin7() != 0 ? String.format("%.1f%%", k.margin7()) : "—");
        marginSub.setText(k.hasMarginData()
                ? String.format("%+.1f pts vs previous 7 days", k.margin7() - k.marginPrev7())
                : "Not enough history to compare");
        inventoryValue.setText(pkr(k.inventoryValue()));
        inventorySub.setText(k.expiryRisk() > 0
                ? pkr(k.expiryRisk()) + " at expiry risk" : "No expiry risk");
        discountValue.setText(pkr(k.discountsToday()));
        discountSub.setText(k.salesToday() > 0
                ? String.format("%.1f%% of today's sales", k.discountsToday() / k.salesToday() * 100)
                : "No sales yet today");

        renderChart(s.week());
        insightsBox.getChildren().setAll(s.insights().stream().map(this::insightCard).toList());
        banner.setText(k.salesCountToday() == 0 ? "No completed sales yet today."
                : k.salesCountToday() + " completed sale(s) today · average "
                  + pkr(k.salesToday() / k.salesCountToday()));
        status.setText("Updated " + java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
    }

    private void renderChart(List<DayPoint> week) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("Sales (PKR)");
        y.setForceZeroInRange(true);
        AreaChart<String, Number> chart = new AreaChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (DayPoint p : week) {
            XYChart.Data<String, Number> d = new XYChart.Data<>(p.date().format(DateTimeFormatter.ofPattern("EEE dd")), p.sales());
            String tip = p.date().format(DateTimeFormatter.ofPattern("EEEE dd MMM")) + "\nSales: " + pkr(p.sales())
                    + (p.sales() > 0 ? String.format(" | Margin: %.1f%%", p.marginPercent()) : "");
            d.nodeProperty().addListener((o, a, n) -> { if (n != null) Tooltip.install(n, new Tooltip(tip)); });
            series.getData().add(d);
        }
        chart.getData().add(series);
        for (XYChart.Data<String, Number> d : series.getData()) {
            if (d.getNode() != null) { /* tooltip installed via listener when node is created */ }
        }
        boolean empty = week.stream().allMatch(p -> p.sales() == 0);
        chartHost.getChildren().setAll(empty ? message("No completed sales in the last 7 days.") : chart);
    }

    // ── Components ───────────────────────────────────────────

    private Node header() {
        Label title = AppUIManager.pageTitle("Overview");
        Label subtitle = new Label("Live figures from sales, batches and inventory.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        Button refresh = AppUIManager.primaryButton("Refresh");
        refresh.setOnAction(e -> refresh());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        openShift.setOnAction(e -> changeShift(false));
        closeShift.setOnAction(e -> changeShift(true));
        HBox h = new HBox(12, new VBox(8, new HBox(8, openShift, closeShift), title, subtitle), spacer, status, refresh);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private void refreshShift() {
        var session = com.pharmacy.util.SessionManager.getInstance();
        var user = session.getCurrentUser();
        openShift.setDisable(true);
        closeShift.setDisable(true);
        if (user == null) return;
        UiTasks.run(() -> shifts.getOpenShift(user.getUserId()), shift -> {
            if (session.getCurrentUser() != user) return;
            session.setCurrentShiftSessionId(shift == null ? null : shift.getSessionId());
            openShift.setText(shift == null ? "Open Shift" : "Shift Active #" + shift.getSessionId());
            openShift.setDisable(shift != null);
            closeShift.setDisable(shift == null);
        }, error -> {
            openShift.setText("Shift unavailable");
            openShift.setTooltip(new Tooltip(error.getMessage()));
        });
    }

    private void changeShift(boolean closing) {
        var session = com.pharmacy.util.SessionManager.getInstance();
        try { session.requireActiveSession(); }
        catch (Exception ex) { com.pharmacy.view.dialogs.MessageDialog.showError("Session", ex.getMessage()); return; }
        var user = session.getCurrentUser();
        var shiftId = session.getCurrentShiftSessionId();
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("0.00");
        dialog.setTitle(closing ? "Close Shift" : "Open Shift");
        dialog.setHeaderText(closing ? "Count the physical cash in the drawer" : "Enter the opening cash float");
        dialog.setContentText("Cash (Rs):");
        dialog.showAndWait().ifPresent(input -> {
            final double amount;
            try {
                var cash = new java.math.BigDecimal(input.trim()).setScale(2, java.math.RoundingMode.UNNECESSARY);
                if (cash.signum() < 0 || cash.precision() > 12) throw new NumberFormatException();
                amount = cash.doubleValue();
            } catch (Exception ex) {
                com.pharmacy.view.dialogs.MessageDialog.showWarning("Invalid cash", "Enter a non-negative cash amount with at most two decimal places.");
                return;
            }
            openShift.setDisable(true);
            closeShift.setDisable(true);
            UiTasks.run(() -> {
                if (closing) {
                    if (shiftId == null) throw new IllegalStateException("No open shift.");
                    shifts.closeShift(shiftId, amount, "");
                    return (Integer) null;
                }
                int id = shifts.openShift(user.getUserId(), user.getFullName(), amount);
                if (id <= 0) throw new IllegalStateException("Shift was not created.");
                return id;
            }, id -> {
                if (session.getCurrentUser() != user) return;
                session.setCurrentShiftSessionId(id);
                refreshShift();
                com.pharmacy.view.dialogs.MessageDialog.showSuccess("Shift", closing ? "Shift closed." : "Shift opened.");
            }, error -> {
                refreshShift();
                com.pharmacy.view.dialogs.MessageDialog.showError("Shift failed", error.getMessage());
            });
        });
    }

    private Node chartHeader(Label title) {
        return new HBox(title);
    }

    private VBox kpiCard(String title, Label value, Label sub) {
        Label t = new Label(title);
        t.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        VBox c = card();
        c.setSpacing(6);
        c.getChildren().addAll(t, value, sub);
        c.setMinWidth(190);
        HBox.setHgrow(c, Priority.ALWAYS);
        return c;
    }

    private Node insightCard(Insight in) {
        String[] pal = switch (in.level()) {
            case DANGER  -> new String[]{"#FEE2E2", "#FCA5A5", "#B91C1C", "#EF4444"};
            case WARNING -> new String[]{"#FEF3C7", "#FCD34D", "#B45309", "#D97706"};
            case SUCCESS -> new String[]{"#DCFCE7", "#86EFAC", "#15803D", "#16A34A"};
            default      -> new String[]{"#DBEAFE", "#93C5FD", "#1D4ED8", "#2563EB"};
        };
        Label title = new Label(in.title());
        title.setWrapText(true);
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + pal[2] + ";");
        Label body = new Label(in.body());
        body.setWrapText(true);
        body.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");
        Button action = new Button(in.actionLabel());
        action.setMaxWidth(Double.MAX_VALUE);
        action.setStyle("-fx-background-color: " + pal[3] + "; -fx-text-fill: white; -fx-font-weight: bold; "
                + "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 7 12;");
        action.setOnAction(e -> ViewManager.getInstance().navigate(in.viewKey()));
        VBox box = new VBox(8, title, body, action);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color: " + pal[0] + "; -fx-border-color: " + pal[1]
                + "; -fx-border-radius: 10; -fx-background-radius: 10;");
        return box;
    }

    private VBox card() {
        VBox c = new VBox(12);
        c.setPadding(new Insets(18));
        c.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: "
                + AppUIManager.COLOR_BORDER + "; -fx-border-radius: 12; -fx-border-width: 1;");
        return c;
    }

    private Label value() {
        Label l = new Label("—");
        l.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + AppUIManager.COLOR_TEXT_DARK + ";");
        return l;
    }

    private Label sub() {
        Label l = new Label(" ");
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        l.setWrapText(true);
        return l;
    }

    private Label message(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 13px; -fx-text-fill: " + AppUIManager.COLOR_TEXT_MEDIUM + ";");
        return l;
    }

    private static String pkr(double v) { return String.format("PKR %,.0f", v); }

    private static String change(double now, double before, String suffix) {
        if (before == 0) return now == 0 ? "No sales yesterday" : "No sales yesterday";
        return String.format("%+.1f%% %s", (now - before) / before * 100, suffix);
    }
}
