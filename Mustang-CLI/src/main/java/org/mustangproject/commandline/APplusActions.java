/*
 * This file is part of Featurepack E-Rechnung VT

 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.commandline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.mustangproject.applus.APplusInterface;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA1;
import org.mustangproject.validator.ZUGFeRDValidator;
public class APplusActions {


    /**
     * Parses an argument value from the command line.
     */
    private static String getArgValue(String[] args, String key) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase(key) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }

	public static String handleGenerateFromXML(String[] args) throws Exception {
	    String result = null;

		// Parse required arguments for XML generation
	    String inputXML = getArgValue(args, "--input-xml");

	    Map<String, String> conversionKeys = new HashMap<String, String>();
	    for (int i = 0; i < args.length; i++) {
	        if (args[i].startsWith("---")) { // Prüfen, ob es ein Argument-Schlüssel ist
	            String key = args[i].substring(3).toUpperCase(); // Entfernt "---" und konvertiert in Großbuchstaben
	            if (i + 1 < args.length && !args[i + 1].startsWith("---")) {
	                String value = args[i + 1]; // Nimmt den nächsten Wert
	                conversionKeys.put(key, value); // Fügt Schlüssel-Wert in die Map ein
	                i++; // Überspringt den Wert
	            } else {
	                conversionKeys.put(key, ""); // Leeren Wert hinzufügen, falls kein Wert angegeben
	            }
	        }
	    }

	    String tempFilePath = null;
	    for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase("--temp-output-file")) {
            	tempFilePath = args[i + 1];
                break;
            }
        }

	    if (inputXML == null) {
	        System.err.println("Error: --input-xml parameter is required for 'GENERATE_FROM_XML'.");
	        System.exit(1);
	    }

	    StringBuilder content = new StringBuilder();
	    File inputFile = new File(inputXML);
	    try (BufferedReader fileReader = new BufferedReader(
	            new InputStreamReader(new FileInputStream(inputFile), "UTF-8"))) {
	       content = new StringBuilder();
	       String line;
	       while ((line = fileReader.readLine()) != null) {
	           content.append(line).append("\n");
	       }
	   }


	    result = APplusInterface.getErechnungXML(content.toString(), conversionKeys, tempFilePath);

	    return result;

	}

	/**
	 * Validiert eine bereits erzeugte E-Rechnung (XRechnung / ZUGFeRD) und gibt den
	 * Validierungsbericht als XML-String zurueck.
	 *
	 * Bewusst NICHT ueber "--action validate" geloest: die Standard-Action schreibt den
	 * Bericht nach System.out (vom Wrapper per inheritIO verschluckt) und ruft bei einer
	 * ungueltigen Rechnung System.exit(-1) auf, was im Wrapper zu einer RuntimeException
	 * fuehrt. Als Custom-Action liefern wir den Bericht regulaer zurueck; "ungueltig" ist
	 * ein Ergebnis, kein Fehler.
	 *
	 * @param args CLI-Argumente, erwartet --input-xml, optional --no-notices
	 * @return Validierungsbericht als XML (enthaelt &lt;summary status="valid|invalid"/&gt;)
	 */
	public static String handleValidate(String[] args) throws Exception {
	    String inputXML = getArgValue(args, "--input-xml");
	    if (inputXML == null) {
	        System.err.println("Error: --input-xml parameter is required for 'VALIDATE'.");
	        System.exit(1);
	    }

	    File inputFile = new File(inputXML);
	    if (!inputFile.exists()) {
	        System.err.println("Error: input file not found: " + inputXML);
	        System.exit(1);
	    }

	    ZUGFeRDValidator validator = new ZUGFeRDValidator();
	    for (String arg : args) {
	        if (arg.equalsIgnoreCase("--no-notices")) {
	            validator.disableNotices();
	            break;
	        }
	    }
	    // CUSTOM:TEK/MEB:19.11.2026: --peppol prueft PEPPOL-UBL zusaetzlich gegen die OpenPEPPOL-Regeln
	    for (String arg : args) {
	        if (arg.equalsIgnoreCase("--peppol")) {
	            validator.enablePeppolRules();
	            break;
	        }
	    }

	    // Kein System.exit bei ungueltiger Rechnung - der Bericht IST das Ergebnis.
	    return validator.validate(inputFile.getAbsolutePath());
	}

	public static String handleGenerateSimple(String[] args) throws Exception {
		String result = null;

		// Parse required arguments for XML generation
	    String inputFileContent = getArgValue(args, "--input-file");
	    if (inputFileContent == null) {
	        System.err.println("Error: --input-file parameter is required for 'GENERATE_SIMPLE'.");
	        System.exit(1);
	    }

		// Parse required arguments for XML generation
	    String outputFormat = getArgValue(args, "--output-format");
	    if (outputFormat == null) {
	        System.err.println("Error: --output-format parameter is required for 'GENERATE_SIMPLE'.");
	        System.exit(1);
	    }

	    String tempFilePath = null;
	    for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase("--temp-output-file")) {
            	tempFilePath = args[i + 1];
                break;
            }
        }

	    File inputFile = new File(inputFileContent);

	    result = APplusInterface.generateSimpleInvoice(inputFile, outputFormat, tempFilePath);

	    return result;
	}

	/**
	 * CUSTOM:TEK/MEB:19.11.2026: Haengt eine ZUGFeRD-XML an eine bestehende PDF und hebt sie auf
	 * PDF/A-3 an - wie Mogler/src/AmcZugferdCombine.java, aber mit einstellbarem Producer/Creator
	 * (fuer die Cloud-Variante). Der Seiteninhalt wird nicht angefasst; die anschliessende
	 * Validierung gehoert dazu. Nicht eingebettete Standardschriften werden vorher eingebettet (PdfFontEmbedder).
	 *
	 * --input-pdf, --input-xml, --target-pdf, optional --producer, --creator.
	 * @return Profilname, das in die PDF geschrieben wurde
	 */
	public static String handleCombine(String[] args) throws Exception {
	    String pdf = getArgValue(args, "--input-pdf");
	    String xml = getArgValue(args, "--input-xml");
	    String target = getArgValue(args, "--target-pdf");
	    if (pdf == null || xml == null || target == null) {
	        System.err.println("Error: --input-pdf, --input-xml and --target-pdf are required for 'COMBINE'.");
	        System.exit(1);
	    }
	    String producer = getArgValue(args, "--producer");
	    String creator = getArgValue(args, "--creator");
	    byte[] xmlBytes = java.nio.file.Files.readAllBytes(new File(xml).toPath());
	    String profileName = combineProfile(new String(xmlBytes, java.nio.charset.StandardCharsets.UTF_8));
	    File out = new File(target);
	    if (out.exists() && !out.delete()) {
	        throw new IllegalStateException("Zieldatei laesst sich nicht ueberschreiben: " + target);
	    }
	    ZUGFeRDExporterFromA1 exporter = new ZUGFeRDExporterFromA1();
	    exporter.ignorePDFAErrors();
	    // CUSTOM:TEK/MEB:20.11.2026 (12a, A-12): nicht eingebettete Standardschriften vorher einbetten (PDF/A-3)
	    byte[] embedded = PdfFontEmbedder.embedStandardFonts(new File(pdf));
	    if (embedded != null) {
	        exporter.load(embedded);
	    } else {
	        exporter.load(new File(pdf).getAbsolutePath());
	    }
	    exporter.setProducer(producer == null ? "E-Rechnung" : producer)
	            .setCreator(creator == null ? "E-Rechnung" : creator)
	            .setZUGFeRDVersion(2)
	            .setProfile(Profiles.getByName(profileName, 2));
	    exporter.setXML(xmlBytes);
	    exporter.export(out.getAbsolutePath());
	    exporter.close();
	    return profileName;
	}

	/** Profil aus der Guideline-ID (ram:GuidelineSpecifiedDocumentContextParameter/ram:ID). */
	static String combineProfile(String xml) {
	    java.util.regex.Matcher m = java.util.regex.Pattern
	            .compile("GuidelineSpecifiedDocumentContextParameter>\\s*<[^>]*ID>([^<]+)<").matcher(xml);
	    if (!m.find()) {
	        return "EN16931";
	    }
	    String id = m.group(1).trim().toLowerCase();
	    if (id.contains("xrechnung")) {
	        return "XRECHNUNG";
	    }
	    if (id.contains("extended")) {
	        return "EXTENDED";
	    }
	    if (id.contains("basicwl")) {
	        return "BASICWL";
	    }
	    if (id.contains("minimum")) {
	        return "MINIMUM";
	    }
	    if (id.contains("basic")) {
	        return "BASIC";
	    }
	    return "EN16931";
	}

}
