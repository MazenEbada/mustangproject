package org.mustangproject.applus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/*
 * MEB: APplus Parser
 */
public class APplusIncomingXMLParser {

	public static String getFriendlyXML(String complexXML, Map<String,String> conversionKeys) {
	    try {
	    	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	    	DocumentBuilder builder = factory.newDocumentBuilder();

	    	InputSource inputSource = new InputSource(new StringReader(complexXML));
	    	Document doc = builder.parse(inputSource);
	    	doc.getDocumentElement().normalize();


	        Element rechnungElement = getElement(doc, "rechnung");
	        Map<String, String> manuelleLieferadresseData = extractManuelleLieferadresse(rechnungElement);
	        Map<String, String> textData = extractText(doc, rechnungElement, "stdtxt");
	        Map<String, String> eRechnungData = extractERechnung(rechnungElement);
	        Map<String, String> datumData = extractDatum(rechnungElement);
	        Map<String, String> betragData = extractBetrag(rechnungElement);
	        Map<String, String> ustData = extractUST(doc, "ust");
	        Map<String, String> fkData = extractFK(doc, "frachtkosten");
	        Map<String, String> weitereRechnungsdaten = extractWeitereRechnungsdaten(rechnungElement);

	        Map<String, String> verkaeuferAdresseData = extractAddress(doc, "supplierAdresse", "supplierFirma", "supplierBank");
	        Map<String, String> adresseData = extractAddress(doc, "customerAdresse", "customerFirma", "customerBank");
	        Map<String, String> lieferAdresseData = extractAddressWithFallback(doc, "customerLAdresse", "customerLFirma", "customerLBank", "customerAdresse", "customerFirma", "customerBank");
	        Map<String, String> rechnungAdresseData = extractAddressWithFallback(doc, "customerRAdresse", "customerRFirma", "customerRBank", "customerAdresse", "customerFirma", "customerBank");
	        Map<String, String> bearbeiterData = extractBearbeiter(doc, "personal", "", conversionKeys.get("PERSONALDATA"));
	        Map<String, String> zahlungsbedingungenData = extractZahlungsbedingungen(doc, rechnungElement, "zahlungsbed", "zahlungsbedlng", conversionKeys.get("ZBDETAILS"));
	        List<Map<String, Object>> rechnungspositionen = extractRechnungspositionen(doc);

	        // StringWriter to hold the XML output
	        StringWriter stringWriter = new StringWriter();
	        try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
	            writer.write("<RECHNUNG>\n");

	            // Write Verkäuferadresse
	            writer.write("  <VERKAEUFERADRESSE>\n");
	            writeAddress(writer, verkaeuferAdresseData, "V");
	            writer.write("  </VERKAEUFERADRESSE>\n");
	            
	            // Write Kundenadresse
	            writer.write("  <ADRESSE>\n");
	            writeAddress(writer, adresseData, "");
	            writer.write("  </ADRESSE>\n");

	            // Write Lieferadresse
	            writer.write("  <LIEFERADRESSE>\n");
	            writeAddress(writer, lieferAdresseData, "L");
	            writer.write("  </LIEFERADRESSE>\n");


	            // Schreibe Manuelle Lieferadresse
	            writer.write("  <MANUELLLIEFERADRESSE>\n");
	            writeAddress(writer, manuelleLieferadresseData, "L");
	            writer.write("  </MANUELLLIEFERADRESSE>\n");

	            // Write Rechnungsadresse
	            writer.write("  <RECHNUNGADRESSE>\n");
	            writeAddress(writer, rechnungAdresseData, "R");
	            writer.write("  </RECHNUNGADRESSE>\n");

	            // Write Bearbeiter
	            writer.write("  <BEARBEITER>\n");
	            writeAddress(writer, bearbeiterData, "");
	            writer.write("  </BEARBEITER>\n");

	            // Schreibe Text
	            writer.write("  <TEXT>\n");
	            writeAddress(writer, textData, "");
	            writer.write("  </TEXT>\n");

	            // Write Zahlungsbedingungen
	            writer.write("  <ZAHLUNGSBEDINGUNG>\n");
	            writeAddress(writer, zahlungsbedingungenData, "");
	            writer.write("  </ZAHLUNGSBEDINGUNG>\n");
	            
	            // Schreibe E-Rechnung
	            writer.write("  <ERECHNUNG>\n");
	            writeAddress(writer, eRechnungData, "");
	            writer.write("  </ERECHNUNG>\n");

	            // Schreibe Datum
	            writer.write("  <DATUM>\n");
	            writeAddress(writer, datumData, "");
	            writer.write("  </DATUM>\n");

	            // Schreibe Betrag
	            writer.write("  <BETRAG>\n");
	            writeAddress(writer, betragData, "");
	            writer.write("  </BETRAG>\n");

	            // Schreibe UST
	            writer.write("  <UST>\n");
	            writeAddress(writer, ustData, "");
	            writer.write("  </UST>\n");

	            // Schreibe FK
	            writer.write("  <FRACHTKOSTEN>\n");
	            writeAddress(writer, fkData, "");
	            writer.write("  </FRACHTKOSTEN>\n");

	            // Schreibe weitere Rechnungsdaten
	            for (Map.Entry<String, String> entry : weitereRechnungsdaten.entrySet()) {
	                String tagName = entry.getKey();
	                String value = entry.getValue() != null ? entry.getValue() : "";
	                writer.write(String.format("  <%s>%s</%s>\n", tagName, value, tagName));
	            }

	            writeRechnungspositionen(writer, rechnungspositionen);

	            writer.write("</RECHNUNG>");
	        }

	        // Return the XML content as a String
	        return stringWriter.toString();
	    } catch (Exception e) {
	        e.printStackTrace();
	        return null;
	    }
	}

	
	private static Map<String, String> extractManuelleLieferadresse(Element rechnungElement) {
	    Map<String, String> data = new HashMap<>();

	    data.put("LADRMANUELL", getElementValue(rechnungElement, "LADRMANUELL"));
	    data.put("LFIRMA1", getElementValue(rechnungElement, "LFIRMA"));
	    data.put("LFIRMA2", getElementValue(rechnungElement, "LFIRMA2"));
	    data.put("LFIRMA3", getElementValue(rechnungElement, "LFIRMA3"));
	    data.put("LLAND", getElementValue(rechnungElement, "LANDISO"));
	    data.put("LNAME", getElementValue(rechnungElement, "LNAME"));
	    data.put("LABTEILUNG", getElementValue(rechnungElement, "LABTEILUNG"));
	    data.put("LORT", getElementValue(rechnungElement, "LORT"));
	    data.put("LPLZ", getElementValue(rechnungElement, "LPLZ"));
	    data.put("LPLZ2", getElementValue(rechnungElement, "LPLZ2"));
	    data.put("LSTRASSE", getElementValue(rechnungElement, "LSTRASSE"));
	    data.put("LTELEFAX", getElementValue(rechnungElement, "LTELEFAX"));
	    data.put("LTELEFON", getElementValue(rechnungElement, "LTELEFON"));
	    data.put("LEMAIL", getElementValue(rechnungElement, "EMAIL"));
	    data.put("LEGSTEUERNR", getElementValue(rechnungElement, "LEGSTEUERNR"));
	    data.put("LDUNSNR", getElementValue(rechnungElement, "DUNSNR"));
	    data.put("LUSTID", getElementValue(rechnungElement, "USTID"));

	    return data;
	}
	
	private static Map<String, String> extractText(Document doc, Element rechnungElement, String stdtxtTag) {
	    Map<String, String> data = new HashMap<>();

	    Element stdtxtElement = getElement(doc, stdtxtTag);
	    
	    data.put("STDTEXT", getElementValue(stdtxtElement, "HTMLSTDTXT"));
	    data.put("FREITEXT", getElementValue(rechnungElement, "HTMLFREITEXT"));
	    data.put("IHRTEXT", getElementValue(rechnungElement, "HTMLIHRTEXT"));
	    data.put("FUSSTEXT", getElementValue(rechnungElement, "HTMLFUSSTEXT"));
	    data.put("KOPFTEXT", getElementValue(rechnungElement, "HTMLKOPFTEXT"));

	    return data;
	}
	
	private static Map<String, String> extractERechnung(Element rechnungElement) {
	    Map<String, String> data = new HashMap<>();

	    data.put("LEITWEGID", getElementValue(rechnungElement, "LEITWEGID"));
	    data.put("EINVOICE_DISPATCH", getElementValue(rechnungElement, "EINVOICE_DISPATCH"));
	    data.put("EINVOICE_INTERFACE", getElementValue(rechnungElement, "EINVOICE_INTERFACE"));

	    return data;
	}
	
	private static Map<String, String> extractDatum(Element rechnungElement) {
	    Map<String, String> data = new HashMap<>();

	    data.put("DATUM", getElementValue(rechnungElement, "DATUM"));
	    data.put("LEISTUNGSDATUM", getElementValue(rechnungElement, "LEISTUNGSDATUM"));

	    return data;
	}
	
	private static Map<String, String> extractBetrag(Element rechnungElement) {
	    Map<String, String> data = new HashMap<>();

	    data.put("RABATTPREIS", getElementValue(rechnungElement, "RABATTPREIS"));
	    data.put("NETTOERLOES", getElementValue(rechnungElement, "NETTOERLOES"));
	    data.put("USTPREIS", getElementValue(rechnungElement, "USTPREIS"));
	    data.put("BRUTTO", getElementValue(rechnungElement, "BRUTTO"));

	    return data;
	}
	
	private static Map<String, String> extractUST(Document doc, String ustTag) {
	    Map<String, String> data = new HashMap<>();

	    Element ustElement = getElement(doc, ustTag);
	    
	    data.put("USTKATEGORIE", getElementValue(ustElement, "KATEGORIE"));
	    data.put("USTBETRAG", getElementValue(ustElement, "BETRAG"));
	    data.put("USTPROZENT", getElementValue(ustElement, "UST"));
	    data.put("USTBASIS", getElementValue(ustElement, "ERLOES"));

	    return data;
	}
	
	private static Map<String, String> extractFK(Document doc, String fkTag) {
	    Map<String, String> data = new HashMap<>();

	    Element fkElement = getElement(doc, fkTag);
	    
	    data.put("FRACHTKOSTENBETRAG", getElementValue(fkElement, "FRACHTKOSTENBETRAG"));
	    data.put("FRACHTKOSTENUSTKATEGORIE", getElementValue(fkElement, "FRACHTKOSTENUSTKATEGORIE"));
	    data.put("FRACHTKOSTENUSTPROZENT", getElementValue(fkElement, "FRACHTKOSTENUSTPROZENT"));

	    return data;
	}
	
	private static Map<String, String> extractWeitereRechnungsdaten(Element rechnungElement) {
	    Map<String, String> data = new HashMap<>();

	    data.put("RECHNUNGNR", getElementValue(rechnungElement, "RECHNUNG"));
	    data.put("ART", getElementValue(rechnungElement, "ART"));
	    data.put("PARECHNUNGSART", getElementValue(rechnungElement, "PARECHNUNGSART"));
	    data.put("WAEHRUNG", getElementValue(rechnungElement, "WAEHRUNG"));
	    data.put("IHREBESTELLUNG", getElementValue(rechnungElement, "IHREBESTELLUNG"));
	    data.put("BESTELLDATUM", getElementValue(rechnungElement, "BESTELLDATUM"));
	    data.put("URRECHNUNG", getElementValue(rechnungElement, "URRECHNUNG"));

	    return data;
	}

	/**
	 * Extract address data from specified tags.
	 */
	private static Map<String, String> extractAddress(Document doc, String adresseTag, String firmaTag, String bankTag) {
	    Map<String, String> data = new HashMap<>();
	    Element adresseElement = getElement(doc, adresseTag);
	    Element firmaElement = getElement(doc, firmaTag);
	    Element bankElement = getElement(doc, bankTag);

	    data.put("FIRMA1", getElementValue(adresseElement, "FIRMA1"));
	    data.put("FIRMA2", getElementValue(adresseElement, "FIRMA2"));
	    data.put("FIRMA3", getElementValue(adresseElement, "FIRMA3"));
	    data.put("LAND", getElementValue(adresseElement, "LANDISO"));
	    data.put("NAME", getElementValue(adresseElement, "NAME"));
	    data.put("ABTEILUNG", getElementValue(adresseElement, "ABTEILUNG"));
	    data.put("ORT", getElementValue(adresseElement, "ORT"));
	    data.put("PLZ", getElementValue(adresseElement, "PLZ"));
	    data.put("PLZ2", getElementValue(adresseElement, "PLZ2"));
	    data.put("STRASSE", getElementValue(adresseElement, "STRASSE"));
	    data.put("TELEFAX", getElementValue(adresseElement, "TELEFAX"));
	    data.put("TELEFON", getElementValue(adresseElement, "TELEFON"));
	    data.put("EMAIL", getElementValue(adresseElement, "EMAIL"));
	    data.put("DUNSNR", getElementValue(adresseElement, "DUNSNR"));

	    // Correct ISNULL logic for USTID
	    data.put("USTID", getElementValue(adresseElement, "EGSTEUERNR") != null 
	    		&& !getElementValue(adresseElement, "EGSTEUERNR").isEmpty()
	        ? getElementValue(adresseElement, "EGSTEUERNR") 
	        : getElementValue(firmaElement, "EGSTEUERNR"));

	    data.put("STEUERNUMMER", getElementValue(firmaElement, "STEUERNUMMER"));
	    data.put("BIC", getElementValue(bankElement, "SWIFT"));
	    data.put("IBAN", getElementValue(firmaElement, "IBAN"));
	    data.put("ZAHLARTEN", getElementValue(firmaElement, "ZAHLARTEN"));

	    return data;
	}
	
	private static Map<String, String> extractBearbeiter(Document doc, String personalTag, String adresseTag, String personaldata) {
	    Map<String, String> data = new HashMap<>();
	    Element personalElement = getElement(doc, personalTag);

	    data.put("NAME", getElementValue(personalElement, "NAME"));
	    if (personaldata != null && personaldata.equalsIgnoreCase("PERSONAL")) {
		    data.put("EMAIL", getElementValue(personalElement, "ANP_EMAIL"));
		    data.put("TELEFON", getElementValue(personalElement, "ANP_TELDURCHWAHL"));
		    data.put("TELEFAX", getElementValue(personalElement, "ANP_FAXDURCHWAHL"));
		    data.put("ABTEILUNG", getElementValue(personalElement, "ABTEILUNG"));
	    } else {
		    data.put("EMAIL", getElementValue(personalElement, "EMAIL"));
		    data.put("TELEFON", getElementValue(personalElement, "TELEFON"));
		    data.put("TELEFAX", getElementValue(personalElement, "TELEFAX"));
		    data.put("ABTEILUNG", getElementValue(personalElement, "ABTEILUNG"));
	    }

	    return data;
	}
	
	private static Map<String, String> extractZahlungsbedingungen(Document doc, 
			Element rechnungElement, 
			String zahlungsbedTag,
			String zahlungsbedlngTag,
			String xmlzbDetails) throws ParserConfigurationException, SAXException, IOException {
	    Map<String, String> data = new HashMap<>();
	    Element zahlungsbedElement = getElement(doc, zahlungsbedTag);
	    Element zahlungsbedlngElement = getElement(doc, zahlungsbedlngTag);

	    if (zahlungsbedlngElement != null)
	    	data.put("ZAHLUNGSBEDINGUNG", getElementValue(zahlungsbedlngElement, "HTMLZBTXT"));
	    else
	    	data.put("ZAHLUNGSBEDINGUNG", getElementValue(zahlungsbedElement, "HTMLZBTXT"));
	    data.put("VALUTADATUM", getElementValue(rechnungElement, "VALUTADATUM"));
	    
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
	            data.put(fieldName, value);
	        }
	    }

	    return data;
	}

	/**
	 * Extract address data with fallback logic.
	 */
	private static Map<String, String> extractAddressWithFallback(Document doc, String adresseTag1, String firmaTag1, String bankTag1, String adresseTag2, String firmaTag2, String bankTag2) {
	    Map<String, String> primaryData = extractAddress(doc, adresseTag1, firmaTag1, bankTag1);
	    if (primaryData.values().stream().anyMatch(Objects::nonNull)) {
	        return primaryData; // Use primary if any value exists
	    }
	    return extractAddress(doc, adresseTag2, firmaTag2, bankTag2); // Fallback
	}
	
	private static List<Map<String, Object>> extractRechnungspositionen(Document doc) {
	    List<Map<String, Object>> rechnungspositionen = new ArrayList<>();

	    // Step 1: Group rechnungpospos by their db_pos attribute
	    Map<String, List<Map<String, Object>>> rechnungposposMap = new HashMap<>();
	    NodeList unterpositions = doc.getElementsByTagName("rechnungpospos");

	    for (int i = 0; i < unterpositions.getLength(); i++) {
	        Node unterposition = unterpositions.item(i);

	        if (unterposition.getNodeType() == Node.ELEMENT_NODE) {
	            Element sourceElement2 = (Element) unterposition;

	            // Extract db_pos attribute
	            String dbPos = sourceElement2.getAttribute("anp_db_pos");
	            if (dbPos == null || dbPos.isEmpty()) continue;

	            // Create a map for the rechnungpospos node
	            Map<String, Object> rechnungsunterposition = new HashMap<>();

	            // BETRAG
	            Map<String, Object> betrag2 = new HashMap<>();
	            betrag2.put("ERLOES", getElementValue(sourceElement2, "ERLOES"));
	            betrag2.put("NETTOERLOES", getElementValue(sourceElement2, "NETTOERLOES"));
	            betrag2.put("BRUTTO", getElementValue(sourceElement2, "PREIS"));
	            betrag2.put("NETTO", getElementValue(sourceElement2, "NETTO"));
	            betrag2.put("NETTOANTRAG", getElementValue(sourceElement2, "NETTOANTRAG"));
	            betrag2.put("PACKMENGE", getElementValue(sourceElement2, "PACKMENGE"));
	            betrag2.put("PREISME", getElementValue(sourceElement2, "PREISME"));
	            betrag2.put("PREIS", getElementValue(sourceElement2, "PREIS"));
	            betrag2.put("MRABATT", getElementValue(sourceElement2, "MRABATT"));
	            betrag2.put("MRABATTPREIS", getElementValue(sourceElement2, "MRABATTPREIS"));
	            betrag2.put("RABATT", getElementValue(sourceElement2, "RABATT"));
	            betrag2.put("RABATT2", getElementValue(sourceElement2, "RABATT2"));
	            betrag2.put("RABATTPREIS", getElementValue(sourceElement2, "RABATTPREIS"));
	            betrag2.put("RABATTPREIS2", getElementValue(sourceElement2, "RABATTPREIS2"));
	            betrag2.put("STKERLOES", getElementValue(sourceElement2, "STKERLOES"));

	            Map<String, String> ust2 = new HashMap<>();
	            ust2.put("USTPREIS", getElementValue(sourceElement2, "USTPREIS"));
	            ust2.put("USTKATEGORIE", getElementValue(sourceElement2, "USTKATEGORIE"));
	            ust2.put("USTSATZ", getElementValue(sourceElement2, "UST"));
	            betrag2.put("UST", ust2);

	            rechnungsunterposition.put("BETRAG", betrag2);

	            // STAMMDATEN
	            Map<String, String> stammdaten2 = new HashMap<>();
	            stammdaten2.put("CHARGE", getElementValue(sourceElement2, "CHARGE"));
	            stammdaten2.put("URSPRUNGSLAND", getElementValue(sourceElement2, "URSPRUNGSLANDISO"));
	            stammdaten2.put("ZOLLTARIFNR", getElementValue(sourceElement2, "ZOLLTARIFNR"));
	            stammdaten2.put("EANCODE", getElementValue(sourceElement2, "EANCODE"));
	            stammdaten2.put("ARTIKEL", getElementValue(sourceElement2, "ARTIKEL"));
	            stammdaten2.put("KARTIKEL", getElementValue(sourceElement2, "KARTIKEL"));
	            rechnungsunterposition.put("STAMMDATEN", stammdaten2);

	            // TEXT
	            Map<String, String> text2 = new HashMap<>();
	            text2.put("NAME", getElementValue(sourceElement2, "NAME"));
	            text2.put("TEXT", getElementValue(sourceElement2, "HTML"));
	            rechnungsunterposition.put("TEXT", text2);

	            // Weitere Felder
	            rechnungsunterposition.put("DATUM", getElementValue(sourceElement2, "DATUM"));
	            rechnungsunterposition.put("DONTCALC", getElementValue(sourceElement2, "DONTCALC"));
	            rechnungsunterposition.put("DONTPRINT", getElementValue(sourceElement2, "DONTPRINT"));
	            rechnungsunterposition.put("DONTPRINTPRICE", getElementValue(sourceElement2, "DONTPRINTPRICE"));
	            rechnungsunterposition.put("MENGE", getElementValue(sourceElement2, "MENGE"));
	            rechnungsunterposition.put("POSITION", getElementValue(sourceElement2, "RECHNUNG"));
	            rechnungsunterposition.put("POSITION", getElementValue(sourceElement2, "POSITION"));
	            rechnungsunterposition.put("SUBPOS", getElementValue(sourceElement2, "SUBPOS"));
	            rechnungsunterposition.put("VKME", getElementValue(sourceElement2, "VKME"));

	            // Add the unterposition to the map
	            rechnungposposMap.computeIfAbsent(dbPos, k -> new ArrayList<>()).add(rechnungsunterposition);
	        }
	    }
	    
	    NodeList positions = doc.getElementsByTagName("rechnungpos");

	    for (int i = 0; i < positions.getLength(); i++) {
	        Node position = positions.item(i);

	        if (position.getNodeType() == Node.ELEMENT_NODE) {
	            Element sourceElement = (Element) position;
	            
	            
	            Map<String, Object> rechnungsposition = new HashMap<>();

	            // BETRAG
	            Map<String, Object> betrag = new HashMap<>();
	            betrag.put("ERLOES", getElementValue(sourceElement, "ERLOES"));
	            betrag.put("NETTOERLOES", getElementValue(sourceElement, "NETTOERLOES"));
	            betrag.put("BRUTTO", getElementValue(sourceElement, "PREIS"));
	            betrag.put("NETTO", getElementValue(sourceElement, "NETTO"));
	            betrag.put("NETTOANTRAG", getElementValue(sourceElement, "NETTOANTRAG"));
	            betrag.put("PACKMENGE", getElementValue(sourceElement, "PACKMENGE"));
	            betrag.put("PREISME", getElementValue(sourceElement, "PREISME"));
	            betrag.put("PREIS", getElementValue(sourceElement, "PREIS"));
	            betrag.put("MRABATT", getElementValue(sourceElement, "MRABATT"));
	            betrag.put("MRABATTPREIS", getElementValue(sourceElement, "MRABATTPREIS"));
	            betrag.put("RABATT", getElementValue(sourceElement, "RABATT"));
	            betrag.put("RABATT2", getElementValue(sourceElement, "RABATT2"));
	            betrag.put("RABATTPREIS", getElementValue(sourceElement, "RABATTPREIS"));
	            betrag.put("RABATTPREIS2", getElementValue(sourceElement, "RABATTPREIS2"));
	            betrag.put("STKERLOES", getElementValue(sourceElement, "STKERLOES"));

	            Map<String, String> ust = new HashMap<>();
	            ust.put("USTPREIS", getElementValue(sourceElement, "USTPREIS"));
	            ust.put("USTKATEGORIE", getElementValue(sourceElement, "USTKATEGORIE"));
	            ust.put("USTSATZ", getElementValue(sourceElement, "UST"));
	            betrag.put("UST", ust);

	            rechnungsposition.put("BETRAG", betrag);

	            // STAMMDATEN
	            Map<String, String> stammdaten = new HashMap<>();
	            stammdaten.put("CHARGE", getElementValue(sourceElement, "CHARGE"));
	            stammdaten.put("URSPRUNGSLAND", getElementValue(sourceElement, "URSPRUNGSLANDISO"));
	            stammdaten.put("ZOLLTARIFNR", getElementValue(sourceElement, "ZOLLTARIFNR"));
	            stammdaten.put("EANCODE", getElementValue(sourceElement, "EANCODE"));
	            stammdaten.put("ARTIKEL", getElementValue(sourceElement, "ARTIKEL"));
	            stammdaten.put("KARTIKEL", getElementValue(sourceElement, "KARTIKEL"));
	            rechnungsposition.put("STAMMDATEN", stammdaten);

	            // TEXT
	            Map<String, String> text = new HashMap<>();
	            text.put("ANZTEXT", getElementValue(sourceElement, "ANZTEXT"));
	            if (getElementValue(sourceElement, "NAME") == null || getElementValue(sourceElement, "NAME").isEmpty())
	            	text.put("NAME", getElementValue(sourceElement, "NAMEINTERN"));
	            else
	            	text.put("NAME", getElementValue(sourceElement, "NAME"));
	            text.put("TEXT", getElementValue(sourceElement, "HTMLTEXT"));
	            rechnungsposition.put("TEXT", text);

	            // BELEGE
	            Map<String, String> belege = new HashMap<>();
	            belege.put("URRECHNUNG", getElementValue(sourceElement, "URRECHNUNG"));
	            belege.put("URRECHNUNGPOS", getElementValue(sourceElement, "URRECHNUNGPOS"));
	            belege.put("AUFTRAG", getElementValue(sourceElement, "AUFTRAG"));
	            belege.put("AUFTRAGPOS", getElementValue(sourceElement, "AUFTRAGPOS"));
	            belege.put("LIEFERSCHEIN", getElementValue(sourceElement, "LIEFERSCHEIN"));
	            rechnungsposition.put("BELEGE", belege);

	            // NOPOS
	            Map<String, String> nopos = new HashMap<>();
	            nopos.put("TEXTPOS", getElementValue(sourceElement, "TEXTPOS"));
	            nopos.put("KAPITELSUMME", getElementValue(sourceElement, "KAPITELSUMME"));
	            nopos.put("ZWISCHENSUMME", getElementValue(sourceElement, "ZWISCHENSUMME"));
	            nopos.put("ZSBIS", getElementValue(sourceElement, "ZSBIS"));
	            nopos.put("ZSVON", getElementValue(sourceElement, "ZSVON"));
	            nopos.put("PAKET", getElementValue(sourceElement, "PAKET"));
	            nopos.put("ISTPAKETPREIS", 
	            		getElementValue(sourceElement, "POSITION") == getElementValue(sourceElement, "SETNR") ? "true" : "false");
	            rechnungsposition.put("NOPOS", nopos);

	            // Weitere Felder
	            rechnungsposition.put("DATUM", getElementValue(sourceElement, "DATUM"));
	            rechnungsposition.put("DONTPRINT", getElementValue(sourceElement, "DONTPRINT"));
	            rechnungsposition.put("DONTPRINTPRICE", getElementValue(sourceElement, "DONTPRINTPRICE"));
	            rechnungsposition.put("POSDRUCKEN", getElementValue(sourceElement, "POSDRUCKEN"));
	            rechnungsposition.put("INVENTAR", getElementValue(sourceElement, "INVENTAR"));
	            rechnungsposition.put("ISSTUELI", getElementValue(sourceElement, "ISSTUELI"));
	            rechnungsposition.put("LEISTUNGSDATUM", getElementValue(sourceElement, "LEISTUNGSDATUM"));
	            rechnungsposition.put("MENGE", getElementValue(sourceElement, "MENGE"));
	            rechnungsposition.put("POSITION", getElementValue(sourceElement, "POSITION"));
	            rechnungsposition.put("VKME", getElementValue(sourceElement, "VKME"));
	            rechnungsposition.put("MTZART", getElementValue(sourceElement, "MTZART"));
	            rechnungsposition.put("MTZSUM", getElementValue(sourceElement, "MTZSUM"));
	            


	            // Link corresponding unterpositionen
	            String dbPos = sourceElement.getAttribute("anp_db_pos");
	            if (dbPos != null && !dbPos.isEmpty()) {
	                List<Map<String, Object>> unterpositionen = rechnungposposMap.getOrDefault(dbPos, new ArrayList<>());
	                rechnungsposition.put("RECHNUNGSUNTERPOSITIONEN", unterpositionen);
	            }

	            rechnungspositionen.add(rechnungsposition);
	        }
	    }

	    return rechnungspositionen;
	}

	
	private static void writeMap(BufferedWriter writer, Map<String, ?> data, String indent) throws IOException {
	    for (Map.Entry<String, ?> entry : data.entrySet()) {
	        String tagName = entry.getKey();
	        Object value = entry.getValue();

	        if (value instanceof Map) {
	            // Rekursiv verschachtelte Map verarbeiten
	            writer.write(String.format("%s<%s>\n", indent, tagName));
	            writeMap(writer, (Map<String, ?>) value, indent + "  ");
	            writer.write(String.format("%s</%s>\n", indent, tagName));
	        } else {
	            // Einfache Werte schreiben
	        	String textValue = value != null ? escapeXml(value.toString()) : "";
	        	writer.write(String.format("%s<%s>%s</%s>\n", indent, tagName, textValue, tagName));
	        }
	    }
	}
	
	
	private static void writeRechnungspositionen(BufferedWriter writer, List<Map<String, Object>> rechnungspositionen) throws IOException {
	    writer.write("  <RECHNUNGSPOSITIONEN>\n");

	    for (Map<String, Object> rechnungsposition : rechnungspositionen) {
	        writer.write("    <RECHNUNGSPOSITION>\n");

	        // Write all data of the main position
	        for (Map.Entry<String, Object> entry : rechnungsposition.entrySet()) {
	            String tagName = entry.getKey();
	            Object value = entry.getValue();

	            if ("RECHNUNGSUNTERPOSITIONEN".equals(tagName)) {
	                // Handle unterpositionen separately
	                @SuppressWarnings("unchecked")
	                List<Map<String, Object>> unterpositionen = (List<Map<String, Object>>) value;
	                writeUnterpositionen(writer, unterpositionen);
	            } else {
	                // Write other data
	                if (value instanceof Map) {
	                    writer.write(String.format("      <%s>\n", tagName));
	                    writeMap(writer, (Map<String, ?>) value, "        ");
	                    writer.write(String.format("      </%s>\n", tagName));
	                } else {
	                    String textValue = value != null ? escapeXml(value.toString()) : "";
	                    writer.write(String.format("      <%s>%s</%s>\n", tagName, textValue, tagName));
	                }
	            }
	        }

	        writer.write("    </RECHNUNGSPOSITION>\n");
	    }

	    writer.write("  </RECHNUNGSPOSITIONEN>\n");
	}
	
	private static void writeUnterpositionen(BufferedWriter writer, List<Map<String, Object>> unterpositionen) throws IOException {
	    writer.write("      <RECHNUNGSUNTERPOSITIONEN>\n");

	    for (Map<String, Object> unterposition : unterpositionen) {
	        writer.write("        <RECHNUNGSUNTERPOSITION>\n");

	        // Write all data of the unterposition
	        writeMap(writer, unterposition, "          ");

	        writer.write("        </RECHNUNGSUNTERPOSITION>\n");
	    }

	    writer.write("      </RECHNUNGSUNTERPOSITIONEN>\n");
	}


	private static void writeAddress(BufferedWriter writer, Map<String, String> data, String prefix) throws IOException {
	    for (Map.Entry<String, String> entry : data.entrySet()) {
	        String tagName = prefix + entry.getKey();
	        String value = entry.getValue() != null ? escapeXml(entry.getValue()) : "";
	        writer.write(String.format("    <%s>%s</%s>\n", tagName, value, tagName));
	    }
	}
	
	/**
	 * Helper function to safely retrieve the value of an XML element.
	 */
	private static String getElementValue(Element element, String tagName) {
	    if (element == null) return null;
	    NodeList nodeList = element.getElementsByTagName(tagName);
	    if (nodeList.getLength() > 0 && nodeList.item(0).getTextContent() != null) {
	        return nodeList.item(0).getTextContent().trim();
	    }
	    return null; // Return null if not found or empty
	}

	/**
	 * Helper function to retrieve an XML element by tag name.
	 */
	private static Element getElement(Document doc, String tagName) {
	    NodeList nodeList = doc.getElementsByTagName(tagName);
	    if (nodeList.getLength() > 0) {
	        return (Element) nodeList.item(0);
	    }
	    return null; // Return null if not found
	}
	
	private static String escapeXml(String input) {
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
