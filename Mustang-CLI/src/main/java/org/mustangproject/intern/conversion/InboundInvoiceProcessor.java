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
 * Interface für Prozessoren zum Konvertieren von Mustang-Invoice in InternInvoice
 */
public interface InboundInvoiceProcessor {
    
    /**
     * Verarbeitet eine Mustang-Invoice und konvertiert sie in eine InternInvoice
     * 
     * @throws Exception Bei Verarbeitungsfehlern
     */
    void process() throws Exception;
    
    /**
     * Liefert das interne Invoice-Objekt
     * 
     * @return Das InternInvoice-Objekt
     */
    InternInvoice getInternInvoice();
    
    /**
     * Factory-Methode zur Erstellung eines Processors
     * 
     * @param mustangInvoice Die Mustang-Invoice
     * @param conversionKeys Konvertierungsschlüssel
     * @return Ein InboundInvoiceProcessor
     */
    static InboundInvoiceProcessor createProcessor(Invoice mustangInvoice, Map<String, String> conversionKeys) {
        return new MustangToInternProcessor(mustangInvoice, conversionKeys);
    }
}