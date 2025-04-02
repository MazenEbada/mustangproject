
/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.conversion;

import org.mustangproject.Allowance;
import org.mustangproject.BankDetails;
import org.mustangproject.Contact;
import org.mustangproject.IncludedNote;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.LegalOrganisation;
import org.mustangproject.Product;
import org.mustangproject.SchemedID;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentDiscountTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentTerms;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;
import org.mustangproject.ZUGFeRD.model.DocumentCodeTypeConstants;
import org.mustangproject.intern.model.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Processor für die Generierung von ZUGFeRD/XRechnung aus InternInvoice
 */
public class InternToMustangProcessor implements OutboundInvoiceProcessor {
    
    private static final Map<String, String> UNIT_CODE_MAPPING = Map.ofEntries(
        Map.entry("me", "C62"),
        Map.entry("100_Stk", "C62"),
        Map.entry("1000_Stk", "C62"),
        Map.entry("bar", "BAR"),
        Map.entry("cm2", "CMK"),
        Map.entry("g", "GRM"),
        Map.entry("h", "HUR"),
        Map.entry("Karton", "CT"),
        Map.entry("kg", "KGM"),
        Map.entry("kPa", "KPA"),
        Map.entry("l", "LTR"),
        Map.entry("m", "MTR"),
        Map.entry("m2", "MTK"),
        Map.entry("mbar", "MBAR"),
        Map.entry("mi", "MILE"),
        Map.entry("mm", "MMT"),
        Map.entry("mmWS", "MMWS"),
        Map.entry("Pa", "PAL"),
        Map.entry("St", "C62"),
        Map.entry("Stück", "C62"),
        Map.entry("t", "TNE")
    );

    private Map<String, String> conversionKeys;
    private InternInvoice internInvoice;
    private Invoice mustangInvoice;
    private String invoiceXML;
    private String language;
    private String applusEinterface;

    /**
     * Konstruktor mit InternInvoice und Konvertierungsschlüsseln
     * 
     * @param internInvoice Das interne Rechnungsobjekt
     * @param conversionKeys Konvertierungsschlüssel
     */
    public InternToMustangProcessor(InternInvoice internInvoice, Map<String, String> conversionKeys) {
        this.internInvoice = internInvoice;
        this.conversionKeys = conversionKeys;
        this.applusEinterface = getInterface();
    }

    /**
     * Verarbeitung durchführen
     * 
     * @throws Exception Bei Verarbeitungsfehlern
     */
    public void process() throws Exception {
        generateMustangInvoice();
        generateInvoiceXML();
    }

    /**
     * Liefert das Mustang-Invoice-Objekt zurück
     * 
     * @return Das Mustang-Invoice-Objekt
     */
    public Invoice getMustangInvoice() {
        return mustangInvoice;
    }

    /**
     * Liefert das generierte XML zurück
     * 
     * @return Das generierte XML als String
     */
    public String getInvoiceXML() {
        return invoiceXML;
    }

    /**
     * Generiert das XML aus dem Mustang-Invoice-Objekt
     * 
     * @throws Exception Bei Generierungsfehlern
     */
    public void generateInvoiceXML() throws Exception {
        String einterface;
        switch (applusEinterface) {
            case "Z":
                einterface = "EXTENDED";
                break;
            case "X":
                einterface = "XRECHNUNG";
                break;
            default:
                throw new Exception("ERechnung-Schnittstelle nicht bekannt: " + applusEinterface + "!");
        }

        ZUGFeRD2PullProvider provider = new ZUGFeRD2PullProvider();
        provider.setProfile(Profiles.getByName(einterface)); // Set ZUGFeRD profile
        provider.generateXML(mustangInvoice);
        invoiceXML = new String(provider.getXML(), StandardCharsets.UTF_8);
    }

