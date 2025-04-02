/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.model;

/**
 * Stammdaten einer Position
 */
public class InternItemMasterData {
    private String batch;
    private String countryOfOrigin;
    private String customsTariffNumber;
    private String eanCode;
    private String articleNumber;
    private String customerArticleNumber;

    // Getter und Setter
    public String getBatch() {
        return batch;
    }

    public void setBatch(String batch) {
        this.batch = batch;
    }

    public String getCountryOfOrigin() {
        return countryOfOrigin;
    }

    public void setCountryOfOrigin(String countryOfOrigin) {
        this.countryOfOrigin = countryOfOrigin;
    }

    public String getCustomsTariffNumber() {
        return customsTariffNumber;
    }

    public void setCustomsTariffNumber(String customsTariffNumber) {
        this.customsTariffNumber = customsTariffNumber;
    }

    public String getEanCode() {
        return eanCode;
    }

    public void setEanCode(String eanCode) {
        this.eanCode = eanCode;
    }

    public String getArticleNumber() {
        return articleNumber;
    }

    public void setArticleNumber(String articleNumber) {
        this.articleNumber = articleNumber;
    }

    public String getCustomerArticleNumber() {
        return customerArticleNumber;
    }

    public void setCustomerArticleNumber(String customerArticleNumber) {
        this.customerArticleNumber = customerArticleNumber;
    }
}