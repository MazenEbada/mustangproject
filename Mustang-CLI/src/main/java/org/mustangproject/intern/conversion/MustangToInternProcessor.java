/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.conversion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mustangproject.Allowance;
import org.mustangproject.BankDetails;
import org.mustangproject.Contact;
import org.mustangproject.IncludedNote;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.SchemedID;
import org.mustangproject.SubjectCode;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDAllowanceCharge;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableContact;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableItem;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableProduct;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentDiscountTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentTerms;
import org.mustangproject.ZUGFeRD.model.DocumentCodeTypeConstants;
import org.mustangproject.intern.model.*;

/**
 * Processor für die Konvertierung von Mustang-Invoice zu InternInvoice
 */
public class MustangToInternProcessor implements InboundInvoiceProcessor {
    
    // Reverse Mapping von Unit-Codes zu internen Einheiten
    private static final Map<String, String> REVERSE_UNIT_CODE_MAPPING = new HashMap<>();
    static {
        REVERSE_UNIT_CODE_MAPPING.put("C62", "St");
        REVERSE_UNIT_CODE_MAPPING.put("BAR", "bar");
        REVERSE_UNIT_CODE_MAPPING.put("CMK", "cm2");
        REVERSE_UNIT_CODE_MAPPING.put("GRM", "g");
        REVERSE_UNIT_CODE_MAPPING.put("HUR", "h");
        REVERSE_UNIT_CODE_MAPPING.put("CT", "Karton");
        REVERSE_UNIT_CODE_MAPPING.put("KGM", "kg");
        REVERSE_UNIT_CODE_MAPPING.put("KPA", "kPa");
        REVERSE_UNIT_CODE_MAPPING.put("LTR", "l");
        REVERSE_UNIT_CODE_MAPPING.put("MTR", "m");
        REVERSE_UNIT_CODE_MAPPING.put("MTK", "m2");
        REVERSE_UNIT_CODE_MAPPING.put("MBAR", "mbar");
        REVERSE_UNIT_CODE_MAPPING.put("MILE", "mi");
        REVERSE_UNIT_CODE_MAPPING.put("MMT", "mm");
        REVERSE_UNIT_CODE_MAPPING.put("MMWS", "mmWS");
        REVERSE_UNIT_CODE_MAPPING.put("PAL", "Pa");
        REVERSE_UNIT_CODE_MAPPING.put("TNE", "t");
    }

    private Map<String, String> conversionKeys;
    private Invoice mustangInvoice;
    private InternInvoice internInvoice;

    /**
     * Konstruktor mit Mustang-Invoice und Konvertierungsschlüsseln
     * 
     * @param mustangInvoice Das Mustang-Invoice-Objekt
     * @param conversionKeys Konvertierungsschlüssel
     */
    public MustangToInternProcessor(Invoice mustangInvoice, Map<String, String> conversionKeys) {
        this.mustangInvoice = mustangInvoice;
        this.conversionKeys = new HashMap<String, String>();
    }

    @Override
    public void process() throws Exception {
        internInvoice = new InternInvoice();
        
        // 1. Metadaten konvertieren
        convertMetadata();
        
        // 2. Adressen konvertieren
        convertAddresses();
        
        // 3. Zahlungsbedingungen konvertieren
        convertPaymentTerms();
        
        // 4. Texte konvertieren
        convertTexts();
        
        // 5. Positionen konvertieren
        convertItems();
    }

    @Override
    public InternInvoice getInternInvoice() {
        return internInvoice;
    }
    
