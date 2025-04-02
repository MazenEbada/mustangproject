/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.worker;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.mustangproject.intern.model.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * XML-Worker zur direkten Verarbeitung von XML-Eingabedaten in ein InternInvoice-Objekt
 */
public class XMLInvoiceWorkerImpl implements XMLInvoiceWorker {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    @Override
    public InternInvoice processInput(String inputData, Map<String, String> conversionKeys) throws Exception {
        try {
            // Parsen des XML-Eingabestrings
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            ByteArrayInputStream inputSource = new java.io.ByteArrayInputStream(inputData.getBytes("UTF-8"));
            Document doc = builder.parse(inputSource);
            doc.getDocumentElement().normalize();
            
            // Erstellen eines InternInvoice-Objekts
            InternInvoice invoice = new InternInvoice();
            
            // Auslesen des Rechnungelements
            Element rechnungElement = getElement(doc, "rechnung");
            
            // Extrahieren und Setzen der Verkäuferadresse
            extractSellerAddress(doc, invoice);
            
            // Extrahieren und Setzen der Kundenadresse
            extractCustomerAddress(doc, invoice);
            
            // Extrahieren und Setzen der Lieferadresse
            extractDeliveryAddress(doc, invoice);
            
            // Extrahieren und Setzen der manuellen Lieferadresse
            extractManualDeliveryAddress(rechnungElement, invoice);
            
            // Extrahieren und Setzen der Rechnungsadresse
            extractInvoiceAddress(doc, invoice);
            
            // Extrahieren und Setzen des Bearbeiters
            extractProcessor(doc, invoice, conversionKeys.get("PERSONALDATA"));
            
            // Extrahieren und Setzen der Texte
            extractTexts(doc, rechnungElement, invoice);
            
            // Extrahieren und Setzen der Zahlungsbedingungen
            extractPaymentTerms(doc, rechnungElement, invoice, conversionKeys.get("ZBDETAILS"));
            
            // Extrahieren und Setzen der E-Rechnungsdaten
            extractEInvoiceData(rechnungElement, invoice);
            
            // Extrahieren und Setzen der Datumsangaben
            extractDates(rechnungElement, invoice);
            
            // Extrahieren und Setzen der Beträge
            extractAmounts(rechnungElement, invoice);
            
            // Extrahieren und Setzen der Steuerinformationen
            extractTax(doc, invoice);
            
            // Extrahieren und Setzen der Frachtkosten
            extractShippingCosts(doc, invoice);
            
            // Extrahieren und Setzen der weiteren Rechnungsdaten
            extractAdditionalInvoiceData(rechnungElement, invoice);
            
            // Extrahieren und Setzen der Rechnungspositionen
            extractInvoiceItems(doc, invoice);
            
            return invoice;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * Extrahiert die Verkäuferadresse aus dem XML und setzt sie im Invoice-Objekt
     */
    private void extractSellerAddress(Document doc, InternInvoice invoice) {
        Element adresseElement = getElement(doc, "supplierAdresse");
        Element firmaElement = getElement(doc, "supplierFirma");
        Element bankElement = getElement(doc, "supplierBank");
        
        InternAddress address = invoice.getSellerAddress();
        
        address.setGlnId(getElementValue(adresseElement, "ANP_GLN"));
        address.setCompanyName1(getElementValue(adresseElement, "FIRMA1"));
        address.setCompanyName2(getElementValue(adresseElement, "FIRMA2"));
        address.setCompanyName3(getElementValue(adresseElement, "FIRMA3"));
        address.setCountryIso(getElementValue(adresseElement, "LANDISO"));
        address.setName(getElementValue(adresseElement, "NAME"));
        address.setDepartment(getElementValue(adresseElement, "ABTEILUNG"));
        address.setCity(getElementValue(adresseElement, "ORT"));
        address.setPostalCode(getElementValue(adresseElement, "PLZ"));
        address.setPostalCode2(getElementValue(adresseElement, "PLZ2"));
        address.setStreet(getElementValue(adresseElement, "STRASSE"));
        address.setFax(getElementValue(adresseElement, "TELEFAX"));
        address.setPhone(getElementValue(adresseElement, "TELEFON"));
        address.setEmail(getElementValue(adresseElement, "EMAIL"));
        address.setDunsNumber(getElementValue(adresseElement, "DUNSNR"));
        address.setCommercialRegister(getElementValue(firmaElement, "HANDELSREGISTER"));
        address.setManagingDirector1(getElementValue(firmaElement, "GF1"));
        address.setManagingDirector2(getElementValue(firmaElement, "GF2"));
        
        // Correct ISNULL logic for USTID
        address.setVatId(getElementValue(adresseElement, "EGSTEUERNR") != null 
                && !getElementValue(adresseElement, "EGSTEUERNR").isEmpty()
                ? getElementValue(adresseElement, "EGSTEUERNR") 
                : getElementValue(firmaElement, "EGSTEUERNR"));
        
        address.setTaxNumber(getElementValue(firmaElement, "STEUERNUMMER"));
        address.setBic(getElementValue(bankElement, "SWIFT"));
        address.setIban(getElementValue(firmaElement, "IBAN"));
        address.setPaymentMethods(getElementValue(firmaElement, "ZAHLARTEN"));
    }
    
    /**
     * Extrahiert die Kundenadresse aus dem XML und setzt sie im Invoice-Objekt
     */
    private void extractCustomerAddress(Document doc, InternInvoice invoice) {
        Element adresseElement = getElement(doc, "customerAdresse");
        Element firmaElement = getElement(doc, "customerFirma");
        Element bankElement = getElement(doc, "customerBank");
        
        InternAddress address = invoice.getCustomerAddress();
        
        address.setGlnId(getElementValue(adresseElement, "ANP_GLN"));
        address.setCompanyName1(getElementValue(adresseElement, "FIRMA1"));
        address.setCompanyName2(getElementValue(adresseElement, "FIRMA2"));
        address.setCompanyName3(getElementValue(adresseElement, "FIRMA3"));
        address.setCountryIso(getElementValue(adresseElement, "LANDISO"));
        address.setName(getElementValue(adresseElement, "NAME"));
        address.setDepartment(getElementValue(adresseElement, "ABTEILUNG"));
        address.setCity(getElementValue(adresseElement, "ORT"));
        address.setPostalCode(getElementValue(adresseElement, "PLZ"));
        address.setPostalCode2(getElementValue(adresseElement, "PLZ2"));
        address.setStreet(getElementValue(adresseElement, "STRASSE"));
        address.setFax(getElementValue(adresseElement, "TELEFAX"));
        address.setPhone(getElementValue(adresseElement, "TELEFON"));
        address.setEmail(getElementValue(adresseElement, "EMAIL"));
        address.setDunsNumber(getElementValue(adresseElement, "DUNSNR"));
        address.setCommercialRegister(getElementValue(firmaElement, "HANDELSREGISTER"));
        address.setManagingDirector1(getElementValue(firmaElement, "GF1"));
        address.setManagingDirector2(getElementValue(firmaElement, "GF2"));
        
        // Correct ISNULL logic for USTID
        address.setVatId(getElementValue(adresseElement, "EGSTEUERNR") != null 
                && !getElementValue(adresseElement, "EGSTEUERNR").isEmpty()
                ? getElementValue(adresseElement, "EGSTEUERNR") 
                : getElementValue(firmaElement, "EGSTEUERNR"));
        
        address.setTaxNumber(getElementValue(firmaElement, "STEUERNUMMER"));
        address.setBic(getElementValue(bankElement, "SWIFT"));
        address.setIban(getElementValue(firmaElement, "IBAN"));
        address.setPaymentMethods(getElementValue(firmaElement, "ZAHLARTEN"));
    }
    
    /**
     * Extrahiert die Lieferadresse aus dem XML und setzt sie im Invoice-Objekt
     * Mit Fallback auf die Kundenadresse, wenn keine spezifische Lieferadresse vorhanden ist
     */
    private void extractDeliveryAddress(Document doc, InternInvoice invoice) {
        Element lAdresseElement = getElement(doc, "customerLAdresse");
        Element lFirmaElement = getElement(doc, "customerLFirma");
        Element lBankElement = getElement(doc, "customerLBank");
        
        // Prüfen, ob die Lieferadresse existiert
        boolean lieferadresseExists = lAdresseElement != null && 
                lAdresseElement.getElementsByTagName("*").getLength() > 0;
        
        if (lieferadresseExists) {
            // Lieferadresse vorhanden, direkt auslesen
            InternAddress address = invoice.getDeliveryAddress();
            
            address.setGlnId(getElementValue(lAdresseElement, "ANP_GLN"));
            address.setCompanyName1(getElementValue(lAdresseElement, "FIRMA1"));
            address.setCompanyName2(getElementValue(lAdresseElement, "FIRMA2"));
            address.setCompanyName3(getElementValue(lAdresseElement, "FIRMA3"));
            address.setCountryIso(getElementValue(lAdresseElement, "LANDISO"));
            address.setName(getElementValue(lAdresseElement, "NAME"));
            address.setDepartment(getElementValue(lAdresseElement, "ABTEILUNG"));
            address.setCity(getElementValue(lAdresseElement, "ORT"));
            address.setPostalCode(getElementValue(lAdresseElement, "PLZ"));
            address.setPostalCode2(getElementValue(lAdresseElement, "PLZ2"));
            address.setStreet(getElementValue(lAdresseElement, "STRASSE"));
            address.setFax(getElementValue(lAdresseElement, "TELEFAX"));
            address.setPhone(getElementValue(lAdresseElement, "TELEFON"));
            address.setEmail(getElementValue(lAdresseElement, "EMAIL"));
            address.setDunsNumber(getElementValue(lAdresseElement, "DUNSNR"));
            address.setCommercialRegister(getElementValue(lFirmaElement, "HANDELSREGISTER"));
            address.setManagingDirector1(getElementValue(lFirmaElement, "GF1"));
            address.setManagingDirector2(getElementValue(lFirmaElement, "GF2"));
            
            // Correct ISNULL logic for USTID
            address.setVatId(getElementValue(lAdresseElement, "EGSTEUERNR") != null 
                    && !getElementValue(lAdresseElement, "EGSTEUERNR").isEmpty()
                    ? getElementValue(lAdresseElement, "EGSTEUERNR") 
                    : getElementValue(lFirmaElement, "EGSTEUERNR"));
            
            address.setTaxNumber(getElementValue(lFirmaElement, "STEUERNUMMER"));
            address.setBic(getElementValue(lBankElement, "SWIFT"));
            address.setIban(getElementValue(lFirmaElement, "IBAN"));
            address.setPaymentMethods(getElementValue(lFirmaElement, "ZAHLARTEN"));
        } else {
            // Keine Lieferadresse vorhanden, Kundenadresse als Fallback verwenden
            InternAddress customerAddress = invoice.getCustomerAddress();
            InternAddress deliveryAddress = invoice.getDeliveryAddress();
            
            // Kopieren aller Felder von customerAddress zu deliveryAddress
            deliveryAddress.setGlnId(customerAddress.getGlnId());
            deliveryAddress.setCompanyName1(customerAddress.getCompanyName1());
            deliveryAddress.setCompanyName2(customerAddress.getCompanyName2());
            deliveryAddress.setCompanyName3(customerAddress.getCompanyName3());
            deliveryAddress.setCountryIso(customerAddress.getCountryIso());
            deliveryAddress.setName(customerAddress.getName());
            deliveryAddress.setDepartment(customerAddress.getDepartment());
            deliveryAddress.setCity(customerAddress.getCity());
            deliveryAddress.setPostalCode(customerAddress.getPostalCode());
            deliveryAddress.setPostalCode2(customerAddress.getPostalCode2());
            deliveryAddress.setStreet(customerAddress.getStreet());
            deliveryAddress.setFax(customerAddress.getFax());
            deliveryAddress.setPhone(customerAddress.getPhone());
            deliveryAddress.setEmail(customerAddress.getEmail());
            deliveryAddress.setDunsNumber(customerAddress.getDunsNumber());
            deliveryAddress.setVatId(customerAddress.getVatId());
            deliveryAddress.setCommercialRegister(customerAddress.getCommercialRegister());
            deliveryAddress.setManagingDirector1(customerAddress.getManagingDirector1());
            deliveryAddress.setManagingDirector2(customerAddress.getManagingDirector2());
            deliveryAddress.setTaxNumber(customerAddress.getTaxNumber());
            deliveryAddress.setBic(customerAddress.getBic());
            deliveryAddress.setIban(customerAddress.getIban());
            deliveryAddress.setPaymentMethods(customerAddress.getPaymentMethods());
        }
    }
    
    /**
     * Extrahiert die manuelle Lieferadresse aus dem Rechnungelement und setzt sie im Invoice-Objekt
     */
    private void extractManualDeliveryAddress(Element rechnungElement, InternInvoice invoice) {
        InternAddress address = invoice.getManualDeliveryAddress();
        
        address.setCompanyName1(getElementValue(rechnungElement, "LFIRMA"));
        address.setCompanyName2(getElementValue(rechnungElement, "LFIRMA2"));
        address.setCompanyName3(getElementValue(rechnungElement, "LFIRMA3"));
        address.setCountryIso(getElementValue(rechnungElement, "LANDISO"));
        address.setName(getElementValue(rechnungElement, "LNAME"));
        address.setDepartment(getElementValue(rechnungElement, "LABTEILUNG"));
        address.setCity(getElementValue(rechnungElement, "LORT"));
        address.setPostalCode(getElementValue(rechnungElement, "LPLZ"));
        address.setPostalCode2(getElementValue(rechnungElement, "LPLZ2"));
        address.setStreet(getElementValue(rechnungElement, "LSTRASSE"));
        address.setFax(getElementValue(rechnungElement, "LTELEFAX"));
        address.setPhone(getElementValue(rechnungElement, "LTELEFON"));
        address.setEmail(getElementValue(rechnungElement, "EMAIL"));
        address.setVatId(getElementValue(rechnungElement, "USTID"));
        address.setDunsNumber(getElementValue(rechnungElement, "DUNSNR"));
    }
    
    /**
     * Extrahiert die Rechnungsadresse aus dem XML und setzt sie im Invoice-Objekt
     * Mit Fallback auf die Kundenadresse, wenn keine spezifische Rechnungsadresse vorhanden ist
     */
    private void extractInvoiceAddress(Document doc, InternInvoice invoice) {
        Element rAdresseElement = getElement(doc, "customerRAdresse");
        Element rFirmaElement = getElement(doc, "customerRFirma");
        Element rBankElement = getElement(doc, "customerRBank");
        
        // Prüfen, ob die Rechnungsadresse existiert
        boolean rechnungsadresseExists = rAdresseElement != null && 
                rAdresseElement.getElementsByTagName("*").getLength() > 0;
        
        if (rechnungsadresseExists) {
            // Rechnungsadresse vorhanden, direkt auslesen
            InternAddress address = invoice.getInvoiceAddress();
            
            address.setGlnId(getElementValue(rAdresseElement, "ANP_GLN"));
            address.setCompanyName1(getElementValue(rAdresseElement, "FIRMA1"));
            address.setCompanyName2(getElementValue(rAdresseElement, "FIRMA2"));
            address.setCompanyName3(getElementValue(rAdresseElement, "FIRMA3"));
            address.setCountryIso(getElementValue(rAdresseElement, "LANDISO"));
            address.setName(getElementValue(rAdresseElement, "NAME"));
            address.setDepartment(getElementValue(rAdresseElement, "ABTEILUNG"));
            address.setCity(getElementValue(rAdresseElement, "ORT"));
            address.setPostalCode(getElementValue(rAdresseElement, "PLZ"));
            address.setPostalCode2(getElementValue(rAdresseElement, "PLZ2"));
            address.setStreet(getElementValue(rAdresseElement, "STRASSE"));
            address.setFax(getElementValue(rAdresseElement, "TELEFAX"));
            address.setPhone(getElementValue(rAdresseElement, "TELEFON"));
            address.setEmail(getElementValue(rAdresseElement, "EMAIL"));
            address.setDunsNumber(getElementValue(rAdresseElement, "DUNSNR"));
            address.setCommercialRegister(getElementValue(rFirmaElement, "HANDELSREGISTER"));
            address.setManagingDirector1(getElementValue(rFirmaElement, "GF1"));
            address.setManagingDirector2(getElementValue(rFirmaElement, "GF2"));
            
            // Correct ISNULL logic for USTID
            address.setVatId(getElementValue(rAdresseElement, "EGSTEUERNR") != null 
                    && !getElementValue(rAdresseElement, "EGSTEUERNR").isEmpty()
                    ? getElementValue(rAdresseElement, "EGSTEUERNR") 
                    : getElementValue(rFirmaElement, "EGSTEUERNR"));
            
            address.setTaxNumber(getElementValue(rFirmaElement, "STEUERNUMMER"));
            address.setBic(getElementValue(rBankElement, "SWIFT"));
            address.setIban(getElementValue(rFirmaElement, "IBAN"));
            address.setPaymentMethods(getElementValue(rFirmaElement, "ZAHLARTEN"));
        } else {
            // Keine Rechnungsadresse vorhanden, Kundenadresse als Fallback verwenden
            InternAddress customerAddress = invoice.getCustomerAddress();
            InternAddress invoiceAddress = invoice.getInvoiceAddress();
            
            // Kopieren aller Felder von customerAddress zu invoiceAddress
            invoiceAddress.setGlnId(customerAddress.getGlnId());
            invoiceAddress.setCompanyName1(customerAddress.getCompanyName1());
            invoiceAddress.setCompanyName2(customerAddress.getCompanyName2());
            invoiceAddress.setCompanyName3(customerAddress.getCompanyName3());
            invoiceAddress.setCountryIso(customerAddress.getCountryIso());
            invoiceAddress.setName(customerAddress.getName());
            invoiceAddress.setDepartment(customerAddress.getDepartment());
            invoiceAddress.setCity(customerAddress.getCity());
            invoiceAddress.setPostalCode(customerAddress.getPostalCode());
            invoiceAddress.setPostalCode2(customerAddress.getPostalCode2());
            invoiceAddress.setStreet(customerAddress.getStreet());
            invoiceAddress.setFax(customerAddress.getFax());
            invoiceAddress.setPhone(customerAddress.getPhone());
            invoiceAddress.setEmail(customerAddress.getEmail());
            invoiceAddress.setDunsNumber(customerAddress.getDunsNumber());
            invoiceAddress.setVatId(customerAddress.getVatId());
            invoiceAddress.setCommercialRegister(customerAddress.getCommercialRegister());
            invoiceAddress.setManagingDirector1(customerAddress.getManagingDirector1());
            invoiceAddress.setManagingDirector2(customerAddress.getManagingDirector2());
            invoiceAddress.setTaxNumber(customerAddress.getTaxNumber());
            invoiceAddress.setBic(customerAddress.getBic());
            invoiceAddress.setIban(customerAddress.getIban());
            invoiceAddress.setPaymentMethods(customerAddress.getPaymentMethods());
        }
    }
    
    /**
     * Extrahiert die Bearbeiterdaten aus dem XML und setzt sie im Invoice-Objekt
     */
    private void extractProcessor(Document doc, InternInvoice invoice, String personaldata) {
        Element personalElement = getElement(doc, "personal");
        Element adresseElement = getElement(doc, "personalAdresse");
        
        InternPerson processor = invoice.getProcessor();
        
        processor.setName(getElementValue(personalElement, "NAME"));
        if (personaldata != null && personaldata.equalsIgnoreCase("PERSONAL")) {
            processor.setEmail(getElementValue(personalElement, "ANP_EMAIL"));
            processor.setPhone(getElementValue(personalElement, "ANP_TELDURCHWAHL"));
            processor.setFax(getElementValue(personalElement, "ANP_FAXDURCHWAHL"));
            processor.setDepartment(getElementValue(personalElement, "ABTEILUNG"));
        } else {
            processor.setEmail(getElementValue(adresseElement, "EMAIL"));
            processor.setPhone(getElementValue(adresseElement, "TELEFON"));
            processor.setFax(getElementValue(adresseElement, "TELEFAX"));
            processor.setDepartment(getElementValue(adresseElement, "ABTEILUNG"));
        }
    }
    
    /**
     * Extrahiert die Textdaten aus dem XML und setzt sie im Invoice-Objekt
     */
    private void extractTexts(Document doc, Element rechnungElement, InternInvoice invoice) {
        Element stdtxtElement = getElement(doc, "stdtxt");
        
        InternInvoiceTexts texts = invoice.getTexts();
        
        texts.setStandardText(getElementValue(stdtxtElement, "HTMLSTDTXT"));
        texts.setFreeText(getElementValue(rechnungElement, "HTMLFREITEXT"));
        texts.setCustomerText(getElementValue(rechnungElement, "HTMLIHRTEXT"));
        texts.setFooterText(getElementValue(rechnungElement, "HTMLFUSSTEXT"));
        texts.setHeaderText(getElementValue(rechnungElement, "HTMLKOPFTEXT"));
    }
    
    /**
     * Extrahiert die Zahlungsbedingungen aus dem XML und setzt sie im Invoice-Objekt
     */
    private void extractPaymentTerms(Document doc, Element rechnungElement, InternInvoice invoice, String xmlzbDetails) throws ParserConfigurationException, SAXException, java.io.IOException {
        Element zahlungsbedElement = getElement(doc, "zahlungsbed");
        Element zahlungsbedlngElement = getElement(doc, "zahlungsbedlng");
        
        InternPaymentTerms paymentTerms = invoice.getPaymentTerms();
        
        if (zahlungsbedlngElement != null)
            paymentTerms.setPaymentTermsText(getElementValue(zahlungsbedlngElement, "HTMLZBTXT"));
        else
            paymentTerms.setPaymentTermsText(getElementValue(zahlungsbedElement, "HTMLZBTXT"));
        
        String valueDateStr = getElementValue(rechnungElement, "VALUTADATUM");
        if (valueDateStr != null && !valueDateStr.isEmpty()) {
            try {
                paymentTerms.setValueDate(LocalDate.parse(valueDateStr, DATE_FORMATTER));
            } catch (DateTimeParseException e) {
                // Ignoriere Datumsfehler
            }
        }
        
        if (xmlzbDetails != null && !xmlzbDetails.isEmpty()) {
            // Parse the XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new java.io.StringReader(xmlzbDetails)));
            Element root = document.getDocumentElement();
            
            NodeList nodeList = root.getChildNodes();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                // Check if the node is an element
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String fieldName = element.getTagName().toUpperCase();
                    String value = element.getTextContent().trim();
                    System.out.println(fieldName + ": " + value);
                    paymentTerms.addAdditionalData(fieldName, value);
                }
            }
        }
    }
    
