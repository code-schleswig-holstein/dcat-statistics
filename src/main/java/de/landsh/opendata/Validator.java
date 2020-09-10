package de.landsh.opendata;

import de.landsh.opendata.validator.ValidationResult;
import de.landsh.opendata.validator.Violation;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.DCTerms;

import java.util.HashSet;
import java.util.Set;

public class Validator {

    Set<ValidationResult> validationResults = new HashSet<>();

    public void validateDataset() {

    }

    public void validateCatalog(Resource catalog) {
        mustHaveAtLeastOneLiteral(catalog, DCTerms.description);
        mustHaveAtLeastOne(catalog, DCTerms.hasPart);
        mustHaveAtMostOne(catalog, DCTerms.isPartOf);

        mustHaveAtMostOne(catalog, DCTerms.issued);
        if ( catalog.hasProperty(DCTerms.issued) ) {
            mustHaveLiteralDateOrDateTime(catalog, DCTerms.issued);
        }

        mustHaveAtMostOne(catalog, DCTerms.modified);
        if ( catalog.hasProperty(DCTerms.modified) ) {
            mustHaveLiteralDateOrDateTime(catalog, DCTerms.modified);
        }

        mustHaveAtLeastOneLiteral(catalog, DCTerms.title);

    }

    /**
     * The specified resource must have a property value of type Date or DateTime.
     */
    void mustHaveLiteralDateOrDateTime(Resource resource, Property property) {
        mustHaveAtLeastOneLiteral(resource, property);
        final Statement statement = resource.getProperty(property);
        final String datatypeURI = statement.getObject().asLiteral().getDatatypeURI();
        if (!("http://www.w3.org/2001/XMLSchema#date".equals(datatypeURI) || "http://www.w3.org/2001/XMLSchema#dateTime".equals(datatypeURI))) {
            validationResults.add(new Violation(resource, property.getLocalName() + " must have a literal of type xsd:date or xsd:dateTime"));

        }
    }

    void mustHaveAtLeastOneLiteral(Resource resource, Property property) {
        final Statement statement = resource.getProperty(property);
        if (statement == null || !statement.getObject().isLiteral()) {
            validationResults.add(new Violation(resource, "must have at least one literal " + property.getLocalName()));
        }
    }

    void mustHaveAtLeastOne(Resource resource, Property property) {
        if ( !resource.hasProperty(property)) {
            validationResults.add(new Violation(resource, "must have at least one " + property.getLocalName()));
        }
    }

    void mustHaveAtMostOne(Resource resource, Property property) {
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
