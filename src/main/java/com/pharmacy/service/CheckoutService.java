package com.pharmacy.service;

import com.pharmacy.dao.SaleDAO;
import com.pharmacy.model.*;
import com.pharmacy.util.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Owns the transaction covering sale persistence, inventory and prescription dispensing. */
public class CheckoutService {
    public Sale complete(Sale sale, Prescription prescription, List<SaleItem> prescribedItems) throws SQLException {
        var session=SessionManager.getInstance();session.requireActiveSession();
        if(sale.getUserId()!=session.getCurrentUser().getUserId() || sale.getSessionId()==null || sale.getItems().isEmpty())
            throw new IllegalArgumentException("An active cashier shift and a non-empty cart are required.");
        try(var con=DatabaseConnection.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                try(var ps=con.prepareStatement("SELECT user_id,status FROM shift_sessions WHERE session_id=? FOR UPDATE")) {
                    ps.setInt(1,sale.getSessionId());
                    try(var rs=ps.executeQuery()) {
                        if(!rs.next() || rs.getInt("user_id")!=sale.getUserId() || !"OPEN".equals(rs.getString("status")))
                            throw new SQLException("The cashier shift is no longer open.");
                    }
                }
                int id=new SaleDAO().saveSale(con,sale);sale.setSaleId(id);sale.setSaleDate(LocalDateTime.now());
                if(prescription!=null && !prescribedItems.isEmpty())new PrescriptionService().recordDispensing(con,sale,prescription,prescribedItems);
                con.commit();
                AuditLogger.logSale(id,sale.getGrandTotal(),sale.getPaymentMethod());
                return sale;
            }catch(Exception ex){con.rollback();throw ex;}
        }
    }
}
