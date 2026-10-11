package com.pharmacy.view;

import com.pharmacy.dao.MedicineDAO;
import com.pharmacy.dao.SaleDAO;
import com.pharmacy.model.Medicine;
import com.pharmacy.model.ShiftSession;
import com.pharmacy.model.User;
import com.pharmacy.service.ShiftSessionService;
import com.pharmacy.util.AlertUtil;
import com.pharmacy.util.SessionManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Pharmacy operations dashboard — fully inline styled (no CSS file).
 */
public class DashboardView {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");

    // ── Design tokens ────────────────────────────────────────
    private static final String FONT_STACK   = "-fx-font-family: 'DM Sans','SF Pro Text','Segoe UI Variable','Segoe UI',sans-serif;";

    private static final String C_BG         = "#F0F4F8";
    private static final String C_WHITE      = "#FFFFFF";
    private static final String C_BORDER     = "#E9EEF5";
    private static final String C_BORDER_MED = "#DDE3EC";
    private static final String C_SURFACE    = "#F8FAFC";
    private static final String C_STRIP      = "#F1F5F9";

    private static final String C_TEXT_DARK  = "#0F172A";
    private static final String C_TEXT_MED   = "#334155";
    private static final String C_TEXT_LIGHT = "#94A3B8";
    private static final String C_TEXT_SUB   = "#64748B";

    private static final String C_PRIMARY    = "#1A6DB5";
    private static final String C_SUCCESS    = "#059669";
    private static final String C_DANGER     = "#DC2626";
    private static final String C_PURPLE     = "#7C3AED";

    private static final String C_ICON_BLUE  = "#EFF6FF";
    private static final String C_ICON_GREEN = "#ECFDF5";
    private static final String C_ICON_RED   = "#FEF2F2";
    private static final String C_ICON_PURP  = "#F5F3FF";

    // ── Shared shadow/panel style ────────────────────────────
    private static final String PANEL_STYLE =
            FONT_STACK +
                    "-fx-background-color: " + C_WHITE + ";" +
                    "-fx-background-radius: 12;" +
                    "-fx-border-color: " + C_BORDER + ";" +
                    "-fx-border-width: 1;" +
                    "-fx-border-radius: 12;" +
                    "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.05), 10, 0, 0, 2);" +
                    "-fx-padding: 16 18;";

    private final SaleDAO           saleDAO     = new SaleDAO();
    private final MedicineDAO       medicineDAO = new MedicineDAO();
    private final ShiftSessionService shiftService = new ShiftSessionService();

    private Button shiftOpenBtn;
    private Button shiftCloseBtn;

    // ── Public entry ─────────────────────────────────────────

    public ScrollPane getView() {
        MainView.ensureSidebarOpen();

        VBox content = new VBox(24);
        content.setPadding(new Insets(0));
        content.setStyle(FONT_STACK + "-fx-background-color: " + C_BG + ";");

        content.getChildren().addAll(
                buildHeader(),
                buildMetricsSection(),
                buildBottomSection()
        );

        ScrollPane scroll = AppUIManager.scrollWrap(content);
        scroll.setStyle(
                "-fx-background-color: " + C_BG + ";" +
                        "-fx-background: " + C_BG + ";"
        );
        return scroll;
    }

    // ── Header ───────────────────────────────────────────────

    private VBox buildHeader() {
        User user = SessionManager.getInstance().getCurrentUser();
        String firstName = "there";
        if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
            firstName = user.getFullName().trim().split("\\s+")[0];
        }

        Label greeting = new Label(timeGreeting() + " — " + firstName);
        greeting.setStyle(
                FONT_STACK +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + C_TEXT_DARK + ";"
        );

        Label dateLbl = new Label(LocalDate.now().format(DATE_FMT));
        dateLbl.setStyle(
                FONT_STACK +
                        "-fx-font-size: 11px;" +
                        "-fx-text-fill: " + C_TEXT_LIGHT + ";"
        );

        VBox titles = new VBox(2, greeting, dateLbl);
        titles.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        shiftOpenBtn  = createShiftButton("shift-open");
        shiftCloseBtn = createShiftButton("shift-close");
        refreshShiftButtons();

