/*
 * CUSTOM:TEK/MEB:19.11.2026: Dauerhafter Dienst fuer die E-Rechnung-Cloud (Vollausbau t4).
 *
 * Start:  java -jar Mustang-CLI.jar --custom-action SERVE
 *
 * Zeilen-Protokoll ueber stdin/stdout (ein Auftrag nach dem anderen; parallele Arbeit = mehrere
 * Prozesse, der Pool sitzt auf der Python-Seite):
 *   -> READY <version>                       einmal nach dem Start
 *   <- PING                                  -> PONG
 *   <- RUN <base64(args, durch \0 getrennt)> -> DONE <exitcode> <base64 stdout> <base64 stderr>
 *                                               bzw. UNSUPPORTED (Aufrufer nimmt den Subprozess)
 *   <- QUIT                                  -> Prozess endet
 *
 * Die Argumente sind dieselben wie beim Kommandozeilen-Aufruf (ohne "-jar <jar>"), die
 * Ergebnisse landen wie dort in den Ausgabedateien. Unterstuetzt: --custom-action
 * GENERATE_FROM_XML / VALIDATE / COMBINE und --action visualize / pdf. System.out und
 * System.err werden je Auftrag mitgeschnitten, damit nichts das Protokoll stoert.
 * Gewinn gegenueber dem Subprozess: kein JVM-Start, Klassen geladen und JIT-kompiliert,
 * Schematron-XSLTs (ph-schematron-Cache) und Visualizer-Templates nur einmal kompiliert.
 */
package org.mustangproject.commandline;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

import org.mustangproject.ZUGFeRD.ZUGFeRDVisualizer;

public class ERechnungServer {

	/** Mitschnitt mit Obergrenze (nur das Ende zaehlt fuer Fehlermeldungen). */
	static final class Capture extends OutputStream {
		private static final int MAX = 256 * 1024;
		private ByteArrayOutputStream buf = new ByteArrayOutputStream();

		@Override
		public synchronized void write(int b) {
			buf.write(b);
			trim();
		}

		@Override
		public synchronized void write(byte[] b, int off, int len) {
			buf.write(b, off, len);
			trim();
		}

		private void trim() {
			if (buf.size() > 2 * MAX) {
				byte[] all = buf.toByteArray();
				buf = new ByteArrayOutputStream();
				buf.write(all, all.length - MAX, MAX);
			}
		}

		synchronized byte[] take() {
			byte[] out = buf.toByteArray();
			buf = new ByteArrayOutputStream();
			return out;
		}
	}

	private static final Capture OUT = new Capture();
	private static final Capture ERR = new Capture();
	private static ZUGFeRDVisualizer visualizer;

	public static void serve() throws Exception {
		PrintStream protocol = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
		System.setOut(new PrintStream(OUT, true, "UTF-8"));
		System.setErr(new PrintStream(ERR, true, "UTF-8"));
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		String version = ERechnungServer.class.getPackage().getImplementationVersion();
		protocol.println("READY " + (version == null ? "dev" : version));
		String line;
		while ((line = in.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.equals("PING")) {
				protocol.println("PONG");
			} else if (line.equals("QUIT")) {
				break;
			} else if (line.startsWith("RUN ")) {
				String joined = new String(Base64.getDecoder().decode(line.substring(4).trim()), StandardCharsets.UTF_8);
				String[] args = joined.isEmpty() ? new String[0] : joined.split("\0", -1);
				OUT.take();
				ERR.take();
				Integer code = run(args);
				System.out.flush();
				System.err.flush();
				if (code == null) {
					protocol.println("UNSUPPORTED");
				} else {
					Base64.Encoder b64 = Base64.getEncoder();
					protocol.println("DONE " + code + " " + b64.encodeToString(OUT.take()) + " "
							+ b64.encodeToString(ERR.take()));
				}
			} else {
				protocol.println("UNSUPPORTED");
			}
		}
		protocol.flush();
	}

	private static String arg(String[] args, String key) {
		for (int i = 0; i < args.length - 1; i++) {
			if (args[i].equalsIgnoreCase(key)) {
				return args[i + 1];
			}
		}
		return null;
	}

	/** Exitcode wie beim Subprozess; null = Auftrag hier nicht unterstuetzt. */
	static Integer run(String[] args) {
		try {
			if (args.length >= 2 && args[0].equalsIgnoreCase("--custom-action")) {
				String result;
				switch (args[1].toUpperCase()) {
				case "GENERATE_FROM_XML":
					result = APplusActions.handleGenerateFromXML(args);
					break;
				case "VALIDATE":
					result = APplusActions.handleValidate(args);
					break;
				case "COMBINE":
					result = APplusActions.handleCombine(args);
					break;
				default:
					return null;
				}
				// wie Main.main: Ergebnis-String in --output-file (UTF-8)
				String outputFile = arg(args, "--output-file");
				if (result != null && outputFile != null) {
					try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(outputFile)), "UTF-8")) {
						w.write(result);
					}
				}
				return 0;
			}
			String action = arg(args, "--action");
			if ("visualize".equals(action) || "pdf".equals(action)) {
				return visualize(arg(args, "--source"), arg(args, "--language"), arg(args, "--out"),
						"pdf".equals(action));
			}
			return null;
		} catch (OutOfMemoryError e) {
			throw e; // -XX:+ExitOnOutOfMemoryError beendet den Prozess, der Pool startet neu
		} catch (Throwable e) {
			// wie Main.main: Stacktrace, Exitcode -1 (= 255)
			e.printStackTrace();
			return 255;
		}
	}

	/** Wie Main.performVisualization, aber mit wiederverwendetem Visualizer (Templates gecacht). */
	private static int visualize(String source, String lang, String out, boolean intoPDF) {
		if (source == null || out == null || lang == null) {
			System.err.println("--source, --out und --language sind im Dienst Pflicht");
			return 2;
		}
		if (new File(out).exists()) {
			System.err.println("File " + out + " already exists");
		}
		if (visualizer == null) {
			visualizer = new ZUGFeRDVisualizer();
		}
		ZUGFeRDVisualizer.Language code = ZUGFeRDVisualizer.Language.EN;
		if (lang.equalsIgnoreCase("de")) {
			code = ZUGFeRDVisualizer.Language.DE;
		}
		if (lang.equalsIgnoreCase("fr")) {
			code = ZUGFeRDVisualizer.Language.FR;
		}
		try {
			if (!intoPDF) {
				String html = visualizer.visualize(source, code);
				Files.write(Paths.get(out), html.getBytes());
			} else {
				visualizer.toPDF(source, out, code);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
		System.out.println("Written to " + out);
		return 0;
	}
}
