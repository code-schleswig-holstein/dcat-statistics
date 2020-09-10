package de.landsh.opendata;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class SPDX {
    public static final String NS = "http://spdx.org/rdf/terms#";
    public static final Resource NAMESPACE;
    public static final Property checksum;
    public static final Resource Checksum;
    private static final Model m_model = ModelFactory.createDefaultModel();

    static {
        NAMESPACE = m_model.createResource(NS);
        checksum = m_model.createProperty(NS, "checksum");
        Checksum = m_model.createResource(NS + "Checksum");
    }

    private SPDX() {
    }

    public static String getURI() {
        return NS;
    }
}
