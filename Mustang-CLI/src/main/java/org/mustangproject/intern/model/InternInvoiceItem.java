/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Rechnungsposition
 */
public class InternInvoiceItem {
    private InternItemAmounts amounts;
    private InternItemMasterData masterData;
    private InternItemText text;
    private InternItemReferences references;
    private InternItemSpecialFlags specialFlags;
    private LocalDate date;
    private Boolean dontPrint;
    private Boolean dontPrintPrice;
    private Boolean printPosition;
    private String inventory;
    private Boolean isBom;
    private LocalDate serviceDate;
    private BigDecimal quantity;
    private String position;
    private String unit;
    private String materialCostType;
    private BigDecimal materialCostAmount;
    
    private List<InternInvoiceSubItem> subItems;

    public InternInvoiceItem() {
        this.amounts = new InternItemAmounts();
        this.masterData = new InternItemMasterData();
        this.text = new InternItemText();
        this.references = new InternItemReferences();
        this.specialFlags = new InternItemSpecialFlags();
        this.subItems = new ArrayList<>();
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

    public InternItemReferences getReferences() {
        return references;
    }

    public void setReferences(InternItemReferences references) {
        this.references = references;
    }

    public InternItemSpecialFlags getSpecialFlags() {
        return specialFlags;
    }

    public void setSpecialFlags(InternItemSpecialFlags specialFlags) {
        this.specialFlags = specialFlags;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Boolean getDontPrint() {
        return dontPrint;
    }

    public void setDontPrint(Boolean dontPrint) {
        this.dontPrint = dontPrint;
    }

    public Boolean getDontPrintPrice() {
        return dontPrintPrice;
    }

    public void setDontPrintPrice(Boolean dontPrintPrice) {
        this.dontPrintPrice = dontPrintPrice;
    }

    public Boolean getPrintPosition() {
        return printPosition;
    }

    public void setPrintPosition(Boolean printPosition) {
        this.printPosition = printPosition;
    }

    public String getInventory() {
        return inventory;
    }

    public void setInventory(String inventory) {
        this.inventory = inventory;
    }

    public Boolean getIsBom() {
        return isBom;
    }

    public void setIsBom(Boolean isBom) {
        this.isBom = isBom;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(LocalDate serviceDate) {
        this.serviceDate = serviceDate;
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

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getMaterialCostType() {
        return materialCostType;
    }

    public void setMaterialCostType(String materialCostType) {
        this.materialCostType = materialCostType;
    }

    public BigDecimal getMaterialCostAmount() {
        return materialCostAmount;
    }

    public void setMaterialCostAmount(BigDecimal materialCostAmount) {
        this.materialCostAmount = materialCostAmount;
    }

    public List<InternInvoiceSubItem> getSubItems() {
        return subItems;
    }

    public void setSubItems(List<InternInvoiceSubItem> subItems) {
        this.subItems = subItems;
    }
    
    public void addSubItem(InternInvoiceSubItem subItem) {
        this.subItems.add(subItem);
    }
}