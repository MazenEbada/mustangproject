/*
 * CUSTOM:TEK/MEB:20.11.2026 (12a, A-12): nicht eingebettete Standardschriften eines mitgelieferten PDFs einbetten.
 */
package org.mustangproject.commandline;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDSimpleFont;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.encoding.Encoding;
import org.apache.pdfbox.pdmodel.font.encoding.WinAnsiEncoding;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;

/**
 * PDF/A verlangt eingebettete Schriften (ISO 19005-3 6.2.11.4.1). Viele ERP-PDFs nutzen die Standardschriften
 * Helvetica/Arial, Times oder Courier ohne Einbettung; dann war das ZUGFeRD-PDF ungueltig. Hier wird jede solche
 * einfache Schrift durch die massgleiche Liberation-Schrift (OFL) mit derselben Kodierung ersetzt - die
 * Zeichencodes im Seiteninhalt bleiben gueltig, das Seitenbild bleibt gleich. Andere Schriften (Symbol, eigene
 * Schriften, CID) bleiben unveraendert; die Pruefung meldet sie dann.
 */
final class PdfFontEmbedder {

    private PdfFontEmbedder() {
    }

    /** @return neues PDF oder null, wenn nichts zu tun war oder es nicht ging. */
    static byte[] embedStandardFonts(File pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.isEncrypted()) {
                return null;
            }
            Map<String, PDFont> cache = new HashMap<>();
            Set<COSName> seen = new HashSet<>();
            int changed = 0;
            for (PDPage page : doc.getPages()) {
                changed += replace(doc, page.getResources(), cache, seen, 0);
            }
            if (changed == 0) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            System.err.println("Schriften nicht eingebettet: " + e.getMessage());
            return null;
        }
    }

    private static int replace(PDDocument doc, PDResources res, Map<String, PDFont> cache, Set<COSName> seen, int depth)
            throws IOException {
        if (res == null || depth > 10) {
            return 0;
        }
        int n = 0;
        for (COSName name : res.getFontNames()) {
            PDFont font;
            try {
                font = res.getFont(name);
            } catch (IOException e) {
                continue;
            }
            if (font == null || font.isEmbedded() || !(font instanceof PDSimpleFont)) {
                continue;
            }
            String file = liberationFor(font.getName());
            if (file == null) {
                continue;
            }
            Encoding enc = ((PDSimpleFont) font).getEncoding();
            if (enc == null || font.isStandard14() && enc.getEncodingName() == null) {
                enc = WinAnsiEncoding.INSTANCE;
            }
            String key = file + "|" + (enc.getEncodingName() == null ? System.identityHashCode(enc) : enc.getEncodingName());
            PDFont repl = cache.get(key);
            if (repl == null) {
                try (InputStream in = PdfFontEmbedder.class.getResourceAsStream("/fonts/" + file)) {
                    if (in == null) {
                        continue;
                    }
                    repl = PDTrueTypeFont.load(doc, in, enc);
                } catch (Exception e) {
                    continue; // Kodierung nicht darstellbar: Schrift so lassen
                }
                cache.put(key, repl);
            }
            res.put(name, repl);
            n++;
        }
        for (COSName name : res.getXObjectNames()) {
            PDXObject x;
            try {
                x = res.getXObject(name);
            } catch (IOException e) {
                continue;
            }
            if (x instanceof PDFormXObject && seen.add(name)) {
                n += replace(doc, ((PDFormXObject) x).getResources(), cache, seen, depth + 1);
            }
        }
        return n;
    }

    /** Massgleiche Liberation-Schrift zum Schriftnamen, null = keine passende (Symbol, Dingbats). */
    static String liberationFor(String name) {
        String s = name == null ? "" : name.replaceFirst("^[A-Z]{6}\\+", "").toLowerCase();
        if (s.contains("symbol") || s.contains("dingbat") || s.contains("wingding")) {
            return null;
        }
        String family = s.contains("courier") || s.contains("mono") ? "Mono"
                : s.contains("times") || s.contains("serif") && !s.contains("sans") || s.contains("roman")
                || s.contains("georgia") ? "Serif" : "Sans";
        boolean bold = s.contains("bold") || s.contains("black") || s.contains("heavy");
        boolean italic = s.contains("italic") || s.contains("oblique");
        String style = bold && italic ? "BoldItalic" : bold ? "Bold" : italic ? "Italic" : "Regular";
        return "Liberation" + family + "-" + style + ".ttf";
    }
}
