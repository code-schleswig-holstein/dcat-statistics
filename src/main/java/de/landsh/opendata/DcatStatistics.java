package de.landsh.opendata;

import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Berechnet zu einem DCAT-AP.de konformen Katalog diverse Statistiken.
 */
public class DcatStatistics {
    public static final String URL_FILE_TYPES = "http://publications.europa.eu/resource/authority/file-type";

    public static final String URL_LANGUAGES = "http://publications.europa.eu/resource/authority/language";

    private static final Logger log = LoggerFactory.getLogger(DcatStatistics.class);
    private static final Property contributorID = ResourceFactory.createProperty("http://dcat-ap.de/def/dcatde/contributorID");
    private static final String DCAT_AP_DE_DEF_LICENSES = "http://dcat-ap.de/def/licenses/";
    private static final String DCAT_AP_DE_DEF_CONTRIBUTORS = "http://dcat-ap.de/def/contributors/";
    private static final Collection<Resource> UNWANTED_FORMATS = Arrays.asList(
            ResourceFactory.createResource("http://publications.europa.eu/resource/authority/file-type/PDF"),
            ResourceFactory.createResource("http://publications.europa.eu/resource/authority/file-type/DOC"),
            ResourceFactory.createResource("http://publications.europa.eu/resource/authority/file-type/DOCX"),
            ResourceFactory.createResource("http://publications.europa.eu/resource/authority/file-type/HTML")
    );
    Statistics global = new Statistics();
    Map<String, Statistics> statisticsPerContributor = new HashMap<>();
    Set<String> validFileTypes;
    Set<String> validLanguages;
    Set<String> validLicenses;
    Set<String> validPlannedAvailabilities;
    Set<String> validPoliticalGeocodingLevels;
    Set<String> validContributors;

    static Collection<Resource> getDistributionsForDataset(Resource dataset) {
        Set<Resource> result = new HashSet<>();
        StmtIterator it = dataset.listProperties(ResourceFactory.createProperty("http://www.w3.org/ns/dcat#distribution"));
        while (it.hasNext()) {
            Statement next = it.next();
            Resource distribution = next.getObject().asResource();
            result.add(distribution);
        }

        return result;
    }

    public static void main(String[] args) throws IOException {
        new DcatStatistics().work();
    }

    /**
     * Reads a RDF document contaning standard vocabulary, such as the European file formats.
     */
    private Set<String> readVocabulary(String url) throws IOException {
        final Model model = ModelFactory.createDefaultModel();

        // Check if we have a local copy of the file.
        File localCache = new File("target/" + Math.abs(url.hashCode()) + ".xml");
        if (localCache.exists()) {
            log.debug("Using local cache for {}", url);
            RDFParser.create()
                    .source(localCache.toPath())
                    .lang(RDFLanguages.RDFXML)
                    .errorHandler(ErrorHandlerFactory.errorHandlerNoWarnings(log))
                    .base("http://open/data")
                    .parse(model);
        } else {
            RDFParser.create()
                    .source(url)
                    .lang(RDFLanguages.RDFXML)
                    .errorHandler(ErrorHandlerFactory.errorHandlerNoWarnings(log))
                    .base("http://open/data")
                    .parse(model);
            model.write(new FileWriter(localCache));
        }

        final Set<String> result = new HashSet<>();

        final ResIterator it = model.listSubjectsWithProperty(SKOS.topConceptOf);
        while (it.hasNext()) {
            final Resource next = it.next();
            result.add(next.getURI());
        }

        return result;
    }

    private void work() throws IOException {

        validFileTypes = readVocabulary(URL_FILE_TYPES);
        validLanguages = readVocabulary(URL_LANGUAGES);
        validLicenses = readVocabulary("https://www.dcat-ap.de/def/licenses/20200715.rdf");
        validPlannedAvailabilities = readVocabulary("https://www.dcat-ap.de/def/plannedAvailability/1_0.rdf");
        validPoliticalGeocodingLevels = readVocabulary("https://www.dcat-ap.de/def/politicalGeocoding/Level/1_0.rdf");
        validContributors = readVocabulary("https://www.dcat-ap.de/def/contributors/20190531.rdf");

        Model model = ModelFactory.createDefaultModel();
        File dir = new File("target/");
        File[] files = dir.listFiles((file, name) -> name.endsWith(".ttl"));
        if (files != null) {
            for (File file : ProgressBar.wrap(Arrays.asList(files), "Reading RDF docments")) {
                try {
                    RDFParser.create()
                            .source(new FileInputStream(file))
                            .lang(RDFLanguages.TTL)
                            .errorHandler(ErrorHandlerFactory.errorHandlerNoWarnings(log))
                            .base("http://open/data")
                            .parse(model);
                } catch (RiotException e) {
                    log.error("Could not parse file {}: {}", file, e.getMessage());
                    System.exit(2);
                }

            }
        }

        log.debug("Read DCAT-AP.de data.");

        final ResIterator it = model.listSubjectsWithProperty(RDF.type, DCAT.Dataset);

        while (it.hasNext()) {
            Resource dataset = it.next();
            processDataset(dataset);
        }

        PrintStream out = new PrintStream(new FileOutputStream("/tmp/govdata-statistics.csv"));
        global.writeHeader(out);
        global.writeStatistics(global, "ALL", out);

        for (String contributor : statisticsPerContributor.keySet()) {
            log.info("Contributor {}", contributor);
            statisticsPerContributor.get(contributor).writeStatistics(global, contributor, out);
        }

        out.close();

    }