    /**
     * Konvertiert die Metadaten der Rechnung
     */
    private void convertMetadata() {
        InternInvoiceMetadata metadata = internInvoice.getMetadata();
        
        metadata.setInvoiceNumber(mustangInvoice.getNumber());
        metadata.setCurrency(mustangInvoice.getCurrency());
        metadata.setCustomerOrderNumber(mustangInvoice.getBuyerOrderReferencedDocumentID());
        metadata.setOrderNumber(mustangInvoice.getSellerOrderReferencedDocumentID());
        metadata.setOriginalInvoice(mustangInvoice.getInvoiceReferencedDocumentID());
        
        // Art der Rechnung aus DocumentCode ableiten
        metadata.setInvoiceType(mapDocumentCodeToArt(mustangInvoice.getDocumentCode()));
        metadata.setInvoiceTypePa(mustangInvoice.getDocumentName());
        
        // Datumsfelder konvertieren
        if (mustangInvoice.getIssueDate() != null) {
            LocalDate issueDate = convertToLocalDate(mustangInvoice.getIssueDate());
            metadata.addAdditionalData("INVOICE_DATE", issueDate.toString());
        }
        
        if (mustangInvoice.getDeliveryDate() != null) {
            LocalDate deliveryDate = convertToLocalDate(mustangInvoice.getDeliveryDate());
            metadata.setDeliveryDate(deliveryDate);
        }
        
        // Bestelldatum konvertieren, falls vorhanden
        String orderDateStr = mustangInvoice.getBuyerOrderReferencedDocumentIssueDateTime();
        if (orderDateStr != null && !orderDateStr.isEmpty()) {
            try {
                LocalDate orderDate = LocalDate.parse(orderDateStr.substring(0, 10));
                metadata.setOrderDate(orderDate);
            } catch (Exception e) {
                // Fehler bei der Datumskonvertierung ignorieren
            }
        }
        
        // E-Rechnungsdaten
        InternEInvoiceData eInvoiceData = internInvoice.getEInvoiceData();
        eInvoiceData.setRouteId(mustangInvoice.getReferenceNumber());
        
        // Interface aus ConversionKeys übernehmen
        String interfaceType = conversionKeys.get("INTERFACE");
        if (interfaceType != null) {
            eInvoiceData.setInterfaceType(interfaceType);
        }
    }
    
    /**
     * Mappt den DocumentCode auf einen internen Rechnungstyp
     * 
     * @param documentCode Der DocumentCode
     * @return Der interne Rechnungstyp
     */
    private String mapDocumentCodeToArt(String documentCode) {
        if (documentCode == null) return "RE";
        
        switch (documentCode) {
            case DocumentCodeTypeConstants.INVOICE:
                return "RE";
            case DocumentCodeTypeConstants.PARTIAL_BILLING:
                return "TR";
            case DocumentCodeTypeConstants.CREDITNOTE:
                return "GU";
            case DocumentCodeTypeConstants.CORRECTEDINVOICE:
                return "RE";
            default:
                return "RE";
        }
    }
    
