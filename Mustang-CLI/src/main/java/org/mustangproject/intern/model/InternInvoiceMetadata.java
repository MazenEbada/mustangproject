/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.model;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Metadaten einer Rechnung
 */
public class InternInvoiceMetadata {
    private String invoiceNumber;
    private String invoiceType;
    private String invoiceTypePa;
    private String currency;
    private String customerOrderNumber;
    private LocalDate orderDate;
    private String originalInvoice;
    private String orderNumber;
    private String language;
    private LocalDate deliveryDate;
    private Map<String, String> additionalData;

    public InternInvoiceMetadata() {
        this.additionalData = new HashMap<>();
    }

    // Getter und Setter
    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getInvoiceType() {
        return invoiceType;
    }

    public void setInvoiceType(String invoiceType) {
        this.invoiceType = invoiceType;
    }

    public String getInvoiceTypePa() {
        return invoiceTypePa;
    }

    public void setInvoiceTypePa(String invoiceTypePa) {
        this.invoiceTypePa = invoiceTypePa;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCustomerOrderNumber() {
        return customerOrderNumber;
    }

    public void setCustomerOrderNumber(String customerOrderNumber) {
        this.customerOrderNumber = customerOrderNumber;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public String getOriginalInvoice() {
        return originalInvoice;
    }

    public void setOriginalInvoice(String originalInvoice) {
        this.originalInvoice = originalInvoice;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public Map<String, String> getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(Map<String, String> additionalData) {
        this.additionalData = additionalData;
    }

    public void addAdditionalData(String key, String value) {
        this.additionalData.put(key, value);
    }
}