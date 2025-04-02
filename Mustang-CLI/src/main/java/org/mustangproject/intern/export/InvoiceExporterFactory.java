/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.export;

/**
 * Factory für die Erstellung von Exportern
 */
public class InvoiceExporterFactory {
    
    /**
     * Erstellt einen Exporter für das angegebene Format
     * 
     * @param format Das gewünschte Exportformat
     * @return Ein passender Exporter
     * @throws UnsupportedOperationException Wenn das Format nicht unterstützt wird
     */
    public static InvoiceExporter createExporter(ExportFormat format) {
        switch (format) {
            case XML:
                return new XMLInvoiceExporter();
            case JSON:
                throw new UnsupportedOperationException("JSON-Export ist noch nicht implementiert");
            default:
                throw new IllegalArgumentException("Unbekanntes Exportformat: " + format);
        }
    }
}