    /**
     * Konvertiert ein Date-Objekt in ein LocalDate-Objekt
     * 
     * @param date Das Date-Objekt
     * @return Das LocalDate-Objekt
     */
    private LocalDate convertToLocalDate(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
    
    /**
     * Konvertiert die Adressen der Rechnung
     */
    private void convertAddresses() {
        // Verkäuferadresse
        TradeParty sender = mustangInvoice.getSender();
        if (sender != null) {
            convertTradePartyToAddress(sender, internInvoice.getSellerAddress());
            
            // Bearbeiter/Kontakt extrahieren
            IZUGFeRDExportableContact contact = sender.getContact();
            if (contact != null) {
                convertContactToPerson(contact, internInvoice.getProcessor());
            }
        }
        
        // Kundenadresse
        TradeParty recipient = mustangInvoice.getRecipient();
        if (recipient != null) {
            convertTradePartyToAddress(recipient, internInvoice.getCustomerAddress());
            convertTradePartyToAddress(recipient, internInvoice.getInvoiceAddress());
        }
        
        // Lieferadresse
        TradeParty deliveryParty = mustangInvoice.getDeliveryAddress();
        if (deliveryParty != null) {
            convertTradePartyToAddress(deliveryParty, internInvoice.getDeliveryAddress());
            convertTradePartyToAddress(deliveryParty, internInvoice.getManualDeliveryAddress());
        }
    }
    
    /**
     * Konvertiert eine TradeParty in eine InternAddress
     * 
     * @param party Die TradeParty
     * @param address Die InternAddress
     */
    private void convertTradePartyToAddress(TradeParty party, InternAddress address) {
        // Global IDs extrahieren
        String id = party.getGlobalID();
        if ("0088".equals(party.getGlobalIDScheme())) {
            address.setGlnId(party.getGlobalID());
        }
        
        
        // Grundlegende Adressinformationen
        address.setCompanyName1(party.getName());
        address.setCompanyName2(party.getAdditionalAddress());
        address.setCompanyName3(party.getAdditionalAddressExtension());
        address.setStreet(party.getStreet());
        address.setCity(party.getLocation());
        address.setPostalCode(party.getZIP());
        address.setCountryIso(party.getCountry());
        address.setEmail(party.getEmail());
        
        // Steuerdaten
        address.setVatId(party.getVATID());
        address.setTaxNumber(party.getTaxID());
        
        // Bankdaten
        if (!party.getBankDetails().isEmpty()) {
            BankDetails bankDetails = party.getBankDetails().get(0);
            address.setIban(bankDetails.getIBAN());
            address.setBic(bankDetails.getBIC());
        }
        
        // Kontaktdaten aus TradeParty-Kontakt
        IZUGFeRDExportableContact contact = party.getContact();
        if (contact != null) {
            address.setName(contact.getName());
            address.setPhone(contact.getPhone());
            address.setFax(contact.getFax());
            if (address.getEmail() == null) {
                address.setEmail(contact.getEMail());
            }
        }
    }
    
    /**
     * Konvertiert einen Contact in eine InternPerson
     * 
     * @param contact Der Contact
     * @param person Die InternPerson
     */
    private void convertContactToPerson(IZUGFeRDExportableContact contact, InternPerson person) {
        person.setName(contact.getName());
        person.setPhone(contact.getPhone());
        person.setFax(contact.getFax());
        person.setEmail(contact.getEMail());
    }
    
    /**
     * Konvertiert die Zahlungsbedingungen der Rechnung
     */
    private void convertPaymentTerms() {
        InternPaymentTerms paymentTerms = internInvoice.getPaymentTerms();
        IZUGFeRDPaymentTerms[] mustangPaymentTerms = mustangInvoice.getPaymentTerms();
        
        if (mustangPaymentTerms != null && mustangPaymentTerms.length > 0) {
            // Hauptzahlungsbedingung
            IZUGFeRDPaymentTerms mainTerm = mustangPaymentTerms[0];
            paymentTerms.setPaymentTermsText(mainTerm.getDescription());
            
            if (mainTerm.getDueDate() != null) {
                paymentTerms.setValueDate(convertToLocalDate(mainTerm.getDueDate()));
                paymentTerms.addAdditionalData("NETTODATUM", convertToLocalDate(mainTerm.getDueDate()).toString());
            }
            
            // Skonto-Bedingungen
            for (int i = 1; i < mustangPaymentTerms.length; i++) {
                IZUGFeRDPaymentTerms term = mustangPaymentTerms[i];
                IZUGFeRDPaymentDiscountTerms discountTerms = term.getDiscountTerms();
                
                if (discountTerms != null) {
                    if (i == 1) {
                        // Skonto 1
                        paymentTerms.addAdditionalData("PROZENTSKONTO1", 
                                discountTerms.getCalculationPercentage().toString());
                        paymentTerms.addAdditionalData("SKONTOTAGE1", 
                                String.valueOf(discountTerms.getBasePeriodMeasure()));
                    } else if (i == 2) {
                        // Skonto 2
                        paymentTerms.addAdditionalData("PROZENTSKONTO2", 
                                discountTerms.getCalculationPercentage().toString());
                        paymentTerms.addAdditionalData("SKONTOTAGE2", 
                                String.valueOf(discountTerms.getBasePeriodMeasure()));
                    }
                }
            }
        }
    }
    
    /**
     * Konvertiert die Texte der Rechnung
     */
    private void convertTexts() {
        InternInvoiceTexts texts = internInvoice.getTexts();
        List<IncludedNote> notes = mustangInvoice.getNotesWithSubjectCode();
        
        if (notes != null) {
            for (IncludedNote note : notes) {
                String content = note.getContent();
                if (content == null || content.isEmpty()) continue;
                
                // Je nach SubjectCode den richtigen Text setzen
                SubjectCode subjectCode = note.getSubjectCode();
                String subjectCodeType = null;
                if (subjectCode != null)
                	subjectCodeType = subjectCode.toString();
                if (subjectCodeType == null)
                	subjectCodeType = "";
                
                switch (subjectCodeType) {
                    case "AAI": // Allgemeine Information
                        texts.setStandardText(content);
                        break;
                    case "REG": // Regulatorische Information
                        texts.setFooterText(content);
                        break;
                    case "AAB": // Verkäuferinformation
                        texts.setCustomerText(content);
                        break;
                    case "AAA": // Einleitende Information
                        texts.setHeaderText(content);
                        break;
                    default:
                        texts.setFreeText(content);
                        break;
                }
            }
        }
    }
    
    /**
     * Konvertiert die Positionen der Rechnung
     */
    private void convertItems() {
        IZUGFeRDExportableItem[] mustangItems = mustangInvoice.getZFItems();
        
        if (mustangItems != null) {
            for (IZUGFeRDExportableItem mustangItem : mustangItems) {
                InternInvoiceItem internItem = new InternInvoiceItem();
                
                // Menge und Preis
                internItem.setQuantity(mustangItem.getQuantity());
                
                // Beträge
                InternItemAmounts amounts = internItem.getAmounts();
                amounts.setNet(mustangItem.getPrice());
                
                BigDecimal basisQuantity = mustangItem.getBasisQuantity();
                if (basisQuantity != null) {
                    amounts.setPackageQuantity(basisQuantity);
                }
                
                // Rabatte
                IZUGFeRDAllowanceCharge[] allowances = mustangItem.getItemAllowances();
                if (allowances != null && allowances.length > 0) {
                    BigDecimal totalAllowance = BigDecimal.ZERO;
                    for (IZUGFeRDAllowanceCharge allowance : allowances) {
                        totalAllowance = totalAllowance.add(allowance.getTotalAmount(mustangItem));
                    }
                    
                    // Gesamtrabatt in discountAmount setzen
                    amounts.setDiscountAmount(totalAllowance);
                }
                
                // Steuer
                InternItemTax tax = amounts.getTax();
                //tax.setTaxAmount(BigDecimal.ZERO); //TODO
                
                IZUGFeRDExportableProduct product = mustangItem.getProduct();
                if (product != null) {
                    // Produktinformationen
                    InternItemText text = internItem.getText();
                    text.setName(product.getName());
                    text.setText(product.getDescription());
                    
                    // Stammdaten
                    InternItemMasterData masterData = internItem.getMasterData();
                    masterData.setArticleNumber(product.getSellerAssignedID());
                    masterData.setCustomerArticleNumber(product.getBuyerAssignedID());
                    masterData.setCountryOfOrigin(product.getCountryOfOrigin());
                    
                    // Zolltarifnr aus Attributen extrahieren
                    Map<String, String> attributes = product.getAttributes();
                    if (attributes != null) {
                        String customsTariffNumber = attributes.get("Zolltarifnr."); 
                        if (customsTariffNumber == null) {
                            customsTariffNumber = attributes.get("Customs tariff number");
                        }
                        masterData.setCustomsTariffNumber(customsTariffNumber);
                    }
                    
                    // Steuersatz
                    tax.setTaxRate(product.getVATPercent());
                    
                    // Einheit
                    String unitCode = product.getUnit();
                    String unitType = REVERSE_UNIT_CODE_MAPPING.getOrDefault(unitCode, "St");
                    internItem.setUnit(unitType);
                }
                
                // Item zur Liste hinzufügen
                internInvoice.addItem(internItem);
            }
        }
    }
}