/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.model;

import java.util.ArrayList;
import java.util.List;

import org.mustangproject.intern.export.ExportFormat;
import org.mustangproject.intern.export.InvoiceExporterFactory;

/**
 * Hauptklasse für eine Rechnung
 */
public class InternInvoice {
    // Adressdaten
    private InternAddress sellerAddress;
    private InternAddress customerAddress;
    private InternAddress deliveryAddress;
    private InternAddress manualDeliveryAddress;
    private InternAddress invoiceAddress;
    private InternPerson processor;
    
    // Rechnungsdaten
    private InternInvoiceMetadata metadata;
    private InternInvoiceAmounts amounts;
    private InternPaymentTerms paymentTerms;
    private InternTax tax;
    private InternShippingCosts shippingCosts;
    private InternInvoiceTexts texts;
    private InternEInvoiceData eInvoiceData;
    
    // Rechnungspositionen
    private List<InternInvoiceItem> items;

    // Default-Konstruktor
    public InternInvoice() {
        this.sellerAddress = new InternAddress();
        this.customerAddress = new InternAddress();
        this.deliveryAddress = new InternAddress();
        this.manualDeliveryAddress = new InternAddress();
        this.invoiceAddress = new InternAddress();
        this.processor = new InternPerson();
        
        this.metadata = new InternInvoiceMetadata();
        this.amounts = new InternInvoiceAmounts();
        this.paymentTerms = new InternPaymentTerms();
        this.tax = new InternTax();
        this.shippingCosts = new InternShippingCosts();
        this.texts = new InternInvoiceTexts();
        this.eInvoiceData = new InternEInvoiceData();
        
        this.items = new ArrayList<>();
    }

    // Getter und Setter
    public InternAddress getSellerAddress() {
        return sellerAddress;
    }

    public void setSellerAddress(InternAddress sellerAddress) {
        this.sellerAddress = sellerAddress;
    }

    public InternAddress getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(InternAddress customerAddress) {
        this.customerAddress = customerAddress;
    }

    public InternAddress getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(InternAddress deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public InternAddress getManualDeliveryAddress() {
        return manualDeliveryAddress;
    }

    public void setManualDeliveryAddress(InternAddress manualDeliveryAddress) {
        this.manualDeliveryAddress = manualDeliveryAddress;
    }

    public InternAddress getInvoiceAddress() {
        return invoiceAddress;
    }

    public void setInvoiceAddress(InternAddress invoiceAddress) {
        this.invoiceAddress = invoiceAddress;
    }

    public InternPerson getProcessor() {
        return processor;
    }

    public void setProcessor(InternPerson processor) {
        this.processor = processor;
    }

    public InternInvoiceMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(InternInvoiceMetadata metadata) {
        this.metadata = metadata;
    }

    public InternInvoiceAmounts getAmounts() {
        return amounts;
    }

    public void setAmounts(InternInvoiceAmounts amounts) {
        this.amounts = amounts;
    }

    public InternPaymentTerms getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(InternPaymentTerms paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public InternTax getTax() {
        return tax;
    }

    public void setTax(InternTax tax) {
        this.tax = tax;
    }

    public InternShippingCosts getShippingCosts() {
        return shippingCosts;
    }

    public void setShippingCosts(InternShippingCosts shippingCosts) {
        this.shippingCosts = shippingCosts;
    }

    public InternInvoiceTexts getTexts() {
        return texts;
    }

    public void setTexts(InternInvoiceTexts texts) {
        this.texts = texts;
    }

    public InternEInvoiceData getEInvoiceData() {
        return eInvoiceData;
    }

    public void setEInvoiceData(InternEInvoiceData eInvoiceData) {
        this.eInvoiceData = eInvoiceData;
    }

    public List<InternInvoiceItem> getItems() {
        return items;
    }

    public void setItems(List<InternInvoiceItem> items) {
        this.items = items;
    }

    public void addItem(InternInvoiceItem item) {
        this.items.add(item);
    }
    
    /**
     * Exportiert diese InternInvoice in das gewünschte Format
     * 
     * @param format Das gewünschte Exportformat
     * @return Ein String im gewünschten Format
     * @throws Exception Bei Export-Fehlern
     */
    public String export(ExportFormat format) throws Exception {
        // Erstelle den passenden Exporter über die Factory
        return InvoiceExporterFactory.createExporter(format).export(this);
    }
}