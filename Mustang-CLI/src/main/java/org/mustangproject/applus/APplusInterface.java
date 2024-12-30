package org.mustangproject.applus;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;

import org.mustangproject.Invoice;

public class APplusInterface {

	/*
	 *  APplus Interface
	 */
	public static String getErechnungXML(String rechnungDetails, Map<String,String> conversionKeys, String tempOutputFile) throws Exception {
		String erechnungXML = null;
		String xmlContent = APplusIncomingXMLParser.getFriendlyXML(rechnungDetails,conversionKeys);

		if (tempOutputFile != null) {
			File outputFile = new File(tempOutputFile);
	
	        // Write the result to the provided file
	        try (FileWriter writer = new FileWriter(outputFile)) {
	            writer.write(xmlContent);
	        }

	        System.out.println("Successfully wrote output to: " + tempOutputFile);
		}
        APplusIncomingXMLProcessor processer = new APplusIncomingXMLProcessor(xmlContent, conversionKeys);
        processer.process();
        erechnungXML = processer.getInvoiceXML();
        return erechnungXML;
    }
}
