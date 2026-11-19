/*
 * CUSTOM:TEK/MEB:19.11.2026: Erweiterungsabschnitt der Cloud-Variante (Standardschema V2).
 */
package org.mustangproject.intern.conversion;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.mustangproject.Allowance;
import org.mustangproject.BankDetails;
import org.mustangproject.Charge;
import org.mustangproject.ClassCode;
import org.mustangproject.DesignatedProductClassification;
import org.mustangproject.DirectDebit;
import org.mustangproject.FileAttachment;
import org.mustangproject.IncludedNote;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.ReferencedDocument;
import org.mustangproject.SchemedID;
import org.mustangproject.SubjectCode;
import org.mustangproject.TradeParty;
import org.mustangproject.XMLTools;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableItem;
import org.mustangproject.ZUGFeRD.Profile;
import org.mustangproject.ZUGFeRD.Profiles;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Wendet den optionalen Abschnitt {@code <zusatz>} des Zwischenformats auf das
 * Mustang-Objekt an. Das ERP-Zwischenformat kennt viele Angaben der EN 16931 nicht
 * (Kopf-Zu-/Abschlaege mit Grund, Leistungszeitraum, Anhaenge, Zahlungsempfaenger,
 * Kartenzahlung, eigene Befreiungsgruende ...). Die Cloud-Variante schreibt sie in
 * diesen Abschnitt; fehlt er, bleibt die Ausgabe byte-gleich wie bisher.
 *
 * <pre>
 * &lt;zusatz&gt;
 *   TYPECODE, DOKUMENTNAME, ZEITRAUMVON, ZEITRAUMBIS, PROJEKT, PROJEKTNAME, VERTRAG,
 *   LIEFERSCHEIN, AUSSCHREIBUNG, OBJEKT, OBJEKTSCHEMA, BUCHUNGSKONTO, ZAHLUNGSREFERENZ,
 *   VORAUSBEZAHLT, RUNDUNG
 *   &lt;vorgaenger&gt; NUMMER, DATUM
 *   &lt;notiz&gt; THEMA, TEXT
 *   &lt;zuabschlag&gt; ZUSCHLAG (true/false), BETRAG, BASIS, PROZENT, GRUND, GRUNDCODE, UST, USTKATEGORIE
 *   &lt;befreiung&gt; USTKATEGORIE, GRUND, GRUNDCODE
 *   &lt;zahlungsart&gt; CODE, TEXT, IBAN, BIC, KONTOINHABER, MANDAT, GLAEUBIGERID, SCHULDNERIBAN,
 *                 KARTENNUMMER, KARTENINHABER
 *   &lt;skonto&gt; TAGE, PROZENT   (liest der XMLInvoiceWorkerImpl)
 *   &lt;anhang&gt; ID, DATEINAME, MIME, BESCHREIBUNG, INHALT (Base64), URI
 *   &lt;rechnungsempfaenger&gt; FIRMA1, FIRMA2, NAME, STRASSE, PLZ, ORT, LANDISO, EGSTEUERNR, EMAIL, ANP_PARTYID, ANP_GLN
 *   &lt;zahlungsempfaenger&gt; FIRMA1, ANP_PARTYID, ANP_GLN
 *   &lt;zeile pos="n"&gt; ZEITRAUMVON, ZEITRAUMBIS, EINHEIT, GTIN, ZOLLTARIF, URSPRUNGSLAND, NOTIZ,
 *                  OBJEKT, BESTELLPOS, BUCHUNGSKONTO, USTKATEGORIE,
 *                  &lt;merkmal&gt; NAME, WERT   &lt;zuabschlag&gt; wie im Kopf
 * &lt;/zusatz&gt;
 * </pre>
 */
public final class CloudExtension {

    private CloudExtension() {
    }

    // ------------------------------------------------------------------ Position

