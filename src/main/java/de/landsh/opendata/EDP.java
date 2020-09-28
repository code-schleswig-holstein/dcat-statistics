package de.landsh.opendata;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class EDP {
    public static final String NS = "https://europeandataportal.eu/voc#";
    public static final Resource NAMESPACE;
    public static final Property isMachineReadable;
    public static final Property isNonProprietary;
    private static final Model m_model = ModelFactory.createDefaultModel();

    static {
        NAMESPACE = m_model.createResource(NS);
        isMachineReadable = m_model.createProperty(NS, "isMachineReadable");
        isNonProprietary = m_model.createProperty(NS, "isNonProprietary");
    }

    private EDP() {
    }

    public static String getURI() {
        return NS;
    }
}
