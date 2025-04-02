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
 * Zahlungsbedingungen
 */
public class InternPaymentTerms {
    private String paymentTermsText;
    private LocalDate valueDate;
    private Map<String, String> additionalData;

    public InternPaymentTerms() {
        this.additionalData = new HashMap<>();
    }

    // Getter und Setter
    public String getPaymentTermsText() {
        return paymentTermsText;
    }

    public void setPaymentTermsText(String paymentTermsText) {
        this.paymentTermsText = paymentTermsText;
    }

    public LocalDate getValueDate() {
        return valueDate;
    }

    public void setValueDate(LocalDate valueDate) {
        this.valueDate = valueDate;
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