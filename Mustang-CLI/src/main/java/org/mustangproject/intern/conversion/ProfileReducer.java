/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.conversion;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * CUSTOM:TEK/MEB:19.11.2026: Kuerzt ein CII-XML auf die Elemente, die das Factur-X-Profil
 * BASIC, BASIC WL oder MINIMUM erlaubt. Mustang laesst dort nicht alles weg, was nur
 * EN16931/EXTENDED kennt (z.B. SellerAssignedID, DefinedTradeContact, IncludedNote).
 *
 * Gefuehrt vom offiziellen XSD des Profils (aus dem Validator): Jedes Element, das das XSD an
 * seiner Stelle nicht zulaesst (cvc-complex-type.2.4.a/d), wird entfernt, ein Element, das leer
 * sein muss (cvc-complex-type.2.1), wird geleert - bis das XSD passt.
 * Nur fuer die Schnittstellen B/W/M - Z/X/E/P laufen hier nie durch.
 */
final class ProfileReducer {

    private static final String CURRENT_NODE = "http://apache.org/xml/properties/dom/current-element-node";
    private static final int MAX_STEPS = 500;

    private ProfileReducer() {
    }

    static String reduce(String xml, String profile) throws Exception {
        String xsd;
        switch (profile) {
            case "BASIC":
                xsd = "/schema/ZF_250/BASIC/FACTUR-X_BASIC.xsd";
                break;
            case "BASICWL":
                xsd = "/schema/ZF_250/BASIC-WL/FACTUR-X_BASICWL.xsd";
                break;
            case "MINIMUM":
                xsd = "/schema/ZF_250/MINIMUM/FACTUR-X_MINIMUM.xsd";
                break;
            default:
                return xml;
        }
        URL url = ProfileReducer.class.getResource(xsd);
        if (url == null) {
            throw new Exception("XSD fuer Profil " + profile + " nicht gefunden: " + xsd);
        }
        Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(url);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        for (int i = 0; i < MAX_STEPS; i++) {
            final Validator validator = schema.newValidator();
            final Node[] bad = new Node[1];
            final boolean[] emptyOnly = new boolean[1];
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    if (bad[0] == null && (msg.startsWith("cvc-complex-type.2.4.a") || msg.startsWith("cvc-complex-type.2.4.d")
                            || msg.startsWith("cvc-complex-type.2.1"))) {
                        emptyOnly[0] = msg.startsWith("cvc-complex-type.2.1");
                        try {
                            bad[0] = (Node) validator.getProperty(CURRENT_NODE);
                        } catch (SAXException ex) {
                            bad[0] = null;
                        }
                    }
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            try {
                validator.validate(new DOMSource(doc));
                break; // passt
            } catch (SAXException e) {
                if (bad[0] == null || bad[0].getParentNode() == null || bad[0] == doc.getDocumentElement()) {
                    break; // anderer Fehler: so lassen, die Validierung meldet ihn
                }
                if (emptyOnly[0]) {
                    // Element muss leer sein (z.B. ApplicableHeaderTradeDelivery in MINIMUM)
                    if (!bad[0].hasChildNodes()) {
                        break;
                    }
                    while (bad[0].hasChildNodes()) {
                        bad[0].removeChild(bad[0].getFirstChild());
                    }
                } else {
                    bad[0].getParentNode().removeChild(bad[0]);
                }
            }
        }

        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter w = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(w));
        return w.toString();
    }
}
