/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.model;

import java.math.BigDecimal;

/**
 * Beträge einer Rechnungsposition
 */
public class InternItemAmounts {
    private BigDecimal revenue;
    private BigDecimal netRevenue;
    private BigDecimal gross;
    private BigDecimal net;
    private BigDecimal netApplication;
    private BigDecimal packageQuantity;
    private String pricePerUnit;
    private BigDecimal price;
    private BigDecimal quantityDiscount;
    private BigDecimal quantityDiscountAmount;
    private BigDecimal discount;
    private BigDecimal discount2;
    private BigDecimal discountAmount;
    private BigDecimal discountAmount2;
    private BigDecimal unitRevenue;
    private InternItemTax tax;

    public InternItemAmounts() {
        this.tax = new InternItemTax();
    }

    // Getter und Setter
    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }

    public BigDecimal getNetRevenue() {
        return netRevenue;
    }

    public void setNetRevenue(BigDecimal netRevenue) {
        this.netRevenue = netRevenue;
    }

    public BigDecimal getGross() {
        return gross;
    }

    public void setGross(BigDecimal gross) {
        this.gross = gross;
    }

    public BigDecimal getNet() {
        return net;
    }

    public void setNet(BigDecimal net) {
        this.net = net;
    }

    public BigDecimal getNetApplication() {
        return netApplication;
    }

    public void setNetApplication(BigDecimal netApplication) {
        this.netApplication = netApplication;
    }

    public BigDecimal getPackageQuantity() {
        return packageQuantity;
    }

    public void setPackageQuantity(BigDecimal packageQuantity) {
        this.packageQuantity = packageQuantity;
    }

    public String getPricePerUnit() {
        return pricePerUnit;
    }

    public void setPricePerUnit(String pricePerUnit) {
        this.pricePerUnit = pricePerUnit;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getQuantityDiscount() {
        return quantityDiscount;
    }

    public void setQuantityDiscount(BigDecimal quantityDiscount) {
        this.quantityDiscount = quantityDiscount;
    }

    public BigDecimal getQuantityDiscountAmount() {
        return quantityDiscountAmount;
    }

    public void setQuantityDiscountAmount(BigDecimal quantityDiscountAmount) {
        this.quantityDiscountAmount = quantityDiscountAmount;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getDiscount2() {
        return discount2;
    }

    public void setDiscount2(BigDecimal discount2) {
        this.discount2 = discount2;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getDiscountAmount2() {
        return discountAmount2;
    }

    public void setDiscountAmount2(BigDecimal discountAmount2) {
        this.discountAmount2 = discountAmount2;
    }

    public BigDecimal getUnitRevenue() {
        return unitRevenue;
    }

    public void setUnitRevenue(BigDecimal unitRevenue) {
        this.unitRevenue = unitRevenue;
    }

    public InternItemTax getTax() {
        return tax;
    }

    public void setTax(InternItemTax tax) {
        this.tax = tax;
    }
}