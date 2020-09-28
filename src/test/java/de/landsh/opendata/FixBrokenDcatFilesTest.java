package de.landsh.opendata;

import org.apache.commons.io.FileUtils;
import org.apache.jena.rdf.model.*;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FixBrokenDcatFilesTest {
    @Test
    public void fixFile() throws IOException {
        final File file = new File(getClass().getResource("/148.ttl").getFile());
        final File tempFile = File.createTempFile("data", "ttl");
        tempFile.deleteOnExit();
        FileUtils.copyFile(file, tempFile);

        FixBrokenDcatFiles.fixFile(tempFile);

        final Model model = ModelFactory.createDefaultModel();
        model.read(new FileReader(tempFile), "", "TTL");

        Set<String> subjects = getSubjectURIs(model);
        assertEquals(3,subjects.size());
        assertTrue(subjects.contains("https://ckan.www.open.nrw.de/dataset/b32de71c-6045-4348-9ca7-31008d9a8ac6"));
        assertTrue(subjects.contains("https://ckan.www.open.nrw.de/dataset/fee85fc5-2522-4234-8f67-726c4848baad/resource/e88e1fa5-6495-4c96-a525-8628fb7c78ee"));
        assertTrue(subjects.contains("https://ckan.www.open.nrw.de/dataset/b3f15c00-8ac0-460f-9788-ed7ff0409357/resource/25cadb30-651f-48e9-8fe0-7664c5c8ceec"));

    }

    private static Set<String> getSubjectURIs(Model model) {
        final Set<String> result = new HashSet<>();
        final ResIterator it = model.listSubjects();
        while (it.hasNext()) {
            Resource resource = it.next();
            if( resource.getURI() != null) {
                   result.add(resource.getURI());
            }
        }
        return result;
    }


}