        HBox shiftBox = new HBox(10, shiftOpenBtn, shiftCloseBtn);
        shiftBox.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(12, titles, spacer, shiftBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 28, 8, 28));

        VBox header = new VBox(row);
        header.setStyle(
                FONT_STACK +
                        "-fx-background-color: " + C_WHITE + ";" +
                        "-fx-border-color: transparent transparent " + C_BORDER_MED + " transparent;" +
                        "-fx-border-width: 0 0 1 0;"
        );
        header.setPrefSize(0, 45);
        header.setMaxSize(Integer.MAX_VALUE, 45);
        return header;
    }

    private String timeGreeting() {
        int hour = LocalDateTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    // ── Metrics ──────────────────────────────────────────────

    private VBox buildMetricsSection() {
        GridPane metrics = new GridPane();
        metrics.setHgap(16);
        metrics.setVgap(16);
        metrics.setPadding(new Insets(0, 28, 0, 28));

        try {
            Map<String, Object> daily = saleDAO.getDailyStats(LocalDate.now());
            double revenue  = daily.containsKey("revenue")   ? (double) daily.get("revenue")   : 0.0;
            int    txnCount = daily.containsKey("txn_count") ? (int)    daily.get("txn_count") : 0;

            Map<String, Object> monthly = saleDAO.getMonthlyStats(
                    LocalDate.now().getYear(), LocalDate.now().getMonthValue());
            double monthRevenue = monthly.containsKey("revenue") ? (double) monthly.get("revenue") : 0.0;

            int lowStockCount = medicineDAO.getLowStock().size();

            metrics.add(metricCard("Today's Revenue",    formatRs(revenue),           "💰", C_PRIMARY, C_ICON_BLUE),  0, 0);
            metrics.add(metricCard("Transactions Today", String.valueOf(txnCount),     "📊", C_SUCCESS, C_ICON_GREEN), 1, 0);
            metrics.add(metricCard("Low Stock Items",    String.valueOf(lowStockCount),"⚠️", C_DANGER,  C_ICON_RED),   2, 0);
            metrics.add(metricCard("Monthly Revenue",    formatRs(monthRevenue),       "📅", C_PURPLE,  C_ICON_PURP),  3, 0);

            for (int i = 0; i < 4; i++) {
                ColumnConstraints cc = new ColumnConstraints();
                cc.setPercentWidth(25);
                cc.setHgrow(Priority.ALWAYS);
                metrics.getColumnConstraints().add(cc);
            }
        } catch (Exception e) {
            Label err = new Label("Could not load metrics: " + e.getMessage());
            err.setStyle("-fx-text-fill: " + C_DANGER + "; -fx-font-size: 11px; -fx-font-weight: bold;");
            metrics.add(err, 0, 0);
        }

        VBox section = new VBox(12, metrics);
        section.setPadding(new Insets(8, 0, 0, 0));
        return section;
    }

    private String formatRs(double amount) {
        return String.format("Rs %,.0f", amount);
    }

    private VBox metricCard(String label, String value, String icon, String accent, String iconBg) {
        // Icon wrapper
        StackPane iconWrap = new StackPane();
        iconWrap.setStyle(
                "-fx-background-color: " + iconBg + ";" +
                        "-fx-background-radius: 10;" +
                        "-fx-min-width: 42; -fx-min-height: 42;" +
                        "-fx-max-width: 42; -fx-max-height: 42;" +
                        "-fx-alignment: center;"
        );
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 20px;");
        iconWrap.getChildren().add(iconLbl);

        Label lbl = new Label(label);
        lbl.setStyle(
                FONT_STACK +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + C_TEXT_LIGHT + ";"
        );

        Label val = new Label(value);
        val.setStyle(
                FONT_STACK +
                        "-fx-font-size: 24px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + accent + ";"
        );

        VBox text = new VBox(4, lbl, val);
        text.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(16, iconWrap, text);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(row);
        card.setStyle(PANEL_STYLE + "-fx-padding: 16 18;");
        card.setOnMouseEntered(e -> card.setStyle(
                PANEL_STYLE +
                        "-fx-padding: 16 18;" +
                        "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.10), 16, 0, 0, 4);"
        ));
        card.setOnMouseExited(e -> card.setStyle(PANEL_STYLE + "-fx-padding: 16 18;"));
        GridPane.setHgrow(card, Priority.ALWAYS);
        GridPane.setFillWidth(card, true);
        return card;
    }

    // ── Bottom panels ────────────────────────────────────────

    private HBox buildBottomSection() {
        VBox topMeds = buildTopMedicinesCard();
        VBox alerts  = buildAlertsCard();
        HBox.setHgrow(topMeds, Priority.ALWAYS);
        HBox.setHgrow(alerts,  Priority.ALWAYS);

        HBox row = new HBox(20, topMeds, alerts);
        row.setPadding(new Insets(0, 28, 28, 28));
        row.setAlignment(Pos.TOP_CENTER);
        return row;
    }

    private VBox buildTopMedicinesCard() {
        VBox card = panelShell("🏆 Top Sellers", "Best-performing medicines in the last 30 days");

        VBox list = new VBox(4);
        list.setFillWidth(true);

        try {
            LocalDate endDate   = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30);
            List<Map<String, Object>> top = saleDAO.getTopMedicines(startDate, endDate, 10);

            if (top.isEmpty()) {
                list.getChildren().add(emptyState("No sales recorded in the last 30 days.", "📭"));
            } else {
                int max  = top.stream().mapToInt(m -> (int) m.get("qty")).max().orElse(1);
                int rank = 1;
                for (Map<String, Object> m : top) {
                    list.getChildren().add(medicineRow(rank++, (String) m.get("name"), (int) m.get("qty"), max));
                }
            }
        } catch (Exception e) {
            list.getChildren().add(emptyState("Error: " + e.getMessage(), "⚠️"));
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(320);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        card.getChildren().add(scroll);
        return card;
    }

    private HBox medicineRow(int rank, String name, int qty, int maxQty) {
        // Rank badge
        Label rankBadge = new Label(String.valueOf(rank));
        rankBadge.setAlignment(Pos.CENTER);
        rankBadge.setStyle(rankBadgeStyle(rank));

        Label nameLbl = new Label(name);
        nameLbl.setStyle(
                FONT_STACK +
                        "-fx-font-size: 13px;" +
                        "-fx-text-fill: " + C_TEXT_MED + ";"
        );
        nameLbl.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLbl, Priority.ALWAYS);

        double pct = maxQty > 0 ? (double) qty / maxQty : 0;
        // Custom bar — ProgressBar's -fx-accent is ignored without a base stylesheet,
        // so we build a reliable track + fill from plain Panes instead.
        Pane fill = new Pane();
        fill.setStyle(
                "-fx-background-color: " + C_PRIMARY + ";" +
                        "-fx-background-radius: 4;"
        );
        fill.setPrefHeight(6);
        fill.setMaxHeight(6);
        // Bind fill width to track width * percentage
        StackPane track = new StackPane(fill);
        track.setStyle(
                "-fx-background-color: #DDE3EC;" +
                        "-fx-background-radius: 4;"
        );
        track.setPrefHeight(6);
        track.setMaxHeight(6);
        track.setMaxWidth(Double.MAX_VALUE);
        // Once layout is known, set fill width proportionally
        track.layoutBoundsProperty().addListener((obs, oldB, newB) -> {
            fill.setPrefWidth(newB.getWidth() * pct);
            fill.setMaxWidth(newB.getWidth() * pct);
        });
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        HBox.setHgrow(track, Priority.ALWAYS);

        Label qtyLbl = new Label(qty + " units");
        qtyLbl.setStyle(
                FONT_STACK +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + C_TEXT_SUB + ";" +
                        "-fx-min-width: 64;" +
                        "-fx-alignment: center-right;"
        );
        qtyLbl.setAlignment(Pos.CENTER_RIGHT);

        VBox barCol = new VBox(6, nameLbl, track);
        barCol.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(barCol, Priority.ALWAYS);

        HBox row = new HBox(12, rankBadge, barCol, qtyLbl);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(
                "-fx-padding: 7 0;" +
                        "-fx-border-color: transparent transparent " + C_STRIP + " transparent;" +
                        "-fx-border-width: 0 0 1 0;"
        );
        return row;
    }

    private String rankBadgeStyle(int rank) {
        String bg, fg;
        switch (rank) {
            case 1  -> { bg = "#FEF3C7"; fg = "#B45309"; }
            case 2  -> { bg = "#F1F5F9"; fg = "#64748B"; }
            default -> { bg = "#EFF6FF"; fg = "#1A5FA0"; }
        }
        return FONT_STACK +
                "-fx-background-color: " + bg + ";" +
                "-fx-text-fill: " + fg + ";" +
                "-fx-background-radius: 50%;" +
                "-fx-min-width: 24; -fx-min-height: 24;" +
                "-fx-max-width: 24; -fx-max-height: 24;" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: bold;" +
                "-fx-alignment: center;";
    }

    private VBox buildAlertsCard() {
        VBox card = panelShell("🔔 Stock Alerts", "Medicines that need immediate attention");

        VBox list = new VBox(8);
        list.setFillWidth(true);

        try {
            List<Medicine> lowStock = medicineDAO.getLowStock();
            if (lowStock.isEmpty()) {
                list.getChildren().add(allClearState());
            } else {
                int limit = Math.min(lowStock.size(), 10);
                for (int i = 0; i < limit; i++) {
                    list.getChildren().add(alertRow(lowStock.get(i)));
                }
                if (lowStock.size() > limit) {
                    Label more = new Label("+ " + (lowStock.size() - limit) + " more items");
                    more.setStyle(FONT_STACK + "-fx-font-size: 12px; -fx-text-fill: " + C_TEXT_LIGHT + ";");
                    list.getChildren().add(more);
                }
            }
        } catch (Exception e) {
            list.getChildren().add(emptyState("Error loading alerts", "⚠️"));
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(320);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        card.getChildren().add(scroll);
        return card;
    }

    private HBox alertRow(Medicine m) {
        Label icon = new Label("⚠");
        icon.setStyle("-fx-font-size: 16px;");

        Label name = new Label(m.getName());
        name.setStyle(
                FONT_STACK +
                        "-fx-font-size: 13px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + C_TEXT_DARK + ";"
        );
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        Label qty = new Label(m.getStockQty() + " left");
        qty.setStyle(
                FONT_STACK +
                        "-fx-font-size: 10px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-color: #FEF2F2;" +
                        "-fx-text-fill: " + C_DANGER + ";" +
                        "-fx-background-radius: 14;" +
                        "-fx-padding: 2 7;"
        );

        HBox row = new HBox(10, icon, name, qty);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(
                "-fx-background-color: #FEF2F2;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: #FECACA;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 8;" +
                        "-fx-padding: 10;"
        );
        return row;
    }

    // ── Panel shell ──────────────────────────────────────────

    private VBox panelShell(String title, String subtitle) {
        Label titleLbl = new Label(title);
        titleLbl.setStyle(
                FONT_STACK +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + C_TEXT_DARK + ";"
        );

        Label subLbl = new Label(subtitle);
        subLbl.setStyle(
                FONT_STACK +
                        "-fx-font-size: 11px;" +
                        "-fx-text-fill: " + C_TEXT_LIGHT + ";"
        );
        subLbl.setWrapText(true);

        VBox card = new VBox(14, titleLbl, subLbl);
        card.setStyle(PANEL_STYLE);
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    // ── State placeholders ───────────────────────────────────

    private VBox emptyState(String message, String icon) {
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 32px;");

        Label msg = new Label(message);
        msg.setStyle(FONT_STACK + "-fx-font-size: 13px; -fx-text-fill: " + C_TEXT_LIGHT + ";");
        msg.setWrapText(true);
        msg.setTextAlignment(TextAlignment.CENTER);

        VBox box = new VBox(10, iconLbl, msg);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(32, 16, 32, 16));
        return box;
    }

    private VBox allClearState() {
        Label icon = new Label("✅");
        icon.setStyle("-fx-font-size: 28px;");

        Label msg = new Label("All stock levels look healthy");
        msg.setStyle(
                FONT_STACK +
                        "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + C_SUCCESS + ";"
        );

        VBox box = new VBox(8, icon, msg);
        box.setAlignment(Pos.CENTER);
        box.setStyle(
                "-fx-background-color: #ECFDF5;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-color: #A7F3D0;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 8;" +
                        "-fx-padding: 16;" +
                        "-fx-alignment: center;"
        );
        return box;
    }

    // ── Shift controls ───────────────────────────────────────

    private Button createShiftButton(String role) {
        Button btn = new Button();
        btn.setUserData(role);
        btn.setPadding(new Insets(8, 16, 8, 16));
        return btn;
    }

    private void refreshShiftButtons() {
        updateShiftButton(shiftOpenBtn);
        updateShiftButton(shiftCloseBtn);
    }

    private void updateShiftButton(Button btn) {
        if (btn == null) return;

        String role      = String.valueOf(btn.getUserData());
        boolean isOpenRole = "shift-open".equals(role);

        try {
            User user = SessionManager.getInstance().getCurrentUser();
            if (user == null) {
                btn.setVisible(false);
                btn.setManaged(false);
                return;
            }

            btn.setVisible(true);
            btn.setManaged(true);
            ShiftSession openShift = shiftService.getOpenShift(user.getUserId());

            if (openShift != null) {
                SessionManager.getInstance().setCurrentShiftSessionId(openShift.getSessionId());
                if (isOpenRole) {
                    btn.setText("● Shift Active · #" + openShift.getSessionId());
                    btn.setStyle(
                            FONT_STACK +
                                    "-fx-background-color: #ECFDF5;" +
                                    "-fx-text-fill: " + C_SUCCESS + ";" +
                                    "-fx-border-color: #A7F3D0;" +
                                    "-fx-border-width: 1;" +
                                    "-fx-border-radius: 8;" +
                                    "-fx-background-radius: 8;" +
                                    "-fx-padding: 4 12;" +
                                    "-fx-font-size: 11px;" +
                                    "-fx-font-weight: bold;"
                    );
                    btn.setDisable(true);
                    btn.setOnAction(null);
                } else {
                    btn.setText("Close Shift");
                    styleShiftButton(btn, C_DANGER, true);
                    btn.setOnAction(e -> handleCloseShift());
                }
            } else {
                SessionManager.getInstance().setCurrentShiftSessionId(null);
                if (isOpenRole) {
                    btn.setText("▶  Open Shift");
                    styleShiftButton(btn, C_PRIMARY, true);
                    btn.setOnAction(e -> handleOpenShift());
                } else {
                    btn.setVisible(false);
                    btn.setManaged(false);
                }
            }
        } catch (Exception e) {
            btn.setText("Shift unavailable");
            btn.setDisable(true);
        }
    }

    private void styleShiftButton(Button btn, String bg, boolean enabled) {
        btn.setStyle(
                FONT_STACK +
                        "-fx-background-color: " + bg + ";" +
                        "-fx-text-fill: white;" +
                        "-fx-font-weight: bold;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-radius: 8;" +
                        "-fx-cursor: hand;" +
                        "-fx-font-size: 13px;" +
                        "-fx-padding: 6 14;"
        );
        btn.setDisable(!enabled);
    }

    // ── Shift dialog handlers ────────────────────────────────

    private void handleOpenShift() {
        TextInputDialog dialog = new TextInputDialog("500");
        dialog.setTitle("Open Shift");
        dialog.setHeaderText("Opening float");
        dialog.setContentText("Enter starting cash in drawer (Rs):");

        dialog.showAndWait().ifPresent(val -> {
            try {
                double amount = Double.parseDouble(val.trim());
                if (amount < 0) throw new NumberFormatException("Negative amount");

                User user      = SessionManager.getInstance().getCurrentUser();
                int sessionId  = shiftService.openShift(user.getUserId(), user.getFullName(), amount);
                SessionManager.getInstance().setCurrentShiftSessionId(sessionId);

                AlertUtil.showInfo("Success", "Shift opened with float Rs " + amount);
                refreshShiftButtons();
            } catch (NumberFormatException e) {
                AlertUtil.showError("Invalid Input", "Please enter a valid positive number.");
            } catch (Exception e) {
                AlertUtil.showError("Error", "Could not open shift: " + e.getMessage());
            }
        });
    }

    private void handleCloseShift() {
        TextInputDialog dialog = new TextInputDialog("1000");
        dialog.setTitle("Close Shift");
        dialog.setHeaderText("Physical cash count");
        dialog.setContentText("Enter total cash in drawer (Rs):");

        dialog.showAndWait().ifPresent(val -> {
            try {
                double amount = Double.parseDouble(val.trim());
                if (amount < 0) throw new NumberFormatException("Negative amount");

                User user          = SessionManager.getInstance().getCurrentUser();
                ShiftSession openShift = shiftService.getOpenShift(user.getUserId());
                shiftService.closeShift(openShift.getSessionId(), amount, "");
                SessionManager.getInstance().setCurrentShiftSessionId(null);

                AlertUtil.showInfo("Success", "Shift closed. Cash counted: Rs " + amount);
                refreshShiftButtons();
            } catch (NumberFormatException e) {
                AlertUtil.showError("Invalid Input", "Please enter a valid positive number.");
            } catch (Exception e) {
                AlertUtil.showError("Error", "Could not close shift: " + e.getMessage());
            }
        });
    }
}