/*
 * This file is part of Featurepack E-Rechnung VT

 * Copyright by AM - Consulting GmbH 2025
 * License under /AppServer/XML/License-AMC.txt
 */
package org.mustangproject.applus;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;

import org.mustangproject.Invoice;

public class DeprecatedAPplusInterface {

	/*
	 *  APplus Interface
	 */
	public static String getErechnungXML(String rechnungDetails, Map<String,String> conversionKeys, String tempOutputFile) throws Exception {
		String erechnungXML = null;
		String xmlContent = DeprecatedAPplusIncomingXMLParser.getFriendlyXML(rechnungDetails,conversionKeys);

		// FOR TESTING PURPOSE ONLY! 
		if (tempOutputFile != null) {
			File outputFile = new File(tempOutputFile);
	
	        // Write the result to the provided file
	        try (FileWriter writer = new FileWriter(outputFile)) {
	            writer.write(xmlContent);
	        }
		}
        DeprecatedAPplusIncomingXMLProcessor processer = new DeprecatedAPplusIncomingXMLProcessor(xmlContent, conversionKeys);
        processer.process();
        erechnungXML = processer.getInvoiceXML();
        return erechnungXML;
    }
}
