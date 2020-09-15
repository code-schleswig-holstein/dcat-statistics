package de.landsh.opendata;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Downloads an RDF document containing vocabulary.
 */
public class DownloadVocabulary {
    public static final Logger log = LoggerFactory.getLogger(DownloadVocabulary.class);
    Model model = ModelFactory.createDefaultModel();

    public static void main(String[] args) throws IOException {
        DownloadVocabulary self = new DownloadVocabulary();
        self.download("http://publications.europa.eu/resource/authority/data-theme", true);
        self.save("target/data-theme.ttl");

        self = new DownloadVocabulary();
        self.download("http://publications.europa.eu/resource/authority/file-type", true);
        self.save("target/file-type.ttl");

        self = new DownloadVocabulary();
        self.download("http://publications.europa.eu/resource/authority/frequency", true);
        self.save("target/frequency.ttl");

        self = new DownloadVocabulary();
        self.download("http://publications.europa.eu/resource/authority/language", false);
        self.download("http://publications.europa.eu/resource/authority/language/DEU", false);
        self.download("http://publications.europa.eu/resource/authority/language/ENG", false);
        self.download("http://publications.europa.eu/resource/authority/language/GER", false);
        self.save("target/language.ttl");

        self = new DownloadVocabulary();
        self.download("https://www.dcat-ap.de/def/licenses/20200715.rdf", false);
        self.save("target/licenses.ttl");

        self = new DownloadVocabulary();
        self.download("https://www.dcat-ap.de/def/plannedAvailability/1_0.rdf", false);
        self.save("target/plannedAvailability.ttl");

        self = new DownloadVocabulary();
        self.download("https://www.dcat-ap.de/def/politicalGeocoding/Level/1_0.rdf", false);
        self.save("target/politicalGeocodingLevel.ttl");

        self = new DownloadVocabulary();
        self.download("https://www.dcat-ap.de/def/contributors/20200728.rdf", false);
        self.save("target/contributors.ttl");

        self = new DownloadVocabulary();
        self.download("https://www.dcat-ap.de/def/datasetTypes/1_0.rdf", false);
        self.save("target/datasetTypes.ttl");
    }

    private void save(String targetFileName) throws IOException {
        model.write(new FileWriter(targetFileName), "TTL");
    }

    void download(String url, boolean downloadResources) {
        RDFParser.create()
                .source(url)
                .lang(RDFLanguages.RDFXML)
                .errorHandler(ErrorHandlerFactory.errorHandlerNoWarnings(log))
                .base("http://example.org")
                .parse(model);

        if (downloadResources) {
            // Download the resources mentions in the vocabulary file.
            ResIterator it = model.listSubjects();
            while (it.hasNext()) {
                Resource resource = it.next();
                log.debug("Downloading {}", resource.getURI());
                RDFParser.create()
                        .source(resource.getURI())
                        .lang(RDFLanguages.RDFXML)
                        .errorHandler(ErrorHandlerFactory.errorHandlerNoWarnings(log))
                        .base("http://example.org")
                        .parse(model);
            }
        }

    }

}
