/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.worker;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

import org.mustangproject.intern.model.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * JSON-Worker zur direkten Verarbeitung von JSON-Eingabedaten in ein InternInvoice-Objekt
 */
public class JSONInvoiceWorkerImpl implements JSONInvoiceWorker {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public InternInvoice processInput(String inputData, Map<String, String> conversionKeys) throws Exception {
        try {
            // Parse JSON input string
            JsonNode rootNode = objectMapper.readTree(inputData);
            
            // Create InternInvoice object
            InternInvoice invoice = new InternInvoice();
            
            // Extract and set addresses
            extractSellerAddress(rootNode, invoice);
            extractCustomerAddress(rootNode, invoice);
            extractDeliveryAddress(rootNode, invoice);
            extractInvoiceAddress(rootNode, invoice);
            
            // Extract and set processor information
            extractProcessor(rootNode, invoice, conversionKeys.get("PERSONALDATA"));
            
            // Extract and set texts
            extractTexts(rootNode, invoice);
            
            // Extract and set payment terms
            extractPaymentTerms(rootNode, invoice, conversionKeys.get("ZBDETAILS"));
            
            // Extract and set e-invoice data
            extractEInvoiceData(rootNode, invoice);
            
            // Extract and set dates
            extractDates(rootNode, invoice);
            
            // Extract and set amounts
            extractAmounts(rootNode, invoice);
            
            // Extract and set tax information
            extractTax(rootNode, invoice);
            
            // Extract and set shipping costs
            extractShippingCosts(rootNode, invoice);
            
            // Extract and set additional invoice data
            extractAdditionalInvoiceData(rootNode, invoice);
            
            // Extract and set invoice items
            extractInvoiceItems(rootNode, invoice);
            
            return invoice;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * Extracts the seller address from the JSON and sets it in the Invoice object
     */
    private void extractSellerAddress(JsonNode rootNode, InternInvoice invoice) {
        JsonNode sellerAddressNode = rootNode.path("seller_address");
        JsonNode sellerBankNode = rootNode.path("seller_bank");
        
        InternAddress address = invoice.getSellerAddress();
        
        if (!sellerAddressNode.isMissingNode()) {
            address.setGlnId(getStringValue(sellerAddressNode, "gln_id"));
            address.setCompanyName1(getStringValue(sellerAddressNode, "company_name_1"));
            address.setCompanyName2(getStringValue(sellerAddressNode, "company_name_2"));
            address.setCompanyName3(getStringValue(sellerAddressNode, "company_name_3"));
            address.setCountryIso(getStringValue(sellerAddressNode, "country_iso"));
            address.setName(getStringValue(sellerAddressNode, "name"));
            address.setDepartment(getStringValue(sellerAddressNode, "department"));
            address.setCity(getStringValue(sellerAddressNode, "city"));
            address.setPostalCode(getStringValue(sellerAddressNode, "postal_code"));
            address.setPostalCode2(getStringValue(sellerAddressNode, "postal_code_2"));
            address.setStreet(getStringValue(sellerAddressNode, "street"));
            address.setFax(getStringValue(sellerAddressNode, "fax"));
            address.setPhone(getStringValue(sellerAddressNode, "phone"));
            address.setEmail(getStringValue(sellerAddressNode, "email"));
            address.setDunsNumber(getStringValue(sellerAddressNode, "duns_number"));
            address.setCommercialRegister(getStringValue(sellerAddressNode, "commercial_register"));
            address.setManagingDirector1(getStringValue(sellerAddressNode, "managing_director_1"));
            address.setManagingDirector2(getStringValue(sellerAddressNode, "managing_director_2"));
            
            // Set VAT ID with fallback logic
            String vatId = getStringValue(sellerAddressNode, "vat_id");
            if (vatId == null || vatId.isEmpty()) {
                vatId = getStringValue(sellerAddressNode, "eg_steuer_nr");
            }
            address.setVatId(vatId);
            
            address.setTaxNumber(getStringValue(sellerAddressNode, "tax_number"));
        }
        
        if (!sellerBankNode.isMissingNode()) {
            address.setBic(getStringValue(sellerBankNode, "bic"));
            address.setIban(getStringValue(sellerBankNode, "iban"));
            address.setPaymentMethods(getStringValue(sellerBankNode, "payment_methods"));
        }
    }
    
    /**
     * Extracts the customer address from the JSON and sets it in the Invoice object
     */
    private void extractCustomerAddress(JsonNode rootNode, InternInvoice invoice) {
        JsonNode customerAddressNode = rootNode.path("buyer_address");
        JsonNode customerBankNode = rootNode.path("buyer_bank");
        
        InternAddress address = invoice.getCustomerAddress();
        
        if (!customerAddressNode.isMissingNode()) {
            address.setGlnId(getStringValue(customerAddressNode, "gln_id"));
            address.setCompanyName1(getStringValue(customerAddressNode, "company_name_1"));
            address.setCompanyName2(getStringValue(customerAddressNode, "company_name_2"));
            address.setCompanyName3(getStringValue(customerAddressNode, "company_name_3"));
            address.setCountryIso(getStringValue(customerAddressNode, "country_iso"));
            address.setName(getStringValue(customerAddressNode, "name"));
            address.setDepartment(getStringValue(customerAddressNode, "department"));
            address.setCity(getStringValue(customerAddressNode, "city"));
            address.setPostalCode(getStringValue(customerAddressNode, "postal_code"));
            address.setPostalCode2(getStringValue(customerAddressNode, "postal_code_2"));
            address.setStreet(getStringValue(customerAddressNode, "street"));
            address.setFax(getStringValue(customerAddressNode, "fax"));
            address.setPhone(getStringValue(customerAddressNode, "phone"));
            address.setEmail(getStringValue(customerAddressNode, "email"));
            address.setDunsNumber(getStringValue(customerAddressNode, "duns_number"));
            address.setCommercialRegister(getStringValue(customerAddressNode, "commercial_register"));
            address.setManagingDirector1(getStringValue(customerAddressNode, "managing_director_1"));
            address.setManagingDirector2(getStringValue(customerAddressNode, "managing_director_2"));
            
            // Set VAT ID with fallback logic
            String vatId = getStringValue(customerAddressNode, "vat_id");
            if (vatId == null || vatId.isEmpty()) {
                vatId = getStringValue(customerAddressNode, "eg_steuer_nr");
            }
            address.setVatId(vatId);
            
            address.setTaxNumber(getStringValue(customerAddressNode, "tax_number"));
        }
        
        if (!customerBankNode.isMissingNode()) {
            address.setBic(getStringValue(customerBankNode, "bic"));
            address.setIban(getStringValue(customerBankNode, "iban"));
            address.setPaymentMethods(getStringValue(customerBankNode, "payment_methods"));
        }
    }
    
    /**
     * Extracts the delivery address from the JSON and sets it in the Invoice object
     * With fallback to the customer address if no specific delivery address is provided
     */
    private void extractDeliveryAddress(JsonNode rootNode, InternInvoice invoice) {
        JsonNode deliveryAddressNode = rootNode.path("delivery_address");
        
        InternAddress address = invoice.getDeliveryAddress();
        
        if (!deliveryAddressNode.isMissingNode() && deliveryAddressNode.isObject() && deliveryAddressNode.size() > 0) {
            // Delivery address available, extract directly
            address.setGlnId(getStringValue(deliveryAddressNode, "gln_id"));
            address.setCompanyName1(getStringValue(deliveryAddressNode, "company_name_1"));
            address.setCompanyName2(getStringValue(deliveryAddressNode, "company_name_2"));
            address.setCompanyName3(getStringValue(deliveryAddressNode, "company_name_3"));
            address.setCountryIso(getStringValue(deliveryAddressNode, "country_iso"));
            address.setName(getStringValue(deliveryAddressNode, "name"));
            address.setDepartment(getStringValue(deliveryAddressNode, "department"));
            address.setCity(getStringValue(deliveryAddressNode, "city"));
            address.setPostalCode(getStringValue(deliveryAddressNode, "postal_code"));
            address.setPostalCode2(getStringValue(deliveryAddressNode, "postal_code_2"));
            address.setStreet(getStringValue(deliveryAddressNode, "street"));
            address.setFax(getStringValue(deliveryAddressNode, "fax"));
            address.setPhone(getStringValue(deliveryAddressNode, "phone"));
            address.setEmail(getStringValue(deliveryAddressNode, "email"));
            address.setDunsNumber(getStringValue(deliveryAddressNode, "duns_number"));
            address.setCommercialRegister(getStringValue(deliveryAddressNode, "commercial_register"));
            address.setManagingDirector1(getStringValue(deliveryAddressNode, "managing_director_1"));
            address.setManagingDirector2(getStringValue(deliveryAddressNode, "managing_director_2"));
            
            // Set VAT ID with fallback logic
            String vatId = getStringValue(deliveryAddressNode, "vat_id");
            if (vatId == null || vatId.isEmpty()) {
                vatId = getStringValue(deliveryAddressNode, "eg_steuer_nr");
            }
            address.setVatId(vatId);
            
            address.setTaxNumber(getStringValue(deliveryAddressNode, "tax_number"));
            address.setBic(getStringValue(deliveryAddressNode, "bic"));
            address.setIban(getStringValue(deliveryAddressNode, "iban"));
            address.setPaymentMethods(getStringValue(deliveryAddressNode, "payment_methods"));
        } else {
            // No delivery address available, use customer address as fallback
            InternAddress customerAddress = invoice.getCustomerAddress();
            
            // Copy all fields from customerAddress to deliveryAddress
            address.setGlnId(customerAddress.getGlnId());
            address.setCompanyName1(customerAddress.getCompanyName1());
            address.setCompanyName2(customerAddress.getCompanyName2());
            address.setCompanyName3(customerAddress.getCompanyName3());
            address.setCountryIso(customerAddress.getCountryIso());
            address.setName(customerAddress.getName());
            address.setDepartment(customerAddress.getDepartment());
            address.setCity(customerAddress.getCity());
            address.setPostalCode(customerAddress.getPostalCode());
            address.setPostalCode2(customerAddress.getPostalCode2());
            address.setStreet(customerAddress.getStreet());
            address.setFax(customerAddress.getFax());
            address.setPhone(customerAddress.getPhone());
            address.setEmail(customerAddress.getEmail());
            address.setDunsNumber(customerAddress.getDunsNumber());
            address.setVatId(customerAddress.getVatId());
            address.setCommercialRegister(customerAddress.getCommercialRegister());
            address.setManagingDirector1(customerAddress.getManagingDirector1());
            address.setManagingDirector2(customerAddress.getManagingDirector2());
            address.setTaxNumber(customerAddress.getTaxNumber());
            address.setBic(customerAddress.getBic());
            address.setIban(customerAddress.getIban());
            address.setPaymentMethods(customerAddress.getPaymentMethods());
        }
    }
    
    /**
     * Extracts the invoice address from the JSON and sets it in the Invoice object
     * With fallback to the customer address if no specific invoice address is provided
     */
    private void extractInvoiceAddress(JsonNode rootNode, InternInvoice invoice) {
        JsonNode invoiceAddressNode = rootNode.path("invoice_address");
        
        InternAddress address = invoice.getInvoiceAddress();
        
        if (!invoiceAddressNode.isMissingNode() && invoiceAddressNode.isObject() && invoiceAddressNode.size() > 0) {
            // Invoice address available, extract directly
            address.setGlnId(getStringValue(invoiceAddressNode, "gln_id"));
            address.setCompanyName1(getStringValue(invoiceAddressNode, "company_name_1"));
            address.setCompanyName2(getStringValue(invoiceAddressNode, "company_name_2"));
            address.setCompanyName3(getStringValue(invoiceAddressNode, "company_name_3"));
            address.setCountryIso(getStringValue(invoiceAddressNode, "country_iso"));
            address.setName(getStringValue(invoiceAddressNode, "name"));
            address.setDepartment(getStringValue(invoiceAddressNode, "department"));
            address.setCity(getStringValue(invoiceAddressNode, "city"));
            address.setPostalCode(getStringValue(invoiceAddressNode, "postal_code"));
            address.setPostalCode2(getStringValue(invoiceAddressNode, "postal_code_2"));
            address.setStreet(getStringValue(invoiceAddressNode, "street"));
            address.setFax(getStringValue(invoiceAddressNode, "fax"));
            address.setPhone(getStringValue(invoiceAddressNode, "phone"));
            address.setEmail(getStringValue(invoiceAddressNode, "email"));
            address.setDunsNumber(getStringValue(invoiceAddressNode, "duns_number"));
            address.setCommercialRegister(getStringValue(invoiceAddressNode, "commercial_register"));
            address.setManagingDirector1(getStringValue(invoiceAddressNode, "managing_director_1"));
            address.setManagingDirector2(getStringValue(invoiceAddressNode, "managing_director_2"));
            
            // Set VAT ID with fallback logic
            String vatId = getStringValue(invoiceAddressNode, "vat_id");
            if (vatId == null || vatId.isEmpty()) {
                vatId = getStringValue(invoiceAddressNode, "eg_steuer_nr");
            }
            address.setVatId(vatId);
            
            address.setTaxNumber(getStringValue(invoiceAddressNode, "tax_number"));
            address.setBic(getStringValue(invoiceAddressNode, "bic"));
            address.setIban(getStringValue(invoiceAddressNode, "iban"));
            address.setPaymentMethods(getStringValue(invoiceAddressNode, "payment_methods"));
        } else {
            // No invoice address available, use customer address as fallback
            InternAddress customerAddress = invoice.getCustomerAddress();
            
            // Copy all fields from customerAddress to invoiceAddress
            address.setGlnId(customerAddress.getGlnId());
            address.setCompanyName1(customerAddress.getCompanyName1());
            address.setCompanyName2(customerAddress.getCompanyName2());
            address.setCompanyName3(customerAddress.getCompanyName3());
            address.setCountryIso(customerAddress.getCountryIso());
            address.setName(customerAddress.getName());
            address.setDepartment(customerAddress.getDepartment());
            address.setCity(customerAddress.getCity());
            address.setPostalCode(customerAddress.getPostalCode());
            address.setPostalCode2(customerAddress.getPostalCode2());
            address.setStreet(customerAddress.getStreet());
            address.setFax(customerAddress.getFax());
            address.setPhone(customerAddress.getPhone());
            address.setEmail(customerAddress.getEmail());
            address.setDunsNumber(customerAddress.getDunsNumber());
            address.setVatId(customerAddress.getVatId());
            address.setCommercialRegister(customerAddress.getCommercialRegister());
            address.setManagingDirector1(customerAddress.getManagingDirector1());
            address.setManagingDirector2(customerAddress.getManagingDirector2());
            address.setTaxNumber(customerAddress.getTaxNumber());
            address.setBic(customerAddress.getBic());
            address.setIban(customerAddress.getIban());
            address.setPaymentMethods(customerAddress.getPaymentMethods());
        }
    }
    
    /**
     * Extracts the processor data from the JSON and sets it in the Invoice object
     */
    private void extractProcessor(JsonNode rootNode, InternInvoice invoice, String personaldata) {
        JsonNode processorNode = rootNode.path("processor");
        
        InternPerson processor = invoice.getProcessor();
        
        if (!processorNode.isMissingNode()) {
            processor.setName(getStringValue(processorNode, "name"));
            
            if (personaldata != null && personaldata.equalsIgnoreCase("PERSONAL")) {
                processor.setEmail(getStringValue(processorNode, "email"));
                processor.setPhone(getStringValue(processorNode, "phone"));
                processor.setFax(getStringValue(processorNode, "fax"));
                processor.setDepartment(getStringValue(processorNode, "department"));
            } else {
                // Use address data if personaldata is not set to "PERSONAL"
                JsonNode addressNode = processorNode.path("address");
                if (!addressNode.isMissingNode()) {
                    processor.setEmail(getStringValue(addressNode, "email"));
                    processor.setPhone(getStringValue(addressNode, "phone"));
                    processor.setFax(getStringValue(addressNode, "fax"));
                    processor.setDepartment(getStringValue(addressNode, "department"));
                }
            }
        }
    }
    
    /**
     * Extracts the text data from the JSON and sets it in the Invoice object
     */
    private void extractTexts(JsonNode rootNode, InternInvoice invoice) {
        JsonNode textsNode = rootNode.path("texts");
        
        InternInvoiceTexts texts = invoice.getTexts();
        
        if (!textsNode.isMissingNode()) {
            texts.setStandardText(getStringValue(textsNode, "standard_text"));
            texts.setFreeText(getStringValue(textsNode, "free_text"));
            texts.setCustomerText(getStringValue(textsNode, "customer_text"));
            texts.setFooterText(getStringValue(textsNode, "footer_text"));
            texts.setHeaderText(getStringValue(textsNode, "header_text"));
        }
    }
    
    /**
     * Extracts the payment terms from the JSON and sets them in the Invoice object
     */
    private void extractPaymentTerms(JsonNode rootNode, InternInvoice invoice, String zbDetails) {
        JsonNode paymentTermsNode = rootNode.path("payment_terms");
        
        InternPaymentTerms paymentTerms = invoice.getPaymentTerms();
        
        if (!paymentTermsNode.isMissingNode()) {
            paymentTerms.setPaymentTermsText(getStringValue(paymentTermsNode, "payment_terms_text"));
            
            String valueDateStr = getStringValue(paymentTermsNode, "value_date");
            if (valueDateStr != null && !valueDateStr.isEmpty()) {
                try {
                    paymentTerms.setValueDate(parseDate(valueDateStr));
                } catch (DateTimeParseException e) {
                    // Ignore date parsing errors
                }
            }
            
            // Process additional payment details if available
            if (zbDetails != null && !zbDetails.isEmpty()) {
                try {
                    JsonNode zbDetailsNode = objectMapper.readTree(zbDetails);
                    zbDetailsNode.fields().forEachRemaining(entry -> {
                        paymentTerms.addAdditionalData(entry.getKey().toUpperCase(), entry.getValue().asText());
                    });
                } catch (JsonProcessingException e) {
                    // Ignore JSON parsing errors
                }
            }
            
            // Process additional data from payment_terms
            JsonNode additionalDataNode = paymentTermsNode.path("additional_data");
            if (!additionalDataNode.isMissingNode() && additionalDataNode.isObject()) {
                additionalDataNode.fields().forEachRemaining(entry -> {
                    paymentTerms.addAdditionalData(entry.getKey().toUpperCase(), entry.getValue().asText());
                });
            }
        }
    }
    
    /**
     * Extracts the e-invoice data from the JSON and sets it in the Invoice object
     */
    private void extractEInvoiceData(JsonNode rootNode, InternInvoice invoice) {
        JsonNode eInvoiceNode = rootNode.path("e_invoice");
        
        InternEInvoiceData eInvoiceData = invoice.getEInvoiceData();
        
        if (!eInvoiceNode.isMissingNode()) {
            eInvoiceData.setRouteId(getStringValue(eInvoiceNode, "route_id"));
            eInvoiceData.setDispatchMethod(getStringValue(eInvoiceNode, "dispatch_method"));
            eInvoiceData.setInterfaceType(getStringValue(eInvoiceNode, "interface_type"));
        }
    }
    
    /**
     * Extracts the dates from the JSON and sets them in the Invoice object
     */
    private void extractDates(JsonNode rootNode, InternInvoice invoice) {
        InternInvoiceMetadata metadata = invoice.getMetadata();
        
        // Invoice date
        String invoiceDateStr = getStringValue(rootNode, "invoice_date");
        if (invoiceDateStr != null && !invoiceDateStr.isEmpty()) {
            metadata.addAdditionalData("INVOICE_DATE", invoiceDateStr);
        }
        
        // Service date
        String serviceDateStr = getStringValue(rootNode, "service_date");
        if (serviceDateStr != null && !serviceDateStr.isEmpty()) {
            metadata.addAdditionalData("SERVICE_DATE", serviceDateStr);
        }
        
        // Order date
        String orderDateStr = getStringValue(rootNode, "order_date");
        if (orderDateStr != null && !orderDateStr.isEmpty()) {
            try {
                metadata.setOrderDate(parseDate(orderDateStr));
            } catch (DateTimeParseException e) {
                // Ignore date parsing errors
            }
        }
        
        // Delivery date
        String deliveryDateStr = getStringValue(rootNode, "delivery_date");
        if (deliveryDateStr != null && !deliveryDateStr.isEmpty()) {
            try {
                metadata.setDeliveryDate(parseDate(deliveryDateStr));
            } catch (DateTimeParseException e) {
                // Ignore date parsing errors
            }
        }
    }
    
    /**
     * Extracts the amounts from the JSON and sets them in the Invoice object
     */
    private void extractAmounts(JsonNode rootNode, InternInvoice invoice) {
        JsonNode amountsNode = rootNode.path("amounts");
        
        InternInvoiceAmounts amounts = invoice.getAmounts();
        
        if (!amountsNode.isMissingNode()) {
            // Discount amount
            String discountAmountStr = getStringValue(amountsNode, "discount_amount");
            if (discountAmountStr != null && !discountAmountStr.isEmpty()) {
                try {
                    amounts.setDiscountAmount(new BigDecimal(discountAmountStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Net amount
            String netAmountStr = getStringValue(amountsNode, "net_amount");
            if (netAmountStr != null && !netAmountStr.isEmpty()) {
                try {
                    amounts.setNetAmount(new BigDecimal(netAmountStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Tax amount
            String taxAmountStr = getStringValue(amountsNode, "tax_amount");
            if (taxAmountStr != null && !taxAmountStr.isEmpty()) {
                try {
                    amounts.setTaxAmount(new BigDecimal(taxAmountStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Gross amount
            String grossAmountStr = getStringValue(amountsNode, "gross_amount");
            if (grossAmountStr != null && !grossAmountStr.isEmpty()) {
                try {
                    amounts.setGrossAmount(new BigDecimal(grossAmountStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
        }
    }
    
    /**
     * Extracts the tax information from the JSON and sets it in the Invoice object
     */
    private void extractTax(JsonNode rootNode, InternInvoice invoice) {
        JsonNode taxNode = rootNode.path("tax");
        
        InternTax tax = invoice.getTax();
        
        if (!taxNode.isMissingNode()) {
            tax.setTaxCategory(getStringValue(taxNode, "tax_category"));
            
            // Tax amount
            String taxAmountStr = getStringValue(taxNode, "tax_amount");
            if (taxAmountStr != null && !taxAmountStr.isEmpty()) {
                try {
                    tax.setTaxAmount(new BigDecimal(taxAmountStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Tax rate
            String taxRateStr = getStringValue(taxNode, "tax_rate");
            if (taxRateStr != null && !taxRateStr.isEmpty()) {
                try {
                    tax.setTaxRate(new BigDecimal(taxRateStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Tax base
            String taxBaseStr = getStringValue(taxNode, "tax_base");
            if (taxBaseStr != null && !taxBaseStr.isEmpty()) {
                try {
                    tax.setTaxBase(new BigDecimal(taxBaseStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
        }
    }
    
    /**
     * Extracts the shipping costs information from the JSON and sets it in the Invoice object
     */
    private void extractShippingCosts(JsonNode rootNode, InternInvoice invoice) {
        JsonNode shippingCostsNode = rootNode.path("shipping_costs");
        
        InternShippingCosts shippingCosts = invoice.getShippingCosts();
        
        if (!shippingCostsNode.isMissingNode()) {
            // Amount
            String amountStr = getStringValue(shippingCostsNode, "amount");
            if (amountStr != null && !amountStr.isEmpty()) {
                try {
                    shippingCosts.setAmount(new BigDecimal(amountStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Tax category
            shippingCosts.setTaxCategory(getStringValue(shippingCostsNode, "tax_category"));
            
            // Tax rate
            String taxRateStr = getStringValue(shippingCostsNode, "tax_rate");
            if (taxRateStr != null && !taxRateStr.isEmpty()) {
                try {
                    shippingCosts.setTaxRate(new BigDecimal(taxRateStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
        }
    }
    
    /**
     * Extracts additional invoice data from the JSON and sets it in the Invoice object
     */
    private void extractAdditionalInvoiceData(JsonNode rootNode, InternInvoice invoice) {
        InternInvoiceMetadata metadata = invoice.getMetadata();
        
        metadata.setInvoiceNumber(getStringValue(rootNode, "invoice_number"));
        metadata.setInvoiceType(getStringValue(rootNode, "invoice_type"));
        metadata.setInvoiceTypePa(getStringValue(rootNode, "invoice_type_pa"));
        metadata.setCurrency(getStringValue(rootNode, "currency"));
        metadata.setCustomerOrderNumber(getStringValue(rootNode, "customer_order_number"));
        metadata.setOriginalInvoice(getStringValue(rootNode, "original_invoice"));
        metadata.setOrderNumber(getStringValue(rootNode, "order_number"));
        metadata.setLanguage(getStringValue(rootNode, "language"));
        
        // Additional metadata from any metadata object if available
        JsonNode metadataNode = rootNode.path("metadata");
        if (!metadataNode.isMissingNode() && metadataNode.isObject()) {
            metadataNode.fields().forEachRemaining(entry -> {
                metadata.addAdditionalData(entry.getKey().toUpperCase(), entry.getValue().asText());
            });
        }
    }
    
    /**
     * Extracts the invoice items from the JSON and adds them to the Invoice object
     */
    private void extractInvoiceItems(JsonNode rootNode, InternInvoice invoice) {
        JsonNode itemsNode = rootNode.path("invoice_items");
        
        if (!itemsNode.isMissingNode() && itemsNode.isArray()) {
            ArrayNode itemsArray = (ArrayNode) itemsNode;
            
            for (JsonNode itemNode : itemsArray) {
                InternInvoiceItem item = new InternInvoiceItem();
                
                // Extract amounts
                extractItemAmounts(itemNode, item.getAmounts());
                
                // Extract master data
                extractItemMasterData(itemNode, item.getMasterData());
                
                // Extract text data
                extractItemText(itemNode, item.getText());
                
                // Extract references
                extractItemReferences(itemNode, item.getReferences());
                
                // Extract special flags
                extractItemSpecialFlags(itemNode, item.getSpecialFlags());
                
                // Extract additional data
                extractItemAdditionalData(itemNode, item);
                
                // Extract sub-items if available
                JsonNode subItemsNode = itemNode.path("sub_items");
                if (!subItemsNode.isMissingNode() && subItemsNode.isArray()) {
                    List<InternInvoiceSubItem> subItems = new ArrayList<>();
                    
                    for (JsonNode subItemNode : subItemsNode) {
                        InternInvoiceSubItem subItem = new InternInvoiceSubItem();
                        
                        // Extract amounts
                        extractItemAmounts(subItemNode, subItem.getAmounts());
                        
                        // Extract master data
                        extractItemMasterData(subItemNode, subItem.getMasterData());
                        
                        // Extract text data
                        InternItemText text = subItem.getText();
                        text.setName(getStringValue(subItemNode, "name"));
                        text.setText(getStringValue(subItemNode, "text"));
                        
                        // Extract additional data
                        String dateStr = getStringValue(subItemNode, "date");
                        if (dateStr != null && !dateStr.isEmpty()) {
                            try {
                                subItem.setDate(parseDate(dateStr));
                            } catch (DateTimeParseException e) {
                                // Ignore date parsing errors
                            }
                        }
                        
                        subItem.setDontCalculate(getStringValue(subItemNode, "dont_calculate"));
                        subItem.setDontPrint(getStringValue(subItemNode, "dont_print"));
                        subItem.setDontPrintPrice(getStringValue(subItemNode, "dont_print_price"));
                        
                        String quantityStr = getStringValue(subItemNode, "quantity");
                        if (quantityStr != null && !quantityStr.isEmpty()) {
                            try {
                                subItem.setQuantity(new BigDecimal(quantityStr));
                            } catch (NumberFormatException e) {
                                // Ignore number format errors
                            }
                        }
                        
                        subItem.setPosition(getStringValue(subItemNode, "position"));
                        subItem.setSubPosition(getStringValue(subItemNode, "sub_position"));
                        subItem.setUnit(getStringValue(subItemNode, "unit"));
                        
                        subItems.add(subItem);
                    }
                    
                    item.setSubItems(subItems);
                }
                
                // Add item to invoice
                invoice.addItem(item);
            }
        }
    }
    
    /**
     * Extracts the amounts of an item or sub-item
     */
    private void extractItemAmounts(JsonNode itemNode, InternItemAmounts amounts) {
        JsonNode amountsNode = itemNode.path("amounts");
        
        if (!amountsNode.isMissingNode()) {
            // Revenue
            String revenueStr = getStringValue(amountsNode, "revenue");
            if (revenueStr != null && !revenueStr.isEmpty()) {
                try {
                    amounts.setRevenue(new BigDecimal(revenueStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Net revenue
            String netRevenueStr = getStringValue(amountsNode, "net_revenue");
            if (netRevenueStr != null && !netRevenueStr.isEmpty()) {
                try {
                    amounts.setNetRevenue(new BigDecimal(netRevenueStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Gross
            String grossStr = getStringValue(amountsNode, "gross");
            if (grossStr != null && !grossStr.isEmpty()) {
                try {
                    amounts.setGross(new BigDecimal(grossStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Net
            String netStr = getStringValue(amountsNode, "net");
            if (netStr != null && !netStr.isEmpty()) {
                try {
                    amounts.setNet(new BigDecimal(netStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Net application
            String netApplicationStr = getStringValue(amountsNode, "net_application");
            if (netApplicationStr != null && !netApplicationStr.isEmpty()) {
                try {
                    amounts.setNetApplication(new BigDecimal(netApplicationStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Package quantity
            String packageQuantityStr = getStringValue(amountsNode, "package_quantity");
            if (packageQuantityStr != null && !packageQuantityStr.isEmpty()) {
                try {
                    amounts.setPackageQuantity(new BigDecimal(packageQuantityStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Price per unit
            amounts.setPricePerUnit(getStringValue(amountsNode, "price_per_unit"));
            
            // Price
            String priceStr = getStringValue(amountsNode, "price");
            if (priceStr != null && !priceStr.isEmpty()) {
                try {
                    amounts.setPrice(new BigDecimal(priceStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            } else {
                // Use price from item level if not found in amounts
                priceStr = getStringValue(itemNode, "price");
                if (priceStr != null && !priceStr.isEmpty()) {
                    try {
                        amounts.setPrice(new BigDecimal(priceStr));
                    } catch (NumberFormatException e) {
                        // Ignore number format errors
                    }
                }
            }
            
            // Quantity discount
            String quantityDiscountStr = getStringValue(amountsNode, "quantity_discount");
            if (quantityDiscountStr != null && !quantityDiscountStr.isEmpty()) {
                try {
                    amounts.setQuantityDiscount(new BigDecimal(quantityDiscountStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Quantity discount amount
            String quantityDiscountAmountStr = getStringValue(amountsNode, "quantity_discount_amount");
            if (quantityDiscountAmountStr != null && !quantityDiscountAmountStr.isEmpty()) {
                try {
                    amounts.setQuantityDiscountAmount(new BigDecimal(quantityDiscountAmountStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Discount
            String discountStr = getStringValue(amountsNode, "discount");
            if (discountStr != null && !discountStr.isEmpty()) {
                try {
                    amounts.setDiscount(new BigDecimal(discountStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Discount 2
            String discount2Str = getStringValue(amountsNode, "discount2");
            if (discount2Str != null && !discount2Str.isEmpty()) {
                try {
                    amounts.setDiscount2(new BigDecimal(discount2Str));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Discount amount
            String discountAmountStr = getStringValue(amountsNode, "discount_amount");
            if (discountAmountStr != null && !discountAmountStr.isEmpty()) {
                try {
                    amounts.setDiscountAmount(new BigDecimal(discountAmountStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Discount amount 2
            String discountAmount2Str = getStringValue(amountsNode, "discount_amount2");
            if (discountAmount2Str != null && !discountAmount2Str.isEmpty()) {
                try {
                    amounts.setDiscountAmount2(new BigDecimal(discountAmount2Str));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Unit revenue
            String unitRevenueStr = getStringValue(amountsNode, "unit_revenue");
            if (unitRevenueStr != null && !unitRevenueStr.isEmpty()) {
                try {
                    amounts.setUnitRevenue(new BigDecimal(unitRevenueStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
            
            // Tax information
            JsonNode taxNode = amountsNode.path("tax");
            if (!taxNode.isMissingNode()) {
                InternItemTax tax = amounts.getTax();
                
                // Tax amount
                String taxAmountStr = getStringValue(taxNode, "tax_amount");
                if (taxAmountStr != null && !taxAmountStr.isEmpty()) {
                    try {
                        tax.setTaxAmount(new BigDecimal(taxAmountStr));
                    } catch (NumberFormatException e) {
                        // Ignore number format errors
                    }
                }
                
                // Tax category
                tax.setTaxCategory(getStringValue(taxNode, "tax_category"));
                
                // Tax rate
                String taxRateStr = getStringValue(taxNode, "tax_rate");
                if (taxRateStr != null && !taxRateStr.isEmpty()) {
                    try {
                        tax.setTaxRate(new BigDecimal(taxRateStr));
                    } catch (NumberFormatException e) {
                        // Ignore number format errors
                    }
                }
            } else {
                // Use tax information from item level if not found in amounts
                JsonNode itemTaxNode = itemNode.path("tax");
                if (!itemTaxNode.isMissingNode()) {
                    InternItemTax tax = amounts.getTax();
                    
                    // Tax amount
                    String taxAmountStr = getStringValue(itemTaxNode, "tax_amount");
                    if (taxAmountStr != null && !taxAmountStr.isEmpty()) {
                        try {
                            tax.setTaxAmount(new BigDecimal(taxAmountStr));
                        } catch (NumberFormatException e) {
                            // Ignore number format errors
                        }
                    }
                    
                    // Tax category
                    tax.setTaxCategory(getStringValue(itemTaxNode, "tax_category"));
                    
                    // Tax rate
                    String taxRateStr = getStringValue(itemTaxNode, "tax_rate");
                    if (taxRateStr != null && !taxRateStr.isEmpty()) {
                        try {
                            tax.setTaxRate(new BigDecimal(taxRateStr));
                        } catch (NumberFormatException e) {
                            // Ignore number format errors
                        }
                    }
                }
            }
        } else {
            // Use price from item level if not found in amounts
            String priceStr = getStringValue(itemNode, "price");
            if (priceStr != null && !priceStr.isEmpty()) {
                try {
                    amounts.setPrice(new BigDecimal(priceStr));
                } catch (NumberFormatException e) {
                    // Ignore number format errors
                }
            }
        }
    }
    
    /**
     * Extracts the master data of an item or sub-item
     */
    private void extractItemMasterData(JsonNode itemNode, InternItemMasterData masterData) {
        JsonNode masterDataNode = itemNode.path("master_data");
        
        if (!masterDataNode.isMissingNode()) {
            masterData.setBatch(getStringValue(masterDataNode, "batch"));
            masterData.setCountryOfOrigin(getStringValue(masterDataNode, "country_of_origin"));
            masterData.setCustomsTariffNumber(getStringValue(masterDataNode, "customs_tariff_number"));
            masterData.setEanCode(getStringValue(masterDataNode, "ean_code"));
            masterData.setArticleNumber(getStringValue(masterDataNode, "article_number"));
            masterData.setCustomerArticleNumber(getStringValue(masterDataNode, "customer_article_number"));
        } else {
            // Try to get article number directly from item
            masterData.setArticleNumber(getStringValue(itemNode, "article_number"));
            masterData.setCustomerArticleNumber(getStringValue(itemNode, "customer_article_number"));
        }
    }
    
    /**
     * Extracts the text data of an item
     */
    private void extractItemText(JsonNode itemNode, InternItemText text) {
        JsonNode textNode = itemNode.path("text_data");
        
        if (!textNode.isMissingNode()) {
            text.setQuantityText(getStringValue(textNode, "quantity_text"));
            text.setName(getStringValue(textNode, "name"));
            text.setText(getStringValue(textNode, "text"));
        } else {
            // Try to get description directly from item as name
            String description = getStringValue(itemNode, "description");
            if (description != null && !description.isEmpty()) {
                text.setName(description);
            }
            
            // Try to get text directly from item
            text.setText(getStringValue(itemNode, "text"));
        }
    }
    
    /**
     * Extracts the references of an item
     */
    private void extractItemReferences(JsonNode itemNode, InternItemReferences references) {
        JsonNode referencesNode = itemNode.path("references");
        
        if (!referencesNode.isMissingNode()) {
            references.setOriginalInvoice(getStringValue(referencesNode, "original_invoice"));
            references.setOriginalInvoicePosition(getStringValue(referencesNode, "original_invoice_position"));
            references.setOrder(getStringValue(referencesNode, "order"));
            references.setOrderPosition(getStringValue(referencesNode, "order_position"));
            references.setDeliveryNote(getStringValue(referencesNode, "delivery_note"));
        } else {
            // Try to get references directly from item
            references.setOrder(getStringValue(itemNode, "order_number"));
            references.setDeliveryNote(getStringValue(itemNode, "delivery_note"));
        }
    }
    
    /**
     * Extracts the special flags of an item
     */
    private void extractItemSpecialFlags(JsonNode itemNode, InternItemSpecialFlags flags) {
        JsonNode flagsNode = itemNode.path("special_flags");
        
        if (!flagsNode.isMissingNode()) {
            flags.setTextPosition(getStringValue(flagsNode, "text_position"));
            flags.setChapterSum(getStringValue(flagsNode, "chapter_sum"));
            flags.setSubtotal(getStringValue(flagsNode, "subtotal"));
            flags.setSubtotalTo(getStringValue(flagsNode, "subtotal_to"));
            flags.setSubtotalFrom(getStringValue(flagsNode, "subtotal_from"));
            flags.setPackage(getStringValue(flagsNode, "package"));
            flags.setIsPackagePrice(getBooleanValue(flagsNode, "is_package_price"));
        }
    }
    
    /**
     * Extracts additional data of an item
     */
    private void extractItemAdditionalData(JsonNode itemNode, InternInvoiceItem item) {
        // Date
        String dateStr = getStringValue(itemNode, "date");
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                item.setDate(parseDate(dateStr));
            } catch (DateTimeParseException e) {
                // Ignore date parsing errors
            }
        }
        
        // DontPrint
        Boolean dontPrint = getBooleanValue(itemNode, "dont_print");
        if (dontPrint != null) {
            item.setDontPrint(dontPrint);
        }
        
        // DontPrintPrice
        Boolean dontPrintPrice = getBooleanValue(itemNode, "dont_print_price");
        if (dontPrintPrice != null) {
            item.setDontPrintPrice(dontPrintPrice);
        }
        
        // PrintPosition
        Boolean printPosition = getBooleanValue(itemNode, "print_position");
        if (printPosition != null) {
            item.setPrintPosition(printPosition);
        }
        
        // Inventory
        item.setInventory(getStringValue(itemNode, "inventory"));
        
        // IsBom
        Boolean isBom = getBooleanValue(itemNode, "is_bom");
        if (isBom != null) {
            item.setIsBom(isBom);
        }
        
        // ServiceDate
        String serviceDateStr = getStringValue(itemNode, "service_date");
        if (serviceDateStr != null && !serviceDateStr.isEmpty()) {
            try {
                item.setServiceDate(parseDate(serviceDateStr));
            } catch (DateTimeParseException e) {
                // Ignore date parsing errors
            }
        }
        
        // Quantity
        String quantityStr = getStringValue(itemNode, "quantity");
        if (quantityStr != null && !quantityStr.isEmpty()) {
            try {
                item.setQuantity(new BigDecimal(quantityStr));
            } catch (NumberFormatException e) {
                // Ignore number format errors
            }
        }
        
        // Position
        item.setPosition(getStringValue(itemNode, "position"));
        
        // Unit
        item.setUnit(getStringValue(itemNode, "unit"));
        
        // Material cost type
        item.setMaterialCostType(getStringValue(itemNode, "material_cost_type"));
        
        // Material cost amount
        String materialCostAmountStr = getStringValue(itemNode, "material_cost_amount");
        if (materialCostAmountStr != null && !materialCostAmountStr.isEmpty()) {
            try {
                item.setMaterialCostAmount(new BigDecimal(materialCostAmountStr));
            } catch (NumberFormatException e) {
                // Ignore number format errors
            }
        }
        
        // Check for material object
        JsonNode materialNode = itemNode.path("material");
        if (!materialNode.isMissingNode() && materialNode.isObject()) {
            // Handle material object if needed
            // This is a placeholder as per your JSON example showing "material": id or "object"
        }
    }
    
    /**
     * Helper method to safely get a string value from a JSON node.
     */
    private String getStringValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }
    
    /**
     * Helper method to safely get a boolean value from a JSON node.
     */
    private Boolean getBooleanValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.path(fieldName);
        if (fieldNode.isMissingNode() || fieldNode.isNull()) {
            return null;
        }
        if (fieldNode.isBoolean()) {
            return fieldNode.asBoolean();
        } else if (fieldNode.isTextual()) {
            String text = fieldNode.asText().toLowerCase();
            return "true".equals(text) || "yes".equals(text) || "1".equals(text);
        } else if (fieldNode.isNumber()) {
            return fieldNode.asInt() != 0;
        }
        return null;
    }
    
    /**
     * Helper method to parse a date from different formats.
     */
    private LocalDate parseDate(String dateStr) {
        try {
            // Try to parse as LocalDateTime first
            return LocalDateTime.parse(dateStr, DATE_FORMATTER).toLocalDate();
        } catch (DateTimeParseException e) {
            try {
                // Then try as LocalDate
                return LocalDate.parse(dateStr, DATE_ONLY_FORMATTER);
            } catch (DateTimeParseException e2) {
                // If all else fails, try a more flexible approach
                if (dateStr.contains("T")) {
                    dateStr = dateStr.substring(0, dateStr.indexOf('T'));
                }
                return LocalDate.parse(dateStr);
            }
        }
    }
}