    void checkDatasetProperty(Statistics statistics, Resource dataset, Property property) {
        if (dataset.hasProperty(property)) {
            statistics.incrementDatasetInformation(property);
            global.incrementDatasetInformation(property);
        }
    }

    void checkDistributionProperty(Statistics statistics, Resource distribution, Property property) {
        if (distribution.hasProperty(property)) {
            statistics.incrementDistributionInformation(property);
            global.incrementDistributionInformation(property);
        }
    }

    private void processDataset(Resource dataset) {
        final String contributor = workOnContributorId(dataset);

        if (!statisticsPerContributor.containsKey(contributor)) {
            statisticsPerContributor.put(contributor, new Statistics());
        }
        final Statistics statistics = statisticsPerContributor.get(contributor);

        statistics.numberOfDatasets++;
        global.numberOfDatasets++;

        workOnIdentifier(dataset, statistics);
        checkDatasetProperty(statistics, dataset, DCTerms.publisher);
        checkDatasetProperty(statistics, dataset, DCTerms.contributor);
        checkDatasetProperty(statistics, dataset, DCTerms.creator);
        checkDatasetProperty(statistics, dataset, DCATAPde.maintainer);
        checkDatasetProperty(statistics, dataset, DCATAPde.originator);
        checkDatasetProperty(statistics, dataset, DCAT.contactPoint);
        checkDatasetProperty(statistics, dataset, DCATAPde.politicalGeocodingLevelURI);
        checkDatasetProperty(statistics, dataset, DCATAPde.politicalGeocodingURI);
        checkDatasetProperty(statistics, dataset, DCTerms.accrualPeriodicity);
        checkDatasetProperty(statistics, dataset, DCTerms.source);
        checkDatasetProperty(statistics, dataset, DCTerms.provenance);

        final Collection<Resource> distributions = getDistributionsForDataset(dataset);

        for (final Resource distribution : distributions) {
            statistics.numberOfDistributions++;
            global.numberOfDistributions++;

            workOnFormat(distribution, statistics, contributor);
            workOnDistributionLicense(distribution, statistics);
        }
    }

    private void workOnDistributionLicense(Resource distribution, Statistics statistics) {
        final Set<String> licenseIds = new HashSet<>();
        final StmtIterator stmtIterator = distribution.listProperties(DCTerms.license);
        while (stmtIterator.hasNext()) {
            final Statement next = stmtIterator.next();
            if (next.getObject().isResource()) {
                licenseIds.add(next.getObject().asResource().getURI());
            } else {
                licenseIds.add(next.getObject().toString());
            }
        }

        if (licenseIds.isEmpty()) {
            statistics.distributionWithoutLicense++;
            global.distributionWithoutLicense++;
        } else if (licenseIds.size() == 1) {
            final String licenseId = licenseIds.iterator().next();
            if (!licenseId.startsWith(DCAT_AP_DE_DEF_LICENSES)) {
                log.info("Distribution {} has invalid license id {}", distribution.getURI(), licenseId);
                statistics.distributionWithoutLicense++;
                global.distributionWithoutLicense++;
            }
        } else {
            boolean atLeastOneValidLicenseId = false;
            for (String contributorId : licenseIds) {
                if (contributorId.startsWith(DCAT_AP_DE_DEF_LICENSES)) {
                    atLeastOneValidLicenseId = true;
                    break;
                }
            }
            if (!atLeastOneValidLicenseId) {
                log.info("Distribution {} does not have a valid license id", distribution.getURI());
                statistics.distributionWithoutLicense++;
                global.distributionWithoutLicense++;
            }
        }

    }

    private void workOnFormat(Resource distribution, Statistics statistics, String contributor) {
        Statement formatStatement = distribution.getProperty(DCTerms.format);
        RDFNode format = formatStatement == null ? null : formatStatement.getObject();

        String formatString = StringUtils.EMPTY;

        if (format == null) {
            global.formatMissing++;
            statistics.formatMissing++;
        } else {
            if (format.isLiteral()) {
                statistics.formatAsLiteral++;
                global.formatAsLiteral++;
                formatString = format.asLiteral().getString();
            } else if (format.isResource()) {
                statistics.formatAsResource++;
                global.formatAsResource++;
                formatString = StringUtils.substringAfterLast(format.asResource().getURI(), "/");
            }
        }

        statistics.formats.add(formatString);
        global.formats.add(formatString);
    }