    /**
     * Positionsangaben. Wird in extractInvoiceItems vor dem Hinzufuegen der Position
     * aufgerufen (Preis und Menge stehen schon fest).
     */
    public static void applyLine(Item item, Product product, Element zeile, String iface) throws Exception {
        if (zeile == null) {
            return;
        }
        String einheit = text(zeile, "EINHEIT");
        if (einheit != null) {
            product.setUnit(einheit);
        }
        String kategorie = text(zeile, "USTKATEGORIE");
        if (kategorie != null) {
            // Kategorie ohne Einschraenkung (auch O, L, M); Befreiungsgruende setzt applyHead
            product.setTaxCategoryCode(kategorie.toUpperCase());
            if (!"AE".equals(kategorie.toUpperCase()) && !"K".equals(kategorie.toUpperCase())
                    && !"G".equals(kategorie.toUpperCase()) && !"E".equals(kategorie.toUpperCase())) {
                product.setTaxExemptionReason(null);
                product.setTaxExemptionReasonCode(null);
            }
        }
        String gtin = text(zeile, "GTIN");
        if (gtin != null) {
            product.addGlobalID(new SchemedID("0160", gtin));
        }
        String land = text(zeile, "URSPRUNGSLAND");
        if (land != null) {
            product.setCountryOfOrigin(land.toUpperCase());
        }
        String zoll = text(zeile, "ZOLLTARIF");
        if (zoll != null) {
            product.addClassification(new DesignatedProductClassification(new ClassCode("HS", zoll), null));
        }
        for (Element m : children(zeile, "merkmal")) {
            String name = text(m, "NAME");
            String wert = text(m, "WERT");
            if (name != null && wert != null) {
                product.addAttribute(name, wert);
            }
        }
        Date von = date(text(zeile, "ZEITRAUMVON"));
        Date bis = date(text(zeile, "ZEITRAUMBIS"));
        if (von != null || bis != null) {
            item.setDetailedDeliveryPeriod(von, bis);
        }
        String notiz = text(zeile, "NOTIZ");
        if (notiz != null) {
            item.addNote(notiz);
        }
        String objekt = text(zeile, "OBJEKT");
        if (objekt != null) {
            item.addAdditionalReference(new ReferencedDocument(objekt));
        }
        String bestellpos = text(zeile, "BESTELLPOS");
        if (bestellpos != null) {
            item.setBuyerOrderReferencedDocument(new ReferencedDocument().setLineID(bestellpos));
        }
        String konto = text(zeile, "BUCHUNGSKONTO");
        if (konto != null) {
            item.setAccountingReference(konto);
        }
        for (Element za : children(zeile, "zuabschlag")) {
            Charge c = allowanceCharge(za, product.getVATPercent(), product.getTaxCategoryCode(), iface);
            if (c == null) {
                continue;
            }
            if (c instanceof Allowance) {
                item.addAllowance(c);
            } else {
                item.addCharge(c);
            }
        }
    }

    // ------------------------------------------------------------------ Kopf

