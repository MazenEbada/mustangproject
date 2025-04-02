/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.export;

import org.mustangproject.intern.model.InternInvoice;

/**
 * Interface für alle Exporter
 */
public interface InvoiceExporter {
    
    /**
     * Exportiert eine InternInvoice in ein bestimmtes Format
     * 
     * @param invoice Die zu exportierende InternInvoice
     * @return Eine String-Repräsentation des exportierten Inhalts
     * @throws Exception Bei Exportfehlern
     */
    String export(InternInvoice invoice) throws Exception;
}