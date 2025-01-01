package org.mustangproject.applus;

import org.mustangproject.Allowance;
import org.mustangproject.BankDetails;
import org.mustangproject.Contact;
import org.mustangproject.IncludedNote;
import org.mustangproject.Invoice;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentDiscountTerms;
import org.mustangproject.ZUGFeRD.IZUGFeRDPaymentTerms;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;
import org.mustangproject.ZUGFeRD.model.DocumentCodeTypeConstants;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.ReferencedDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.ObjectMapper;

/*
 * MEB: APplus Processor
 */
public class APplusIncomingXMLProcessor {
	
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
	private Invoice invoice;
	private String invoiceXML;
	private Document doc;

	private String language;
	
	public APplusIncomingXMLProcessor(String rechnungDetails, Map<String,String> conversionKeys) throws Exception {
		// Parse the XML content
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        this.doc = builder.parse(new java.io.ByteArrayInputStream(rechnungDetails.getBytes("UTF-8")));
        doc.getDocumentElement().normalize();
        
		this.conversionKeys = conversionKeys;
	}
	
	public void process() throws Exception {
		generateInvoice();
		generateInvoiceXML();
	}
	
	public Invoice getInvoice() {
		return invoice;
	}
	

	public String getInvoiceXML() {
		return invoiceXML;
	}

	public void generateInvoiceXML() throws Exception {
		String applusEinterface = getInterface();
		String einterface;
		switch (applusEinterface) {
			case "Z":
				einterface = "EN16931";
				break;
			case "X":
				einterface = "XRECHNUNG";
				break;
			default:
				throw new Exception("ERechnung-Schnittstelle nicht bekannt: " + applusEinterface + "!");
		}


        ZUGFeRD2PullProvider provider = new ZUGFeRD2PullProvider();
        provider.setProfile(Profiles.getByName(einterface)); // Set ZUGFeRD profile
        provider.generateXML(invoice);


        invoiceXML = new String(provider.getXML(), StandardCharsets.UTF_8);

	}
	
	
	private String getInterface() {
		String rechnung = conversionKeys.get("INTERFACE");
		if (rechnung == null) {
			rechnung = getTextContent(getElement(doc, "ERECHNUNG"),"EINVOICE_INTERFACE");
		}
		return rechnung;
	}
	


	public void printInvoiceDetails(Invoice invoice) {
	    try {
	        ObjectMapper mapper = new ObjectMapper();
	        String prettyInvoice = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(invoice);
	        System.out.println(prettyInvoice);
	    } catch (Exception e) {
	        System.err.println("Error while pretty printing invoice: " + e.getMessage());
	    }
	}
	
	
	public void generateInvoice() throws ParserConfigurationException, UnsupportedEncodingException, SAXException, IOException {
	    
	    invoice = new Invoice();

	    // Step 1: Extract Invoice Metadata
	    extractInvoiceMetadata(invoice);

	    // Step 2: Extract Seller Information (VERKAEUFERADRESSE)
	    Element sellerElement = getElement(doc, "VERKAEUFERADRESSE");
	    TradeParty seller = extractTradeParty(sellerElement, "V");
	    invoice.setSender(seller);
	    

	    // Step 3: Extract Bearbeiter (Processor/Handler)
	    Element bearbeiterElement = getElement(doc, "BEARBEITER");
	    Contact bearbeiterContact = extractBearbeiter(bearbeiterElement);
	    if (bearbeiterContact != null) {
	        TradeParty sender = invoice.getSender();
	        if (sender != null) {
	            sender.setContact(bearbeiterContact); // Associate Bearbeiter with the sender
	        }
	    }

	    // Step 4: Extract Customer Information (ADRESSE)
	    Element customerElement = getElement(doc, "RECHNUNGADRESSE");
	    TradeParty customer = extractTradeParty(customerElement, "R");
	    invoice.setRecipient(customer);

	    // Step 5: Extract Delivery Address (LIEFERADRESSE)
	    Element deliveryElement = getElement(doc, "LIEFERADRESSE");
	    Element deliveryManualElement = getElement(doc, "MANUELLLIEFERADRESSE");
	    TradeParty deliveryAddress = extractTradeParty(deliveryManualElement, "L", deliveryElement);
	    invoice.setDeliveryAddress(deliveryAddress);

	    // Step 6: Extract Payment Terms (ZAHLUNGSBEDINGUNG)
	    extractInvoicePaymentTerms(invoice);

	    // Step 7: Extract Dates (DATUM)
	    extractDates(invoice);


	    // Step 8: Extract Invoice Line Items (RECHNUNGSPOSITIONEN)
	    NodeList lineItemNodes = doc.getElementsByTagName("RECHNUNGSPOSITION");
	    List<Item> items = extractInvoiceItems(lineItemNodes);
	    for (Item item : items) {
	        invoice.addItem(item);
	    }

	    // Step 9: Extract Additional Notes (TEXT)
	    Element textElement = getElement(doc, "TEXT");
	    Map<String, String> textFields = extractTextFields(textElement);
	    integrateTexts(invoice, textFields);
	    

	    // Optionally log the extracted invoice for debugging
	    //printInvoiceDetails(invoice);
	    
	}

