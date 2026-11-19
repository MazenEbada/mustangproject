/*
 * This file is part of Featurepack E-Rechnung VT
 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.intern.conversion;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.mustangproject.CII.CIIToUBL;

/**
 * CUSTOM:TEK/MEB:19.11.2026: EN16931-CII -> UBL 2.1 nach PEPPOL BIS Billing 3.0
 * (Schnittstelle "P"). Dieselbe Umwandlung wie "--action ubl" mit den PEPPOL-Kennungen.
 */
public final class PeppolUBL {

    public static final String PROFILE_ID = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
    public static final String CUSTOMIZATION_ID =
            "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0";

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
            return new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        } finally {
            in.delete();
            out.delete();
        }
    }
}
