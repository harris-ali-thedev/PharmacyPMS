package com.pharmacy.service;

import com.pharmacy.model.Prescription;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowValidationTest {
    private Prescription rx() {
        Prescription rx=new Prescription();rx.setExpiryDate(LocalDate.now().plusDays(10));
        var item=new Prescription.PrescriptionItem();item.setMedId(1);item.setQtyPrescribed(10);item.setDosage("1 tablet");item.setFrequency("Daily");
        rx.setItems(List.of(item));return rx;
    }
    @Test void emptyPrescriptionCannotBeDispensedOrCreated() {
        PrescriptionService service=new PrescriptionService();
        assertThrows(PrescriptionService.PrescriptionException.class,()->service.validateForDispensing(new Prescription()));
        assertThrows(IllegalArgumentException.class,()->service.createPrescription(new Prescription()));
    }
    @Test void partialFillDoesNotConsumeASeparateRefill() {
        var rx=rx();rx.setStatus(Prescription.STATUS_PARTIAL);rx.setRefillsUsed(1);rx.setMaxRefills(0);
        assertDoesNotThrow(()->new PrescriptionService().validateForDispensing(rx));
    }
    @Test void expiredAndCompletedPrescriptionsAreRejected() {
        var expired=rx();expired.setExpiryDate(LocalDate.now().minusDays(1));
        var completed=rx();completed.setStatus(Prescription.STATUS_DISPENSED);
        var service=new PrescriptionService();
        assertThrows(PrescriptionService.PrescriptionException.class,()->service.validateForDispensing(expired));
        assertThrows(PrescriptionService.PrescriptionException.class,()->service.validateForDispensing(completed));
    }
    @Test void invalidCashIsRejectedBeforeDatabaseAccess() {
        var service=new ShiftSessionService();
        assertThrows(IllegalArgumentException.class,()->service.openShift(1,"Cashier",Double.NaN));
        assertThrows(IllegalArgumentException.class,()->service.openShift(1,"Cashier",Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->service.closeShift(1,-1,""));
        assertThrows(IllegalArgumentException.class,()->service.openShift(1,"Cashier",0.001));
    }
    @Test void invalidReceiptCannotChangeStock() {
        var service=new StockService();
        assertThrows(IllegalArgumentException.class,()->service.receive(null,new StockService.ReceiveRequest(1,0,1,1,null,null,"TEST",null,"")));
        assertThrows(IllegalArgumentException.class,()->service.receive(null,new StockService.ReceiveRequest(1,1,-1,1,null,null,"TEST",null,"")));
    }
}
