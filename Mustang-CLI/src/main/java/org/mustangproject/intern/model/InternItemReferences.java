/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.model;

/**
 * Referenzen einer Position
 */
public class InternItemReferences {
    private String originalInvoice;
    private String originalInvoicePosition;
    private String order;
    private String orderPosition;
    private String deliveryNote;

    // Getter und Setter
    public String getOriginalInvoice() {
        return originalInvoice;
    }

    public void setOriginalInvoice(String originalInvoice) {
        this.originalInvoice = originalInvoice;
    }

    public String getOriginalInvoicePosition() {
        return originalInvoicePosition;
    }

    public void setOriginalInvoicePosition(String originalInvoicePosition) {
        this.originalInvoicePosition = originalInvoicePosition;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public String getOrderPosition() {
        return orderPosition;
    }

    public void setOrderPosition(String orderPosition) {
        this.orderPosition = orderPosition;
    }

    public String getDeliveryNote() {
        return deliveryNote;
    }

    public void setDeliveryNote(String deliveryNote) {
        this.deliveryNote = deliveryNote;
    }
}