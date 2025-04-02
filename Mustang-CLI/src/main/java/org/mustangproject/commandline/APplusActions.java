/*
 * This file is part of Featurepack E-Rechnung VT

 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.commandline;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.mustangproject.applus.APplusInterface;
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
	    
	    Map<String,String> conversionKeys = new HashMap<String, String>();
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

}
    
