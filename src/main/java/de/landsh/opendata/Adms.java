package de.landsh.opendata;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class Adms {
    public static final Resource NAMESPACE;
    public static final String NS = "http://www.w3.org/ns/adms#";
    public static final Property status;
    public static Property identifier;
    public static Property sample;
    public static Resource Identifier;
    private static final Model m_model = ModelFactory.createDefaultModel();

    static {
        NAMESPACE = m_model.createResource(NS);
        status = m_model.createProperty(NS, "status");
        sample = m_model.createProperty(NS, "sample");
        identifier = m_model.createProperty(NS, "identifier");
        Identifier = m_model.createResource(NS + "Identifier");

    }

    ;

    private Adms() {
    }
}
