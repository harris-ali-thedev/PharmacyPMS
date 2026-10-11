module com.pharmacy {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.sql;
//    requires com.mysql.cj;
//    requires at.favre.lib.bcrypt;
    requires itextpdf;
    requires org.apache.poi.ooxml;
    requires org.slf4j;
    requires bcrypt;
    requires mysql.connector.j;
    requires java.desktop;
//    requires jfreechart;

    opens com.pharmacy to javafx.fxml;
    opens com.pharmacy.controller to javafx.fxml;
    opens com.pharmacy.model to javafx.base;
    opens com.pharmacy.view to javafx.fxml;

    exports com.pharmacy;
    exports com.pharmacy.controller;
    exports com.pharmacy.model;
    exports com.pharmacy.view;
    exports com.pharmacy.dao;
    exports com.pharmacy.service;
    exports com.pharmacy.util;
    exports com.pharmacy.view.inventory;
    opens com.pharmacy.view.inventory to javafx.fxml;
    exports com.pharmacy.view.admin;
    opens com.pharmacy.view.admin to javafx.fxml;
    exports com.pharmacy.view.prescription;
    opens com.pharmacy.view.prescription to javafx.fxml;
    exports com.pharmacy.view.reports;
    opens com.pharmacy.view.reports to javafx.fxml;
    exports com.pharmacy.view.supplier;
    opens com.pharmacy.view.supplier to javafx.fxml;
}
