/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.export;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.mustangproject.intern.model.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * XML-Exporter für die Ausgabe einer InternInvoice als XML-String
 */
public class XMLInvoiceExporter implements InvoiceExporter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    @Override
    public String export(InternInvoice invoice) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        
        // Wurzelelement <RECHNUNG> erstellen
        Element rootElement = doc.createElement("RECHNUNG");
        doc.appendChild(rootElement);
        
        // VERKAEUFERADRESSE
        Element verkElement = doc.createElement("VERKAEUFERADRESSE");
        rootElement.appendChild(verkElement);
        addAddressToXml(doc, verkElement, invoice.getSellerAddress(), "V");
        
        // ADRESSE (Kundenadresse)
        Element adresseElement = doc.createElement("ADRESSE");
        rootElement.appendChild(adresseElement);
        addAddressToXml(doc, adresseElement, invoice.getCustomerAddress(), "");
        
        // LIEFERADRESSE
        Element lieferElement = doc.createElement("LIEFERADRESSE");
        rootElement.appendChild(lieferElement);
        addAddressToXml(doc, lieferElement, invoice.getDeliveryAddress(), "L");
        
        // MANUELLLIEFERADRESSE
        Element manlieferElement = doc.createElement("MANUELLLIEFERADRESSE");
        rootElement.appendChild(manlieferElement);
        addAddressToXml(doc, manlieferElement, invoice.getManualDeliveryAddress(), "L");
        
        // RECHNUNGADRESSE
        Element rechnungElement = doc.createElement("RECHNUNGADRESSE");
        rootElement.appendChild(rechnungElement);
        addAddressToXml(doc, rechnungElement, invoice.getInvoiceAddress(), "R");
        
        // BEARBEITER
        Element bearbeiterElement = doc.createElement("BEARBEITER");
        rootElement.appendChild(bearbeiterElement);
        addPersonToXml(doc, bearbeiterElement, invoice.getProcessor());
        
        // TEXT
        Element textElement = doc.createElement("TEXT");
        rootElement.appendChild(textElement);
        addTextsToXml(doc, textElement, invoice.getTexts());
        
        // ZAHLUNGSBEDINGUNG
        Element zahlungElement = doc.createElement("ZAHLUNGSBEDINGUNG");
        rootElement.appendChild(zahlungElement);
        addPaymentTermsToXml(doc, zahlungElement, invoice.getPaymentTerms());
        
        // ERECHNUNG
        Element eRechnungElement = doc.createElement("ERECHNUNG");
        rootElement.appendChild(eRechnungElement);
        addEInvoiceDataToXml(doc, eRechnungElement, invoice.getEInvoiceData());
        
        // DATUM
        Element datumElement = doc.createElement("DATUM");
        rootElement.appendChild(datumElement);
        InternInvoiceMetadata metadata = invoice.getMetadata();
        
        if (metadata.getAdditionalData().containsKey("INVOICE_DATE")) {
            Element invoiceDateElem = doc.createElement("DATUM");
            invoiceDateElem.setTextContent(metadata.getAdditionalData().get("INVOICE_DATE"));
            datumElement.appendChild(invoiceDateElem);
        }
        
        if (metadata.getAdditionalData().containsKey("SERVICE_DATE")) {
            Element serviceDateElem = doc.createElement("LEISTUNGSDATUM");
            serviceDateElem.setTextContent(metadata.getAdditionalData().get("SERVICE_DATE"));
            datumElement.appendChild(serviceDateElem);
        }
        
        // BETRAG
        Element betragElement = doc.createElement("BETRAG");
        rootElement.appendChild(betragElement);
        addAmountsToXml(doc, betragElement, invoice.getAmounts());
        
        // UST
        Element ustElement = doc.createElement("UST");
        rootElement.appendChild(ustElement);
        addTaxToXml(doc, ustElement, invoice.getTax());
        
        // FRACHTKOSTEN
        Element frachtElement = doc.createElement("FRACHTKOSTEN");
        rootElement.appendChild(frachtElement);
        addShippingCostsToXml(doc, frachtElement, invoice.getShippingCosts());
        
        // Weitere Rechnungsdaten direkt ins Root-Element
        addMetadataToXml(doc, rootElement, metadata);
        
        // RECHNUNGSPOSITIONEN
        Element positionenElement = doc.createElement("RECHNUNGSPOSITIONEN");
        rootElement.appendChild(positionenElement);
        
        for (InternInvoiceItem item : invoice.getItems()) {
            Element positionElement = doc.createElement("RECHNUNGSPOSITION");
            positionenElement.appendChild(positionElement);
            
            // BETRAG
            Element itemBetragElement = doc.createElement("BETRAG");
            positionElement.appendChild(itemBetragElement);
            addItemAmountsToXml(doc, itemBetragElement, item.getAmounts());
            
            // STAMMDATEN
            Element stammdatenElement = doc.createElement("STAMMDATEN");
            positionElement.appendChild(stammdatenElement);
            addItemMasterDataToXml(doc, stammdatenElement, item.getMasterData());
            
            // TEXT
            Element itemTextElement = doc.createElement("TEXT");
            positionElement.appendChild(itemTextElement);
            addItemTextToXml(doc, itemTextElement, item.getText());
            
            // BELEGE
            Element belegeElement = doc.createElement("BELEGE");
            positionElement.appendChild(belegeElement);
            addItemReferencesToXml(doc, belegeElement, item.getReferences());
            
            // NOPOS
            Element noposElement = doc.createElement("NOPOS");
            positionElement.appendChild(noposElement);
            addItemSpecialFlagsToXml(doc, noposElement, item.getSpecialFlags());
            
            // Weitere Felder der Position
            addItemAdditionalDataToXml(doc, positionElement, item);
            
            // RECHNUNGSUNTERPOSITIONEN
            if (item.getSubItems() != null && !item.getSubItems().isEmpty()) {
                Element unterpositionenElement = doc.createElement("RECHNUNGSUNTERPOSITIONEN");
                positionElement.appendChild(unterpositionenElement);
                
                for (InternInvoiceSubItem subItem : item.getSubItems()) {
                    Element unterpositionElement = doc.createElement("RECHNUNGSUNTERPOSITION");
                    unterpositionenElement.appendChild(unterpositionElement);
                    
                    // BETRAG
                    Element subItemBetragElement = doc.createElement("BETRAG");
                    unterpositionElement.appendChild(subItemBetragElement);
                    addItemAmountsToXml(doc, subItemBetragElement, subItem.getAmounts());
                    
                    // STAMMDATEN
                    Element subItemStammdatenElement = doc.createElement("STAMMDATEN");
                    unterpositionElement.appendChild(subItemStammdatenElement);
                    addItemMasterDataToXml(doc, subItemStammdatenElement, subItem.getMasterData());
                    
                    // TEXT
                    Element subItemTextElement = doc.createElement("TEXT");
                    unterpositionElement.appendChild(subItemTextElement);
                    addItemTextToXml(doc, subItemTextElement, subItem.getText());
                    
                    // Weitere Felder der Unterposition
                    addSubItemAdditionalDataToXml(doc, unterpositionElement, subItem);
                }
            }
        }
        
        // XML in String umwandeln
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        
        DOMSource source = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        transformer.transform(source, result);
        
        return writer.toString();
    }
    
    /**
     * Fügt Adressdaten zum XML hinzu
     */
    private void addAddressToXml(Document doc, Element parent, InternAddress address, String prefix) {
        addElement(doc, parent, prefix + "GLNID", address.getGlnId());
        addElement(doc, parent, prefix + "FIRMA1", address.getCompanyName1());
        addElement(doc, parent, prefix + "FIRMA2", address.getCompanyName2());
        addElement(doc, parent, prefix + "FIRMA3", address.getCompanyName3());
        addElement(doc, parent, prefix + "LAND", address.getCountryIso());
        addElement(doc, parent, prefix + "NAME", address.getName());
        addElement(doc, parent, prefix + "ABTEILUNG", address.getDepartment());
        addElement(doc, parent, prefix + "ORT", address.getCity());
        addElement(doc, parent, prefix + "PLZ", address.getPostalCode());
        addElement(doc, parent, prefix + "PLZ2", address.getPostalCode2());
        addElement(doc, parent, prefix + "STRASSE", address.getStreet());
        addElement(doc, parent, prefix + "TELEFAX", address.getFax());
        addElement(doc, parent, prefix + "TELEFON", address.getPhone());
        addElement(doc, parent, prefix + "EMAIL", address.getEmail());
        addElement(doc, parent, prefix + "DUNSNR", address.getDunsNumber());
        addElement(doc, parent, prefix + "USTID", address.getVatId());
        addElement(doc, parent, prefix + "HANDELSREGISTER", address.getCommercialRegister());
        addElement(doc, parent, prefix + "GF1", address.getManagingDirector1());
        addElement(doc, parent, prefix + "GF2", address.getManagingDirector2());
        addElement(doc, parent, prefix + "STEUERNUMMER", address.getTaxNumber());
        addElement(doc, parent, prefix + "BIC", address.getBic());
        addElement(doc, parent, prefix + "IBAN", address.getIban());
        addElement(doc, parent, prefix + "ZAHLARTEN", address.getPaymentMethods());
    }
    
    /**
     * Fügt Personendaten zum XML hinzu
     */
    private void addPersonToXml(Document doc, Element parent, InternPerson person) {
        addElement(doc, parent, "NAME", person.getName());
        addElement(doc, parent, "EMAIL", person.getEmail());
        addElement(doc, parent, "TELEFON", person.getPhone());
        addElement(doc, parent, "TELEFAX", person.getFax());
        addElement(doc, parent, "ABTEILUNG", person.getDepartment());
    }
    
    /**
     * Fügt Textdaten zum XML hinzu
     */
    private void addTextsToXml(Document doc, Element parent, InternInvoiceTexts texts) {
        addElement(doc, parent, "STDTEXT", texts.getStandardText());
        addElement(doc, parent, "FREITEXT", texts.getFreeText());
        addElement(doc, parent, "IHRTEXT", texts.getCustomerText());
        addElement(doc, parent, "FUSSTEXT", texts.getFooterText());
        addElement(doc, parent, "KOPFTEXT", texts.getHeaderText());
    }
    
    /**
     * Fügt Zahlungsbedingungen zum XML hinzu
     */
    private void addPaymentTermsToXml(Document doc, Element parent, InternPaymentTerms paymentTerms) {
        addElement(doc, parent, "ZAHLUNGSBEDINGUNG", paymentTerms.getPaymentTermsText());
        
        if (paymentTerms.getValueDate() != null) {
            addElement(doc, parent, "VALUTADATUM", paymentTerms.getValueDate().format(DATE_FORMATTER));
        }
        
        // Zusätzliche Zahlungsbedingungsdaten
        for (Map.Entry<String, String> entry : paymentTerms.getAdditionalData().entrySet()) {
            addElement(doc, parent, entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Fügt E-Rechnungsdaten zum XML hinzu
     */
    private void addEInvoiceDataToXml(Document doc, Element parent, InternEInvoiceData eInvoiceData) {
        addElement(doc, parent, "LEITWEGID", eInvoiceData.getRouteId());
        addElement(doc, parent, "EINVOICE_DISPATCH", eInvoiceData.getDispatchMethod());
        addElement(doc, parent, "EINVOICE_INTERFACE", eInvoiceData.getInterfaceType());
    }
    
    /**
     * Fügt Beträge zum XML hinzu
     */
    private void addAmountsToXml(Document doc, Element parent, InternInvoiceAmounts amounts) {
        addElement(doc, parent, "RABATTPREIS", amounts.getDiscountAmount());
        addElement(doc, parent, "NETTOERLOES", amounts.getNetAmount());
        addElement(doc, parent, "USTPREIS", amounts.getTaxAmount());
        addElement(doc, parent, "BRUTTO", amounts.getGrossAmount());
    }
    
    /**
     * Fügt Steuerinformationen zum XML hinzu
     */
    private void addTaxToXml(Document doc, Element parent, InternTax tax) {
        addElement(doc, parent, "USTKATEGORIE", tax.getTaxCategory());
        addElement(doc, parent, "USTBETRAG", tax.getTaxAmount());
        addElement(doc, parent, "USTPROZENT", tax.getTaxRate());
        addElement(doc, parent, "USTBASIS", tax.getTaxBase());
    }
    
    /**
     * Fügt Frachtkosten zum XML hinzu
     */
    private void addShippingCostsToXml(Document doc, Element parent, InternShippingCosts shippingCosts) {
        addElement(doc, parent, "FRACHTKOSTENBETRAG", shippingCosts.getAmount());
        addElement(doc, parent, "FRACHTKOSTENUSTKATEGORIE", shippingCosts.getTaxCategory());
        addElement(doc, parent, "FRACHTKOSTENUSTPROZENT", shippingCosts.getTaxRate());
    }
    
    /**
     * Fügt Metadaten zum XML hinzu
     */
    private void addMetadataToXml(Document doc, Element parent, InternInvoiceMetadata metadata) {
        addElement(doc, parent, "RECHNUNGNR", metadata.getInvoiceNumber());
        addElement(doc, parent, "ART", metadata.getInvoiceType());
        addElement(doc, parent, "PARECHNUNGSART", metadata.getInvoiceTypePa());
        addElement(doc, parent, "WAEHRUNG", metadata.getCurrency());
        addElement(doc, parent, "IHREBESTELLUNG", metadata.getCustomerOrderNumber());
        
        if (metadata.getOrderDate() != null) {
            addElement(doc, parent, "BESTELLDATUM", metadata.getOrderDate().format(DATE_FORMATTER));
        }
        
        addElement(doc, parent, "URRECHNUNG", metadata.getOriginalInvoice());
        addElement(doc, parent, "AUFTRAG", metadata.getOrderNumber());
        addElement(doc, parent, "SPRACHE", metadata.getLanguage());
        
        if (metadata.getDeliveryDate() != null) {
            addElement(doc, parent, "LIEFERTERMIN", metadata.getDeliveryDate().format(DATE_FORMATTER));
        }
    }
    
    /**
     * Fügt Positions-Beträge zum XML hinzu
     */
    private void addItemAmountsToXml(Document doc, Element parent, InternItemAmounts amounts) {
        addElement(doc, parent, "ERLOES", amounts.getRevenue());
        addElement(doc, parent, "NETTOERLOES", amounts.getNetRevenue());
        addElement(doc, parent, "BRUTTO", amounts.getGross());
        addElement(doc, parent, "NETTO", amounts.getNet());
        addElement(doc, parent, "NETTOANTRAG", amounts.getNetApplication());
        addElement(doc, parent, "PACKMENGE", amounts.getPackageQuantity());
        addElement(doc, parent, "PREISME", amounts.getPricePerUnit());
        addElement(doc, parent, "PREIS", amounts.getPrice());
        addElement(doc, parent, "MRABATT", amounts.getQuantityDiscount());
        addElement(doc, parent, "MRABATTPREIS", amounts.getQuantityDiscountAmount());
        addElement(doc, parent, "RABATT", amounts.getDiscount());
        addElement(doc, parent, "RABATT2", amounts.getDiscount2());
        addElement(doc, parent, "RABATTPREIS", amounts.getDiscountAmount());
        addElement(doc, parent, "RABATTPREIS2", amounts.getDiscountAmount2());
        addElement(doc, parent, "STKERLOES", amounts.getUnitRevenue());
        
        // Steuerinformationen
        Element ustElement = doc.createElement("UST");
        parent.appendChild(ustElement);
        addElement(doc, ustElement, "USTPREIS", amounts.getTax().getTaxAmount());
        addElement(doc, ustElement, "USTKATEGORIE", amounts.getTax().getTaxCategory());
        addElement(doc, ustElement, "USTSATZ", amounts.getTax().getTaxRate());
    }
    
    /**
     * Fügt Positions-Stammdaten zum XML hinzu
     */
    private void addItemMasterDataToXml(Document doc, Element parent, InternItemMasterData masterData) {
        addElement(doc, parent, "CHARGE", masterData.getBatch());
        addElement(doc, parent, "URSPRUNGSLAND", masterData.getCountryOfOrigin());
        addElement(doc, parent, "ZOLLTARIFNR", masterData.getCustomsTariffNumber());
        addElement(doc, parent, "EANCODE", masterData.getEanCode());
        addElement(doc, parent, "ARTIKEL", masterData.getArticleNumber());
        addElement(doc, parent, "KARTIKEL", masterData.getCustomerArticleNumber());
    }
    
    /**
     * Fügt Positions-Text zum XML hinzu
     */
    private void addItemTextToXml(Document doc, Element parent, InternItemText text) {
        addElement(doc, parent, "ANZTEXT", text.getQuantityText());
        addElement(doc, parent, "NAME", text.getName());
        addElement(doc, parent, "TEXT", text.getText());
    }
    
    /**
     * Fügt Positions-Referenzen zum XML hinzu
     */
    private void addItemReferencesToXml(Document doc, Element parent, InternItemReferences references) {
        addElement(doc, parent, "URRECHNUNG", references.getOriginalInvoice());
        addElement(doc, parent, "URRECHNUNGPOS", references.getOriginalInvoicePosition());
        addElement(doc, parent, "AUFTRAG", references.getOrder());
        addElement(doc, parent, "AUFTRAGPOS", references.getOrderPosition());
        addElement(doc, parent, "LIEFERSCHEIN", references.getDeliveryNote());
    }
    
    /**
     * Fügt Positions-Spezialflags zum XML hinzu
     */
    private void addItemSpecialFlagsToXml(Document doc, Element parent, InternItemSpecialFlags flags) {
        addElement(doc, parent, "TEXTPOS", flags.getTextPosition());
        addElement(doc, parent, "KAPITELSUMME", flags.getChapterSum());
        addElement(doc, parent, "ZWISCHENSUMME", flags.getSubtotal());
        addElement(doc, parent, "ZSBIS", flags.getSubtotalTo());
        addElement(doc, parent, "ZSVON", flags.getSubtotalFrom());
        addElement(doc, parent, "PAKET", flags.getPackage());
        addElement(doc, parent, "ISTPAKETPREIS", flags.getIsPackagePrice() != null ? flags.getIsPackagePrice().toString() : null);
    }
    
    /**
     * Fügt weitere Positions-Daten zum XML hinzu
     */
    private void addItemAdditionalDataToXml(Document doc, Element parent, InternInvoiceItem item) {
        if (item.getDate() != null) {
            addElement(doc, parent, "DATUM", item.getDate().format(DATE_FORMATTER));
        }
        
        addElement(doc, parent, "DONTPRINT", item.getDontPrint() != null ? item.getDontPrint().toString() : null);
        addElement(doc, parent, "DONTPRINTPRICE", item.getDontPrintPrice() != null ? item.getDontPrintPrice().toString() : null);
        addElement(doc, parent, "POSDRUCKEN", item.getPrintPosition() != null ? item.getPrintPosition().toString() : null);
        addElement(doc, parent, "INVENTAR", item.getInventory());
        addElement(doc, parent, "ISSTUELI", item.getIsBom() != null ? item.getIsBom().toString() : null);
        
        if (item.getServiceDate() != null) {
            addElement(doc, parent, "LEISTUNGSDATUM", item.getServiceDate().format(DATE_FORMATTER));
        }
        
        addElement(doc, parent, "MENGE", item.getQuantity());
        addElement(doc, parent, "POSITION", item.getPosition());
        addElement(doc, parent, "VKME", item.getUnit());
        addElement(doc, parent, "MTZART", item.getMaterialCostType());
        addElement(doc, parent, "MTZSUM", item.getMaterialCostAmount());
    }
    
    /**
     * Fügt weitere Unterpositions-Daten zum XML hinzu
     */
    private void addSubItemAdditionalDataToXml(Document doc, Element parent, InternInvoiceSubItem subItem) {
        if (subItem.getDate() != null) {
            addElement(doc, parent, "DATUM", subItem.getDate().format(DATE_FORMATTER));
        }
        
        addElement(doc, parent, "DONTCALC", subItem.getDontCalculate());
        addElement(doc, parent, "DONTPRINT", subItem.getDontPrint());
        addElement(doc, parent, "DONTPRINTPRICE", subItem.getDontPrintPrice());
        addElement(doc, parent, "MENGE", subItem.getQuantity());
        addElement(doc, parent, "POSITION", subItem.getPosition());
        addElement(doc, parent, "SUBPOS", subItem.getSubPosition());
        addElement(doc, parent, "VKME", subItem.getUnit());
    }
    
    /**
     * Hilfsmethode zum Hinzufügen eines Elements mit String-Wert
     */
    private void addElement(Document doc, Element parent, String name, String value) {
        if (value != null) {
            Element element = doc.createElement(name);
            element.setTextContent(value);
            parent.appendChild(element);
        }
    }
    
    /**
     * Hilfsmethode zum Hinzufügen eines Elements mit BigDecimal-Wert
     */
    private void addElement(Document doc, Element parent, String name, BigDecimal value) {
        if (value != null) {
            Element element = doc.createElement(name);
            element.setTextContent(value.toString());
            parent.appendChild(element);
        }
    }
    
    /**
     * Hilfsmethode zur XML-Escaping.
     */
    private String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}