    /** Kopfangaben, ganz am Ende von generateMustangInvoice. */
    public static void applyHead(Invoice invoice, Element z, String iface, String language) throws Exception {
        if (z == null) {
            return;
        }
        String typ = text(z, "TYPECODE");
        if (typ != null) {
            invoice.setDocumentCode(typ);
        }
        // Dokumentname nur aus dem Abschnitt (sonst stuende das ERP-Kuerzel "RE"/"GU" darin)
        invoice.setDocumentName(text(z, "DOKUMENTNAME"));

        Date von = date(text(z, "ZEITRAUMVON"));
        Date bis = date(text(z, "ZEITRAUMBIS"));
        if (von != null || bis != null) {
            invoice.setDetailedDeliveryPeriod(von, bis);
        }
        String projekt = text(z, "PROJEKT");
        if (projekt != null) {
            invoice.setSpecifiedProcuringProjectID(projekt);
            // BT-11 verlangt in CII einen Namen; ohne Angabe steht dort die Kennung
            String name = text(z, "PROJEKTNAME");
            invoice.setSpecifiedProcuringProjectName(name != null ? name : projekt);
        }
        String vertrag = text(z, "VERTRAG");
        if (vertrag != null) {
            invoice.setContractReferencedDocument(new ReferencedDocument(vertrag));
        }
        String lieferschein = text(z, "LIEFERSCHEIN");
        if (lieferschein != null) {
            invoice.setDespatchAdviceReferencedDocumentID(lieferschein);
        }
        String ausschreibung = text(z, "AUSSCHREIBUNG");
        if (ausschreibung != null) {
            invoice.setTenderReferencedDocument(ausschreibung);
        }
        String objekt = text(z, "OBJEKT");
        if (objekt != null) {
            String schema = text(z, "OBJEKTSCHEMA");
            invoice.setObjectIdentifierReferencedDocument(objekt, schema);
        }
        String konto = text(z, "BUCHUNGSKONTO");
        if (konto != null) {
            invoice.setBuyerAccountingReference(konto);
        }
        String ref = text(z, "ZAHLUNGSREFERENZ");
        if (ref != null) {
            invoice.setPaymentReference(ref);
        }

        // Vorgaengerrechnungen (BG-3): Kopfbezug aus URRECHNUNG bleibt, Datum wird ergaenzt
        for (Element v : children(z, "vorgaenger")) {
            String nr = text(v, "NUMMER");
            if (nr == null) {
                continue;
            }
            Date datum = date(text(v, "DATUM"));
            ReferencedDocument vorhanden = null;
            if (invoice.getInvoiceReferencedDocuments() != null) {
                for (ReferencedDocument d : invoice.getInvoiceReferencedDocuments()) {
                    if (nr.equals(d.getIssuerAssignedID())) {
                        vorhanden = d;
                    }
                }
            }
            if (vorhanden == null && nr.equals(invoice.getInvoiceReferencedDocumentID())) {
                if (datum != null) {
                    invoice.setInvoiceReferencedIssueDate(datum);
                }
                continue;
            }
            if (vorhanden == null) {
                ReferencedDocument d = new ReferencedDocument().setIssuerAssignedID(nr);
                if (datum != null) {
                    d.setFormattedIssueDateTime(datum);
                }
                invoice.addInvoiceReferencedDocument(d);
            } else if (datum != null) {
                vorhanden.setFormattedIssueDateTime(datum);
            }
        }

        // Hinweise (BG-1) mit Betreff (UNTDID 4451)
        for (Element n : children(z, "notiz")) {
            String t = text(n, "TEXT");
            if (t == null) {
                continue;
            }
            SubjectCode code = null;
            String thema = text(n, "THEMA");
            if (thema != null) {
                try {
                    code = SubjectCode.valueOf(thema.toUpperCase());
                } catch (IllegalArgumentException e) {
                    code = null;
                }
            }
            invoice.addNotes(java.util.Collections.singletonList(
                    code != null ? new IncludedNote(t, code) : IncludedNote.unspecifiedNote(t)));
        }

        // Rechnungsempfaenger (nur EXTENDED schreibt InvoiceeTradeParty)
        Element re = child(z, "rechnungsempfaenger");
        if (re != null && text(re, "FIRMA1") != null) {
            invoice.setInvoicee(party(re));
        }
        // Zahlungsempfaenger (BG-10)
        Element ze = child(z, "zahlungsempfaenger");
        if (ze != null && text(ze, "FIRMA1") != null) {
            TradeParty payee = new TradeParty();
            payee.setName(text(ze, "FIRMA1"));
            if (text(ze, "ANP_PARTYID") != null) {
                payee.setID(text(ze, "ANP_PARTYID"));
            }
            if (text(ze, "ANP_GLN") != null) {
                payee.addGlobalID(new SchemedID("0088", text(ze, "ANP_GLN")));
            }
            invoice.setPayee(payee);
        }

        applyPaymentMeans(invoice, z);

        // Kopf-Zu-/Abschlaege (BG-20/BG-21)
        for (Element za : children(z, "zuabschlag")) {
            Charge c = allowanceCharge(za, null, null, iface);
            if (c == null) {
                continue;
            }
            if (c instanceof Allowance) {
                invoice.addAllowance(c);
            } else {
                invoice.addCharge(c);
            }
        }

        // Befreiungsgruende je Kategorie (BT-120/BT-121) fuer Positionen und Kopf-Zu-/Abschlaege
        for (Element b : children(z, "befreiung")) {
            String kat = text(b, "USTKATEGORIE");
            if (kat == null) {
                continue;
            }
            kat = kat.toUpperCase();
            String grund = text(b, "GRUND");
            String code = text(b, "GRUNDCODE");
            if (invoice.getZFItems() != null) {
                for (IZUGFeRDExportableItem it : invoice.getZFItems()) {
                    if (it.getProduct() instanceof Product && kat.equals(it.getProduct().getTaxCategoryCode())) {
                        ((Product) it.getProduct()).setTaxExemptionReason(grund);
                        ((Product) it.getProduct()).setTaxExemptionReasonCode(code);
                    }
                }
            }
            for (Object o : concat(invoice.getZFAllowances(), invoice.getZFCharges())) {
                if (o instanceof Charge && kat.equals(((Charge) o).getCategoryCode())) {
                    ((Charge) o).setTaxExemptionReason(grund);
                    ((Charge) o).setTaxExemptionReasonCode(code);
                }
            }
        }

        // Anhaenge (BG-24)
        for (Element a : children(z, "anhang")) {
            String inhalt = text(a, "INHALT");
            String uri = text(a, "URI");
            if (inhalt == null && uri == null) {
                continue;
            }
            FileAttachment f = new FileAttachment(text(a, "DATEINAME"), text(a, "MIME"),
                    inhalt == null ? null : Base64.getMimeDecoder().decode(inhalt));
            f.setDescription(text(a, "BESCHREIBUNG"));
            f.setId(text(a, "ID"));
            f.setUri(uri);
            invoice.embedFileInXML(f);
        }

        // Bereits gezahlt (BT-113) ohne Anzahlungsrechnung: zu verrechneten Anzahlungen addieren
        BigDecimal bezahlt = number(text(z, "VORAUSBEZAHLT"));
        if (bezahlt != null && bezahlt.signum() != 0) {
            BigDecimal vorher = invoice.getTotalPrepaidAmount();
            invoice.setTotalPrepaidAmount(vorher == null ? bezahlt : vorher.add(bezahlt));
        }
        // Rundungsbetrag (BT-114) aus den Daten hat Vorrang vor dem automatischen Ausgleich
        BigDecimal rundung = number(text(z, "RUNDUNG"));
        if (rundung != null) {
            invoice.setRoundingAmount(rundung.signum() == 0 ? null : rundung);
        }
    }

