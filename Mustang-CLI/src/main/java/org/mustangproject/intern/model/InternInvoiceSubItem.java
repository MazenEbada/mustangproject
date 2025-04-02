/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Unterposition einer Rechnungsposition
 */
public class InternInvoiceSubItem {
    private InternItemAmounts amounts;
    private InternItemMasterData masterData;
    private InternItemText text;
    private LocalDate date;
    private String dontCalculate;
    private String dontPrint;
    private String dontPrintPrice;
    private BigDecimal quantity;
    private String position;
    private String subPosition;
    private String unit;

    public InternInvoiceSubItem() {
        this.amounts = new InternItemAmounts();
        this.masterData = new InternItemMasterData();
        this.text = new InternItemText();
    }

    // Getter und Setter
    public InternItemAmounts getAmounts() {
        return amounts;
    }

    public void setAmounts(InternItemAmounts amounts) {
        this.amounts = amounts;
    }

    public InternItemMasterData getMasterData() {
        return masterData;
    }

    public void setMasterData(InternItemMasterData masterData) {
        this.masterData = masterData;
    }

    public InternItemText getText() {
        return text;
    }

    public void setText(InternItemText text) {
        this.text = text;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDontCalculate() {
        return dontCalculate;
    }

    public void setDontCalculate(String dontCalculate) {
        this.dontCalculate = dontCalculate;
    }

    public String getDontPrint() {
        return dontPrint;
    }

    public void setDontPrint(String dontPrint) {
        this.dontPrint = dontPrint;
    }

    public String getDontPrintPrice() {
        return dontPrintPrice;
    }

    public void setDontPrintPrice(String dontPrintPrice) {
        this.dontPrintPrice = dontPrintPrice;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getSubPosition() {
        return subPosition;
    }

    public void setSubPosition(String subPosition) {
        this.subPosition = subPosition;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}