    /**
     * Extrahiert die E-Rechnungsdaten aus dem XML und setzt sie im Invoice-Objekt
     */
    private void extractEInvoiceData(Element rechnungElement, InternInvoice invoice) {
        InternEInvoiceData eInvoiceData = invoice.getEInvoiceData();
        
        eInvoiceData.setRouteId(getElementValue(rechnungElement, "LEITWEGID"));
        eInvoiceData.setDispatchMethod(getElementValue(rechnungElement, "EINVOICE_DISPATCH"));
        eInvoiceData.setInterfaceType(getElementValue(rechnungElement, "EINVOICE_INTERFACE"));
    }
    
    /**
     * Extrahiert die Datumsangaben aus dem XML und setzt sie im Invoice-Objekt
     */
    private void extractDates(Element rechnungElement, InternInvoice invoice) {
        InternInvoiceMetadata metadata = invoice.getMetadata();
        
        // Rechnungsdatum
        String invoiceDateStr = getElementValue(rechnungElement, "DATUM");
        if (invoiceDateStr != null && !invoiceDateStr.isEmpty()) {
            metadata.addAdditionalData("INVOICE_DATE", invoiceDateStr);
        }
        
        // Leistungsdatum
        String serviceDateStr = getElementValue(rechnungElement, "LEISTUNGSDATUM");
        if (serviceDateStr != null && !serviceDateStr.isEmpty()) {
            metadata.addAdditionalData("SERVICE_DATE", serviceDateStr);
        }
        
        // Bestelldatum
        String orderDateStr = getElementValue(rechnungElement, "BESTELLDATUM");
        if (orderDateStr != null && !orderDateStr.isEmpty()) {
            try {
                metadata.setOrderDate(LocalDate.parse(orderDateStr, DATE_FORMATTER));
            } catch (DateTimeParseException e) {
                // Ignoriere Datumsfehler
            }
        }
        
        // Liefertermin
        String deliveryDateStr = getElementValue(rechnungElement, "ANP_LIEFERTERMIN");
        if (deliveryDateStr != null && !deliveryDateStr.isEmpty()) {
            try {
                metadata.setDeliveryDate(LocalDate.parse(deliveryDateStr, DATE_FORMATTER));
            } catch (DateTimeParseException e) {
                // Ignoriere Datumsfehler
            }
        }
    }
    