    private void workOnIdentifier(Resource dataset, Statistics statistics) {
        Statement identifier = dataset.getProperty(DCTerms.identifier);
        if (identifier == null) {
            statistics.noIdentifier++;
            global.noIdentifier++;
        } else if (identifier.getObject().isLiteral() && identifier.getObject().asLiteral().getString().startsWith("http")) {
            statistics.identifierIsURI++;
            global.identifierIsURI++;
        }
    }

    private String workOnContributorId(Resource dataset) {
        String result = null;
        final String datasetId = dataset.getURI();
        final Set<String> contributorIds = new HashSet<>();
        final StmtIterator stmtIterator = dataset.listProperties(contributorID);
        while (stmtIterator.hasNext()) {
            final Statement next = stmtIterator.next();
            if (next.getObject().isResource()) {
                contributorIds.add(next.getObject().asResource().getURI());
            } else {
                contributorIds.add(next.getObject().toString());
            }
        }

        if (contributorIds.isEmpty()) {
            global.datasetWithoutContributor++;
        } else if (contributorIds.size() == 1) {
            final String contributorId = contributorIds.iterator().next();
            if (  validContributors.contains( contributorId)) {
                result = StringUtils.substringAfter(contributorId, DCAT_AP_DE_DEF_CONTRIBUTORS);
            } else {
                log.info("Dataset {} has invalid contributor id {}", datasetId, contributorId);
            }
        } else {
            boolean atLeastOneValidContributorId = false;
            for (String contributorId : contributorIds) {
                if (contributorId.startsWith(DCAT_AP_DE_DEF_CONTRIBUTORS)) {
                    atLeastOneValidContributorId = true;
                    result = StringUtils.substringAfter(contributorId, DCAT_AP_DE_DEF_CONTRIBUTORS);
                    break;
                }
            }
            if (!atLeastOneValidContributorId) {
                log.info("Dataset {} does not have a valid contributor id", datasetId);
                global.datasetWithoutContributor++;
            }
        }
        return result;
    }

    private static class Statistics {
        public int formatMissing;
        int formatAsResource = 0;
        int formatAsLiteral = 0;
        int numberOfDistributions = 0;
        int datasetWithoutContributor = 0;
        int noIdentifier = 0;
        int identifierIsURI = 0;
        Set<String> formats = new TreeSet<>();
        int distributionWithoutLicense = 0;
        int numberOfDatasets = 0;

        Map<Property, Integer> datasetInformation = new HashMap<>();
        Map<Property, Integer> distributionInformation = new HashMap<>();

        void incrementDatasetInformation(Property property) {
            datasetInformation.put(property, datasetInformation.getOrDefault(property, 0) + 1);
        }

        void incrementDistributionInformation(Property property) {
            distributionInformation.put(property, distributionInformation.getOrDefault(property, 0) + 1);
        }

        void writeHeader(PrintStream out) {
            out.print("contributor\t" +
                    "Number of Datasets\t" +
                    "Datasets without contributor (bad)\t" +
                    "Datasets without identifier (bad)\t" +
                    "Datasets with URI identifier (good)\t");

            for (Property p : datasetInformation.keySet()) {
                out.print("DS with ");
                out.print(p.getLocalName());
                out.print('\t');
            }

            out.print("Number of Distributions\t");
            for (Property p : distributionInformation.keySet()) {
                out.print("Dist with ");
                out.print(p.getLocalName());
                out.print('\t');
            }

            out.print("Distributions without license (bad)\t" +
                    "Format missing (bad)\t" +
                    "Format as literal (bad)\t" +
                    "Format as resource (good)\n");
        }

        /**
         * @param global      The global statistics are needed to get all header names.
         * @param contributor
         * @param out
         */
        void writeStatistics(Statistics global, String contributor, PrintStream out) {
            out.print(contributor);
            out.print('\t');
            out.print(numberOfDatasets);
            out.print('\t');
            out.print(datasetWithoutContributor);
            out.print('\t');
            out.print(noIdentifier);
            out.print('\t');
            out.print(identifierIsURI);
            out.print('\t');

            for (Property p : global.datasetInformation.keySet()) {
                out.print(datasetInformation.getOrDefault(p, 0));
                out.print('\t');
            }

            out.print(numberOfDistributions);
            out.print('\t');

            for (Property p : global.distributionInformation.keySet()) {
                out.print(distributionInformation.getOrDefault(p, 0));
                out.print('\t');
            }

            out.print(distributionWithoutLicense);
            out.print('\t');
            out.print(formatMissing);
            out.print('\t');
            out.print(formatAsLiteral);
            out.print('\t');
            out.print(formatAsResource);
            out.println();
        }
    }
}
