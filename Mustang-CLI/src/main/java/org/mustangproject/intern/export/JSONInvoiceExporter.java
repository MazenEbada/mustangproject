/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.export;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mustangproject.intern.model.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * JSON-Exporter für die Ausgabe einer InternInvoice als JSON-String
 */
public class JSONInvoiceExporter implements InvoiceExporter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public String export(InternInvoice invoice) throws Exception {
        // Configure ObjectMapper
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // Create root object
        ObjectNode rootNode = objectMapper.createObjectNode();
        
        // Add address information
        rootNode.set("seller_address", createAddressNode(invoice.getSellerAddress()));
        rootNode.set("buyer_address", createAddressNode(invoice.getCustomerAddress()));
        rootNode.set("delivery_address", createAddressNode(invoice.getDeliveryAddress()));
        rootNode.set("invoice_address", createAddressNode(invoice.getInvoiceAddress()));
        
        // Add processor information
        rootNode.set("processor", createProcessorNode(invoice.getProcessor()));
        
        // Add text information
        rootNode.set("texts", createTextsNode(invoice.getTexts()));
        
        // Add payment terms
        rootNode.set("payment_terms", createPaymentTermsNode(invoice.getPaymentTerms()));
        
        // Add e-invoice data
        rootNode.set("e_invoice", createEInvoiceNode(invoice.getEInvoiceData()));
        
        // Add dates
        InternInvoiceMetadata metadata = invoice.getMetadata();
        
        if (metadata.getAdditionalData().containsKey("INVOICE_DATE")) {
            rootNode.put("invoice_date", metadata.getAdditionalData().get("INVOICE_DATE"));
        }
        
        if (metadata.getAdditionalData().containsKey("SERVICE_DATE")) {
            rootNode.put("service_date", metadata.getAdditionalData().get("SERVICE_DATE"));
        }
        
        // Add amounts
        rootNode.set("amounts", createAmountsNode(invoice.getAmounts()));
        
        // Add tax information
        rootNode.set("tax", createTaxNode(invoice.getTax()));
        
        // Add shipping costs
        rootNode.set("shipping_costs", createShippingCostsNode(invoice.getShippingCosts()));
        
        // Add additional invoice metadata
        addMetadataToJson(rootNode, metadata);
        
        // Add invoice items
        ArrayNode itemsNode = objectMapper.createArrayNode();
        
        for (InternInvoiceItem item : invoice.getItems()) {
            ObjectNode itemNode = objectMapper.createObjectNode();
            
            // Add amounts
            itemNode.set("amounts", createItemAmountsNode(item.getAmounts()));
            
            // Add master data
            itemNode.set("master_data", createItemMasterDataNode(item.getMasterData()));
            
            // Add text data
            itemNode.set("text_data", createItemTextNode(item.getText()));
            
            // Add references
            itemNode.set("references", createItemReferencesNode(item.getReferences()));
            
            // Add special flags
            itemNode.set("special_flags", createItemSpecialFlagsNode(item.getSpecialFlags()));
            
            // Add additional item data
            addItemAdditionalDataToJson(itemNode, item);
            
            // Add sub-items if available
            if (item.getSubItems() != null && !item.getSubItems().isEmpty()) {
                ArrayNode subItemsNode = objectMapper.createArrayNode();
                
                for (InternInvoiceSubItem subItem : item.getSubItems()) {
                    ObjectNode subItemNode = objectMapper.createObjectNode();
                    
                    // Add amounts
                    subItemNode.set("amounts", createItemAmountsNode(subItem.getAmounts()));
                    
                    // Add master data
                    subItemNode.set("master_data", createItemMasterDataNode(subItem.getMasterData()));
                    
                    // Add text data
                    ObjectNode subItemTextNode = objectMapper.createObjectNode();
                    if (subItem.getText().getName() != null) {
                        subItemTextNode.put("name", subItem.getText().getName());
                    }
                    if (subItem.getText().getText() != null) {
                        subItemTextNode.put("text", subItem.getText().getText());
                    }
                    subItemNode.set("text_data", subItemTextNode);
                    
                    // Add additional sub-item data
                    addSubItemAdditionalDataToJson(subItemNode, subItem);
                    
                    subItemsNode.add(subItemNode);
                }
                
                itemNode.set("sub_items", subItemsNode);
            }
            
            itemsNode.add(itemNode);
        }
        
