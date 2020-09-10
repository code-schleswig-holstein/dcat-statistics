package de.landsh.opendata.validator;


import org.apache.jena.rdf.model.Resource;

/**
 * A violation validation result.
 */
public class Violation extends ValidationResult {


    public Violation(Resource resource, String message) {
        super(resource, message);
    }
}
