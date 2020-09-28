package de.landsh.opendata;

import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Einige Länderportal senden so dermaßen kaputte Datensätze an GovData, dass der RDF-Parser den ganzen Katalog nicht
 * verarbeiten kann. Dieses Programm filtert solch defekte Datensätze heraus.
 */
public class FixBrokenDcatFiles {
    public static final Logger log = LoggerFactory.getLogger(FixBrokenDcatFiles.class);

    public static void main(String[] args) throws IOException {

        final Set<File> filesWithErrors = new HashSet<>();

        final File dir = new File("target/");
        final File[] files = dir.listFiles((file, name) -> name.endsWith(".ttl"));
        if (files != null) {
            for (File file : ProgressBar.wrap(Arrays.asList(files), "Fixing broker RDF docments")) {
                if (!isValidRdf(file)) {
                    filesWithErrors.add(file);
                }
            }
        }

        log.info("{} files contain errors.", filesWithErrors.size());
        for (File file : filesWithErrors) {
            log.info("Fixing {}...", file);
            fixFile(file);
        }
    }

    static void fixFile(File file) throws IOException {
        int lineWithError = findFirstErrorLine(file);
        if (lineWithError > 0) {
            final List<String> lines = Files.readAllLines(file.toPath());

            while (!isValidRdf(String.join("\n", lines))) {
                final int totalNumberOfLines = lines.size();

                // Wir suchen rückwärts, bis wir gültiges RDF haben.
                int endOfValidRdf = lineWithError - 1;
                while (endOfValidRdf > 0 && !isValidRdf(String.join("\n", lines.subList(0, endOfValidRdf)) + "\n<http://example.org/dummy> a <http://example.org/Dummy> .\n")) {
                    endOfValidRdf--;
                }

                // Das vermutliche Ende der defekten Resource finden.
                int endOfDefect = lineWithError + 1;
                while (endOfDefect < totalNumberOfLines && !lines.get(endOfDefect).endsWith(" .")) {
                    endOfDefect++;
                }

                lines.subList(endOfValidRdf, endOfDefect + 1).clear();
            }

            final PrintStream out = new PrintStream(new FileOutputStream(file));
            for (String line : lines) {
                out.println(line);
            }
            out.close();
        }
    }

    static boolean isValidRdf(String text) {
        final Model model = ModelFactory.createDefaultModel();

        try {
            RDFParser.create()
                    .source(new StringReader(text))
                    .lang(RDFLanguages.TTL)
                    .base("http://open/data")
                    .errorHandler(ErrorHandlerFactory.errorHandlerSimple())
                    .parse(model);
            return true;
        } catch (RiotException e) {
            return false;
        }
    }

    /**
     * Check if the specified file contains a readable RDF document.
     */
    static boolean isValidRdf(File file) throws FileNotFoundException {
        final Model model = ModelFactory.createDefaultModel();
        try {
            RDFParser.create()
                    .source(new FileInputStream(file))
                    .lang(RDFLanguages.TTL)
                    .base("http://open/data")
                    .errorHandler(ErrorHandlerFactory.errorHandlerSimple())
                    .parse(model);
            return true;
        } catch (RiotException e) {
            return false;
        }
    }

    static int findFirstErrorLine(File file) throws FileNotFoundException {
        // Find the line with the first error
        final Model model = ModelFactory.createDefaultModel();
        try {
            RDFParser.create()
                    .source(new FileInputStream(file))
                    .lang(RDFLanguages.TTL)
                    .base("http://open/data")
                    .errorHandler(ErrorHandlerFactory.errorHandlerSimple())
                    .parse(model);
            return -1;
        } catch (RiotException e) {
            return NumberUtils.toInt(StringUtils.substringBetween(e.getMessage(), "line: ", ", col"));
        }
    }

}