	private void extractInvoiceMetadata(Invoice invoice) {
		
	    String invoiceNumber = getTextContent(doc.getDocumentElement(), "RECHNUNGNR");
	    String currency = getTextContent(doc.getDocumentElement(), "WAEHRUNG");
	    String orderNo = getTextContent(doc.getDocumentElement(), "IHREBESTELLUNG");
	    String orderDate = getTextContent(doc.getDocumentElement(), "BESTELLDATUM");
	    String art = getTextContent(doc.getDocumentElement(), "PARECHNUNGSART");
	    String parechnungsart = getTextContent(doc.getDocumentElement(), "PARECHNUNGSART");
	    String urrechnung = getTextContent(doc.getDocumentElement(), "URRECHNUNG");
	    String auftrag = getTextContent(doc.getDocumentElement(), "AUFTRAG");
	    language = getTextContent(doc.getDocumentElement(), "SPRACHE");
	    
	    invoice.setNumber(invoiceNumber);
	    invoice.setCurrency(currency);
	    invoice.setBuyerOrderReferencedDocumentID(orderNo);
	    invoice.setBuyerOrderReferencedDocumentIssueDateTime(orderDate);
	    invoice.setDocumentCode(mapArtToDocumentCode(art));
	    invoice.setDocumentName(parechnungsart);
	    invoice.setInvoiceReferencedDocumentID(urrechnung);
	    invoice.setSellerOrderReferencedDocumentID(auftrag);
	    
	    
	    Element eInvoiceElement = getElement(doc, "ERECHNUNG");
	    String einvoiceID = getTextContent(eInvoiceElement, "LEITWEGID");
	    invoice.setReferenceNumber(einvoiceID);
	}
	

	
	private String mapArtToDocumentCode(String art) {
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
	

	private TradeParty extractTradeParty(Element element, String type) {
		return extractTradeParty(element, type, null);
	}
	
	private TradeParty extractTradeParty(Element element, String type, Element fallbackElement) {
	    if (element == null && fallbackElement == null) {
	        return null; 
	    }

	    TradeParty tradeParty = new TradeParty();

	    // Setze grundlegende Adressinformationen
	    tradeParty.setName(getTextContent(element, type + "FIRMA1", fallbackElement)); // Hauptfirma
	    tradeParty.setAdditionalAddress(getTextContent(element, type + "FIRMA2", fallbackElement)); // Zusatzfirma
	    tradeParty.setAdditionalAddressExtension(getTextContent(element, type + "FIRMA3", fallbackElement)); // Dritte Zeile
	    tradeParty.setStreet(getTextContent(element, type + "STRASSE", fallbackElement));
	    tradeParty.setZIP(getTextContent(element, type + "PLZ", fallbackElement));
	    tradeParty.setLocation(getTextContent(element, type + "ORT", fallbackElement));
	    tradeParty.setCountry(getTextContent(element, type + "LAND", fallbackElement));

	    // Setze Steuerdaten
	    tradeParty.setTaxID(getTextContent(element, type + "STEUERNUMMER", fallbackElement));
	    tradeParty.setVATID(getTextContent(element, type + "USTID", fallbackElement));

	    // Setze Kommunikationsdetails
	    Contact contact = new Contact();
	    contact.setPhone(getTextContent(element, type + "TELEFON", fallbackElement));
	    contact.setFax(getTextContent(element, type + "TELEFAX", fallbackElement));
	    contact.setEMail(getTextContent(element, type + "EMAIL", fallbackElement));
	    contact.setName(getTextContent(element, type + "NAME", fallbackElement));
	    tradeParty.setContact(contact);

	    // Setze Bankdaten
	    BankDetails bankDetails = new BankDetails();
	    bankDetails.setIBAN(getTextContent(element, type + "IBAN", fallbackElement));
	    bankDetails.setBIC(getTextContent(element, type + "BIC", fallbackElement));
	    bankDetails.setAccountName(getTextContent(element, type + "FIRMA1", fallbackElement));
	    tradeParty.getBankDetails().add(bankDetails);

	    return tradeParty;
	}

	
	private Contact extractBearbeiter(Element bearbeiterElement) {
	    if (bearbeiterElement == null) {
	        return null; // No data to process
	    }

	    Contact contact = new Contact();
	    contact.setName(getTextContent(bearbeiterElement, "NAME"));
	    contact.setPhone(getTextContent(bearbeiterElement, "TELEFON"));
	    contact.setFax(getTextContent(bearbeiterElement, "TELEFAX"));
	    contact.setEMail(getTextContent(bearbeiterElement, "EMAIL"));

	    return contact;
	}



	private void extractInvoicePaymentTerms(Invoice invoice) {
	    Element paymentTermsElement = getElement(doc, "ZAHLUNGSBEDINGUNG");

	    if (paymentTermsElement != null) {
	        String discountPercent1 = getTextContent(paymentTermsElement, "PROZENTSKONTO1");
	        String discountDays1 = getTextContent(paymentTermsElement, "SKONTOTAGE1");
	        String nettoDate = getTextContent(paymentTermsElement, "NETTODATUM");
	        String skontoDate1 = getTextContent(paymentTermsElement, "SKONTODATUM1");

	        invoice.setPaymentTerms(new IZUGFeRDPaymentTerms() {
	            @Override
	            public String getDescription() {
	                return getTextContent(paymentTermsElement, "ZAHLUNGSBEDINGUNG");
	            }

	            @Override
	            public Date getDueDate() {
	                return nettoDate != null ? parseDate(nettoDate) : null;
	            }

	            @Override
	            public IZUGFeRDPaymentDiscountTerms getDiscountTerms() {
	                if (false && discountPercent1 != null && discountDays1 != null) {
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
	                return null;
	            }
	        });
	    }
	}


	private void integrateTexts(Invoice invoice, Map<String, String> textFields) {
	    // Add notes as IncludedNotes with appropriate subject codes
	    if (textFields.get("STDTEXT") != null) {
	        invoice.addNotes(Collections.singletonList(IncludedNote.generalNote(textFields.get("STDTEXT"))));
	    }
	    if (textFields.get("FUSSTEXT") != null) {
	        invoice.addNotes(Collections.singletonList(IncludedNote.regulatoryNote(textFields.get("FUSSTEXT"))));
	    }
	    if (textFields.get("FREITEXT") != null) {
	        invoice.addNotes(Collections.singletonList(IncludedNote.unspecifiedNote(textFields.get("FREITEXT"))));
	    }
	    if (textFields.get("IHRTEXT") != null) {
	        invoice.addNotes(Collections.singletonList(IncludedNote.sellerNote(textFields.get("IHRTEXT"))));
	    }
	    if (textFields.get("KOPFTEXT") != null) {
	        invoice.addNotes(Collections.singletonList(IncludedNote.introductionNote(textFields.get("KOPFTEXT"))));
	    }
	}
	

	private Map<String, String> extractTextFields(Element element) {
	    Map<String, String> textFields = new HashMap<>();
	    if (element != null) {
	        if (getTextContent(element, "STDTEXT") != null && !getTextContent(element, "STDTEXT").isEmpty())
	        	textFields.put("STDTEXT", getTextContent(element, "STDTEXT"));
	        if (getTextContent(element, "FUSSTEXT") != null && !getTextContent(element, "FUSSTEXT").isEmpty())
	        	textFields.put("FUSSTEXT", getTextContent(element, "FUSSTEXT"));
	        if (getTextContent(element, "FREITEXT") != null && !getTextContent(element, "FREITEXT").isEmpty())
	        	textFields.put("FREITEXT", getTextContent(element, "FREITEXT"));
	        if (getTextContent(element, "IHRTEXT") != null && !getTextContent(element, "IHRTEXT").isEmpty())
	        	textFields.put("IHRTEXT", getTextContent(element, "IHRTEXT"));
	        if (getTextContent(element, "KOPFTEXT") != null && !getTextContent(element, "KOPFTEXT").isEmpty())
	        	textFields.put("KOPFTEXT", getTextContent(element, "KOPFTEXT"));
	    }
	    return textFields;
	}
	
	private void extractDates(Invoice invoice) {
		// Step 7: Extract Dates (DATUM)
	    Element dateElement = getElement(doc, "DATUM");
	    Date issueDate = parseDate(getTextContent(dateElement, "DATUM"));
	    Date deliveryDate = parseDate(getTextContent(dateElement, "LEISTUNGSDATUM"));
	    invoice.setIssueDate(issueDate);
	    invoice.setDeliveryDate(deliveryDate);
	}
	
	
	private List<Item> extractInvoiceItems(NodeList itemNodes) {
	    List<Item> items = new ArrayList<>();

	    for (int i = 0; i < itemNodes.getLength(); i++) {
	        Node positionNode = itemNodes.item(i);

	        if (positionNode.getNodeType() == Node.ELEMENT_NODE) {
	            Element positionElement = (Element) positionNode;


	            // Menge und Preis
	            BigDecimal quantity = parseBigDecimal(getTextContent(positionElement, "MENGE"));
	            BigDecimal unitPrice = parseBigDecimal(getTextContent(positionElement, "NETTO"));
	            BigDecimal packmenge = parseBigDecimal(getTextContent(positionElement, "PACKMENGE"));

	            if (packmenge == BigDecimal.ZERO)
	            	packmenge = BigDecimal.ONE;
	            
	            // Umsatzsteuer (optional)
	            Element betragElement = getElement(positionElement, "BETRAG");
	            BigDecimal discount = BigDecimal.ZERO;
	            BigDecimal vat = BigDecimal.ZERO;
	            BigDecimal percent = BigDecimal.ZERO;
	            BigDecimal rabatt1 = BigDecimal.ZERO;
	            BigDecimal rabatt2 = BigDecimal.ZERO;
	            BigDecimal mrabatt = BigDecimal.ZERO;
	            if (betragElement != null) {
	                Element ustElement = getElement(betragElement, "UST");
	                if (ustElement != null) {
	                    vat = parseBigDecimal(getTextContent(ustElement, "USTPREIS"));
	                    percent = parseBigDecimal(getTextContent(ustElement, "USTSATZ"));
	                }
	                // Extract Rabatt fields
	             // Extract Rabatt fields
	                rabatt1 = parseBigDecimal(getTextContent(betragElement, "RABATTPREIS"));
	                rabatt2 = parseBigDecimal(getTextContent(betragElement, "RABATTPREIS2"));
	                mrabatt = parseBigDecimal(getTextContent(betragElement, "MRABATTPREIS"));
	            }
	            
	            // Produktinformationen
	            Product product = new Product();
	            product.setName(getTextContent(positionElement, "NAME"));
	            product.setDescription(getTextContent(positionElement, "TEXT"));
	            
	            // Product classifications
	            Element stammdatenElement = getElement(positionElement, "STAMMDATEN");
	            if (stammdatenElement != null) {
	                product.setSellerAssignedID(getTextContent(stammdatenElement, "ARTIKEL"));
	                product.setBuyerAssignedID(getTextContent(stammdatenElement, "KARTIKEL"));
	                product.setCountryOfOrigin(getTextContent(stammdatenElement, "URSPRUNGSLAND"));
	                
	                String customsTariffNumber = getTextContent(stammdatenElement, "ZOLLTARIFNR");
	                if (customsTariffNumber != null) {
	                	String zolltarifnrAtt = language == null || language.isEmpty() || language.equalsIgnoreCase("de") ? "Zolltarifnr." : "Customs tariff number";
	                	product.addAttribute(zolltarifnrAtt, customsTariffNumber);
	                }
	            }
	            product.setVATPercent(percent);
	            
	            // Use mapping
	            String unitType = getTextContent(positionElement, "VKME");
	            String unitCode = UNIT_CODE_MAPPING.getOrDefault(unitType, "C62"); // Default to "Piece"
	            product.setUnit(unitCode);
	            
	            // Add discount as allowance if applicable
	            /*if (rabatt1.compareTo(BigDecimal.ZERO) > 0) {
	                Allowance discountAllowance = new Allowance(rabatt1);
	                discountAllowance.setReason("Rabatt 1"); // Reason for the discount
	                discountAllowance.setReasonCode("60");
	                discountAllowance.setTaxPercent(product.getVATPercent());
	                item.addAllowance(discountAllowance); // Add allowance to the item
	            }
	            
	            if (rabatt2.compareTo(BigDecimal.ZERO) > 0) {
	                Allowance discountAllowance = new Allowance(rabatt2);
	                discountAllowance.setReason("Rabatt 2"); // Reason for the discount
	                discountAllowance.setReasonCode("60");
	                discountAllowance.setTaxPercent(product.getVATPercent());
	                item.addAllowance(discountAllowance); // Add allowance to the item
	            }
	            
	            if (mrabatt.compareTo(BigDecimal.ZERO) > 0) {
	                Allowance discountAllowance = new Allowance(mrabatt);
	                discountAllowance.setReason("Mengenrabatt"); // Reason for the discount
	                discountAllowance.setReasonCode("62");
	                discountAllowance.setTaxPercent(product.getVATPercent());
	                item.addAllowance(discountAllowance); // Add allowance to the item
	            }*/
	            

	            // Erstelle die Position
	            Item item = new Item();
	            
	            BigDecimal summRabatt = rabatt1.add(rabatt2).add(mrabatt);
	            if (summRabatt.compareTo(BigDecimal.ZERO) > 0) {
	            	summRabatt = summRabatt.multiply(packmenge).divide(quantity);
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
	            
	            item.setTax(vat);

	            

	            // Weitere Attribute
	            //item.setLineTotalAmount(quantity.multiply(unitPrice));
	            
	            // Belege (References)
	            Element belegeElement = getElement(positionElement, "BELEGE");
	            if (belegeElement != null) {
	                String auftragPos = getTextContent(belegeElement, "AUFTRAGPOS");
	                String auftrag = getTextContent(belegeElement, "AUFTRAG");
	                String lieferschein = getTextContent(belegeElement, "LIEFERSCHEIN");
	                boolean germanAttributes = language != null && language.equalsIgnoreCase("de");
	                String docattr;
	                
	                if (auftragPos != null) {
	                	docattr = germanAttributes ? "Auftragspos." : "Order Position";
	                	item.addReferencedDocument(new ReferencedDocument(docattr, auftragPos));
	                }
	                if (auftrag != null) {
	                	docattr = germanAttributes ? "Auftrag" : "Order Number";
	                	item.addReferencedDocument(new ReferencedDocument(docattr, auftrag));
	                }
	                if (lieferschein != null) {
	                	docattr = germanAttributes ? "Lieferschein" : "Delivery Note";
	                	item.addReferencedDocument(new ReferencedDocument(docattr, lieferschein));
	                }

	            }

	            // Position zur Liste hinzufügen
	            items.add(item);
	        }
	    }

	    return items;
	}


	//////////////////////////////////////////////////// PARSING METHODS ////////////////////////////////////////////////////////
	private BigDecimal parseBigDecimal(String value) {
	    if (value == null || value.isEmpty()) {
	        return BigDecimal.ZERO;
	    }
	    try {
	        return new BigDecimal(value);
	    } catch (NumberFormatException e) {
	        //e.printStackTrace();
	        return BigDecimal.ZERO;
	    }
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
	
	
	////////////////////////////////////////////////// XML METHODS ////////////////////////////////////////////////////////
	private Element getElement(Element parent, String tagName) {
	    NodeList nodeList = parent.getElementsByTagName(tagName);
	    if (nodeList.getLength() > 0 && nodeList.item(0).getNodeType() == Node.ELEMENT_NODE) {
	        return (Element) nodeList.item(0);
	    }
	    return null;
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
	

	private String getTextContent(Element element, String tagName) {
	    if (element == null) return null;
	    NodeList nodeList = element.getElementsByTagName(tagName);
	    if (nodeList.getLength() > 0 && nodeList.item(0) != null) {
	        return nodeList.item(0).getTextContent().trim();
	    }
	    return null;
	}
	


	private String getTextContent(Element element, String tagName, Element fallbackElement) {
	    if (element != null) {
	        String content = getTextContent(element, tagName);
	        if (content != null && !content.isEmpty()) {
	            return content;
	        }
	    }
	    return fallbackElement != null ? getTextContent(fallbackElement, tagName) : null;
	}

}
