/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.conversion;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.mustangproject.CII.CIIToUBL;

/**
 * CUSTOM:TEK/MEB:19.11.2026: EN16931-CII -> UBL 2.1 nach PEPPOL BIS Billing 3.0
 * (Schnittstelle "P"). Dieselbe Umwandlung wie "--action ubl" mit den PEPPOL-Kennungen.
 */
public final class PeppolUBL {

    public static final String PROFILE_ID = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
    public static final String CUSTOMIZATION_ID =
            "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0";

    private static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String RAM =
            "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";
    /** Elemente in cac:PaymentMeans vor cac:CardAccount (UBL 2.1). */
    private static final List<String> BEFORE_CARD = Arrays.asList("PaymentMeansCode", "PaymentDueDate",
            "PaymentChannelCode", "InstructionID", "InstructionNote", "PaymentID");

    private PeppolUBL() {
    }

    public static String fromCII(String ciiXml) throws Exception {
        File in = File.createTempFile("peppol-cii", ".xml");
        File out = File.createTempFile("peppol-ubl", ".xml");
        try {
            Files.write(in.toPath(), ciiXml.getBytes(StandardCharsets.UTF_8));
            out.delete();
            new CIIToUBL().convert(in, out, PROFILE_ID, CUSTOMIZATION_ID);
            if (!out.exists() || out.length() == 0) {
                throw new Exception("PEPPOL: Umwandlung CII -> UBL ohne Ergebnis.");
            }
            return fix(ciiXml, new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
        } finally {
            in.delete();
            out.delete();
        }
    }

    /**
     * CUSTOM:TEK/MEB:19.11.2026: Lücken der CII->UBL-Umwandlung (helger cii2ubl 2.3) schließen.
     * - Kartenzahlung: CardAccount wird nur für Code 48 erzeugt, nicht für 54/55 (DE-R-024-1).
     * - Gutschrift (CreditNote): Vorgängerrechnung gehört in BillingReference/InvoiceDocumentReference
     *   (BR-55), das Fälligkeitsdatum in PaymentMeans/PaymentDueDate (kein cbc:DueDate im Kopf).
     * Ändert nichts, wenn keiner der Fälle vorliegt.
     */
    static String fix(String ciiXml, String ublXml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document ubl = f.newDocumentBuilder().parse(new ByteArrayInputStream(ublXml.getBytes(StandardCharsets.UTF_8)));
        Document cii = f.newDocumentBuilder().parse(new ByteArrayInputStream(ciiXml.getBytes(StandardCharsets.UTF_8)));
        Element root = ubl.getDocumentElement();
        boolean changed = false;

        List<Element> means = children(root, CAC, "PaymentMeans");
        NodeList ciiMeans = cii.getElementsByTagNameNS(RAM, "SpecifiedTradeSettlementPaymentMeans");
        for (int i = 0; i < means.size() && i < ciiMeans.getLength(); i++) {
            Element pm = means.get(i);
            String code = text(first(pm, CBC, "PaymentMeansCode"));
            if (!("54".equals(code) || "55".equals(code)) || first(pm, CAC, "CardAccount") != null) {
                continue;
            }
            Element card = first((Element) ciiMeans.item(i), RAM, "ApplicableTradeSettlementFinancialCard");
            if (card == null || text(first(card, RAM, "ID")).isEmpty()) {
                continue;
            }
            Element acc = ubl.createElementNS(CAC, "cac:CardAccount");
            add(acc, CBC, "cbc:PrimaryAccountNumberID", text(first(card, RAM, "ID")));
            add(acc, CBC, "cbc:NetworkID", "mapped-from-cii");
            String holder = text(first(card, RAM, "CardholderName"));
            if (!holder.isEmpty()) {
                add(acc, CBC, "cbc:HolderName", holder);
            }
            pm.insertBefore(acc, firstNotIn(pm, BEFORE_CARD));
            changed = true;
        }

        if ("CreditNote".equals(root.getLocalName())) {
            for (Element br : children(root, CAC, "BillingReference")) {
                for (Element ref : children(br, CAC, "CreditNoteDocumentReference")) {
                    ubl.renameNode(ref, CAC, "cac:InvoiceDocumentReference");
                    changed = true;
                }
            }
            Element due = first(root, CBC, "DueDate");
            if (due != null) {
                for (Element pm : means) {
                    if (first(pm, CBC, "PaymentDueDate") == null) {
                        Element d = ubl.createElementNS(CBC, "cbc:PaymentDueDate");
                        d.setTextContent(text(due));
                        Element code = first(pm, CBC, "PaymentMeansCode");
                        pm.insertBefore(d, code == null ? pm.getFirstChild() : code.getNextSibling());
                    }
                }
                root.removeChild(due);
                changed = true;
            }
        }
        if (!changed) {
            return ublXml;
        }
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter w = new StringWriter();
        t.transform(new DOMSource(ubl), new StreamResult(w));
        return w.toString();
    }

    private static List<Element> children(Element parent, String ns, String name) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && ns.equals(n.getNamespaceURI()) && name.equals(n.getLocalName())) {
                out.add((Element) n);
            }
        }
        return out;
    }

    private static Element first(Element parent, String ns, String name) {
        List<Element> l = children(parent, ns, name);
        return l.isEmpty() ? null : l.get(0);
    }

    private static Node firstNotIn(Element parent, List<String> names) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && !names.contains(n.getLocalName())) {
                return n;
            }
        }
        return null;
    }

    private static String text(Element e) {
        return e == null ? "" : e.getTextContent().trim();
    }

    private static void add(Element parent, String ns, String qname, String value) {
        Element e = parent.getOwnerDocument().createElementNS(ns, qname);
        e.setTextContent(value);
        parent.appendChild(e);
    }
}
