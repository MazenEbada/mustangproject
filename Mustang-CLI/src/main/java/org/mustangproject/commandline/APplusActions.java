package org.mustangproject.commandline;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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
	
	public static String handleGenerateFromXML(String[] args, String tempOutputFile) throws Exception {
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

	    if (inputXML == null) {
	        System.err.println("Error: --input-xml parameter is required for 'GENERATE_FROM_XML'.");
	        System.exit(1);
	    }
	    
	    StringBuilder content = new StringBuilder();
	    File inputFile = new File(inputXML);
        try (BufferedReader fileReader = new BufferedReader(new FileReader(inputFile))) {
            content = new StringBuilder();
            String line;
            while ((line = fileReader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
	    
	    result = APplusInterface.getErechnungXML(content.toString(), conversionKeys, tempOutputFile);

	    return result;
	   
	}

}
    
