/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.worker;

import java.util.Map;
import org.mustangproject.intern.model.InternInvoice;

/**
 * Basis-Interface für alle Input-Worker
 */
public interface InvoiceInputWorker {
    
    /**
     * Verarbeitet die Eingabedaten und erzeugt ein InternInvoice-Objekt
     * 
     * @param inputData Die zu verarbeitenden Eingabedaten
     * @param conversionKeys Zusätzliche Konvertierungsschlüssel
     * @return Ein gefülltes InternInvoice-Objekt
     * @throws Exception Bei Verarbeitungsfehlern
     */
    InternInvoice processInput(String inputData, Map<String, String> conversionKeys) throws Exception;
}
