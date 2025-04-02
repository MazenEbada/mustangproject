/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.applus;

import java.io.File;
import java.io.FileWriter;
import java.text.ParseException;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.mustangproject.Invoice;
import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;
import org.mustangproject.intern.conversion.InboundInvoiceProcessor;
import org.mustangproject.intern.conversion.InternToMustangProcessor;
import org.mustangproject.intern.conversion.MustangToInternProcessor;
import org.mustangproject.intern.conversion.OutboundInvoiceProcessor;
import org.mustangproject.intern.export.ExportFormat;
import org.mustangproject.intern.model.InternInvoice;
import org.mustangproject.intern.worker.InvoiceInputWorker;
import org.mustangproject.intern.worker.XMLInvoiceWorkerImpl;

/**
 * Hauptschnittstelle für die Generierung von E-Rechnungen
 */
public class APplusInterface {
    
    /**
     * Generiert eine E-Rechnung im ZUGFeRD- oder XRechnung-Format
     * 
     * @param rechnungDetails XML-Daten der Eingangsrechnung
     * @param conversionKeys Konvertierungsschlüssel
     * @param tempOutputFile Temporäre Ausgabedatei für Debugging (optional)
     * @return Das generierte E-Rechnungs-XML
     * @throws Exception Bei Verarbeitungsfehlern
     */
    public static String getErechnungXML(String rechnungDetails, Map<String,String> conversionKeys, 
            String tempOutputFile) throws Exception {
        
        // 1. XML-Daten in InternInvoice umwandeln
        InvoiceInputWorker xmlWorker = new XMLInvoiceWorkerImpl();
        InternInvoice internInvoice = xmlWorker.processInput(rechnungDetails, conversionKeys);
        
        // Optional: Für Debugging-Zwecke XML ausgeben
        if (tempOutputFile != null) {
            String xmlContent = internInvoice.export(ExportFormat.XML);
            
            // Schreibe XML in temporäre Datei
            File outputFile = new File(tempOutputFile);
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(xmlContent);
            }
        }
        
        // 2. E-Rechnung generieren
        OutboundInvoiceProcessor processor = new InternToMustangProcessor(internInvoice, conversionKeys);
        processor.process();
        
        // 3. XML zurückgeben
        return processor.getInvoiceXML();
    }

    public static String generateSimpleInvoice(File file, String outputFormat, String tempOutputFile) throws Exception {
    	ZUGFeRDInvoiceImporter zii = new ZUGFeRDInvoiceImporter(file.getAbsolutePath());

		Invoice invoice = null;
		invoice = zii.extractInvoice();
		return generateSimpleInvoice(invoice, outputFormat, tempOutputFile);
		
	}
    
	public static String generateSimpleInvoice(Invoice invoice, String outputFormat, String tempOutputFile) throws Exception {
		String simpleInvoice = null;
		
		InboundInvoiceProcessor inboundInvoiceProcessor = InboundInvoiceProcessor.createProcessor(invoice, null);
		inboundInvoiceProcessor.process();
		
		
		InternInvoice internInvoice = inboundInvoiceProcessor.getInternInvoice();
		ExportFormat format = null;
		switch (outputFormat) {
		case "XML":
			format = ExportFormat.XML;
			break;
		case "JSON":
			format = ExportFormat.JSON;
			break;
		default:
			throw new Exception("Export format " + outputFormat + " is not supported!");
		}
		
		simpleInvoice = internInvoice.export(format);
		
		return simpleInvoice;
	}
}