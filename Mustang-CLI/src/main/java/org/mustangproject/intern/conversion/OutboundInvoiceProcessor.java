/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.conversion;

import java.util.Map;

import org.mustangproject.Invoice;
import org.mustangproject.intern.model.InternInvoice;

/**
 * Interface für Prozessoren von E-Rechnungen
 */
public interface OutboundInvoiceProcessor {
    
    /**
     * Verarbeitet eine Rechnung
     * 
     * @throws Exception Bei Verarbeitungsfehlern
     */
    void process() throws Exception;
    
    /**
     * Liefert das Mustang-Invoice-Objekt
     * 
     * @return Das Mustang-Invoice-Objekt
     */
    Invoice getMustangInvoice();
    
    /**
     * Liefert das generierte XML zurück
     * 
     * @return Das generierte XML als String
     */
    String getInvoiceXML();
    
    /**
     * Factory-Methode zur Erstellung eines Processors
     * 
     * @param internInvoice Die interne Rechnung
     * @param conversionKeys Konvertierungsschlüssel
     * @return Ein ERechnungProcessor
     */
    static OutboundInvoiceProcessor createProcessor(InternInvoice internInvoice, Map<String, String> conversionKeys) {
        return new InternToMustangProcessor(internInvoice, conversionKeys);
    }
}