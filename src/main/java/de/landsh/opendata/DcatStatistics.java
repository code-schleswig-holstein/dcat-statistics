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
    final Statistics global = new Statistics();
    final Map<String, Statistics> statisticsPerContributor = new HashMap<>();
    private Set<String> validFileTypes;
    private Set<String> validLanguages;
    private Set<String> validLicenses;
    private Set<String> validPlannedAvailabilities;
    private Set<String> validPoliticalGeocodingLevels;
    private Set<String> validContributors;
    private Set<String> validAccrualFrequencies;

    static Collection<Resource> listDistributionsForDataset(Resource dataset) {
        final Set<Resource> result = new HashSet<>();
        final StmtIterator it = dataset.listProperties(ResourceFactory.createProperty("http://www.w3.org/ns/dcat#distribution"));
        while (it.hasNext()) {
            final Statement next = it.next();
            final Resource distribution = next.getObject().asResource();
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

        validFileTypes = readVocabulary("http://publications.europa.eu/resource/authority/file-type");
        validLanguages = readVocabulary("http://publications.europa.eu/resource/authority/language");
        validAccrualFrequencies = readVocabulary("http://publications.europa.eu/resource/authority/frequency");
        validLicenses = readVocabulary("https://www.dcat-ap.de/def/licenses/20200715.rdf");
        validPlannedAvailabilities = readVocabulary("https://www.dcat-ap.de/def/plannedAvailability/1_0.rdf");
        validPoliticalGeocodingLevels = readVocabulary("https://www.dcat-ap.de/def/politicalGeocoding/Level/1_0.rdf");
        validContributors = readVocabulary("https://www.dcat-ap.de/def/contributors/20190531.rdf");

        final Model model = ModelFactory.createDefaultModel();
        final File dir = new File("target/");
        final File[] files = dir.listFiles((file, name) -> name.endsWith(".ttl"));
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
            workOnDataset(dataset);
        }

        PrintStream out = new PrintStream(new FileOutputStream("target/govdata-statistics.csv"));
        global.writeHeader(out);
        global.writeStatistics(global, "ALL", out);

        for (String contributor : statisticsPerContributor.keySet()) {
            log.info("Contributor {}", contributor);
            statisticsPerContributor.get(contributor).writeStatistics(global, contributor, out);
        }

        out.close();

    }

    /**
     * Check if the specified property is used for a specified dataset.
     */
    void checkDatasetProperty(Statistics statistics, Resource dataset, Property property) {
        if (dataset.hasProperty(property)) {
            final boolean isLiteral = isLiteralValue(dataset, property);
            statistics.incrementDatasetInformation(property, isLiteral, false);
            global.incrementDatasetInformation(property, isLiteral, false);
        }
    }

    /**
     * Check if the specified property is used for the specified dataset and uses one of the values from the vocabulary.
     */
    void checkDatasetProperty(Statistics statistics, Resource dataset, Property property, Set<String> validVocabulary) {
        if (dataset.hasProperty(property)) {
            final boolean isLiteral = isLiteralValue(dataset, property);
            final boolean isInvalid = !validVocabulary.contains(getPropertyValue(dataset, property));

            statistics.incrementDatasetInformation(property, isLiteral, isInvalid);
            global.incrementDatasetInformation(property, isLiteral, isInvalid);

        }
    }


    public boolean isLiteralValue(Resource resource, Property property) {
        if (!resource.hasProperty(property)) return false;
        return resource.getProperty(property).getObject().isLiteral();
    }

    public String getPropertyValue(Resource resource, Property property) {
        if (!resource.hasProperty(property)) return null;
        final Statement statement = resource.getProperty(property);
        if (statement.getObject().isLiteral()) {
            return statement.getObject().asLiteral().getString();
        }
        if (statement.getObject().isResource()) {
            return statement.getObject().asResource().getURI();
        }
        throw new RuntimeException("Unkonwn property value " + statement.getObject());
    }

    /**
     * Check if the specified property is used for the specified dataset and uses one of the values from the vocabulary.
     */
    void checkDistributionProperty(Statistics statistics, Resource distribution, Property property, Set<String> validVocabulary) {
        if (distribution.hasProperty(property)) {
            final boolean isLiteral = isLiteralValue(distribution, property);
            final boolean isInvalid = !validVocabulary.contains(getPropertyValue(distribution, property));
            statistics.incrementDistributionInformation(property, isLiteral, isInvalid);
            global.incrementDistributionInformation(property, isLiteral, isInvalid);
        }
    }

    /**
     * Check if the specified property is used for the specified dataset.
     */
    void checkDistributionProperty(Statistics statistics, Resource distribution, Property property) {
        if (distribution.hasProperty(property)) {
            final boolean isLiteral = isLiteralValue(distribution, property);
            statistics.incrementDistributionInformation(property, isLiteral, false);
            global.incrementDistributionInformation(property, isLiteral, false);
        }
    }

    private void workOnDataset(Resource dataset) {
        final String contributor = workOnContributorId(dataset);

        if (!statisticsPerContributor.containsKey(contributor)) {
            statisticsPerContributor.put(contributor, new Statistics());
        }
        final Statistics statistics = statisticsPerContributor.get(contributor);

        statistics.numberOfDatasets++;
        global.numberOfDatasets++;

        workOnIdentifier(dataset, statistics);
        checkDatasetProperty(statistics, dataset, DCATAPde.maintainer);
        checkDatasetProperty(statistics, dataset, DCATAPde.originator);
        checkDatasetProperty(statistics, dataset, DCATAPde.plannedAvailability, validPlannedAvailabilities);
        checkDatasetProperty(statistics, dataset, DCATAPde.politicalGeocodingLevelURI, validPoliticalGeocodingLevels);
        checkDatasetProperty(statistics, dataset, DCATAPde.politicalGeocodingURI);
        checkDatasetProperty(statistics, dataset, DCAT.contactPoint);
        checkDatasetProperty(statistics, dataset, DCAT.landingPage);
        checkDatasetProperty(statistics, dataset, DCTerms.accrualPeriodicity, validAccrualFrequencies);
        checkDatasetProperty(statistics, dataset, DCTerms.contributor);
        checkDatasetProperty(statistics, dataset, DCTerms.creator);
        checkDatasetProperty(statistics, dataset, DCTerms.language, validLanguages);
        checkDatasetProperty(statistics, dataset, DCTerms.provenance);
        checkDatasetProperty(statistics, dataset, DCTerms.publisher);
        checkDatasetProperty(statistics, dataset, DCTerms.source);
        checkDatasetProperty(statistics, dataset, DCTerms.spatial);
        checkDatasetProperty(statistics, dataset, DCTerms.temporal);
        checkDatasetProperty(statistics, dataset, DCTerms.issued);
        checkDatasetProperty(statistics, dataset, DCTerms.modified);
        checkDatasetProperty(statistics, dataset, DCAT.theme);
        checkDatasetProperty(statistics, dataset, DCAT.keyword);

        final Collection<Resource> distributions = listDistributionsForDataset(dataset);

        for (final Resource distribution : distributions) {
            statistics.numberOfDistributions++;
            global.numberOfDistributions++;

            workOnFormat(distribution, statistics, contributor);
            workOnDistributionLicense(distribution, statistics);
            checkDistributionProperty(statistics, distribution, DCAT.accessURL);
            checkDistributionProperty(statistics, distribution, DCAT.downloadURL);
            checkDistributionProperty(statistics, distribution, DCAT.mediaType);
            checkDistributionProperty(statistics, distribution, DCTerms.conformsTo);
            checkDistributionProperty(statistics, distribution, DCTerms.issued);
            checkDistributionProperty(statistics, distribution, DCTerms.rights);
            checkDistributionProperty(statistics, distribution, DCAT.byteSize);
            checkDistributionProperty(statistics, distribution, SPDX.checksum);
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
            if (!validLicenses.contains(licenseId)) {
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
            if (validContributors.contains(contributorId)) {
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
        private final Map<Property, Integer> datasetProperties = new HashMap<>();
        private final Map<Property, Integer> distributionProperties = new HashMap<>();
        private final Map<Property, Integer> datasetPropertiesLiteral = new HashMap<>();
        private final Map<Property, Integer> distributionPropertiesLiteral = new HashMap<>();
        private final Map<Property, Integer> datasetPropertiesInvalid = new HashMap<>();
        private final Map<Property, Integer> distributionPropertiesInvalid = new HashMap<>();
        public int formatMissing;
        Set<String> formats = new TreeSet<>();
        int formatAsResource = 0;
        int formatAsLiteral = 0;
        int numberOfDistributions = 0;
        int datasetWithoutContributor = 0;
        int noIdentifier = 0;
        int identifierIsURI = 0;
        int distributionWithoutLicense = 0;
        int numberOfDatasets = 0;

        void incrementDatasetInformation(Property property, boolean isLiteral, boolean isInvalid) {
            datasetProperties.put(property, datasetProperties.getOrDefault(property, 0) + 1);
            if (isInvalid)
                datasetPropertiesInvalid.put(property, datasetPropertiesInvalid.getOrDefault(property, 0) + 1);
            if (isLiteral)
                datasetPropertiesLiteral.put(property, datasetPropertiesLiteral.getOrDefault(property, 0) + 1);
        }

        void incrementDistributionInformation(Property property, boolean isLiteral, boolean isInvalid) {
            distributionProperties.put(property, distributionProperties.getOrDefault(property, 0) + 1);
            if (isInvalid)
                distributionPropertiesInvalid.put(property, distributionPropertiesInvalid.getOrDefault(property, 0) + 1);
            if (isLiteral)
                distributionPropertiesLiteral.put(property, distributionPropertiesLiteral.getOrDefault(property, 0) + 1);
        }

        void writeHeader(PrintStream out) {
            out.print("contributor\t" +
                    "Number of Datasets\t" +
                    "Datasets without contributor (bad)\t" +
                    "Datasets without identifier (bad)\t" +
                    "Datasets with URI identifier (good)\t");

            for (Property p : datasetProperties.keySet()) {
                out.print("DS with ");
                out.print(p.getLocalName());
                out.print('\t');
                out.print("DS with literal ");
                out.print(p.getLocalName());
                out.print('\t');
                out.print("DS with invalid ");
                out.print(p.getLocalName());
                out.print('\t');
            }

            out.print("Number of Distributions\t");
            for (Property p : distributionProperties.keySet()) {
                out.print("Dist with ");
                out.print(p.getLocalName());
                out.print('\t');
                out.print("Dist with literal ");
                out.print(p.getLocalName());
                out.print('\t');
                out.print("Dist with invalid ");
                out.print(p.getLocalName());
                out.print('\t');
            }

            out.print("Distributions without license (bad)\t" +
                    "Format missing (bad)\t" +
                    "Format as literal (bad)\t" +
                    "Format as resource (good)\n");
        }

        /**
         * @param global    The global statistics are needed to get all header names.
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

            for (Property p : global.datasetProperties.keySet()) {
                out.print(datasetProperties.getOrDefault(p, 0));
                out.print('\t');
                out.print(datasetPropertiesLiteral.getOrDefault(p, 0));
                out.print('\t');
                out.print(datasetPropertiesInvalid.getOrDefault(p, 0));
                out.print('\t');
            }

            out.print(numberOfDistributions);
            out.print('\t');

            for (Property p : global.distributionProperties.keySet()) {
                out.print(distributionProperties.getOrDefault(p, 0));
                out.print('\t');
                out.print(distributionPropertiesLiteral.getOrDefault(p, 0));
                out.print('\t');
                out.print(distributionPropertiesInvalid.getOrDefault(p, 0));
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