    /** Zahlungsarten (BG-16 bis BG-19). Ersetzen die Bankverbindung des Verkaeufers. */
    private static void applyPaymentMeans(Invoice invoice, Element z) {
        List<Element> arten = children(z, "zahlungsart");
        TradeParty seller = invoice.getSender();
        if (arten.isEmpty() || seller == null) {
            return;
        }
        seller.getBankDetails().clear();
        seller.getDebitDetails().clear();
        for (Element a : arten) {
            String code = text(a, "CODE");
            if (code == null) {
                code = "58";
            }
            String info = text(a, "TEXT");
            switch (code) {
                case "59": // SEPA-Lastschrift
                case "49": // Lastschrift
                    DirectDebit dd = new DirectDebit(nz(text(a, "SCHULDNERIBAN")).replace(" ", ""), nz(text(a, "MANDAT")));
                    dd.setPaymentMeansCode(code);
                    dd.setPaymentMeansInformation(info != null ? info : ("59".equals(code) ? "SEPA-Lastschrift" : "Lastschrift"));
                    seller.addDebitDetails(dd);
                    String glaeubiger = text(a, "GLAEUBIGERID");
                    if (glaeubiger != null) {
                        invoice.setCreditorReferenceID(glaeubiger);
                    }
                    break;
                case "48": // Bankkarte
                case "54": // Kreditkarte
                case "55": // Debitkarte
                    seller.getBankDetails().add(new CardPayment(code, info, text(a, "KARTENNUMMER"), text(a, "KARTENINHABER")));
                    break;
                default: // 58 SEPA-Ueberweisung, 30 Ueberweisung, 10 bar, 57 Dauerauftrag, 1 sonstige ...
                    String iban = text(a, "IBAN");
                    BankDetails bd = new BankDetails(iban == null ? null : iban.replace(" ", ""), text(a, "BIC"));
                    bd.setPaymentMeansCode(code);
                    bd.setPaymentMeansInformation(info != null ? info : defaultInfo(code));
                    if (iban != null) {
                        String inhaber = text(a, "KONTOINHABER");
                        bd.setAccountName(inhaber != null ? inhaber : seller.getName());
                    }
                    seller.getBankDetails().add(bd);
                    break;
            }
        }
    }

    private static String defaultInfo(String code) {
        switch (code) {
            case "58":
                return "SEPA-Überweisung";
            case "30":
                return "Überweisung";
            case "10":
                return "Barzahlung";
            case "57":
                return "Dauerauftrag";
            default:
                return "Zahlung";
        }
    }

    /** Kartenzahlung (BG-18): CII ram:ApplicableTradeSettlementFinancialCard. */
    static final class CardPayment extends BankDetails {
        private final String number;
        private final String holder;

        CardPayment(String code, String info, String number, String holder) {
            super();
            this.paymentMeansCode = code;
            this.paymentMeansInformation = info != null ? info : "Kartenzahlung";
            // BR-51: hoechstens die letzten 4-6 Ziffern der Kartennummer
            String n = number == null ? "" : number.replaceAll("\\s", "");
            this.number = n.length() > 6 ? n.substring(n.length() - 4) : n;
            this.holder = holder;
        }

        @Override
        public String getSettlementXML(Profile profile) {
            StringBuilder xml = new StringBuilder("<ram:SpecifiedTradeSettlementPaymentMeans>")
                    .append("<ram:TypeCode>").append(XMLTools.encodeXML(paymentMeansCode)).append("</ram:TypeCode>");
            if (profile != Profiles.getByName("Basic") && profile != Profiles.getByName("BasicWL")
                    && profile != Profiles.getByName("Minimum")) {
                xml.append("<ram:Information>").append(XMLTools.encodeXML(paymentMeansInformation)).append("</ram:Information>");
                if (!number.isEmpty()) {
                    xml.append("<ram:ApplicableTradeSettlementFinancialCard>")
                            .append("<ram:ID>").append(XMLTools.encodeXML(number)).append("</ram:ID>");
                    if (holder != null) {
                        xml.append("<ram:CardholderName>").append(XMLTools.encodeXML(holder)).append("</ram:CardholderName>");
                    }
                    xml.append("</ram:ApplicableTradeSettlementFinancialCard>");
                }
            }
            return xml.append("</ram:SpecifiedTradeSettlementPaymentMeans>").toString();
        }
    }

