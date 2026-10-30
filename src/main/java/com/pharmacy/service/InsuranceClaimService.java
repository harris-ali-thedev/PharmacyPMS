package com.pharmacy.service;

import com.pharmacy.util.*;
import java.math.BigDecimal;
import java.sql.*;

public class InsuranceClaimService {
    public record Claim(int id, BigDecimal invoiceTotal, BigDecimal covered, BigDecimal copay) {}
    public Claim record(int saleId, String policy, String insurer, BigDecimal coverage) throws SQLException {
        var session=SessionManager.getInstance();session.requireRole("ADMIN","MAIN_ADMIN","PHARMACIST","TECHNICIAN");
        int userId=session.getCurrentUser().getUserId();
        if(saleId<=0 || policy.isBlank() || policy.length()>100 || insurer.isBlank() || insurer.length()>150
                || coverage.signum()<0 || coverage.compareTo(new BigDecimal("100"))>0 || coverage.stripTrailingZeros().scale()>2)
            throw new IllegalArgumentException("Enter an invoice ID, policy, insurer and coverage from 0 to 100 (up to two decimal places).");
        try(var con=DatabaseConnection.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                BigDecimal total;
                try(var ps=con.prepareStatement("SELECT grand_total,status FROM sales WHERE sale_id=? FOR UPDATE")) {
                    ps.setInt(1,saleId);
                    try(var rs=ps.executeQuery()) {
                        if(!rs.next() || !"COMPLETED".equals(rs.getString("status")))throw new SQLException("Select an existing completed invoice.");
                        total=rs.getBigDecimal("grand_total");
                    }
                }
                BigDecimal covered=CheckoutTotals.money(total.multiply(coverage).movePointLeft(2)), copay=total.subtract(covered);
                int id;
                try(var ps=con.prepareStatement("INSERT INTO insurance_claims(sale_id,policy_number,insurer,coverage_percent,invoice_total,covered_amount,patient_copay,created_by) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1,saleId);ps.setString(2,policy.trim());ps.setString(3,insurer.trim());ps.setBigDecimal(4,coverage);
                    ps.setBigDecimal(5,total);ps.setBigDecimal(6,covered);ps.setBigDecimal(7,copay);ps.setInt(8,userId);ps.executeUpdate();
                    try(var rs=ps.getGeneratedKeys()) {if(!rs.next())throw new SQLException("Claim was not recorded.");id=rs.getInt(1);}
                }
                con.commit();AuditLogger.log("CLAIM_RECORDED","insurance_claims",id,"Recorded for invoice " + saleId);
                return new Claim(id,total,covered,copay);
            } catch(Exception ex) {con.rollback();throw ex;}
        }
    }
}
