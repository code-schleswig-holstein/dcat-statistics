package de.landsh.opendata.validator;

import org.apache.jena.rdf.model.Resource;

/**
 * A warning validation result.
 */
public class Warning extends ValidationResult {

    public Warning(Resource resource, String message) {
        super(resource, message);
    }
}
