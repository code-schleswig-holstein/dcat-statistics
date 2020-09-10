package de.landsh.opendata.validator;

import org.apache.jena.rdf.model.Resource;

public class ValidationResult {
    Resource resource;
    String message;

    public ValidationResult(Resource resource, String message) {
        this.resource = resource;
        this.message = message;
    }
}
