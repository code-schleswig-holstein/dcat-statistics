package de.landsh.opendata.validator;

import org.apache.jena.rdf.model.Resource;

/**
 * An informational validation result.
 */
public class Info extends ValidationResult {

    public Info(Resource resource, String message) {
        super(resource, message);
    }
}