    /**
     * Ermittelt das Interface für die E-Rechnung
     * 
     * @return Das Interface (Z für ZUGFeRD, X für XRechnung)
     */
    private String getInterface() {
        // Priorisiere das Interface aus den Konvertierungsschlüsseln
        String interface_ = conversionKeys.get("INTERFACE");
        if (interface_ == null) {
            // Falls nicht in den Konvertierungsschlüsseln, versuche es aus InternInvoice zu lesen
            InternEInvoiceData eInvoiceData = internInvoice.getEInvoiceData();
            if (eInvoiceData != null) {
                interface_ = eInvoiceData.getInterfaceType();
            }
        }
        return interface_ != null ? interface_ : "Z"; // Default zu ZUGFeRD
    }

    /**
     * Gibt Details der Mustang-Invoice für Debugging-Zwecke aus
     */
    public void printInvoiceDetails() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String prettyInvoice = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mustangInvoice);
            System.out.println(prettyInvoice);
        } catch (Exception e) {
            System.err.println("Error while pretty printing invoice: " + e.getMessage());
        }
    }

    /**
     * Generiert ein Mustang-Invoice-Objekt aus dem InternInvoice-Objekt
     * 
     * @throws Exception Bei Verarbeitungsfehlern
     */
    public void generateMustangInvoice() throws Exception {
        mustangInvoice = new Invoice();

        // Step 1: Extract Invoice Metadata
        extractInvoiceMetadata(mustangInvoice);

        // Step 2: Extract Seller Information
        TradeParty seller = extractTradeParty(internInvoice.getSellerAddress(), "V");
        mustangInvoice.setSender(seller);

        // Step 3: Extract Bearbeiter (Processor/Handler)
        Contact bearbeiterContact = extractBearbeiter(internInvoice.getProcessor());
        if (bearbeiterContact != null) {
            TradeParty sender = mustangInvoice.getSender();
            if (sender != null) {
                sender.setContact(bearbeiterContact); // Associate Bearbeiter with the sender
            }
        }

        // Step 4: Extract Customer Information
        TradeParty customer = extractTradeParty(internInvoice.getInvoiceAddress(), "R");
        mustangInvoice.setRecipient(customer);

        // Step 5: Extract Delivery Address
        TradeParty deliveryAddress = extractTradeParty(internInvoice.getManualDeliveryAddress(), "L");
        // Falls keine manuelle Lieferadresse vorhanden ist, fallback auf die normale Lieferadresse
        if (isEmpty(deliveryAddress)) {
            deliveryAddress = extractTradeParty(internInvoice.getDeliveryAddress(), "L");
        }
        mustangInvoice.setDeliveryAddress(deliveryAddress);

        // Step 6: Extract Payment Terms
        extractInvoicePaymentTerms(mustangInvoice);

        // Step 7: Extract Dates
        extractDates(mustangInvoice);

        // Step 8: Extract Invoice Line Items
        List<Item> items = extractInvoiceItems();
        for (Item item : items) {
            mustangInvoice.addItem(item);
        }

        // Step 9: Extract Additional Notes
        Map<String, String> textFields = extractTextFields();
        integrateTexts(mustangInvoice, textFields);
    }

    /**
     * Überprüft, ob eine TradeParty leer ist
     * 
     * @param party Die zu prüfende TradeParty
     * @return true, wenn die TradeParty leer ist, sonst false
     */
    private boolean isEmpty(TradeParty party) {
        return party == null || party.getName() == null || party.getName().isEmpty();
    }

    /**
     * Extrahiert die Metadaten aus dem InternInvoice-Objekt
     * 
     * @param mustangInvoice Das Mustang-Invoice-Objekt
     */
    private void extractInvoiceMetadata(Invoice mustangInvoice) {
        InternInvoiceMetadata metadata = internInvoice.getMetadata();
        
        mustangInvoice.setNumber(metadata.getInvoiceNumber());
        mustangInvoice.setCurrency(metadata.getCurrency());
        mustangInvoice.setBuyerOrderReferencedDocumentID(metadata.getCustomerOrderNumber());
        
        // Konvertiere LocalDate zu Date, falls vorhanden
        if (metadata.getOrderDate() != null) {
            Date orderDate = Date.from(metadata.getOrderDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
            mustangInvoice.setBuyerOrderReferencedDocumentIssueDateTime(new SimpleDateFormat("yyyy-MM-dd").format(orderDate));
        }
        
        mustangInvoice.setDocumentCode(mapArtToDocumentCode(metadata.getInvoiceType()));
        mustangInvoice.setDocumentName(metadata.getInvoiceTypePa());
        
        if (metadata.getOriginalInvoice() != null) {
            mustangInvoice.setInvoiceReferencedDocumentID(metadata.getOriginalInvoice());
        }
        
        if (metadata.getDeliveryDate() != null) {
            Date deliveryDate = Date.from(metadata.getDeliveryDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
            mustangInvoice.setDeliveryDate(deliveryDate);
        }
        
        mustangInvoice.setSellerOrderReferencedDocumentID(metadata.getOrderNumber());
        
        // Setze Sprache
        language = metadata.getLanguage();
        
        // E-Rechnungsdaten
        InternEInvoiceData eInvoiceData = internInvoice.getEInvoiceData();
        if (eInvoiceData != null) {
            mustangInvoice.setReferenceNumber(eInvoiceData.getRouteId());
        }
    }

    /**
     * Mappt den Rechnungstyp auf den entsprechenden Dokumenttyp-Code
     * 
     * @param art Der Rechnungstyp
     * @return Der entsprechende Dokumenttyp-Code
     */
    private String mapArtToDocumentCode(String art) {
        if (art == null) return DocumentCodeTypeConstants.INVOICE;
        
        switch (art) {
            case "RE": // Rechnung
                return DocumentCodeTypeConstants.INVOICE;
            case "TR": // Teilrechnung
                return DocumentCodeTypeConstants.PARTIAL_BILLING;
            case "TS": // Teilschlussrechnung
            case "TSG": // Teilschlussrückvergütung
                return DocumentCodeTypeConstants.PARTIAL_BILLING;
            case "SR": // Schlussrechnung
            case "SV": // Schlussrechnung VOB
                return DocumentCodeTypeConstants.INVOICE;
            case "SG": // Schlussrückvergütung
                return DocumentCodeTypeConstants.CREDITNOTE;
            case "AN": // Anzahlungsrechnung
            case "AB": // Abschlagsrechnung
            case "AV": // Abschlagsrechnung VOB
                return DocumentCodeTypeConstants.PARTIAL_BILLING;
            case "GU": // Rückvergütung
            case "BG": // Bonusvergütung
            case "AG": // Anzahlungsrückvergütung
                return DocumentCodeTypeConstants.CREDITNOTE;
            default:
                return DocumentCodeTypeConstants.INVOICE; // Fallback to corrected invoice
        }
    }

    /**
     * Extrahiert eine TradeParty aus einer InternAddress
     * 
     * @param address Die Adresse
     * @param type Der Typ der Adresse (V, R, L)
     * @return Eine TradeParty
     */
    private TradeParty extractTradeParty(InternAddress address, String type) {
        if (address == null) {
            return null;
        }

        TradeParty tradeParty = new TradeParty();

        // Setze GLN/Global ID, falls vorhanden
        if (address.getGlnId() != null && !address.getGlnId().isEmpty()) {
            SchemedID id = new SchemedID();
            id.setId(address.getGlnId());
            id.setScheme("0088");
            tradeParty.addGlobalID(id);
        }

        // Setze grundlegende Adressinformationen
        tradeParty.setName(address.getCompanyName1());
        tradeParty.setAdditionalAddress(address.getCompanyName2());
        tradeParty.setAdditionalAddressExtension(address.getCompanyName3());
        tradeParty.setStreet(address.getStreet());
        tradeParty.setZIP(address.getPostalCode());
        tradeParty.setLocation(address.getCity());
        tradeParty.setCountry(address.getCountryIso());
        tradeParty.setEmail(address.getEmail());

        // Setze Steuerdaten
        tradeParty.setTaxID(address.getTaxNumber());
        tradeParty.setVATID(address.getVatId());

        // Erstelle Kontaktinformationen, wenn mindestens eines der Felder vorhanden ist
        if (address.getPhone() != null || address.getFax() != null || 
            address.getEmail() != null || address.getName() != null) {
            Contact contact = new Contact();
            contact.setPhone(address.getPhone());
            contact.setFax(address.getFax());
            contact.setEMail(address.getEmail());
            contact.setName(address.getName());
            tradeParty.setContact(contact);
        }

        // Setze Bankdaten
        BankDetails bankDetails = new BankDetails();
        bankDetails.setIBAN(address.getIban());
        bankDetails.setBIC(address.getBic());
        bankDetails.setAccountName(address.getCompanyName1());
        tradeParty.getBankDetails().add(bankDetails);

        // Für den Verkäufer (Typ "V") setze zusätzliche Informationen
        if ("V".equalsIgnoreCase(type)) {
            String handelsregister = address.getCommercialRegister();
            if (handelsregister != null) {
                LegalOrganisation lO = new LegalOrganisation(handelsregister, "0002");
                lO.setTradingBusinessName(tradeParty.getName());
                tradeParty.setLegalOrganisation(lO);
            }

            // Geschäftsführer-Informationen als Hinweis hinzufügen
            String gf1 = address.getManagingDirector1();
            String gf2 = address.getManagingDirector2();
            String gf = null;
            if (gf1 != null) {
                gf = "Geshäftsführer: " + gf1;
                if (gf2 != null) gf += ", " + gf2;
            }
            if (gf != null) {
                mustangInvoice.addNotes(Collections.singletonList(IncludedNote.regulatoryNote(gf)));
            }
        }

        return tradeParty;
    }

    /**
     * Extrahiert einen Bearbeiter aus einem InternPerson-Objekt
     * 
     * @param person Die Person
     * @return Ein Contact-Objekt
     */
    private Contact extractBearbeiter(InternPerson person) {
        if (person == null) {
            return null;
        }

        Contact contact = new Contact();
        contact.setName(person.getName());
        contact.setPhone(person.getPhone());
        contact.setFax(person.getFax());
        contact.setEMail(person.getEmail());

        return contact;
    }

    /**
     * Extrahiert Zahlungsbedingungen
     * 
     * @param mustangInvoice Das Mustang-Invoice-Objekt
     */
    private void extractInvoicePaymentTerms(Invoice mustangInvoice) {
        InternPaymentTerms paymentTerms = internInvoice.getPaymentTerms();
        if (paymentTerms == null) {
            return;
        }

        // Hole relevante Daten aus den zusätzlichen Daten
        Map<String, String> additionalData = paymentTerms.getAdditionalData();
        String nettoDate = additionalData.get("NETTODATUM");
        System.out.println(nettoDate);
        String discountPercent1 = additionalData.get("PROZENTSKONTO1");
        String discountDays1 = additionalData.get("SKONTOTAGE1");
        String skontoDate1 = additionalData.get("SKONTODATUM1");
        
        String discountPercent2 = additionalData.get("PROZENTSKONTO2");
        String discountDays2 = additionalData.get("SKONTOTAGE2");
        String skontoDate2 = additionalData.get("SKONTODATUM2");

        // Bestimme die Anzahl der Zahlungsbedingungen
        IZUGFeRDPaymentTerms[] mustangPaymentTerms;
        int count = 1;
        if (!applusEinterface.equals("X") && discountPercent1 != null && discountDays1 != null
                && !discountPercent1.isEmpty() && !discountDays1.isEmpty()) {
            count++;
        }
        if (!applusEinterface.equals("X") && discountPercent2 != null && discountDays2 != null
                && !discountPercent2.isEmpty() && !discountDays2.isEmpty()) {
            count++;
        }
        mustangPaymentTerms = new IZUGFeRDPaymentTerms[count];

        // Standard-Zahlungsbedingung
        mustangPaymentTerms[0] = (new IZUGFeRDPaymentTerms() {
            @Override
            public String getDescription() {
                return paymentTerms.getPaymentTermsText();
            }

            @Override
            public Date getDueDate() {
                if (nettoDate != null) {
                    try {
                        return parseDate(nettoDate);
                    } catch (Exception e) {
                        return null;
                    }
                } else if (paymentTerms.getValueDate() != null) {
                    return Date.from(paymentTerms.getValueDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
                }
                return null;
            }

            @Override
            public IZUGFeRDPaymentDiscountTerms getDiscountTerms() {
                return null;
            }
        });

        // Skonto 1, falls vorhanden
        if (!applusEinterface.equals("X") && discountPercent1 != null && discountDays1 != null
                && !discountPercent1.isEmpty() && !discountDays1.isEmpty()) {
            mustangPaymentTerms[1] = (new IZUGFeRDPaymentTerms() {
                @Override
                public String getDescription() {
                    return paymentTerms.getPaymentTermsText() + " - SKONTO 1 - " + discountPercent1 + "%";
                }

                @Override
                public Date getDueDate() {
                    return null;
                }

                @Override
                public IZUGFeRDPaymentDiscountTerms getDiscountTerms() {
                    return new IZUGFeRDPaymentDiscountTerms() {
                        @Override
                        public BigDecimal getCalculationPercentage() {
                            if (discountPercent1 == null || discountPercent1.isEmpty())
                                return BigDecimal.ZERO;
                            else
                                return new BigDecimal(discountPercent1);
                        }

                        @Override
                        public Date getBaseDate() {
                            return null;
                        }

                        @Override
                        public int getBasePeriodMeasure() {
                            return Integer.parseInt(discountDays1);
                        }

                        @Override
                        public String getBasePeriodUnitCode() {
                            return "DAY"; // Default to "DAY" as the unit
                        }
                    };
                }
            });
        }

        // Skonto 2, falls vorhanden
        if (!applusEinterface.equals("X") && discountPercent2 != null && discountDays2 != null
                && !discountPercent2.isEmpty() && !discountDays2.isEmpty()) {
            int arrayCount = 1;
            if (discountPercent1 != null && discountDays1 != null
                    && !discountPercent1.isEmpty() && !discountDays1.isEmpty()) {
                arrayCount++;
            }
            mustangPaymentTerms[arrayCount] = (new IZUGFeRDPaymentTerms() {
                @Override
                public String getDescription() {
                    return paymentTerms.getPaymentTermsText() + " - SKONTO 2 - " + discountPercent2 + "%";
                }

                @Override
                public Date getDueDate() {
                    return null;
                }

                @Override
                public IZUGFeRDPaymentDiscountTerms getDiscountTerms() {
                    return new IZUGFeRDPaymentDiscountTerms() {
                        @Override
                        public BigDecimal getCalculationPercentage() {
                            if (discountPercent2 == null || discountPercent2.isEmpty())
                                return BigDecimal.ZERO;
                            else
                                return new BigDecimal(discountPercent2);
                        }

                        @Override
                        public Date getBaseDate() {
                            return null;
                        }

                        @Override
                        public int getBasePeriodMeasure() {
                            return Integer.parseInt(discountDays2);
                        }

                        @Override
                        public String getBasePeriodUnitCode() {
                            return "DAY"; // Default to "DAY" as the unit
                        }
                    };
                }
            });
        }

        mustangInvoice.setPaymentTerms(mustangPaymentTerms);
    }

    /**
     * Integriert Texte in das Mustang-Invoice-Objekt
     * 
     * @param mustangInvoice Das Mustang-Invoice-Objekt
     * @param textFields Die Textfelder
     */
    private void integrateTexts(Invoice mustangInvoice, Map<String, String> textFields) {
        // Add notes as IncludedNotes with appropriate subject codes
        if (textFields.get("STDTEXT") != null) {
            mustangInvoice.addNotes(Collections.singletonList(IncludedNote.generalNote(textFields.get("STDTEXT"))));
        }
        if (textFields.get("FUSSTEXT") != null) {
            mustangInvoice.addNotes(Collections.singletonList(IncludedNote.regulatoryNote(textFields.get("FUSSTEXT"))));
        }
        if (textFields.get("FREITEXT") != null) {
            mustangInvoice.addNotes(Collections.singletonList(IncludedNote.unspecifiedNote(textFields.get("FREITEXT"))));
        }
        if (textFields.get("IHRTEXT") != null) {
            mustangInvoice.addNotes(Collections.singletonList(IncludedNote.sellerNote(textFields.get("IHRTEXT"))));
        }
        if (textFields.get("KOPFTEXT") != null) {
            mustangInvoice.addNotes(Collections.singletonList(IncludedNote.introductionNote(textFields.get("KOPFTEXT"))));
        }
    }

    /**
     * Extrahiert Textfelder aus dem InternInvoice-Objekt
     * 
     * @return Eine Map mit Textfeldern
     */
    private Map<String, String> extractTextFields() {
        Map<String, String> textFields = new HashMap<>();
        InternInvoiceTexts texts = internInvoice.getTexts();
        
        if (texts != null) {
            if (texts.getStandardText() != null && !texts.getStandardText().isEmpty()) {
                textFields.put("STDTEXT", texts.getStandardText());
            }
            if (texts.getFooterText() != null && !texts.getFooterText().isEmpty()) {
                textFields.put("FUSSTEXT", texts.getFooterText());
            }
            if (texts.getFreeText() != null && !texts.getFreeText().isEmpty()) {
                textFields.put("FREITEXT", texts.getFreeText());
            }
            if (texts.getCustomerText() != null && !texts.getCustomerText().isEmpty()) {
                textFields.put("IHRTEXT", texts.getCustomerText());
            }
            if (texts.getHeaderText() != null && !texts.getHeaderText().isEmpty()) {
                textFields.put("KOPFTEXT", texts.getHeaderText());
            }
        }
        
        return textFields;
    }

    /**
     * Extrahiert Datumsangaben aus dem InternInvoice-Objekt
     * 
     * @param mustangInvoice Das Mustang-Invoice-Objekt
     */
    private void extractDates(Invoice mustangInvoice) {
        // Rechnungsdatum aus den Metadaten
        InternInvoiceMetadata metadata = internInvoice.getMetadata();
        if (metadata != null) {
            // Prüfe auf Datum in zusätzlichen Daten
            String issueDateStr = metadata.getAdditionalData().get("INVOICE_DATE");
            if (issueDateStr != null) {
                try {
                    Date issueDate = new SimpleDateFormat("yyyy-MM-dd").parse(issueDateStr);
                    mustangInvoice.setIssueDate(issueDate);
                } catch (Exception e) {
                    // Ignoriere Datumsfehler
                }
            }
            
            // Prüfe auf Leistungsdatum in zusätzlichen Daten
            String serviceDateStr = metadata.getAdditionalData().get("SERVICE_DATE");
            if (serviceDateStr != null && mustangInvoice.getDeliveryDate() == null) {
                try {
                    Date serviceDate = new SimpleDateFormat("yyyy-MM-dd").parse(serviceDateStr);
                    mustangInvoice.setDeliveryDate(serviceDate);
                } catch (Exception e) {
                    // Ignoriere Datumsfehler
                }
            }
        }
    }

    /**
     * Extrahiert Rechnungspositionen aus dem InternInvoice-Objekt
     * 
     * @return Eine Liste von Mustang-Item-Objekten
     */
    private List<Item> extractInvoiceItems() {
        List<Item> items = new ArrayList<>();
        List<InternInvoiceItem> internItems = internInvoice.getItems();
        
        if (internItems == null) {
            return items;
        }
        
        for (InternInvoiceItem internItem : internItems) {
            // Menge und Preis
            BigDecimal quantity = internItem.getQuantity();
            if (quantity == null) quantity = BigDecimal.ONE; // Fallback
            
            BigDecimal unitPrice = null;
            InternItemAmounts amounts = internItem.getAmounts();
            if (amounts != null) {
                unitPrice = amounts.getNet();
            }
            if (unitPrice == null) unitPrice = BigDecimal.ZERO; // Fallback
            
            BigDecimal packmenge = null;
            if (amounts != null) {
                packmenge = amounts.getPackageQuantity();
            }
            if (packmenge == null || packmenge.compareTo(BigDecimal.ZERO) == 0) {
                packmenge = BigDecimal.ONE; // Fallback
            }
            
            // Umsatzsteuer
            BigDecimal tax = BigDecimal.ZERO;
            BigDecimal taxPercent = BigDecimal.ZERO;
            
            if (amounts != null && amounts.getTax() != null) {
                InternItemTax itemTax = amounts.getTax();
                tax = itemTax.getTaxAmount();
                taxPercent = itemTax.getTaxRate();
                
                if (tax == null) tax = BigDecimal.ZERO;
                if (taxPercent == null) taxPercent = BigDecimal.ZERO;
            }
            
            // Rabatte
            BigDecimal rabatt1 = BigDecimal.ZERO;
            BigDecimal rabatt2 = BigDecimal.ZERO;
            BigDecimal mrabatt = BigDecimal.ZERO;
            
            if (amounts != null) {
                if (amounts.getDiscountAmount() != null) rabatt1 = amounts.getDiscountAmount();
                if (amounts.getDiscountAmount2() != null) rabatt2 = amounts.getDiscountAmount2();
                if (amounts.getQuantityDiscountAmount() != null) mrabatt = amounts.getQuantityDiscountAmount();
            }
            
            // Produktinformationen
            Product product = new Product();
            if (internItem.getText() != null) {
                product.setName(internItem.getText().getName());
                product.setDescription(internItem.getText().getText());
            }
            
            // Produktklassifikationen
            InternItemMasterData masterData = internItem.getMasterData();
            if (masterData != null) {
                product.setSellerAssignedID(masterData.getArticleNumber());
                product.setBuyerAssignedID(masterData.getCustomerArticleNumber());
                product.setCountryOfOrigin(masterData.getCountryOfOrigin());
                
                String customsTariffNumber = masterData.getCustomsTariffNumber();
                if (customsTariffNumber != null) {
                    String zolltarifnrAtt = language == null || language.isEmpty() || language.equalsIgnoreCase("de") ? 
                            "Zolltarifnr." : "Customs tariff number";
                    product.addAttribute(zolltarifnrAtt, customsTariffNumber);
                }
            }
            
            product.setVATPercent(taxPercent);
            
            // Einheit mappgen
            String unitType = internItem.getUnit();
            String unitCode = UNIT_CODE_MAPPING.getOrDefault(unitType, "C62"); // Default to "Piece"
            product.setUnit(unitCode);
            
            // Erstelle die Position
            Item item = new Item();
            
            // Rabatte als Allowances hinzufügen
            BigDecimal summRabatt = rabatt1.add(rabatt2).add(mrabatt);
            if (summRabatt.compareTo(BigDecimal.ZERO) > 0) {
                summRabatt = summRabatt.multiply(packmenge).divide(quantity, 2, BigDecimal.ROUND_HALF_UP);
                Allowance discountAllowance = new Allowance(summRabatt);
                discountAllowance.setReason("Rabatt");
                discountAllowance.setReasonCode("60");
                discountAllowance.setTaxPercent(product.getVATPercent());
                item.addAllowance(discountAllowance); // Add allowance to the item
            }
            
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setPrice(unitPrice);
            item.setBasisQuantity(packmenge);
            item.setTax(tax);
            
            // Position zur Liste hinzufügen
            items.add(item);
        }
        
        return items;
    }
    

	private Date parseDate(String dateString) {
	    if (dateString == null || dateString.isEmpty()) {
	        return null;
	    }
	    try {
	        // Beispiel-Format "2008-07-18T00:00:00.000"
	        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(dateString);
	    } catch (ParseException e) {
	    	try {
	    		return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").parse(dateString);

			} catch (ParseException e1) {
				//e1.printStackTrace();
				return null;
			}
		    
	    }
	}
}