        rootNode.set("invoice_items", itemsNode);
        
        // Convert to JSON string
        return objectMapper.writeValueAsString(rootNode);
    }
    
    /**
     * Creates a JSON node for an address
     */
    private ObjectNode createAddressNode(InternAddress address) {
        ObjectNode addressNode = objectMapper.createObjectNode();
        
        addJsonField(addressNode, "gln_id", address.getGlnId());
        addJsonField(addressNode, "company_name_1", address.getCompanyName1());
        addJsonField(addressNode, "company_name_2", address.getCompanyName2());
        addJsonField(addressNode, "company_name_3", address.getCompanyName3());
        addJsonField(addressNode, "country_iso", address.getCountryIso());
        addJsonField(addressNode, "name", address.getName());
        addJsonField(addressNode, "department", address.getDepartment());
        addJsonField(addressNode, "city", address.getCity());
        addJsonField(addressNode, "postal_code", address.getPostalCode());
        addJsonField(addressNode, "postal_code_2", address.getPostalCode2());
        addJsonField(addressNode, "street", address.getStreet());
        addJsonField(addressNode, "fax", address.getFax());
        addJsonField(addressNode, "phone", address.getPhone());
        addJsonField(addressNode, "email", address.getEmail());
        addJsonField(addressNode, "duns_number", address.getDunsNumber());
        addJsonField(addressNode, "vat_id", address.getVatId());
        addJsonField(addressNode, "commercial_register", address.getCommercialRegister());
        addJsonField(addressNode, "managing_director_1", address.getManagingDirector1());
        addJsonField(addressNode, "managing_director_2", address.getManagingDirector2());
        addJsonField(addressNode, "tax_number", address.getTaxNumber());
        addJsonField(addressNode, "bic", address.getBic());
        addJsonField(addressNode, "iban", address.getIban());
        addJsonField(addressNode, "payment_methods", address.getPaymentMethods());
        
        return addressNode;
    }
    
    /**
     * Creates a JSON node for a processor
     */
    private ObjectNode createProcessorNode(InternPerson processor) {
        ObjectNode processorNode = objectMapper.createObjectNode();
        
        addJsonField(processorNode, "name", processor.getName());
        addJsonField(processorNode, "email", processor.getEmail());
        addJsonField(processorNode, "phone", processor.getPhone());
        addJsonField(processorNode, "fax", processor.getFax());
        addJsonField(processorNode, "department", processor.getDepartment());
        
        return processorNode;
    }
    
    /**
     * Creates a JSON node for invoice texts
     */
    private ObjectNode createTextsNode(InternInvoiceTexts texts) {
        ObjectNode textsNode = objectMapper.createObjectNode();
        
        addJsonField(textsNode, "standard_text", texts.getStandardText());
        addJsonField(textsNode, "free_text", texts.getFreeText());
        addJsonField(textsNode, "customer_text", texts.getCustomerText());
        addJsonField(textsNode, "footer_text", texts.getFooterText());
        addJsonField(textsNode, "header_text", texts.getHeaderText());
        
        return textsNode;
    }
    
    /**
     * Creates a JSON node for payment terms
     */
    private ObjectNode createPaymentTermsNode(InternPaymentTerms paymentTerms) {
        ObjectNode paymentTermsNode = objectMapper.createObjectNode();
        
        addJsonField(paymentTermsNode, "payment_terms_text", paymentTerms.getPaymentTermsText());
        
        if (paymentTerms.getValueDate() != null) {
            addJsonField(paymentTermsNode, "value_date", paymentTerms.getValueDate().format(DATE_FORMATTER));
        }
        
        // Add additional payment terms data
        if (!paymentTerms.getAdditionalData().isEmpty()) {
            ObjectNode additionalDataNode = objectMapper.createObjectNode();
            
            for (Map.Entry<String, String> entry : paymentTerms.getAdditionalData().entrySet()) {
                addJsonField(additionalDataNode, entry.getKey().toLowerCase(), entry.getValue());
            }
            
            paymentTermsNode.set("additional_data", additionalDataNode);
        }
        
        return paymentTermsNode;
    }
    
    /**
     * Creates a JSON node for e-invoice data
     */
    private ObjectNode createEInvoiceNode(InternEInvoiceData eInvoiceData) {
        ObjectNode eInvoiceNode = objectMapper.createObjectNode();
        
        addJsonField(eInvoiceNode, "route_id", eInvoiceData.getRouteId());
        addJsonField(eInvoiceNode, "dispatch_method", eInvoiceData.getDispatchMethod());
        addJsonField(eInvoiceNode, "interface_type", eInvoiceData.getInterfaceType());
        
        return eInvoiceNode;
    }
    
    /**
     * Creates a JSON node for invoice amounts
     */
    private ObjectNode createAmountsNode(InternInvoiceAmounts amounts) {
        ObjectNode amountsNode = objectMapper.createObjectNode();
        
        addJsonField(amountsNode, "discount_amount", amounts.getDiscountAmount());
        addJsonField(amountsNode, "net_amount", amounts.getNetAmount());
        addJsonField(amountsNode, "tax_amount", amounts.getTaxAmount());
        addJsonField(amountsNode, "gross_amount", amounts.getGrossAmount());
        
        return amountsNode;
    }
    
    /**
     * Creates a JSON node for tax information
     */
    private ObjectNode createTaxNode(InternTax tax) {
        ObjectNode taxNode = objectMapper.createObjectNode();
        
        addJsonField(taxNode, "tax_category", tax.getTaxCategory());
        addJsonField(taxNode, "tax_amount", tax.getTaxAmount());
        addJsonField(taxNode, "tax_rate", tax.getTaxRate());
        addJsonField(taxNode, "tax_base", tax.getTaxBase());
        
        return taxNode;
    }
    
    /**
     * Creates a JSON node for shipping costs
     */
    private ObjectNode createShippingCostsNode(InternShippingCosts shippingCosts) {
        ObjectNode shippingCostsNode = objectMapper.createObjectNode();
        
        addJsonField(shippingCostsNode, "amount", shippingCosts.getAmount());
        addJsonField(shippingCostsNode, "tax_category", shippingCosts.getTaxCategory());
        addJsonField(shippingCostsNode, "tax_rate", shippingCosts.getTaxRate());
        
        return shippingCostsNode;
    }
    
    /**
     * Adds metadata to the JSON root node
     */
    private void addMetadataToJson(ObjectNode rootNode, InternInvoiceMetadata metadata) {
        addJsonField(rootNode, "invoice_number", metadata.getInvoiceNumber());
        addJsonField(rootNode, "invoice_type", metadata.getInvoiceType());
        addJsonField(rootNode, "invoice_type_pa", metadata.getInvoiceTypePa());
        addJsonField(rootNode, "currency", metadata.getCurrency());
        addJsonField(rootNode, "customer_order_number", metadata.getCustomerOrderNumber());
        
        if (metadata.getOrderDate() != null) {
            addJsonField(rootNode, "order_date", metadata.getOrderDate().format(DATE_FORMATTER));
        }
        
        addJsonField(rootNode, "original_invoice", metadata.getOriginalInvoice());
        addJsonField(rootNode, "order_number", metadata.getOrderNumber());
        addJsonField(rootNode, "language", metadata.getLanguage());
        
        if (metadata.getDeliveryDate() != null) {
            addJsonField(rootNode, "delivery_date", metadata.getDeliveryDate().format(DATE_FORMATTER));
        }
        
        // Add additional metadata that isn't mapped to specific fields
        Map<String, String> unmappedMetadata = new HashMap<>(metadata.getAdditionalData());
        unmappedMetadata.remove("INVOICE_DATE");
        unmappedMetadata.remove("SERVICE_DATE");
        
        if (!unmappedMetadata.isEmpty()) {
            ObjectNode metadataNode = objectMapper.createObjectNode();
            
            for (Map.Entry<String, String> entry : unmappedMetadata.entrySet()) {
                addJsonField(metadataNode, entry.getKey().toLowerCase(), entry.getValue());
            }
            
            rootNode.set("metadata", metadataNode);
        }
    }
    
    /**
     * Creates a JSON node for item amounts
     */
    private ObjectNode createItemAmountsNode(InternItemAmounts amounts) {
        ObjectNode amountsNode = objectMapper.createObjectNode();
        
        addJsonField(amountsNode, "revenue", amounts.getRevenue());
        addJsonField(amountsNode, "net_revenue", amounts.getNetRevenue());
        addJsonField(amountsNode, "gross", amounts.getGross());
        addJsonField(amountsNode, "net", amounts.getNet());
        addJsonField(amountsNode, "net_application", amounts.getNetApplication());
        addJsonField(amountsNode, "package_quantity", amounts.getPackageQuantity());
        addJsonField(amountsNode, "price_per_unit", amounts.getPricePerUnit());
        addJsonField(amountsNode, "price", amounts.getPrice());
        addJsonField(amountsNode, "quantity_discount", amounts.getQuantityDiscount());
        addJsonField(amountsNode, "quantity_discount_amount", amounts.getQuantityDiscountAmount());
        addJsonField(amountsNode, "discount", amounts.getDiscount());
        addJsonField(amountsNode, "discount2", amounts.getDiscount2());
        addJsonField(amountsNode, "discount_amount", amounts.getDiscountAmount());
        addJsonField(amountsNode, "discount_amount2", amounts.getDiscountAmount2());
        addJsonField(amountsNode, "unit_revenue", amounts.getUnitRevenue());
        
        // Add tax information
        ObjectNode taxNode = objectMapper.createObjectNode();
        addJsonField(taxNode, "tax_amount", amounts.getTax().getTaxAmount());
        addJsonField(taxNode, "tax_category", amounts.getTax().getTaxCategory());
        addJsonField(taxNode, "tax_rate", amounts.getTax().getTaxRate());
        amountsNode.set("tax", taxNode);
        
        return amountsNode;
    }
    
    /**
     * Creates a JSON node for item master data
     */
    private ObjectNode createItemMasterDataNode(InternItemMasterData masterData) {
        ObjectNode masterDataNode = objectMapper.createObjectNode();
        
        addJsonField(masterDataNode, "batch", masterData.getBatch());
        addJsonField(masterDataNode, "country_of_origin", masterData.getCountryOfOrigin());
        addJsonField(masterDataNode, "customs_tariff_number", masterData.getCustomsTariffNumber());
        addJsonField(masterDataNode, "ean_code", masterData.getEanCode());
        addJsonField(masterDataNode, "article_number", masterData.getArticleNumber());
        addJsonField(masterDataNode, "customer_article_number", masterData.getCustomerArticleNumber());
        
        return masterDataNode;
    }
    
    /**
     * Creates a JSON node for item text data
     */
    private ObjectNode createItemTextNode(InternItemText text) {
        ObjectNode textNode = objectMapper.createObjectNode();
        
        addJsonField(textNode, "quantity_text", text.getQuantityText());
        addJsonField(textNode, "name", text.getName());
        addJsonField(textNode, "text", text.getText());
        
        return textNode;
    }
    
    /**
     * Creates a JSON node for item references
     */
    private ObjectNode createItemReferencesNode(InternItemReferences references) {
        ObjectNode referencesNode = objectMapper.createObjectNode();
        
        addJsonField(referencesNode, "original_invoice", references.getOriginalInvoice());
        addJsonField(referencesNode, "original_invoice_position", references.getOriginalInvoicePosition());
        addJsonField(referencesNode, "order", references.getOrder());
        addJsonField(referencesNode, "order_position", references.getOrderPosition());
        addJsonField(referencesNode, "delivery_note", references.getDeliveryNote());
        
        return referencesNode;
    }
    
    /**
     * Creates a JSON node for item special flags
     */
    private ObjectNode createItemSpecialFlagsNode(InternItemSpecialFlags flags) {
        ObjectNode flagsNode = objectMapper.createObjectNode();
        
        addJsonField(flagsNode, "text_position", flags.getTextPosition());
        addJsonField(flagsNode, "chapter_sum", flags.getChapterSum());
        addJsonField(flagsNode, "subtotal", flags.getSubtotal());
        addJsonField(flagsNode, "subtotal_to", flags.getSubtotalTo());
        addJsonField(flagsNode, "subtotal_from", flags.getSubtotalFrom());
        addJsonField(flagsNode, "package", flags.getPackage());
        
        if (flags.getIsPackagePrice() != null) {
            flagsNode.put("is_package_price", flags.getIsPackagePrice());
        }
        
        return flagsNode;
    }
    
    /**
     * Adds additional item data to the JSON node
     */
    private void addItemAdditionalDataToJson(ObjectNode itemNode, InternInvoiceItem item) {
        if (item.getDate() != null) {
            addJsonField(itemNode, "date", item.getDate().format(DATE_FORMATTER));
        }
        
        if (item.getDontPrint() != null) {
            itemNode.put("dont_print", item.getDontPrint());
        }
        
        if (item.getDontPrintPrice() != null) {
            itemNode.put("dont_print_price", item.getDontPrintPrice());
        }
        
        if (item.getPrintPosition() != null) {
            itemNode.put("print_position", item.getPrintPosition());
        }
        
        addJsonField(itemNode, "inventory", item.getInventory());
        
        if (item.getIsBom() != null) {
            itemNode.put("is_bom", item.getIsBom());
        }
        
        if (item.getServiceDate() != null) {
            addJsonField(itemNode, "service_date", item.getServiceDate().format(DATE_FORMATTER));
        }
        
        addJsonField(itemNode, "quantity", item.getQuantity());
        addJsonField(itemNode, "position", item.getPosition());
        addJsonField(itemNode, "unit", item.getUnit());
        addJsonField(itemNode, "material_cost_type", item.getMaterialCostType());
        addJsonField(itemNode, "material_cost_amount", item.getMaterialCostAmount());
        
        // Add description field as convenience for consumer
        if (item.getText() != null && item.getText().getName() != null) {
            addJsonField(itemNode, "description", item.getText().getName());
        }
    }
    
    /**
     * Adds additional sub-item data to the JSON node
     */
    private void addSubItemAdditionalDataToJson(ObjectNode subItemNode, InternInvoiceSubItem subItem) {
        if (subItem.getDate() != null) {
            addJsonField(subItemNode, "date", subItem.getDate().format(DATE_FORMATTER));
        }
        
        addJsonField(subItemNode, "dont_calculate", subItem.getDontCalculate());
        addJsonField(subItemNode, "dont_print", subItem.getDontPrint());
        addJsonField(subItemNode, "dont_print_price", subItem.getDontPrintPrice());
        addJsonField(subItemNode, "quantity", subItem.getQuantity());
        addJsonField(subItemNode, "position", subItem.getPosition());
        addJsonField(subItemNode, "sub_position", subItem.getSubPosition());
        addJsonField(subItemNode, "unit", subItem.getUnit());
        
        // Add name and description for convenience
        if (subItem.getText() != null) {
            if (subItem.getText().getName() != null) {
                addJsonField(subItemNode, "name", subItem.getText().getName());
            }
            if (subItem.getText().getText() != null) {
                addJsonField(subItemNode, "text", subItem.getText().getText());
            }
        }
    }
    
    /**
     * Helper method to add a String field to a JSON node
     */
    private void addJsonField(ObjectNode node, String fieldName, String value) {
        if (value != null) {
            node.put(fieldName, value);
        }
    }
    
    /**
     * Helper method to add a BigDecimal field to a JSON node
     */
    private void addJsonField(ObjectNode node, String fieldName, BigDecimal value) {
        if (value != null) {
            node.put(fieldName, value);
        }
    }
}