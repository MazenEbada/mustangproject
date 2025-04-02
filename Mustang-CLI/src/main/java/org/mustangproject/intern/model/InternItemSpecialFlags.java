/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.model;

/**
 * Spezielle Flags einer Position
 */
public class InternItemSpecialFlags {
    private String textPosition;
    private String chapterSum;
    private String subtotal;
    private String subtotalTo;
    private String subtotalFrom;
    private String package_;
    private Boolean isPackagePrice;

    // Getter und Setter
    public String getTextPosition() {
        return textPosition;
    }

    public void setTextPosition(String textPosition) {
        this.textPosition = textPosition;
    }

    public String getChapterSum() {
        return chapterSum;
    }

    public void setChapterSum(String chapterSum) {
        this.chapterSum = chapterSum;
    }

    public String getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(String subtotal) {
        this.subtotal = subtotal;
    }

    public String getSubtotalTo() {
        return subtotalTo;
    }

    public void setSubtotalTo(String subtotalTo) {
        this.subtotalTo = subtotalTo;
    }

    public String getSubtotalFrom() {
        return subtotalFrom;
    }

    public void setSubtotalFrom(String subtotalFrom) {
        this.subtotalFrom = subtotalFrom;
    }

    public String getPackage() {
        return package_;
    }

    public void setPackage(String package_) {
        this.package_ = package_;
    }

    public Boolean getIsPackagePrice() {
        return isPackagePrice;
    }

    public void setIsPackagePrice(Boolean isPackagePrice) {
        this.isPackagePrice = isPackagePrice;
    }
}