    /**
     * Extrahiert die Beträge aus dem XML und setzt sie im Invoice-Objekt
     */
    private void extractAmounts(Element rechnungElement, InternInvoice invoice) {
        InternInvoiceAmounts amounts = invoice.getAmounts();
        
        // Rabattpreis
        String discountAmountStr = getElementValue(rechnungElement, "RABATTPREIS");
        if (discountAmountStr != null && !discountAmountStr.isEmpty()) {
            try {
                amounts.setDiscountAmount(new BigDecimal(discountAmountStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Nettoerlös
        String netAmountStr = getElementValue(rechnungElement, "NETTOERLOES");
        if (netAmountStr != null && !netAmountStr.isEmpty()) {
            try {
                amounts.setNetAmount(new BigDecimal(netAmountStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // UST-Preis
        String taxAmountStr = getElementValue(rechnungElement, "USTPREIS");
        if (taxAmountStr != null && !taxAmountStr.isEmpty()) {
            try {
                amounts.setTaxAmount(new BigDecimal(taxAmountStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Brutto
        String grossAmountStr = getElementValue(rechnungElement, "BRUTTO");
        if (grossAmountStr != null && !grossAmountStr.isEmpty()) {
            try {
                amounts.setGrossAmount(new BigDecimal(grossAmountStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
    }
    
    /**
     * Extrahiert die Steuerinformationen aus dem XML und setzt sie im Invoice-Objekt
     */
    private void extractTax(Document doc, InternInvoice invoice) {
        Element ustElement = getElement(doc, "ust");
        
        InternTax tax = invoice.getTax();
        
        tax.setTaxCategory(getElementValue(ustElement, "KATEGORIE"));
        
        // Steuerbetrag
        String taxAmountStr = getElementValue(ustElement, "BETRAG");
        if (taxAmountStr != null && !taxAmountStr.isEmpty()) {
            try {
                tax.setTaxAmount(new BigDecimal(taxAmountStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Steuersatz
        String taxRateStr = getElementValue(ustElement, "UST");
        if (taxRateStr != null && !taxRateStr.isEmpty()) {
            try {
                tax.setTaxRate(new BigDecimal(taxRateStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Steuerbasis
        String taxBaseStr = getElementValue(ustElement, "ERLOES");
        if (taxBaseStr != null && !taxBaseStr.isEmpty()) {
            try {
                tax.setTaxBase(new BigDecimal(taxBaseStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
    }
    
    /**
     * Extrahiert die Frachtkosteninformationen aus dem XML und setzt sie im Invoice-Objekt
     */
    private void extractShippingCosts(Document doc, InternInvoice invoice) {
        Element fkElement = getElement(doc, "frachtkosten");
        
        InternShippingCosts shippingCosts = invoice.getShippingCosts();
        
        // Frachtkosten-Betrag
        String amountStr = getElementValue(fkElement, "FRACHTKOSTENBETRAG");
        if (amountStr != null && !amountStr.isEmpty()) {
            try {
                shippingCosts.setAmount(new BigDecimal(amountStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Frachtkosten-UST-Kategorie
        shippingCosts.setTaxCategory(getElementValue(fkElement, "FRACHTKOSTENUSTKATEGORIE"));
        
        // Frachtkosten-UST-Prozent
        String taxRateStr = getElementValue(fkElement, "FRACHTKOSTENUSTPROZENT");
        if (taxRateStr != null && !taxRateStr.isEmpty()) {
            try {
                shippingCosts.setTaxRate(new BigDecimal(taxRateStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
    }
    
    /**
     * Extrahiert weitere Rechnungsdaten aus dem XML und setzt sie im Invoice-Objekt
     */
    private void extractAdditionalInvoiceData(Element rechnungElement, InternInvoice invoice) {
        InternInvoiceMetadata metadata = invoice.getMetadata();
        
        metadata.setInvoiceNumber(getElementValue(rechnungElement, "RECHNUNG"));
        metadata.setInvoiceType(getElementValue(rechnungElement, "ART"));
        metadata.setInvoiceTypePa(getElementValue(rechnungElement, "PARECHNUNGSART"));
        metadata.setCurrency(getElementValue(rechnungElement, "WAEHRUNG"));
        metadata.setCustomerOrderNumber(getElementValue(rechnungElement, "IHREBESTELLUNG"));
        metadata.setOriginalInvoice(getElementValue(rechnungElement, "URRECHNUNG"));
        metadata.setOrderNumber(getElementValue(rechnungElement, "AUFTRAG"));
        metadata.setLanguage(getElementValue(rechnungElement, "SPRACHE"));
    }
    
    /**
     * Extrahiert die Rechnungspositionen aus dem XML und fügt sie zum Invoice-Objekt hinzu
     */
    private void extractInvoiceItems(Document doc, InternInvoice invoice) {
        // Schritt 1: Gruppiere rechnungpospos nach ihrem db_pos-Attribut
        Map<String, List<InternInvoiceSubItem>> subItemsMap = extractInvoiceSubItems(doc);
        
        // Schritt 2: Extrahiere die Hauptpositionen
        NodeList positions = doc.getElementsByTagName("rechnungpos");
        
        for (int i = 0; i < positions.getLength(); i++) {
            Node position = positions.item(i);
            
            if (position.getNodeType() == Node.ELEMENT_NODE) {
                Element sourceElement = (Element) position;
                
                InternInvoiceItem item = new InternInvoiceItem();
                
                // Betragsdaten der Position extrahieren
                extractItemAmounts(sourceElement, item.getAmounts());
                
                // Stammdaten der Position extrahieren
                extractItemMasterData(sourceElement, item.getMasterData());
                
                // Textdaten der Position extrahieren
                extractItemText(sourceElement, item.getText());
                
                // Referenzdaten der Position extrahieren
                extractItemReferences(sourceElement, item.getReferences());
                
                // Spezielle Flags der Position extrahieren
                extractItemSpecialFlags(sourceElement, item.getSpecialFlags());
                
                // Weitere Daten der Position extrahieren
                extractItemAdditionalData(sourceElement, item);
                
                // Verknüpfe passende Unterpositionen
                String dbPos = sourceElement.getAttribute("anp_db_pos");
                if (dbPos != null && !dbPos.isEmpty() && subItemsMap.containsKey(dbPos)) {
                    item.setSubItems(subItemsMap.get(dbPos));
                }
                
                // Position zur Rechnung hinzufügen
                invoice.addItem(item);
            }
        }
    }
    
    /**
     * Extrahiert alle Unterpositionen aus dem XML und gruppiert sie nach dem db_pos-Attribut
     */
    private Map<String, List<InternInvoiceSubItem>> extractInvoiceSubItems(Document doc) {
        Map<String, List<InternInvoiceSubItem>> subItemsMap = new HashMap<>();
        NodeList unterpositions = doc.getElementsByTagName("rechnungpospos");
        
        for (int i = 0; i < unterpositions.getLength(); i++) {
            Node unterposition = unterpositions.item(i);
            
            if (unterposition.getNodeType() == Node.ELEMENT_NODE) {
                Element sourceElement = (Element) unterposition;
                
                // db_pos-Attribut extrahieren
                String dbPos = sourceElement.getAttribute("anp_db_pos");
                if (dbPos == null || dbPos.isEmpty()) continue;
                
                // Unterposition erstellen
                InternInvoiceSubItem subItem = new InternInvoiceSubItem();
                
                // Betragsdaten der Unterposition extrahieren
                extractItemAmounts(sourceElement, subItem.getAmounts());
                
                // Stammdaten der Unterposition extrahieren
                extractItemMasterData(sourceElement, subItem.getMasterData());
                
                // Textdaten der Unterposition extrahieren
                InternItemText text = subItem.getText();
                text.setName(getElementValue(sourceElement, "NAME"));
                text.setText(getElementValue(sourceElement, "HTML"));
                
                // Weitere Daten der Unterposition extrahieren
                String dateStr = getElementValue(sourceElement, "DATUM");
                if (dateStr != null && !dateStr.isEmpty()) {
                    try {
                        subItem.setDate(LocalDate.parse(dateStr, DATE_FORMATTER));
                    } catch (DateTimeParseException e) {
                        // Ignoriere Datumsfehler
                    }
                }
                
                subItem.setDontCalculate(getElementValue(sourceElement, "DONTCALC"));
                subItem.setDontPrint(getElementValue(sourceElement, "DONTPRINT"));
                subItem.setDontPrintPrice(getElementValue(sourceElement, "DONTPRINTPRICE"));
                
                String quantityStr = getElementValue(sourceElement, "MENGE");
                if (quantityStr != null && !quantityStr.isEmpty()) {
                    try {
                        subItem.setQuantity(new BigDecimal(quantityStr));
                    } catch (NumberFormatException e) {
                        // Ignoriere Zahlenfehler
                    }
                }
                
                subItem.setPosition(getElementValue(sourceElement, "POSITION"));
                subItem.setSubPosition(getElementValue(sourceElement, "SUBPOS"));
                subItem.setUnit(getElementValue(sourceElement, "VKME"));
                
                // Unterposition zur Map hinzufügen
                subItemsMap.computeIfAbsent(dbPos, k -> new ArrayList<>()).add(subItem);
            }
        }
        
        return subItemsMap;
    }
    
    /**
     * Extrahiert die Betragsdaten einer Position oder Unterposition
     */
    private void extractItemAmounts(Element element, InternItemAmounts amounts) {
        // Erlös
        String revenueStr = getElementValue(element, "ERLOES");
        if (revenueStr != null && !revenueStr.isEmpty()) {
            try {
                amounts.setRevenue(new BigDecimal(revenueStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Nettoerlös
        String netRevenueStr = getElementValue(element, "NETTOERLOES");
        if (netRevenueStr != null && !netRevenueStr.isEmpty()) {
            try {
                amounts.setNetRevenue(new BigDecimal(netRevenueStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Brutto
        String grossStr = getElementValue(element, "PREIS");
        if (grossStr != null && !grossStr.isEmpty()) {
            try {
                amounts.setGross(new BigDecimal(grossStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Netto
        String netStr = getElementValue(element, "NETTO");
        if (netStr != null && !netStr.isEmpty()) {
            try {
                amounts.setNet(new BigDecimal(netStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Nettoantrag
        String netApplicationStr = getElementValue(element, "NETTOANTRAG");
        if (netApplicationStr != null && !netApplicationStr.isEmpty()) {
            try {
                amounts.setNetApplication(new BigDecimal(netApplicationStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Packmenge
        String packageQuantityStr = getElementValue(element, "PACKMENGE");
        if (packageQuantityStr != null && !packageQuantityStr.isEmpty()) {
            try {
                amounts.setPackageQuantity(new BigDecimal(packageQuantityStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Preismengeneinheit
        amounts.setPricePerUnit(getElementValue(element, "PREISME"));
        
        // Preis
        String priceStr = getElementValue(element, "PREIS");
        if (priceStr != null && !priceStr.isEmpty()) {
            try {
                amounts.setPrice(new BigDecimal(priceStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Mengenrabatt
        String quantityDiscountStr = getElementValue(element, "MRABATT");
        if (quantityDiscountStr != null && !quantityDiscountStr.isEmpty()) {
            try {
                amounts.setQuantityDiscount(new BigDecimal(quantityDiscountStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Mengenrabattpreis
        String quantityDiscountAmountStr = getElementValue(element, "MRABATTPREIS");
        if (quantityDiscountAmountStr != null && !quantityDiscountAmountStr.isEmpty()) {
            try {
                amounts.setQuantityDiscountAmount(new BigDecimal(quantityDiscountAmountStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Rabatt
        String discountStr = getElementValue(element, "RABATT");
        if (discountStr != null && !discountStr.isEmpty()) {
            try {
                amounts.setDiscount(new BigDecimal(discountStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Rabatt2
        String discount2Str = getElementValue(element, "RABATT2");
        if (discount2Str != null && !discount2Str.isEmpty()) {
            try {
                amounts.setDiscount2(new BigDecimal(discount2Str));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Rabattpreis
        String discountAmountStr = getElementValue(element, "RABATTPREIS");
        if (discountAmountStr != null && !discountAmountStr.isEmpty()) {
            try {
                amounts.setDiscountAmount(new BigDecimal(discountAmountStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Rabattpreis2
        String discountAmount2Str = getElementValue(element, "RABATTPREIS2");
        if (discountAmount2Str != null && !discountAmount2Str.isEmpty()) {
            try {
                amounts.setDiscountAmount2(new BigDecimal(discountAmount2Str));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Stückerlös
        String unitRevenueStr = getElementValue(element, "STKERLOES");
        if (unitRevenueStr != null && !unitRevenueStr.isEmpty()) {
            try {
                amounts.setUnitRevenue(new BigDecimal(unitRevenueStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Steuerinformationen
        InternItemTax tax = amounts.getTax();
        
        // UST-Preis
        String taxAmountStr = getElementValue(element, "USTPREIS");
        if (taxAmountStr != null && !taxAmountStr.isEmpty()) {
            try {
                tax.setTaxAmount(new BigDecimal(taxAmountStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // UST-Kategorie
        tax.setTaxCategory(getElementValue(element, "USTKATEGORIE"));
        
        // UST-Satz
        String taxRateStr = getElementValue(element, "UST");
        if (taxRateStr != null && !taxRateStr.isEmpty()) {
            try {
                tax.setTaxRate(new BigDecimal(taxRateStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
    }
    
    /**
     * Extrahiert die Stammdaten einer Position oder Unterposition
     */
    private void extractItemMasterData(Element element, InternItemMasterData masterData) {
        masterData.setBatch(getElementValue(element, "CHARGE"));
        masterData.setCountryOfOrigin(getElementValue(element, "URSPRUNGSLANDISO"));
        masterData.setCustomsTariffNumber(getElementValue(element, "ZOLLTARIFNR"));
        masterData.setEanCode(getElementValue(element, "EANCODE"));
        masterData.setArticleNumber(getElementValue(element, "ARTIKEL"));
        masterData.setCustomerArticleNumber(getElementValue(element, "KARTIKEL"));
    }
    
    /**
     * Extrahiert die Textdaten einer Position
     */
    private void extractItemText(Element element, InternItemText text) {
        text.setQuantityText(getElementValue(element, "HTMLANZTEXT"));
        
        // Wenn NAME leer ist, verwende NAMEINTERN
        if (getElementValue(element, "NAME") == null || getElementValue(element, "NAME").isEmpty())
            text.setName(getElementValue(element, "NAMEINTERN"));
        else
            text.setName(getElementValue(element, "NAME"));
        
        text.setText(getElementValue(element, "HTMLTEXT"));
    }
    
    /**
     * Extrahiert die Referenzdaten einer Position
     */
    private void extractItemReferences(Element element, InternItemReferences references) {
        references.setOriginalInvoice(getElementValue(element, "URRECHNUNG"));
        references.setOriginalInvoicePosition(getElementValue(element, "URRECHNUNGPOS"));
        references.setOrder(getElementValue(element, "AUFTRAG"));
        references.setOrderPosition(getElementValue(element, "AUFTRAGPOS"));
        references.setDeliveryNote(getElementValue(element, "LIEFERSCHEIN"));
    }
    
    /**
     * Extrahiert die speziellen Flags einer Position
     */
    private void extractItemSpecialFlags(Element element, InternItemSpecialFlags flags) {
        flags.setTextPosition(getElementValue(element, "TEXTPOS"));
        flags.setChapterSum(getElementValue(element, "KAPITELSUMME"));
        flags.setSubtotal(getElementValue(element, "ZWISCHENSUMME"));
        flags.setSubtotalTo(getElementValue(element, "ZSBIS"));
        flags.setSubtotalFrom(getElementValue(element, "ZSVON"));
        flags.setPackage(getElementValue(element, "PAKET"));
        
        // ISTPAKETPREIS berechnen
        String position = getElementValue(element, "POSITION");
        String setNr = getElementValue(element, "SETNR");
        flags.setIsPackagePrice(position != null && setNr != null && position.equals(setNr));
    }
    
    /**
     * Extrahiert die weiteren Daten einer Position
     */
    private void extractItemAdditionalData(Element element, InternInvoiceItem item) {
        // Datum
        String dateStr = getElementValue(element, "DATUM");
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                item.setDate(LocalDate.parse(dateStr, DATE_FORMATTER));
            } catch (DateTimeParseException e) {
                // Ignoriere Datumsfehler
            }
        }
        
        // DontPrint
        String dontPrintStr = getElementValue(element, "DONTPRINT");
        if (dontPrintStr != null) {
            item.setDontPrint(Boolean.valueOf(dontPrintStr));
        }
        
        // DontPrintPrice
        String dontPrintPriceStr = getElementValue(element, "DONTPRINTPRICE");
        if (dontPrintPriceStr != null) {
            item.setDontPrintPrice(Boolean.valueOf(dontPrintPriceStr));
        }
        
        // PrintPosition
        String printPositionStr = getElementValue(element, "POSDRUCKEN");
        if (printPositionStr != null) {
            item.setPrintPosition(Boolean.valueOf(printPositionStr));
        }
        
        // Inventar
        item.setInventory(getElementValue(element, "INVENTAR"));
        
        // IsBom
        String isBomStr = getElementValue(element, "ISSTUELI");
        if (isBomStr != null) {
            item.setIsBom(Boolean.valueOf(isBomStr));
        }
        
        // Leistungsdatum
        String serviceDateStr = getElementValue(element, "LEISTUNGSDATUM");
        if (serviceDateStr != null && !serviceDateStr.isEmpty()) {
            try {
                item.setServiceDate(LocalDate.parse(serviceDateStr, DATE_FORMATTER));
            } catch (DateTimeParseException e) {
                // Ignoriere Datumsfehler
            }
        }
        
        // Menge
        String quantityStr = getElementValue(element, "MENGE");
        if (quantityStr != null && !quantityStr.isEmpty()) {
            try {
                item.setQuantity(new BigDecimal(quantityStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
        
        // Position
        item.setPosition(getElementValue(element, "POSITION"));
        
        // Verkaufsmengeneinheit
        item.setUnit(getElementValue(element, "VKME"));
        
        // Materialzuschlagsart
        item.setMaterialCostType(getElementValue(element, "MTZART"));
        
        // Materialzuschlagssumme
        String materialCostAmountStr = getElementValue(element, "MTZSUM");
        if (materialCostAmountStr != null && !materialCostAmountStr.isEmpty()) {
            try {
                item.setMaterialCostAmount(new BigDecimal(materialCostAmountStr));
            } catch (NumberFormatException e) {
                // Ignoriere Zahlenfehler
            }
        }
    }
    
    /**
     * Hilfsmethode zum sicheren Abrufen des Wertes eines XML-Elements.
     */
    private static String getElementValue(Element element, String tagName) {
        if (element == null) return null;
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0 && nodeList.item(0).getTextContent() != null && !nodeList.item(0).getTextContent().isEmpty()) {
            return nodeList.item(0).getTextContent().trim();
        }
        return null; // Return null if not found or empty
    }
    
    /**
     * Hilfsmethode zum Abrufen eines XML-Elements anhand des Tag-Namens.
     */
    private static Element getElement(Document doc, String tagName) {
        NodeList nodeList = doc.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return (Element) nodeList.item(0);
        }
        return null; // Return null if not found
    }
}