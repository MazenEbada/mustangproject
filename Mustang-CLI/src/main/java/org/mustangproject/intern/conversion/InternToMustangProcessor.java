
/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.conversion;

import org.mustangproject.Allowance;
import org.mustangproject.BankDetails;
import org.mustangproject.Charge;
import org.mustangproject.Contact;
import org.mustangproject.DirectDebit;
import org.mustangproject.IncludedNote;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.LegalOrganisation;
import org.mustangproject.Product;
import org.mustangproject.ReferencedDocument;
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
        Map.entry("Karton", "XCT"),
        Map.entry("kg", "KGM"),
        Map.entry("kPa", "KPA"),
        Map.entry("l", "LTR"),
        Map.entry("m", "MTR"),
        Map.entry("m2", "MTK"),
        Map.entry("mbar", "MBR"),
        Map.entry("mi", "SMI"),
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
    // CUSTOM:AMC/MEB:23.11.2026:V2.9 - Steuertexte der uebernommenen Positionen (USTSATZPOS.STDTXT_AR), je Text einmal
    private final java.util.Set<String> taxTexts = new java.util.LinkedHashSet<>();

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
            case "E":
                // ZUGFeRD/Factur-X Profil EN16931 (Comfort), Guideline urn:cen.eu:en16931:2017.
                // Von Portalen/Grosskunden gefordert (z.B. EUROBAUSTOFF), die EXTENDED nicht lesen.
                einterface = "EN16931";
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
        // trim: der SysConf-Standardeintrag lautet "ZUGFeRD_2.0:Z " (mit Leerzeichen)
        return interface_ != null && !interface_.trim().isEmpty() ? interface_.trim() : "Z"; // Default zu ZUGFeRD
    }

    /**
     * XRechnung (X) und ZUGFeRD EN16931 (E) erlauben nur EINE Zahlungsbedingung
     * (SpecifiedTradePaymentTerms 0..1). Skonto geht dort als BT-20-Text
     * "#SKONTO#TAGE=..#PROZENT=..#" statt als eigene Zahlungsbedingung.
     * Nur EXTENDED (Z) kennt mehrere Zahlungsbedingungen.
     */
    private boolean nurEineZahlungsbedingung() {
        return "X".equals(applusEinterface) || "E".equals(applusEinterface);
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
        taxTexts.clear();

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

        // Step 5b: Lieferscheinbezug (BT-16)
        extractDespatchAdvice(mustangInvoice);

        // Step 6: Extract Payment Terms
        extractInvoicePaymentTerms(mustangInvoice);

        // Step 6b: Zahlart des Kunden (Lastschrift statt Ueberweisung)
        applyPaymentMeans(mustangInvoice, seller);

        // Step 7: Extract Dates
        extractDates(mustangInvoice);

        // Step 8: Extract Invoice Line Items
        List<Item> items = extractInvoiceItems();
        for (Item item : items) {
            mustangInvoice.addItem(item);
        }

        // Step 8b: verrechnete Anzahlungen/Abschlaege als Vorauszahlung (BT-113) mit Rechnungsbezug (BG-3)
        addAdvancePayments(mustangInvoice);

        // Step 9: Extract Additional Notes
        Map<String, String> textFields = extractTextFields();
        integrateTexts(mustangInvoice, textFields);
        integrateTaxTexts(mustangInvoice);
        integrateBuyerAccountingReference(mustangInvoice, textFields);

        // Step 10: Rundungsausgleich bei verrechneten Anzahlungen (BT-114)
        applyAdvanceRounding(mustangInvoice);
    }

    /**
     * CUSTOM:AMC/MEB:17.11.2026:V2.8 - Rundungsausgleich bei Vorauszahlung (BT-114 RoundingAmount).
     *
     * <p>Mit verrechneten Anzahlungen rechnet die E-Rechnung die Steuer auf die VOLLE
     * Basis (BR-CO-17: Basis x Satz), APplus dagegen die Steuer nach Abzug plus die
     * einzeln gerundete Steuer jeder Anzahlungsrechnung. Das kann je Anzahlung um einen
     * Cent abweichen (ServiceUnion 17.11.2026: Steuer 25912,41 statt 25912,40). Der
     * Zahlbetrag muss aber exakt dem APplus-Brutto entsprechen (offener Posten in der
     * FiBu). Die Differenz geht deshalb in den Rundungsbetrag: DuePayable = GrandTotal -
     * Prepaid + Rounding (BR-CO-16). Nur bei Vorauszahlung und nur bis 1 Cent je
     * Anzahlungsposition plus 1 Cent - groessere Abweichungen sind echte Fehler und
     * bleiben fuer den Betragsvergleich sichtbar.
     */
    private void applyAdvanceRounding(Invoice mustangInvoice) {
        BigDecimal prepaid = mustangInvoice.getTotalPrepaidAmount();
        if (prepaid == null || prepaid.signum() == 0 || internInvoice.getAmounts() == null
                || internInvoice.getAmounts().getGrossAmount() == null || internInvoice.getItems() == null) {
            return;
        }
        int anzahl = 0;
        for (InternInvoiceItem internItem : internInvoice.getItems()) {
            if (isAdvanceDeduction(internItem)) {
                anzahl++;
            }
        }
        BigDecimal soll = internInvoice.getAmounts().getGrossAmount().setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal ist = new org.mustangproject.ZUGFeRD.TransactionCalculator(mustangInvoice).getGrandTotal()
                .subtract(prepaid.setScale(2, BigDecimal.ROUND_HALF_UP));
        BigDecimal diff = soll.subtract(ist);
        BigDecimal grenze = new BigDecimal("0.01").multiply(new BigDecimal(anzahl + 1));
        if (diff.signum() != 0 && diff.abs().compareTo(grenze) <= 0) {
            mustangInvoice.setRoundingAmount(diff);
        }
    }

    /** PARECHNUNGSART der Gutschriften - dort ist eine URRECHNUNG keine Verrechnung. */
    private static final java.util.Set<String> GUTSCHRIFT_ARTEN =
            new java.util.HashSet<>(java.util.Arrays.asList("GU", "SG", "TSG", "AG", "BG"));
    /** PARECHNUNGSART der Anzahlungs-/Abschlagsrechnungen. */
    private static final java.util.Set<String> ANZAHLUNG_ARTEN =
            new java.util.HashSet<>(java.util.Arrays.asList("AN", "AB", "AV"));

    /**
     * CUSTOM:AMC/MEB:17.11.2026:V2.8 - Ist die Position die Verrechnung einer Anzahlung oder
     * eines Abschlags?
     *
     * <p>APplus legt sie in der Schluss-/Teilschlussrechnung als eigene Position an
     * (RechnungUtil.fillAnzArtikel): MENGE 1, negativer Betrag, URRECHNUNG = Nummer der
     * Anzahlungs-/Abschlagsrechnung, URRECHPOS = deren Position. Erkannt wird sie an
     * <ul>
     * <li>URRECHNUNG gesetzt (und nicht die eigene Nummer),</li>
     * <li>negativem Betrag,</li>
     * <li>der eigene Beleg ist keine Gutschrift,</li>
     * <li>die Ursprungsrechnung ist eine Anzahlungs-/Abschlagsrechnung (PARECHNUNGSART
     *     AN/AB/AV, Span-Feld ANP_URPARECHNUNGSART ab V2.8). Liefert ein aelterer Span das
     *     Feld nicht, reicht ART = 'A' der Position (nur Anzahlungen erkennbar).</li>
     * </ul>
     * Gleiche Regel in Anp_ERechnungProcessorUtil (Betragsvergleich) - beide zusammen aendern.
     */
    private boolean isAdvanceDeduction(InternInvoiceItem internItem) {
        if (Boolean.TRUE.equals(internItem.getDontPrint()) || internItem.getReferences() == null) {
            return false;
        }
        InternItemReferences ref = internItem.getReferences();
        String urRechnung = ref.getOriginalInvoice();
        if (urRechnung == null || urRechnung.trim().isEmpty()) {
            return false;
        }
        InternInvoiceMetadata metadata = internInvoice.getMetadata();
        if (metadata != null) {
            if (urRechnung.trim().equals(metadata.getInvoiceNumber())) {
                return false;
            }
            String art = metadata.getInvoiceTypePa();
            if (art != null && GUTSCHRIFT_ARTEN.contains(art.trim().toUpperCase())) {
                return false;
            }
        }
        BigDecimal betrag = advanceNet(internItem);
        if (betrag == null || betrag.signum() >= 0) {
            return false;
        }
        if (ref.isOriginalInvoiceTypePaKnown()) {
            String urArt = ref.getOriginalInvoiceTypePa();
            return urArt != null && ANZAHLUNG_ARTEN.contains(urArt.trim().toUpperCase());
        }
        return "A".equalsIgnoreCase(ref.getPositionType() != null ? ref.getPositionType().trim() : null);
    }

    /** Nettobetrag einer Position: NETTOERLOES, sonst ERLOES, sonst NETTO x MENGE. */
    private static BigDecimal advanceNet(InternInvoiceItem internItem) {
        InternItemAmounts amounts = internItem.getAmounts();
        if (amounts == null) {
            return null;
        }
        if (amounts.getNetRevenue() != null && amounts.getNetRevenue().signum() != 0) {
            return amounts.getNetRevenue();
        }
        if (amounts.getRevenue() != null && amounts.getRevenue().signum() != 0) {
            return amounts.getRevenue();
        }
        if (amounts.getNet() != null) {
            BigDecimal menge = internItem.getQuantity() != null ? internItem.getQuantity() : BigDecimal.ONE;
            return amounts.getNet().multiply(menge);
        }
        return null;
    }

    /**
     * CUSTOM:AMC/MEB:17.11.2026:V2.8 - Verrechnete Anzahlungen/Abschlaege nach der Norm.
     *
     * <p>Die Abzugsposition entfaellt (extractInvoiceItems). Stattdessen:
     * <ul>
     * <li>BT-113 TotalPrepaidAmount = Summe der verrechneten Bruttobetraege (Netto + Steuer
     *     der Abzugsposition - die Werte, die APplus aus der Anzahlungsrechnung uebernommen
     *     hat, nicht neu gerechnet). Mustang rechnet DuePayable = GrandTotal - Prepaid.</li>
     * <li>BG-3 InvoiceReferencedDocument je Anzahlungs-/Abschlagsrechnung (BT-25 Nummer,
     *     BT-26 Datum aus ANP_URRECHNUNGDATUM); ein Kopfbezug (Gutschrift) bleibt zuerst.</li>
     * <li>nur EXTENDED: ram:SpecifiedAdvancePayment je Rechnung und Steuersatz mit Steuer
     *     und Bezug (das Profil-Gate sitzt im ZUGFeRD2PullProvider).</li>
     * </ul>
     * Netto- und Steuersumme der E-Rechnung enthalten damit die volle Leistung; der
     * Zahlbetrag entspricht dem APplus-Brutto.
     */
    private void addAdvancePayments(Invoice mustangInvoice) {
        List<InternInvoiceItem> internItems = internInvoice.getItems();
        if (internItems == null) {
            return;
        }
        java.util.Set<String> referenced = new java.util.HashSet<>();
        if (mustangInvoice.getInvoiceReferencedDocuments() != null) {
            for (ReferencedDocument doc : mustangInvoice.getInvoiceReferencedDocuments()) {
                if (doc.getIssuerAssignedID() != null) {
                    referenced.add(doc.getIssuerAssignedID().trim());
                }
            }
        }
        BigDecimal prepaid = BigDecimal.ZERO;
        boolean found = false;
        Map<String, org.mustangproject.AdvancePayment> payments = new java.util.LinkedHashMap<>();
        for (InternInvoiceItem internItem : internItems) {
            if (!isAdvanceDeduction(internItem)) {
                continue;
            }
            found = true;
            InternItemReferences ref = internItem.getReferences();
            String urRechnung = ref.getOriginalInvoice().trim();
            Date urDatum = ref.getOriginalInvoiceDate() != null
                    ? Date.from(ref.getOriginalInvoiceDate().atStartOfDay(ZoneId.systemDefault()).toInstant())
                    : null;

            BigDecimal netto = advanceNet(internItem).negate();
            InternItemTax tax = internItem.getAmounts().getTax();
            BigDecimal satz = tax != null && tax.getTaxRate() != null ? tax.getTaxRate() : BigDecimal.ZERO;
            BigDecimal steuer = tax != null && tax.getTaxAmount() != null
                    ? tax.getTaxAmount().negate()
                    : netto.multiply(satz).divide(new BigDecimal(100), 2, BigDecimal.ROUND_HALF_UP);
            BigDecimal brutto = netto.add(steuer);
            prepaid = prepaid.add(brutto);

            String kategorie = tax != null && tax.getTaxCategory() != null && !tax.getTaxCategory().trim().isEmpty()
                    ? tax.getTaxCategory().trim().toUpperCase()
                    : (satz.signum() > 0 ? "S" : "Z");
            String key = urRechnung + "|" + satz.stripTrailingZeros().toPlainString() + "|" + kategorie;
            org.mustangproject.AdvancePayment ap = payments.get(key);
            if (ap == null) {
                ap = new org.mustangproject.AdvancePayment()
                        .setPaidAmount(BigDecimal.ZERO).setTaxAmount(BigDecimal.ZERO)
                        .setTaxPercent(satz).setTaxCategoryCode(kategorie)
                        .setInvoiceID(urRechnung).setInvoiceDate(urDatum);
                payments.put(key, ap);
            }
            ap.setPaidAmount(ap.getPaidAmount().add(brutto));
            ap.setTaxAmount(ap.getTaxAmount().add(steuer));

            if (!referenced.contains(urRechnung)) {
                ReferencedDocument doc = new ReferencedDocument().setIssuerAssignedID(urRechnung);
                if (urDatum != null) {
                    doc.setFormattedIssueDateTime(urDatum);
                }
                mustangInvoice.addInvoiceReferencedDocument(doc);
                referenced.add(urRechnung);
            }
        }
        if (!found) {
            return;
        }
        mustangInvoice.setTotalPrepaidAmount(prepaid);
        for (org.mustangproject.AdvancePayment ap : payments.values()) {
            mustangInvoice.addAdvancePayment(ap);
        }
    }

    /**
     * APplus-Zahlarten (FIRMA.ZAHLARTEN, SysConf FIBU/ZAHLARTEN), bei denen der
     * Verkaeufer den Betrag einzieht: 1 Abbuchung, 5 Lastschrift,
     * 32 SEPA-Basislastschrift, 33 SEPA-Firmenlastschrift.
     */
    private static final java.util.Set<String> LASTSCHRIFT_ZAHLARTEN =
            new java.util.HashSet<>(java.util.Arrays.asList("1", "5", "32", "33"));

    /**
     * Zahlart (BG-16) aus der Zahlart VK der Rechnungsadresse.
     *
     * <p>Lastschrift-Zahlart: TypeCode 59 (SEPA-Lastschrift) mit Mandatsreferenz
     * (BT-89, FIRMA.ANP_MANDATSREFERENZ), IBAN des Kunden (BT-91, FIRMA.IBAN) und
     * Glaeubiger-ID (BT-90, Konvertierungsschluessel GLAEUBIGERID). Die Bankverbindung
     * des Verkaeufers (TypeCode 58) entfaellt dann - eine Rechnung hat genau eine
     * Zahlart. Fehlende Angaben werden nicht ersetzt: die Validierung meldet sie,
     * statt dass die Rechnung still als Ueberweisung hinausgeht.
     *
     * <p>Alle anderen Zahlarten bleiben beim bisherigen Verhalten (58, Ueberweisung).
     */
    private void applyPaymentMeans(Invoice invoice, TradeParty seller) {
        InternAddress rechnungsadresse = internInvoice.getInvoiceAddress();
        if (seller == null || rechnungsadresse == null
                || !isLastschrift(rechnungsadresse.getPaymentMethods())) {
            return;
        }
        String iban = rechnungsadresse.getIban() == null ? "" : rechnungsadresse.getIban().replace(" ", "");
        String mandat = rechnungsadresse.getMandateReference() == null ? "" : rechnungsadresse.getMandateReference().trim();
        seller.getBankDetails().clear();
        seller.addDebitDetails(new DirectDebit(iban, mandat));

        String glaeubigerId = (conversionKeys == null) ? null : conversionKeys.get("GLAEUBIGERID");
        if (glaeubigerId != null && !glaeubigerId.trim().isEmpty()) {
            invoice.setCreditorReferenceID(glaeubigerId.trim());
        }
    }

    /** Zahlart-Code aus APplus (ggf. "5" oder "5: Lastschrift") gegen die Lastschrift-Codes pruefen. */
    private static boolean isLastschrift(String zahlart) {
        if (zahlart == null) {
            return false;
        }
        String code = zahlart.trim();
        int ende = 0;
        while (ende < code.length() && Character.isDigit(code.charAt(ende))) {
            ende++;
        }
        return ende > 0 && LASTSCHRIFT_ZAHLARTEN.contains(code.substring(0, ende));
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
        
        // Bestelldatum bewusst NICHT setzen - in keinem Profil.
        // Es landet als ram:FormattedIssueDateTime im BuyerOrderReferencedDocument
        // und verstoesst dort gegen CII-DT-027 ("FormattedIssueDateTime should not
        // be present", EN16931-CII-validation.xslt, flag=fatal). Formal erlaubt ist
        // es nur in ZUGFeRD EXTENDED; Empfaengerportale pruefen EXTENDED-Rechnungen
        // aber gegen das EN16931-Schematron (die Profil-URN behauptet
        // "en16931:2017#conformant") und lehnen ab - so geschehen bei Hagelschuer
        // AR126-05945 (11/2026). Die EN16931 kennt kein Bestelldatum; BT-13 (die
        // Bestellnummer, ram:IssuerAssignedID) bleibt unveraendert in beiden Profilen.
        
        mustangInvoice.setDocumentCode(mapArtToDocumentCode(metadata.getInvoiceTypePa()));
        mustangInvoice.setDocumentName(metadata.getInvoiceTypePa());
        
        if (metadata.getOriginalInvoice() != null) {
            mustangInvoice.setInvoiceReferencedDocumentID(metadata.getOriginalInvoice());
        }
        
        if (metadata.getDeliveryDate() != null) {
            Date deliveryDate = Date.from(metadata.getDeliveryDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
            mustangInvoice.setDeliveryDate(deliveryDate);
        }
        
        String order = metadata.getOrderNumber();
        if (order != null && order.startsWith("KA"))
        	order = order.replace("KA", "AB"); //TODO: Abhängig vom Kunden, falls KA = 1 kommt
        mustangInvoice.setSellerOrderReferencedDocumentID(order);
        
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

        // BT-29 (Verkaeufer) bzw. BT-46 (Kaeufer): die Nummer, unter der die
        // Partei beim Gegenueber gefuehrt wird - Lieferanten- bzw. Kundennummer.
        // Sie kommt aus dem eigenen Element ANP_PARTYID und NICHT aus der GLN:
        // die GLN ist eine global vergebene Kennung mit Schema 0088, diese hier
        // eine hausinterne Nummer ohne Schema. Fehlt das Element, bleibt das
        // Feld leer - fuer bestehende Mandanten aendert sich dadurch nichts.
        if (address.getPartyId() != null && !address.getPartyId().isEmpty()) {
            tradeParty.setID(address.getPartyId());
        }

        // Setze GLN/Global ID, falls vorhanden
        if (address.getGlnId() != null && !address.getGlnId().isEmpty()) {
            SchemedID id = new SchemedID();
            id.setId(address.getGlnId());
            id.setScheme("0088");
            tradeParty.addGlobalID(id);
        } else if (address.getDuns() != null && !address.getDuns().isEmpty()) {
            SchemedID id = new SchemedID();
            id.setId(address.getDuns());
            id.setScheme("0060");
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
        // Nur beim Verkaeufer (Typ "V") darf neben der USt-IdNr. (BT-31,
        // schemeID "VA") auch die Steuernummer (BT-32, schemeID "FC") stehen.
        // Bei Rechnungs- und Lieferadresse ist ausschliesslich "VA" zulaessig;
        // eine zweite ram:SpecifiedTaxRegistration reisst in ZUGFeRD EXTENDED
        // BR-FXEXT-03, FX-SCH-A-000166 und FX-SCH-A-000703 auf.
        if ("V".equals(type)) {
            tradeParty.setTaxID(address.getTaxNumber());
        }
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
        bankDetails.setAccountName(
            address.getAccountName() != null ? address.getAccountName() : address.getCompanyName1());
        tradeParty.getBankDetails().add(bankDetails);

        // CUSTOM:AMC/MEB:03.11.2026: weitere Bankverbindungen des Verkaeufers - je eine
        // eigene ram:SpecifiedTradeSettlementPaymentMeans (BG-16/BG-17). Bei Lastschrift
        // ersetzt applyPaymentMeans alle Bankverbindungen.
        if ("V".equalsIgnoreCase(type) && address.getAdditionalBankAccounts() != null) {
            for (String[] konto : address.getAdditionalBankAccounts()) {
                BankDetails weitere = new BankDetails();
                weitere.setIBAN(konto[0]);
                weitere.setBIC(konto[1]);
                weitere.setAccountName(address.getCompanyName1());
                tradeParty.getBankDetails().add(weitere);
            }
        }

        // Für den Verkäufer (Typ "V") setze zusätzliche Informationen
        if ("V".equalsIgnoreCase(type)) {
            String handelsregister = address.getCommercialRegister();
            if (handelsregister != null) {
                // BT-30 ohne schemeID: "0002" waere in ISO 6523 das franzoesische
                // SIRENE-Register. Fuer das deutsche Handelsregister gibt es keinen
                // ICD-Code - die EN16931 laesst das Schema-Attribut optional.
                LegalOrganisation lO = new LegalOrganisation(handelsregister);
                lO.setTradingBusinessName(tradeParty.getName());
                tradeParty.setLegalOrganisation(lO);
            }

            // Geschäftsführer-Informationen als Hinweis hinzufügen
            String gf1 = address.getManagingDirector1();
            String gf2 = address.getManagingDirector2();
            String gf = null;
            if (gf1 != null) {
                gf = "Geschäftsführer: " + gf1;
                if (gf2 != null) gf += ", " + gf2;
            }
            if (gf != null && isNoteEnabled("GF")) {
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
    /**
     * BT-16, Lieferscheinbezug. EN 16931 kennt auf Belegebene genau EINEN
     * Lieferschein, APplus fuehrt ihn dagegen je Position. Deshalb wird der
     * Beleg nur dann gesetzt, wenn alle Positionen auf denselben Lieferschein
     * verweisen - bei einer Sammelrechnung ueber mehrere Lieferscheine waere
     * jede Auswahl willkuerlich, dann bleibt das Feld leer und die Angabe
     * steht weiterhin an den Positionen.
     */
    /**
     * Eine Skontostufe in der von der XRechnung vorgeschriebenen Schreibweise.
     * Die Regel BR-DE-18 prueft gegen
     *   (^|
?
)#(SKONTO)#TAGE=([0-9]+#PROZENT=[0-9]+\.[0-9]{2})...#$
     * Daraus folgt: Grossbuchstaben, Prozentsatz ohne Vorzeichen mit Punkt und
     * genau zwei Nachkommastellen, kein zusaetzliches Leerzeichen - und die
     * Zeile muss mit einem Zeilenumbruch abschliessen. BASISBETRAG entfaellt,
     * weil sich das Skonto immer auf den faelligen Gesamtbetrag (BT-115)
     * bezieht.
     */
    private static String skontoZeile(String tage, String prozent) {
        if (tage == null || prozent == null) {
            return "";
        }
        String t = tage.trim();
        String p = prozent.trim();
        if (t.isEmpty() || p.isEmpty()) {
            return "";
        }
        try {
            BigDecimal satz = new BigDecimal(p);
            if (satz.compareTo(BigDecimal.ZERO) == 0) {
                return "";
            }
            int tageZahl = new BigDecimal(t).intValue();
            return "#SKONTO#TAGE=" + tageZahl
                 + "#PROZENT=" + satz.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "#\n";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private void extractDespatchAdvice(Invoice mustangInvoice) {
        List<InternInvoiceItem> items = internInvoice.getItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        String lieferschein = null;
        for (InternInvoiceItem item : items) {
            if (item == null || item.getReferences() == null) {
                continue;
            }
            String wert = item.getReferences().getDeliveryNote();
            if (wert == null || wert.trim().isEmpty()) {
                continue;
            }
            wert = wert.trim();
            if (lieferschein == null) {
                lieferschein = wert;
            } else if (!lieferschein.equals(wert)) {
                return;   // mehrere verschiedene Lieferscheine
            }
        }
        if (lieferschein != null) {
            mustangInvoice.setDespatchAdviceReferencedDocumentID(lieferschein);
        }
    }

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
        if (!nurEineZahlungsbedingung() && discountPercent1 != null && discountDays1 != null
                && !discountPercent1.isEmpty() && !discountDays1.isEmpty()) {
            count++;
        }
        if (!nurEineZahlungsbedingung() && discountPercent2 != null && discountDays2 != null
                && !discountPercent2.isEmpty() && !discountDays2.isEmpty()) {
            count++;
        }
        mustangPaymentTerms = new IZUGFeRDPaymentTerms[count];

        // Standard-Zahlungsbedingung
        mustangPaymentTerms[0] = (new IZUGFeRDPaymentTerms() {
            @Override
            public String getDescription() {
                String text = paymentTerms.getPaymentTermsText();
                if (text == null) {
                    text = "";
                }
                // XRechnung laesst nur EINE Zahlungsbedingung zu, deshalb sind
                // die Skontostufen oben ausgeklammert. Damit das Skonto trotzdem
                // maschinell lesbar bleibt, schreibt die XRechnung dafuer eine
                // feste Form in BT-20 vor - je Stufe eine eigene Zeile:
                //     #SKONTO#TAGE=10#PROZENT=2.00#
                // BASISBETRAG entfaellt, weil sich das Skonto bei uns immer auf
                // den faelligen Gesamtbetrag (BT-115) bezieht.
                if (nurEineZahlungsbedingung()) {
                    String skonto = skontoZeile(discountDays1, discountPercent1)
                                  + skontoZeile(discountDays2, discountPercent2);
                    if (!skonto.isEmpty()) {
                        if (!text.isEmpty() && !text.endsWith("\n")) {
                            text = text + "\n";
                        }
                        text = text + skonto;
                    }
                }
                // Ohne Text null statt "": ein leeres <ram:Description/> verstoesst in
                // der XRechnung gegen PEPPOL-EN16931-R008. BT-9 (Faelligkeit) bleibt.
                return text.trim().isEmpty() ? null : text;
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
        if (!nurEineZahlungsbedingung() && discountPercent1 != null && discountDays1 != null
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
        if (!nurEineZahlungsbedingung() && discountPercent2 != null && discountDays2 != null
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

        mustangInvoice.setExtendedPaymentTerms(mustangPaymentTerms);
    }

    /**
     * Integriert Texte in das Mustang-Invoice-Objekt
     * 
     * @param mustangInvoice Das Mustang-Invoice-Objekt
     * @param textFields Die Textfelder
     */
    private void integrateTexts(Invoice mustangInvoice, Map<String, String> textFields) {
        // Add notes as IncludedNotes with appropriate subject codes
        if (isNoteEnabled("STDTEXT") && textFields.get("STDTEXT") != null) {
            mustangInvoice.addNotes(Collections.singletonList(IncludedNote.generalNote(textFields.get("STDTEXT"))));
        }
        if (isNoteEnabled("FUSSTEXT") && textFields.get("FUSSTEXT") != null) {
            mustangInvoice.addNotes(Collections.singletonList(IncludedNote.regulatoryNote(textFields.get("FUSSTEXT"))));
        }
        if (isNoteEnabled("FREITEXT") && textFields.get("FREITEXT") != null) {
            mustangInvoice.addNotes(Collections.singletonList(IncludedNote.unspecifiedNote(textFields.get("FREITEXT"))));
        }
        if (isNoteEnabled("IHRTEXT") && textFields.get("IHRTEXT") != null) {
            mustangInvoice.addNotes(Collections.singletonList(IncludedNote.sellerNote(textFields.get("IHRTEXT"))));
        }
        if (isNoteEnabled("KOPFTEXT") && textFields.get("KOPFTEXT") != null) {
            mustangInvoice.addNotes(Collections.singletonList(IncludedNote.introductionNote(textFields.get("KOPFTEXT"))));
        }
        if (isNoteEnabled("FACTORINGTEXT") && textFields.get("FACTORINGTEXT") != null) {
            mustangInvoice.addNotes(Collections.singletonList(IncludedNote.generalNote(textFields.get("FACTORINGTEXT"))));
        }
    }

    /**
     * Uebertraegt einen Belegtext als Kontierungsinformation des Kaeufers
     * (BT-19, ram:ReceivableSpecifiedTradeAccountingAccount/ram:ID im Kopf).
     *
     * <p>Gesteuert ueber den Konvertierungsschluessel <code>ACCOUNTINGREF</code>:
     * Name des Belegtextes aus {STDTEXT, FUSSTEXT, FREITEXT, IHRTEXT, KOPFTEXT,
     * FACTORINGTEXT}, in APplus gefuellt aus der SysConf
     * CUSTOM/E-RECHNUNG/ANP_ERECHNUNG_KONTIERUNG. Fehlt der Schluessel oder ist
     * der Text leer, entsteht kein BT-19 - Ausgabe unveraendert.
     *
     * <p>Der Text ist Klartext aus HTML (udf_plainText3) und kann Zeilenumbrueche
     * tragen; BT-19 ist eine Kennung, daher wird Whitespace zu einem Leerzeichen
     * zusammengefasst. Ein evtl. zusaetzlicher Kopf-Hinweis (NOTES) bleibt
     * davon unberuehrt.
     *
     * <p>Anlass: Morath 11.11.2026 - Kostenstelle des Kunden steht in "Ihre
     * Zeichen" (IHRTEXT).
     *
     * @param mustangInvoice Das Mustang-Invoice-Objekt
     * @param textFields Die Textfelder
     */
    private void integrateBuyerAccountingReference(Invoice mustangInvoice, Map<String, String> textFields) {
        String field = (conversionKeys == null) ? null : conversionKeys.get("ACCOUNTINGREF");
        if (field == null || field.trim().isEmpty()) {
            return;
        }
        String key = field.trim().toUpperCase();
        String value = textFields.get(key);
        if (value == null && "IHRTEXT".equals(key) && internInvoice.getTexts() != null) {
            // Der Span liefert "Ihre Zeichen" als Spalte IHRTEXT, nicht als HTMLIHRTEXT
            value = internInvoice.getTexts().getCustomerReference();
        }
        if (value == null) {
            return;
        }
        value = value.replaceAll("\\s+", " ").trim();
        if (!value.isEmpty()) {
            mustangInvoice.setBuyerAccountingReference(value);
        }
    }

    /**
     * Prueft, ob ein Belegtext als Kopf-Hinweis (ram:IncludedNote) uebertragen
     * werden soll.
     *
     * <p>Gesteuert ueber den Konvertierungsschluessel <code>NOTES</code>: eine
     * kommaseparierte Liste der gewuenschten Feldnamen aus
     * {STDTEXT, FUSSTEXT, FREITEXT, IHRTEXT, KOPFTEXT, FACTORINGTEXT, GF},
     * in APplus gefuellt aus der SysConf CUSTOM/E-RECHNUNG/ANP_ERECHNUNG_NOTES.
     * GF steht dabei nicht fuer einen Belegtext, sondern fuer den aus den
     * FIRMA-Stammdaten (GF1/GF2) erzeugten Geschaeftsfuehrer-Hinweis.
     * Fehlt der Schluessel oder ist er leer, werden - wie bisher - alle
     * gefuellten Texte uebertragen.
     *
     * <p>Hintergrund: manche Kunden nutzen einzelne Belegtexte (z.B. FREITEXT,
     * in der Maske das Register "Zusaetze") fuer interne Vermerke, die nicht in
     * die E-Rechnung gehoeren.
     *
     * @param field Feldname des Belegtextes
     * @return true, wenn der Text uebertragen werden soll
     */
    private boolean isNoteEnabled(String field) {
        String configured = (conversionKeys == null) ? null : conversionKeys.get("NOTES");
        if (configured == null || configured.trim().isEmpty()) {
            return true;
        }
        for (String part : configured.split(",")) {
            if (part.trim().equalsIgnoreCase(field)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prueft, ob der Verkaufstext (Positionstext) statt der Artikelbenennung als
     * Item name (BT-153) uebertragen werden soll.
     *
     * <p>Gesteuert ueber den Konvertierungsschluessel <code>ITEMNAME</code>:
     * <code>TEXT</code> = Verkaufstext wird der Name, die Artikelbenennung
     * entfaellt und es gibt keine Description (BT-154). Fehlt der Schluessel oder
     * steht er auf <code>NAME</code>, bleibt es beim bisherigen Verhalten
     * (Benennung = Name, Verkaufstext = Description).
     * In APplus gefuellt aus der SysConf CUSTOM/E-RECHNUNG/ANP_ERECHNUNG_VERKAUFSTEXTALSNAME.
     *
     * @return true, wenn der Verkaufstext der Item name sein soll
     */
    private boolean isSalesTextAsItemName() {
        String configured = (conversionKeys == null) ? null : conversionKeys.get("ITEMNAME");
        return configured != null && "TEXT".equalsIgnoreCase(configured.trim());
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
            if (texts.getFactoringText() != null && !texts.getFactoringText().isEmpty()) {
                textFields.put("FACTORINGTEXT", texts.getFactoringText());
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

        // Koepfe mit "Preis aus Unterpositionen" (s. findPricedHeads) und die
        // Positionen, deren Betrag bereits in einem solchen Kopf steckt.
        Map<InternInvoiceItem, BigDecimal> pricedHeads = findPricedHeads(internItems);

        for (InternInvoiceItem internItem : internItems) {
            // DONTPRINT: Position komplett überspringen (nicht anzeigen, nicht berechnen).
            // DONTPRINTPRICE ist dagegen reine PDF-Darstellungssteuerung und darf die
            // Position NICHT aus der E-Rechnung entfernen - sie bleibt als Zeile
            // erhalten, nur ohne Preis (s.u.).
            if (Boolean.TRUE.equals(internItem.getDontPrint())) {
                continue;
            }
            // V2.8: verrechnete Anzahlung/Abschlag ist keine Position, sondern
            // Vorauszahlung (BT-113) - s. addAdvancePayments
            if (isAdvanceDeduction(internItem)) {
                continue;
            }

            // Menge und Preis
            BigDecimal quantity = internItem.getQuantity();
            if (quantity == null) quantity = BigDecimal.ONE; // Fallback

            InternItemAmounts amounts = internItem.getAmounts();
            BigDecimal headTaxRate = pricedHeads.get(internItem);
            boolean insidePricedHead = headTaxRate == null && isInsidePricedHead(internItem, pricedHeads);

            BigDecimal packmenge = BigDecimal.ONE;
            if (amounts != null && amounts.getPackageQuantity() != null
                    && amounts.getPackageQuantity().compareTo(BigDecimal.ZERO) != 0) {
                packmenge = amounts.getPackageQuantity();
            }

            BigDecimal net = amounts != null ? amounts.getNet() : null;
            BigDecimal mengeInPreisMe = amounts != null ? amounts.getMengeInPreisMe() : null;
            BigDecimal nettoErloes = amounts != null ? amounts.getNetRevenue() : null;

            BigDecimal unitPrice;
            BigDecimal basisQty;

            if (mengeInPreisMe != null && mengeInPreisMe.compareTo(BigDecimal.ZERO) != 0
                    && quantity.compareTo(BigDecimal.ZERO) != 0
                    && net != null && net.compareTo(BigDecimal.ZERO) != 0
                    && nettoErloes != null && nettoErloes.compareTo(BigDecimal.ZERO) != 0) {
                // NETTO muss gesetzt sein: Zwischensummen (USERPOS "x.y*") tragen keinen
                // Einzelpreis, aber die Summe ihrer Positionen im ERLOES - sie muessen
                // weiter mit Preis 0 hinein, sonst zaehlt der Betrag doppelt.
                // CUSTOM:AMC/MEB:28.10.2026: VKME != PREISME - Preis je VKME aus dem
                // Betrag, den APplus selbst gerechnet hat (NETTOERLOES = ERLOES + Rabatte),
                // NICHT ueber den Umrechnungsfaktor. udf_ConvertMe liefert den Faktor auf
                // 3 Stellen gerundet (6 m / 6,1 m/ST -> 0.984 statt 0.98361), und der
                // Erloes einer Position bleibt auf dem Stand ihrer Anlage, auch wenn die
                // Umrechnung im Artikelstamm spaeter geaendert wird (Hagelschuer
                // AR126-01856: Pos 1.14.6 71,28 statt 71,25; Pos 2.37.68 mit 6 m statt
                // 6,1 m gerechnet). Mit NETTOERLOES / MENGE ergibt MENGE x Nettopreis
                // immer genau den APplus-Erloes.
                unitPrice = nettoErloes.divide(quantity, 10, BigDecimal.ROUND_HALF_UP);
                basisQty = BigDecimal.ONE;
            } else if (mengeInPreisMe != null && mengeInPreisMe.compareTo(BigDecimal.ZERO) != 0
                    && quantity.compareTo(BigDecimal.ZERO) != 0
                    && net != null) {
                // ANP_MENGEINPREISME vorhanden: VKME != PREISME
                // Preis von PREISME-Basis auf VKME umrechnen:
                //   unitPrice = NETTO × ANP_MENGEINPREISME / (MENGE × PACKMENGE)
                // Damit gilt: MENGE × unitPrice = ANP_MENGEINPREISME × NETTO / PACKMENGE = korrekter Zeilenbetrag
                unitPrice = net.multiply(mengeInPreisMe)
                              .divide(quantity.multiply(packmenge), 10, BigDecimal.ROUND_HALF_UP);
                basisQty = BigDecimal.ONE;
            } else {
                // Fallback: bisheriges Verhalten (VKME == PREISME oder kein ANP_MENGEINPREISME)
                unitPrice = net != null ? net : BigDecimal.ZERO;
                basisQty = packmenge;
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

            // Kopf mit "Preis aus Unterpositionen": NETTO/PREIS/UST sind leer, der
            // Betrag steht nur im ERLOES. Preis = ERLOES / MENGE, Steuersatz von den
            // Unterpositionen - sonst ginge der Kopf mit 0 und Kategorie Z raus.
            if (headTaxRate != null) {
                BigDecimal revenue = amounts.getRevenue();
                unitPrice = quantity.compareTo(BigDecimal.ZERO) != 0
                        ? revenue.divide(quantity, 10, BigDecimal.ROUND_HALF_UP)
                        : revenue;
                basisQty = BigDecimal.ONE;
                taxPercent = headTaxRate;
                tax = revenue.multiply(headTaxRate)
                        .divide(new BigDecimal(100), 2, BigDecimal.ROUND_HALF_UP);
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

            // APplus fuehrt Zuschlaege als NEGATIVE Rabattbetraege. Sie duerfen die
            // Rabattsumme nicht mindern, sondern muessen den Zeilenbetrag erhoehen -
            // in ZUGFeRD also eine eigene Charge (BG-28) statt einer Allowance
            // (BG-27). Vorher fiel ein reiner Zuschlag ueber das "> 0" komplett
            // heraus und fehlte im Zeilenbetrag.
            BigDecimal summRabatt = BigDecimal.ZERO;
            BigDecimal summZuschlag = BigDecimal.ZERO;
            for (BigDecimal betrag : new BigDecimal[] { rabatt1, rabatt2, mrabatt }) {
                int vorzeichen = betrag.compareTo(BigDecimal.ZERO);
                if (vorzeichen > 0) {
                    summRabatt = summRabatt.add(betrag);
                } else if (vorzeichen < 0) {
                    summZuschlag = summZuschlag.subtract(betrag); // Betrag ist negativ -> Absolutwert
                }
            }

            // DONTPRINTPRICE: die Position bleibt als Zeile erhalten (nur DONTPRINT
            // filtert sie heraus), geht aber mit Preis und Steuer 0 in den Beleg -
            // sie zaehlt also NICHT in die Rechnungssumme, unabhaengig davon, ob in
            // APplus ein Preis an der Position haengt (Vorgabe 03.09.2026).
            // Weicht die Kopfsumme dadurch von APplus ab, meldet das der
            // Betragsvergleich in Anp_ERechnungProcessorUtil als Validierungsfehler.
            boolean dontPrintPrice = Boolean.TRUE.equals(internItem.getDontPrintPrice());
            // Unter einem Kopf mit "Preis aus Unterpositionen" steckt der Betrag
            // bereits im Kopf - die Position geht wie DONTPRINTPRICE mit 0 hinein,
            // auch wenn sie selbst kein "Preis nicht drucken" traegt.
            boolean zeroPrice = dontPrintPrice || insidePricedHead;

            // CUSTOM:AMC/MEB:17.11.2026:V2.8 - Negativer Einzelpreis ist in der EN16931
            // verboten (BR-27 "Item net price shall NOT be negative", ebenso BR-28 fuer
            // den Bruttopreis). Im Profil Comfort/EN16931 (E) wandert das Vorzeichen
            // einer normalen Abzugsposition deshalb in die Menge: MENGE -1 x Preis +X
            // ergibt denselben Zeilenbetrag (BT-131 darf negativ sein), Summen und
            // Steuer bleiben unveraendert. EXTENDED (Z) bleibt wie bisher.
            // Verrechnete Anzahlungen/Abschlaege kommen hier nicht an - sie gehen als
            // Vorauszahlung hinaus (addAdvancePayments).
            boolean negativerPreis = "E".equals(applusEinterface)
                    && !zeroPrice && unitPrice.signum() < 0 && quantity.signum() != 0;
            if (negativerPreis) {
                quantity = quantity.negate();
                unitPrice = unitPrice.negate();
            }

            // Produktinformationen
            Product product = new Product();
            if (internItem.getText() != null) {
                if (isSalesTextAsItemName()) {
                    // ITEMNAME=TEXT: der Verkaufstext (Positionstext) wird der Item
                    // name (BT-153), die Artikelbenennung entfaellt, eine Description
                    // (BT-154) wird nicht geschrieben. Ist der Verkaufstext leer,
                    // bleibt die Benennung als Ersatz - sonst fiele die Position
                    // ueber BR-25 durch.
                    String salesText = internItem.getText().getText();
                    product.setName(salesText != null && !salesText.trim().isEmpty()
                            ? salesText.trim() : internItem.getText().getName());
                } else {
                    product.setName(internItem.getText().getName());
                    product.setDescription(internItem.getText().getText());
                }
            }
            // BR-25: jede BG-25 MUSS einen Item name (BT-153) tragen. Ersatzname
            // aber nur fuer Gliederungszeilen - eine gewoehnliche Position ohne
            // Bezeichnung soll weiterhin durch die Validierung fallen.
            product.setName(resolveItemName(internItem, product, dontPrintPrice));

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
            String taxText = amounts != null && amounts.getTax() != null ? amounts.getTax().getTaxText() : null;
            applyTaxCategory(product,
                    amounts != null && amounts.getTax() != null ? amounts.getTax().getTaxCategory() : null,
                    taxPercent, taxText);
            if (taxText != null && !taxText.trim().isEmpty()) {
                taxTexts.add(taxText.trim());
            }

            // Einheit mappen
            String unitType = internItem.getUnit();
            String unitCode = (unitType != null) ? UNIT_CODE_MAPPING.getOrDefault(unitType, "C62") : "C62";
            product.setUnit(unitCode);
            
            // Erstelle die Position
            Item item = new Item();
            
            // Rabatt auf PRODUKT-Ebene (ram:GrossPriceProductTradePrice/
            // ram:AppliedTradeAllowanceCharge), also PRO EINHEIT - deshalb die
            // Umrechnung "x PACKMENGE / MENGE". Zuschlag dagegen auf Zeilenebene
            // (s.u.) mit dem absoluten Betrag.
            //
            // ACHTUNG, Upstream-Falle: bis Mustang 2.15.3 lag genau diese Semantik auf
            // item.addAllowance(). Mit 2.24 (Upstream-Commit 7c2e39b9) sind
            // Item::getAllowances/getCharges entfallen; item.addAllowance() bedeutet
            // seither eine ABSOLUTE Allowance auf Zeilenebene (BG-27), die genau EINMAL
            // vom Zeilenbetrag abgezogen wird. Mit der Umrechnung pro Einheit wurde der
            // Rabatt dadurch um den Faktor MENGE zu klein. product.addAllowance() haelt
            // die alte, hier gemeinte Bedeutung.
            //
            // MENGE=0 wuerde in der Umrechnung eine Division durch 0 ausloesen und die
            // komplette Konvertierung abbrechen.
            // Kopf mit Preis aus Unterpositionen: ERLOES ist bereits der Endbetrag,
            // Rabatte/Zuschlaege duerfen nicht noch einmal abgehen.
            if (!zeroPrice && headTaxRate == null && quantity.compareTo(BigDecimal.ZERO) != 0) {
                if (summRabatt.compareTo(BigDecimal.ZERO) > 0 && negativerPreis) {
                    // V2.8: bei umgedrehtem Vorzeichen (Menge negativ) waere ein Rabatt
                    // pro Einheit selbst negativ (BR-28). Deshalb als absolute
                    // Zeilen-Allowance (BG-27): Zeilenbetrag = MENGE x NETTO - Rabatt,
                    // wie in APplus.
                    Allowance lineAllowance = new Allowance(summRabatt);
                    lineAllowance.setReason("Rabatt");
                    if (!applusEinterface.equals("X"))
                    		lineAllowance.setReasonCode("95");
                    lineAllowance.setTaxPercent(product.getVATPercent());
                    item.addAllowance(lineAllowance);
                } else if (summRabatt.compareTo(BigDecimal.ZERO) > 0) {
                    // Rabatt auf dieselbe Preisbasis wie der Bruttopreis (basisQty), NICHT
                    // pauschal PACKMENGE: im ANP_MENGEINPREISME-Zweig ist der Preis bereits
                    // auf VKME umgerechnet (basisQty = 1) - mit PACKMENGE war der Rabatt
                    // dort um den Faktor PACKMENGE zu gross (SL-Rack AR-26013496, Preis-ME
                    // 100: Zeilenbetrag -3.286.193,08 statt 44.103,56).
                    BigDecimal rabattProEinheit =
                            summRabatt.multiply(basisQty).divide(quantity, 10, BigDecimal.ROUND_HALF_UP);
                    Allowance discountAllowance = new Allowance(rabattProEinheit);
                    discountAllowance.setReason("Rabatt");
                    if (!applusEinterface.equals("X"))
                    		discountAllowance.setReasonCode("60");
                    discountAllowance.setTaxPercent(product.getVATPercent());
                    product.addAllowance(discountAllowance);
                }
                if (summZuschlag.compareTo(BigDecimal.ZERO) > 0) {
                    // Zuschlag NICHT auf Preisebene: PEPPOL-EN16931-R044 verbietet dort
                    // ChargeIndicator=true ("Charge on price level is NOT allowed"), und
                    // R046 verlangt netto = brutto - Rabatt. Deshalb als Zeilen-Charge
                    // (BG-28, ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeAllowanceCharge)
                    // mit dem ABSOLUTEN Zeilenbetrag - die wird einmal auf den
                    // Zeilenbetrag addiert, nicht mit der Menge multipliziert.
                    Charge surcharge = new Charge(summZuschlag);
                    surcharge.setReason("Zuschlag");
                    if (!applusEinterface.equals("X"))
                    		surcharge.setReasonCode("ZZZ");
                    surcharge.setTaxPercent(product.getVATPercent());
                    item.addCharge(surcharge);
                }
            }
            // USERPOS als LineID verwenden, Fallback auf POSITION
            String lineId = internItem.getUserPos() != null ? internItem.getUserPos() : internItem.getPosition();
            item.setId(lineId);
            item.setProduct(product);
            item.setQuantity(quantity);

            // DONTPRINTPRICE oder Betrag steckt im Kopf: Position anzeigen, aber ohne Preis
            if (zeroPrice) {
                item.setPrice(BigDecimal.ZERO);
                item.setBasisQuantity(BigDecimal.ONE);
                item.setTax(BigDecimal.ZERO);
            } else {
                item.setPrice(unitPrice);
                item.setBasisQuantity(basisQty);
                item.setTax(tax);
            }
            // BT-132: frueher item.addReferencedLineID(..), seit upstream 2.24.1 ueber ReferencedDocument.
            item.setBuyerOrderReferencedDocument(
                new ReferencedDocument().setLineID(internItem.getReferences().getOrderPosition()));
            
            // Position zur Liste hinzufügen
            items.add(item);
        }
        
        return items;
    }

    /**
     * Uebernimmt die USt-Kategorie der Position (USTKATEGORIE, in APplus
     * USTSATZPOS.STEUERKATEGORIE) samt Befreiungsgrund.
     *
     * <p>Ohne Kategorie entscheidet Mustang allein nach dem Satz: ueber 0 = S,
     * 0 % = Z. Ausfuhr (G), innergemeinschaftliche Lieferung (K), Reverse
     * Charge (AE) und steuerfreie Umsaetze (E) gingen dann als Z hinaus -
     * formal gueltig, fachlich falsch.
     *
     * <p>Uebernommen wird nur, was zum Satz passt: ueber 0 nur S, bei 0 %
     * Z, E, AE, K und G. Alles andere (Tippfehler, O, L, M, S bei 0 %) bleibt
     * beim bisherigen Verhalten - lieber Z als eine Kategorie, deren
     * Folgeregeln (BR-O-*, BR-S-05) die Rechnung ungueltig machen.
     *
     * <p>CUSTOM:AMC/MEB:23.11.2026:V2.9 - Befreiungsgrund (BT-120) ist der Steuertext
     * aus dem USt-Satz (USTSATZPOS.STDTXT_AR, Sprache der Rechnung), wie er auf der
     * gedruckten Rechnung steht. Nur ohne Steuertext gilt der feste Text.
     */
    private void applyTaxCategory(Product product, String category, BigDecimal taxPercent, String taxText) {
        if (category == null || category.trim().isEmpty()) {
            return;
        }
        String code = category.trim().toUpperCase();
        boolean zeroRate = taxPercent == null || taxPercent.compareTo(BigDecimal.ZERO) == 0;
        if (!zeroRate) {
            if ("S".equals(code)) {
                product.setTaxCategoryCode(code);
            }
            return;
        }
        boolean de = language == null || language.isEmpty() || language.equalsIgnoreCase("de");
        String reason;
        switch (code) {
            case "Z":
                product.setTaxCategoryCode(code);
                return;
            case "E":
                reason = de ? "Steuerfreier Umsatz" : "Exempt from VAT";
                break;
            case "AE":
                product.setTaxExemptionReasonCode("VATEX-EU-AE");
                reason = de ? "Steuerschuldnerschaft des Leistungsempfängers" : "Reverse charge";
                break;
            case "K":
                product.setTaxExemptionReasonCode("VATEX-EU-IC");
                reason = de ? "Steuerfreie innergemeinschaftliche Lieferung" : "Intra-community supply";
                break;
            case "G":
                product.setTaxExemptionReasonCode("VATEX-EU-G");
                reason = de ? "Steuerfreie Ausfuhrlieferung" : "Export outside the EU";
                break;
            default:
                return;
        }
        product.setTaxCategoryCode(code);
        product.setTaxExemptionReason(taxText != null && !taxText.trim().isEmpty() ? taxText.trim() : reason);
    }

    /**
     * CUSTOM:AMC/MEB:23.11.2026:V2.9 - Steuertexte als Kopf-Hinweis (IncludedNote, TXD).
     *
     * <p>Der Rechnungsdruck gibt je Position den Steuertext des USt-Satzes aus
     * (USTSATZPOS.STDTXT_AR). Als Befreiungsgrund (BT-120) traegt die E-Rechnung ihn
     * nur bei E/AE/K/G; bei S und Z gibt es dort kein Feld. Deshalb steht jeder
     * Text zusaetzlich einmal als Steuerhinweis im Kopf - ausser er ist schon
     * wortgleich als anderer Hinweis enthalten (z.B. im Standardtext).
     */
    private void integrateTaxTexts(Invoice mustangInvoice) {
        if (taxTexts.isEmpty()) {
            return;
        }
        java.util.Set<String> vorhanden = new java.util.HashSet<>();
        if (mustangInvoice.getNotesWithSubjectCode() != null) {
            for (IncludedNote note : mustangInvoice.getNotesWithSubjectCode()) {
                if (note.getContent() != null) {
                    vorhanden.add(note.getContent().trim());
                }
            }
        }
        for (String text : taxTexts) {
            if (vorhanden.add(text)) {
                mustangInvoice.addNotes(Collections.singletonList(IncludedNote.taxNote(text)));
            }
        }
    }

    /**
     * Ermittelt die Koepfe mit "Preis aus Unterpositionen" und ihren Steuersatz.
     *
     * <p>Erkennung: NETTO leer, ERLOES ungleich 0, keine Zwischensumme, nicht
     * DONTPRINT (beobachtet an Mietkesselanlagen, AR126-04287 Pos 1/2/3:
     * NETTO/PREIS/UST NULL, ERLOES = Summe der Unterpositionen). APplus setzt an
     * diesen Koepfen KAPITELSUMME=1 - das ist gerade das Merkmal und darf nicht
     * ausschliessen. Zwischensummen (ZWISCHENSUMME, USERPOS "x.y*") wiederholen
     * dagegen nur Betraege und bleiben aussen vor. Die
     * Unterpositionen haengen nur ueber USERPOS daran ("3" -> "3.10", "3.10.1").
     *
     * <p>Der Steuersatz kommt aus dem Kopf selbst oder, wie dort ueblich leer, von
     * den Unterpositionen - nur wenn die alle denselben Satz tragen; bei
     * gemischten Saetzen laesst sich der Kopf nicht als eine Zeile abbilden und
     * bleibt beim bisherigen Verhalten. Liegt ein Kopf unter einem anderen (z. B.
     * 3.10 unter 3), zaehlt nur der aeussere.
     *
     * @return Kopf -&gt; Steuersatz, nur die aeussersten Koepfe
     */
    private static Map<InternInvoiceItem, BigDecimal> findPricedHeads(List<InternInvoiceItem> internItems) {
        Map<InternInvoiceItem, BigDecimal> candidates = new HashMap<>();
        for (InternInvoiceItem head : internItems) {
            String headPos = head.getUserPos();
            InternItemAmounts amounts = head.getAmounts();
            InternItemSpecialFlags flags = head.getSpecialFlags();
            if (headPos == null || headPos.trim().isEmpty() || amounts == null
                    || amounts.getNet() != null || amounts.getRevenue() == null
                    || amounts.getRevenue().signum() == 0
                    || Boolean.TRUE.equals(head.getDontPrint())
                    || (flags != null && isFlagSet(flags.getSubtotal()))) {
                continue;
            }

            BigDecimal ownRate = amounts.getTax() != null ? amounts.getTax().getTaxRate() : null;
            BigDecimal rate = ownRate;
            if (rate == null) {
                String prefix = headPos.trim() + ".";
                for (InternInvoiceItem child : internItems) {
                    String childPos = child.getUserPos();
                    if (childPos == null || !childPos.trim().startsWith(prefix)
                            || child.getAmounts() == null || child.getAmounts().getTax() == null) {
                        continue;
                    }
                    BigDecimal childRate = child.getAmounts().getTax().getTaxRate();
                    if (childRate == null) {
                        continue;
                    }
                    if (rate == null) {
                        rate = childRate;
                    } else if (rate.compareTo(childRate) != 0) {
                        rate = null; // gemischte Steuersaetze
                        break;
                    }
                }
            }
            if (rate != null) {
                candidates.put(head, rate);
            }
        }

        Map<InternInvoiceItem, BigDecimal> heads = new HashMap<>();
        for (Map.Entry<InternInvoiceItem, BigDecimal> entry : candidates.entrySet()) {
            if (!isInsidePricedHead(entry.getKey(), candidates)) {
                heads.put(entry.getKey(), entry.getValue());
            }
        }
        return heads;
    }

    /** Liegt die Position (per USERPOS) unter einem der uebergebenen Koepfe? */
    private static boolean isInsidePricedHead(InternInvoiceItem item, Map<InternInvoiceItem, BigDecimal> heads) {
        String pos = item.getUserPos();
        if (pos == null || heads.isEmpty()) {
            return false;
        }
        pos = pos.trim();
        for (InternInvoiceItem head : heads.keySet()) {
            if (head != item && pos.startsWith(head.getUserPos().trim() + ".")) {
                return true;
            }
        }
        return false;
    }

    /** Ersatzbezeichnung fuer eine Gliederungszeile ohne Bezeichnung. */
    private static final String FALLBACK_ITEM_NAME = "Textposition";

    /** Ersatzbezeichnung fuer eine Gliederungszeile mit KAPITELSUMME. */
    private static final String FALLBACK_CHAPTER_SUM_NAME = "Kapitelsumme";

    /** Ersatzbezeichnung fuer eine Gliederungszeile mit ZWISCHENSUMME. */
    private static final String FALLBACK_SUBTOTAL_NAME = "Zwischensumme";

    /**
     * Ermittelt den Item name (BT-153): NAME, ersatzweise NAMEINTERN (beides
     * bereits in {@code product.getName()} zusammengefuehrt).
     *
     * <p>Reihenfolge: NAME/NAMEINTERN, dann - nur fuer Gliederungszeilen, die
     * APplus selbst kennzeichnet (DONTPRINTPRICE, TEXTPOS, KAPITELSUMME,
     * ZWISCHENSUMME) - "Kapitelsumme" / "Zwischensumme" / "Textposition", und
     * fuer jede andere Position ersatzweise die <b>Artikelnummer</b>.
     *
     * <p>TEXTPOS/KAPITELSUMME/ZWISCHENSUMME sind noetig, weil DONTPRINTPRICE
     * allein nicht traegt: eine Textposition kann die Kennzeichnung "Preis
     * nicht drucken" verlieren (beobachtet an einer Schlussrechnung, Pos. 2.3
     * "Zubehoer Schmierstoff") und riss dann mit einem leeren ram:Name
     * gleichzeitig BR-25 und PEPPOL-EN16931-R008 auf.
     *
     * <p>Leer - und damit ein sichtbarer Datenfehler ueber BR-25 /
     * PEPPOL-EN16931-R008 - bleibt der Name nur, wenn eine gewoehnliche Position
     * weder Bezeichnung noch Artikelnummer hat.
     *
     * <p>Der Positionstext bleibt die Description (BT-154) und wird hier nie
     * als Name verwendet. Ausnahme: mit dem Konvertierungsschluessel
     * <code>ITEMNAME=TEXT</code> steht er bereits vorher in
     * {@code product.getName()} (siehe {@link #isSalesTextAsItemName()}).
     */
    private static String resolveItemName(InternInvoiceItem item, Product product,
            boolean dontPrintPrice) {
        String name = product.getName();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }

        InternItemSpecialFlags flags = item.getSpecialFlags();
        boolean chapterSum = flags != null && isFlagSet(flags.getChapterSum());
        boolean subtotal = flags != null && isFlagSet(flags.getSubtotal());
        boolean textPosition = flags != null && isFlagSet(flags.getTextPosition());

        if (dontPrintPrice || chapterSum || subtotal || textPosition) {
            if (chapterSum) {
                return FALLBACK_CHAPTER_SUM_NAME;
            }
            if (subtotal) {
                return FALLBACK_SUBTOTAL_NAME;
            }
            return FALLBACK_ITEM_NAME;
        }

        // Gewoehnliche Position ohne Bezeichnung: die Artikelnummer ist besser als
        // nichts - ohne Item name faellt der ganze Beleg ueber BR-25 durch.
        // Beobachtet an RE 3004532 Pos 6 (Artikel "Rabatt" ohne Benennung).
        // Bewusst NACH den Gliederungs-Flags: eine Textposition, die eine
        // Platzhalter-Artikelnummer traegt, soll weiter "Textposition" heissen.
        InternItemMasterData masterData = item.getMasterData();
        String artikelnummer = (masterData == null) ? null : masterData.getArticleNumber();
        if (artikelnummer != null && !artikelnummer.trim().isEmpty()) {
            return artikelnummer.trim();
        }

        return name; // absichtlich leer -> Validierung muss anschlagen
    }

    /** APplus liefert Bool-Flags als "1"/"0" oder "true"/"false". */
    private static boolean isFlagSet(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return "1".equals(trimmed) || "true".equalsIgnoreCase(trimmed);
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