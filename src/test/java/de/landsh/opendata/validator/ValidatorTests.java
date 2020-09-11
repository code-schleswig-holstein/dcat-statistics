package de.landsh.opendata.validator;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ValidatorTests {

    public static final String TURTLE = "TURTLE";
    private final Validator validator = new Validator();

    @Test
    public void validate_Catalogue() throws IOException {
        final InputStream is = getClass().getResourceAsStream("/catalogue.ttl");
        validator.parseModel(is, TURTLE);
        boolean result = validator.validate();

        Assert.assertFalse(result);

        Set<String> messages = validator.validationResults.stream().map(ValidationResult::getMessage).collect(Collectors.toSet());
        Assert.assertEquals(4, validator.validationResults.size());

        assertTrue(messages.contains("must have at least one description"));
        assertTrue(messages.contains("must have at least one title"));
        assertTrue(messages.contains("must have at least one publisher"));
        assertTrue(messages.contains("must have at least one dataset"));

    }

    @Test
    public void validate_Catalogue1() throws IOException {
        final InputStream is = getClass().getResourceAsStream("/catalogue-1.ttl");
        validator.parseModel(is, TURTLE);
        boolean result = validator.validate();

        Assert.assertFalse(result);
        final Set<String> messages = validator.validationResults.stream().map(ValidationResult::getMessage).collect(Collectors.toSet());

        assertTrue(messages.contains("must have at most one issued"));
        assertTrue(messages.contains("license must only link to an instance of http://purl.org/dc/terms/LicenseDocument"));
        assertTrue(messages.contains("must have at most one license"));
        assertTrue(messages.contains("must have at most one modified"));
        assertTrue(messages.contains("must have at least one publisher"));
        assertTrue(messages.contains("rights must only link to an instance of http://purl.org/dc/terms/RightsStatement"));
        assertTrue(messages.contains("must have at most one rights"));
        assertTrue(messages.contains("must have at least one dataset"));
        assertTrue(messages.contains("homepage must only link to an instance of http://xmlns.com/foaf/0.1/Document"));
        assertTrue(messages.contains("must have at most one homepage"));
    }

    @Test
    public void hasType() {
        final Model model = ModelFactory.createDefaultModel();
        final Resource myResource = model.createResource();
        myResource.addProperty(RDF.type, DCAT.Catalog);

        assertTrue(Validator.hasType(myResource, DCAT.Catalog));
        Assert.assertFalse(Validator.hasType(myResource, DCTerms.MediaType));
    }

    @Test
    public void validate_DatatypeDisjunction() throws IOException {
        final InputStream is = getClass().getResourceAsStream("/datatype-disjunction.ttl");
        validator.parseModel(is, TURTLE);
        boolean result = validator.validate();

        Assert.assertFalse(result);
        final Set<String> messages = validator.validationResults.stream().map(ValidationResult::getMessage).collect(Collectors.toSet());

        assertTrue(messages.contains("issued must have a literal of type xsd:date or xsd:dateTime"));
        assertTrue(messages.contains("publisher must not link to an anonymous resource"));
        assertTrue(messages.contains("must have at least one dataset"));

    }

    @Test
    public void validate_CatalogueOptional() throws IOException {
        final InputStream is = getClass().getResourceAsStream("/catalogue-optional.ttl");
        validator.parseModel(is, TURTLE);
        boolean result = validator.validate();

        Assert.assertFalse(result);
        final Set<String> messages = validator.validationResults.stream().map(ValidationResult::getMessage).collect(Collectors.toSet());

        assertEquals(4, messages.size());
        assertTrue(messages.contains("must have at least one dataset"));
        assertTrue(messages.contains("must have at least one title"));
        assertTrue(messages.contains("must have at least one publisher"));
        assertTrue(messages.contains("must have at least one description"));

    }

}