    /**
     * Zu- oder Abschlag aus {@code <zuabschlag>}. Betrag hat Vorrang; ohne Betrag rechnet
     * Mustang aus Prozent und Basis. Steuersatz/-kategorie: aus dem Element, sonst von der
     * Position.
     */
    private static Charge allowanceCharge(Element za, BigDecimal posSatz, String posKategorie, String iface) {
        BigDecimal betrag = number(text(za, "BETRAG"));
        BigDecimal prozent = number(text(za, "PROZENT"));
        BigDecimal basis = number(text(za, "BASIS"));
        if (betrag == null && (prozent == null || basis == null)) {
            return null;
        }
        if (betrag == null) {
            betrag = basis.multiply(prozent).divide(new BigDecimal(100), 2, BigDecimal.ROUND_HALF_UP);
        }
        boolean zuschlag = "true".equalsIgnoreCase(nz(text(za, "ZUSCHLAG"))) || "1".equals(nz(text(za, "ZUSCHLAG")));
        Charge c = zuschlag ? new Charge(betrag) : new Allowance(betrag);
        if (prozent != null) {
            c.setPercent(prozent);
        }
        if (basis != null) {
            c.setBasisAmount(basis);
        }
        String grund = text(za, "GRUND");
        String code = text(za, "GRUNDCODE");
        if (grund == null && code == null) {
            grund = zuschlag ? "Zuschlag" : "Rabatt";
        }
        c.setReason(grund);
        if (code != null) {
            c.setReasonCode(code);
        }
        BigDecimal satz = number(text(za, "UST"));
        c.setTaxPercent(satz != null ? satz : (posSatz != null ? posSatz : BigDecimal.ZERO));
        String kat = text(za, "USTKATEGORIE");
        if (kat == null) {
            kat = posKategorie;
        }
        if (kat != null) {
            c.setCategoryCode(kat.toUpperCase());
        }
        return c;
    }

    private static TradeParty party(Element e) {
        TradeParty p = new TradeParty();
        p.setName(text(e, "FIRMA1"));
        p.setAdditionalAddress(text(e, "FIRMA2"));
        p.setStreet(text(e, "STRASSE"));
        p.setZIP(text(e, "PLZ"));
        p.setLocation(text(e, "ORT"));
        p.setCountry(text(e, "LANDISO"));
        if (text(e, "EGSTEUERNR") != null) {
            p.setVATID(text(e, "EGSTEUERNR"));
        }
        if (text(e, "ANP_PARTYID") != null) {
            p.setID(text(e, "ANP_PARTYID"));
        }
        if (text(e, "ANP_GLN") != null) {
            p.addGlobalID(new SchemedID("0088", text(e, "ANP_GLN")));
        }
        if (text(e, "NAME") != null || text(e, "EMAIL") != null) {
            org.mustangproject.Contact c = new org.mustangproject.Contact();
            c.setName(text(e, "NAME"));
            c.setEMail(text(e, "EMAIL"));
            p.setContact(c);
        }
        return p;
    }

    // ------------------------------------------------------------------ Hilfen

    private static List<Object> concat(Object[] a, Object[] b) {
        List<Object> l = new ArrayList<>();
        if (a != null) {
            java.util.Collections.addAll(l, a);
        }
        if (b != null) {
            java.util.Collections.addAll(l, b);
        }
        return l;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static Element child(Element parent, String name) {
        List<Element> l = children(parent, name);
        return l.isEmpty() ? null : l.get(0);
    }

    static List<Element> children(Element parent, String name) {
        List<Element> l = new ArrayList<>();
        if (parent == null) {
            return l;
        }
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && name.equals(n.getNodeName())) {
                l.add((Element) n);
            }
        }
        return l;
    }

    /** Text eines direkten Kindelements, getrimmt; leer = null. */
    static String text(Element parent, String name) {
        Element e = child(parent, name);
        if (e == null) {
            return null;
        }
        String t = e.getTextContent();
        return t == null || t.trim().isEmpty() ? null : t.trim();
    }

    private static Date date(String s) {
        if (s == null) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (java.text.ParseException e) {
            return null;
        }
    }

    private static BigDecimal number(String s) {
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
