/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.model;

import java.math.BigDecimal;

/**
 * Steuerinformationen einer Position
 */
public class InternItemTax {
    private BigDecimal taxAmount;
    private String taxCategory;
    private BigDecimal taxRate;
    // CUSTOM:AMC/MEB:23.11.2026:V2.9 - Steuertext aus dem USt-Satz (USTSATZPOS.STDTXT_AR), wie im Rechnungsdruck
    private String taxText;

    // Getter und Setter
    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public String getTaxCategory() {
        return taxCategory;
    }

    public void setTaxCategory(String taxCategory) {
        this.taxCategory = taxCategory;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public String getTaxText() {
        return taxText;
    }

    public void setTaxText(String taxText) {
        this.taxText = taxText;
    }
}