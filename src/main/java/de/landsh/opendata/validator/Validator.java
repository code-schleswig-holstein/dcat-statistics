package de.landsh.opendata.validator;

import de.landsh.opendata.Adms;
import de.landsh.opendata.SPDX;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class Validator {
    private static final Logger log = LoggerFactory.getLogger(Validator.class);

    final Set<ValidationResult> validationResults = new HashSet<>();
    private Model model = ModelFactory.createDefaultModel();

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || !new File(args[0]).exists()) {
            System.out.println("USAGE: java Validator <catalog.ttl>");
            System.exit(2);
        }

        final Validator validator = new Validator();
        boolean isValid = validator.validate(new FileInputStream(new File(args[0])));

        if (isValid) {
            System.out.println("Catalog is valid.");
        } else {
            for (ValidationResult validationResult : validator.validationResults) {
                System.out.println(validationResult.resource.getURI() + "\t" + validationResult.getMessage());
            }
        }


    }

    /**
     * Check if the specified resources has the specified type.
     */
    static boolean hasType(Resource resource, Resource type) {
        final StmtIterator it = resource.listProperties(RDF.type);
        while (it.hasNext()) {
            Statement statement = it.next();
            if (type.equals(statement.getObject())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads a RDF document contaning standard vocabulary, such as the European file formats.
     */
    private void addExternalVocabulary(String resourceName) throws IOException {

        final InputStream is;

        if (resourceName.endsWith(".gz")) {
            is = new GZIPInputStream(getClass().getResourceAsStream(resourceName));
        } else {
            is = getClass().getResourceAsStream(resourceName);
        }

        RDFParser.create()
                .source(is)
                .lang(RDFLanguages.TTL)
                .errorHandler(ErrorHandlerFactory.errorHandlerNoWarnings(log))
                .base("http://open/data")
                .parse(model);
    }

    public void reset() {
        model = ModelFactory.createDefaultModel();
        validationResults.clear();
    }

    public boolean validate(InputStream is) throws IOException {

        addExternalVocabulary("/vocabulary/contributors.ttl.gz");
        addExternalVocabulary("/vocabulary/data-theme.ttl.gz");
        addExternalVocabulary("/vocabulary/file-type.ttl.gz");
        addExternalVocabulary("/vocabulary/frequency.ttl.gz");
        addExternalVocabulary("/vocabulary/language.ttl.gz");
        addExternalVocabulary("/vocabulary/licenses.ttl.gz");
        addExternalVocabulary("/vocabulary/plannedAvailability.ttl");
        addExternalVocabulary("/vocabulary/politicalGeocodingLevel.ttl");

        RDFParser.create()
                .source(is)
                .lang(RDFLanguages.TTL)
                .errorHandler(ErrorHandlerFactory.errorHandlerNoWarnings(log))
                .base("http://example.org")
                .parse(model);

        ResIterator it = model.listSubjectsWithProperty(RDF.type, DCAT.Catalog);
        while (it.hasNext()) {
            validateCatalog(it.next());
        }

        it = model.listSubjectsWithProperty(RDF.type, DCAT.CatalogRecord);
        while (it.hasNext()) {
            validateCatalogRecord(it.next());
        }

        it = model.listSubjectsWithProperty(RDF.type, DCAT.Dataset);
        while (it.hasNext()) {
            validateDataset(it.next());
        }

        it = model.listSubjectsWithProperty(RDF.type, DCAT.Distribution);
        while (it.hasNext()) {
            validateDistribution(it.next());
        }

        return validationResults.isEmpty();
    }

    private void validateCatalogRecord(Resource catalogRecord) {

        maxCount1(catalogRecord, DCTerms.conformsTo);
        linkedResourceMustHaveType(catalogRecord, DCTerms.conformsTo, DCTerms.Standard);
        nodeKindBlankNodeOrIRI(catalogRecord, DCTerms.conformsTo);

        maxCount1(catalogRecord, DCTerms.issued);
        literalWithTypeDateOrDateTime(catalogRecord, DCTerms.issued);

        linkedResourceMustHaveType(catalogRecord, DCTerms.language, DCTerms.LinguisticSystem);

        maxCount1(catalogRecord, DCTerms.modified);
        minCount1(catalogRecord, DCTerms.modified);
        literalWithTypeDateOrDateTime(catalogRecord, DCTerms.modified);

        maxCount1(catalogRecord, DCTerms.source);
        linkedResourceMustHaveType(catalogRecord, DCTerms.source, DCAT.CatalogRecord);

        nodeKindLiteral(catalogRecord, DCTerms.title);

        maxCount1(catalogRecord, Adms.status);
        linkedResourceMustHaveType(catalogRecord, Adms.status, SKOS.Concept);
        nodeKindIRI(catalogRecord, Adms.status);

        maxCount1(catalogRecord, FOAF.primaryTopic);
        minCount1(catalogRecord, FOAF.primaryTopic);
        linkedResourceMustHaveType(catalogRecord, FOAF.primaryTopic, DCAT.Dataset);
    }

    public void validateDataset(Resource dataset) {
        linkedResourceMustHaveType(dataset, DCTerms.accessRights, DCTerms.RightsStatement);
        maxCount1(dataset, DCTerms.accessRights);

        maxCount1(dataset, DCTerms.accrualPeriodicity);
        linkedResourceMustHaveType(dataset, DCTerms.accrualPeriodicity, DCTerms.Frequency);
        nodeKindIRI(dataset, DCTerms.accrualPeriodicity);

        linkedResourceMustHaveType(dataset, DCTerms.conformsTo, DCTerms.Standard);

        minCount1(dataset, DCTerms.description);
        nodeKindLiteral(dataset, DCTerms.description);

        linkedResourceMustHaveType(dataset, DCTerms.hasVersion, DCAT.Dataset);
        linkedResourceMustHaveType(dataset, DCTerms.isVersionOf, DCAT.Dataset);

        maxCount1(dataset, DCTerms.issued);
        literalWithTypeDateOrDateTime(dataset, DCTerms.issued);

        nodeKindIRI(dataset, DCTerms.language);

        maxCount1(dataset, DCTerms.modified);
        literalWithTypeDateOrDateTime(dataset, DCTerms.modified);

        linkedResourceMustHaveType(dataset, DCTerms.provenance, DCTerms.ProvenanceStatement);

        maxCount1(dataset, DCTerms.publisher);
        linkedResourceMustHaveType(dataset, DCTerms.publisher, FOAF.Agent);
        nodeKindIRI(dataset, DCTerms.publisher);

        nodeKindIRI(dataset, DCTerms.relation);

        linkedResourceMustHaveType(dataset, DCTerms.source, DCAT.Dataset);

        linkedResourceMustHaveType(dataset, DCTerms.spatial, DCTerms.Location);
        nodeKindIRI(dataset, DCTerms.spatial);

        linkedResourceMustHaveType(dataset, DCTerms.temporal, DCTerms.PeriodOfTime);

        minCount1(dataset, DCTerms.title);
        nodeKindLiteral(dataset, DCTerms.title);

        nodeKindIRI(dataset, DCAT.theme);

        maxCount1(dataset, DCTerms.type);
        linkedResourceMustHaveType(dataset, DCTerms.type, SKOS.Concept);
        nodeKindIRI(dataset, DCTerms.type);

        maxCount1(dataset, OWL.versionInfo);
        nodeKindLiteral(dataset, OWL.versionInfo);

        linkedResourceMustHaveType(dataset, Adms.identifier, Adms.Identifier);

        linkedResourceMustHaveType(dataset, Adms.sample, DCAT.Distribution);

        linkedResourceMustHaveType(dataset, DCAT.contactPoint, VCARD4.Kind);

        linkedResourceMustHaveType(dataset, DCAT.distribution, DCAT.Distribution);

        linkedResourceMustHaveType(dataset, DCAT.landingPage, FOAF.Document);

        linkedResourceMustHaveType(dataset, DCAT.theme, SKOS.Concept);

        linkedResourceMustHaveType(dataset, FOAF.page, FOAF.Document);
    }

    void datatype(Resource resource, Property property, Resource datatype) {
        nodeKindLiteral(resource, property);
        final StmtIterator it = resource.listProperties(property);
        while (it.hasNext()) {
            Statement statement = it.next();
            if (!datatype.equals(statement.getObject().asLiteral().getDatatype())) {
                validationResults.add(new Violation(resource, property.getLocalName() + " must be a literal of type " + datatype.getURI()));
            }
        }
    }

    public void validateDistribution(Resource distribution) {
        nodeKindBlankNodeOrIRI(distribution, DCTerms.conformsTo);

        nodeKindIRI(distribution, DCTerms.description);

        maxCount1(distribution, DCTerms.format);
        linkedResourceMustHaveType(distribution, DCTerms.format, DCTerms.MediaTypeOrExtent);
        nodeKindIRI(distribution, DCTerms.format);

        maxCount1(distribution, DCTerms.issued);
        literalWithTypeDateOrDateTime(distribution, DCTerms.issued);

        linkedResourceMustHaveType(distribution, DCTerms.language, DCTerms.LinguisticSystem);

        maxCount1(distribution, DCTerms.license);
        linkedResourceMustHaveType(distribution, DCTerms.rights, DCTerms.LicenseDocument);

        maxCount1(distribution, DCTerms.modified);
        literalWithTypeDateOrDateTime(distribution, DCTerms.modified);

        maxCount1(distribution, DCTerms.rights);
        linkedResourceMustHaveType(distribution, DCTerms.rights, DCTerms.RightsStatement);

        nodeKindLiteral(distribution, DCTerms.title);

        maxCount1(distribution, SPDX.checksum);
        linkedResourceMustHaveType(distribution, SPDX.checksum, SPDX.Checksum);

        maxCount1(distribution, Adms.status);
        linkedResourceMustHaveType(distribution, Adms.status, SKOS.Concept);
        nodeKindIRI(distribution, Adms.status);

        nodeKindIRI(distribution, DCAT.accessURL);
        minCount1(distribution, DCAT.accessURL);

        datatype(distribution, DCAT.byteSize, XSD.decimal);
        maxCount1(distribution, DCAT.byteSize);

        nodeKindIRI(distribution, DCAT.downloadURL);

        maxCount1(distribution, DCAT.mediaType);
        linkedResourceMustHaveType(distribution, DCAT.mediaType, DCTerms.MediaTypeOrExtent);

        linkedResourceMustHaveType(distribution, FOAF.page, FOAF.Document);

    }

    public void validateCatalog(Resource catalog) {
        minCount1(catalog, DCTerms.description);
        nodeKindLiteral(catalog, DCTerms.description);

        linkedResourceMustHaveType(catalog, DCTerms.hasPart, DCAT.Catalog);

        maxCount1(catalog, DCTerms.isPartOf);
        linkedResourceMustHaveType(catalog, DCTerms.isPartOf, DCAT.Catalog);

        maxCount1(catalog, DCTerms.issued);
        literalWithTypeDateOrDateTime(catalog, DCTerms.issued);

        nodeKindIRI(catalog, DCTerms.language);

        maxCount1(catalog, DCTerms.license);
        linkedResourceMustHaveType(catalog, DCTerms.license, DCTerms.LicenseDocument);

        maxCount1(catalog, DCTerms.modified);
        literalWithTypeDateOrDateTime(catalog, DCTerms.modified);

        minCount1(catalog, DCTerms.publisher);
        maxCount1(catalog, DCTerms.publisher);
        nodeKindIRI(catalog, DCTerms.publisher);

        maxCount1(catalog, DCTerms.rights);
        linkedResourceMustHaveType(catalog, DCTerms.rights, DCTerms.RightsStatement);

        nodeKindIRI(catalog, DCTerms.spatial);

        minCount1(catalog, DCTerms.title);
        nodeKindLiteral(catalog, DCTerms.title);

        minCount1(catalog, DCAT.dataset);
        linkedResourceMustHaveType(catalog, DCAT.dataset, DCAT.Dataset);

        linkedResourceMustHaveType(catalog, DCAT.record, DCAT.CatalogRecord);

        linkMustBeAResource(catalog, DCAT.themeTaxonomy);
        nodeKindIRI(catalog, DCAT.themeTaxonomy);

        maxCount1(catalog, FOAF.homepage);
        linkedResourceMustHaveType(catalog, FOAF.homepage, FOAF.Document);

    }

    private void nodeKindIRI(Resource resource, Property property) {
        final StmtIterator it = resource.listProperties(property);
        while (it.hasNext()) {
            final Statement statement = it.next();
            if (statement.getObject().isAnon()) {
                validationResults.add(new Violation(resource, property.getLocalName() + " must not link to an anonymous resource"));
            }
            if (statement.getObject().isLiteral()) {
                validationResults.add(new Violation(resource, property.getLocalName() + " must not link to a literal"));
            }
        }
    }

    private void nodeKindBlankNodeOrIRI(Resource resource, Property property) {
        final StmtIterator it = resource.listProperties(property);
        while (it.hasNext()) {
            final Statement statement = it.next();
            if (statement.getObject().isLiteral()) {
                validationResults.add(new Violation(resource, property.getLocalName() + " must not link to a literal"));
            }
        }
    }

    private void nodeKindLiteral(Resource resource, Property property) {
        final StmtIterator it = resource.listProperties(property);
        while (it.hasNext()) {
            final Statement statement = it.next();
            if (!statement.getObject().isLiteral()) {
                validationResults.add(new Violation(resource, property.getLocalName() + " must not link to an anonymous resource"));
            }
        }
    }

    /**
     * The specified resource must have a property value of type Date or DateTime.
     */
    void literalWithTypeDateOrDateTime(Resource resource, Property property) {
        nodeKindLiteral(resource, property);
        final Statement statement = resource.getProperty(property);

        if (statement != null) {
            final String datatypeURI = statement.getObject().asLiteral().getDatatypeURI();
            if (!("http://www.w3.org/2001/XMLSchema#date".equals(datatypeURI) || "http://www.w3.org/2001/XMLSchema#dateTime".equals(datatypeURI))) {
                validationResults.add(new Violation(resource, property.getLocalName() + " must have a literal of type xsd:date or xsd:dateTime"));
            }
        }
    }

    /**
     * All occurrences of this properties must link to another resource.
     */
    void linkMustBeAResource(Resource resource, Property property) {
        final StmtIterator it = resource.listProperties(property);
        while (it.hasNext()) {
            final Statement statement = it.next();
            if (!statement.getObject().isResource()) {
                validationResults.add(new Violation(resource, property.getLocalName() + " must only link to other resources"));
            }
        }
    }

    /**
     * All occurrences of this properties must link to another resource with a specified type.
     */
    void linkedResourceMustHaveType(Resource resource, Property property, Resource type) {
        final StmtIterator it = resource.listProperties(property);
        while (it.hasNext()) {
            final Statement statement = it.next();
            final boolean hasCorrectType = statement.getObject().isResource()
                    && hasType(statement.getObject().asResource(), type);

            if (!hasCorrectType) {
                validationResults.add(new Violation(resource, property.getLocalName() + " must only link to an instance of " + type.getURI()));
            }
        }
    }

    void minCount1(Resource resource, Property property) {
        if (!resource.hasProperty(property)) {
            validationResults.add(new Violation(resource, "must have at least one " + property.getLocalName()));
        }
    }

    void maxCount1(Resource resource, Property property) {
        final StmtIterator it = resource.listProperties(property);
        int count = 0;
        while (it.hasNext()) {
            it.nextStatement();
            count++;
        }
        if (count > 1) {
            validationResults.add(new Violation(resource, "must have at most one " + property.getLocalName()));
        }
    }

}
