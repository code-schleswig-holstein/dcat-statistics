package de.landsh.opendata;

import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * Lädt den kompletten DCAT-AP.de konformen Datenbestand einer CKAN-Instanz herunter
 */
public class DownloadDCATCatalog {

    public static final Logger log = LoggerFactory.getLogger(DownloadDCATCatalog.class);

    public static void main(String[] args) throws DocumentException, IOException {
        final String baseURL = "https://ckan.govdata.de/";

        downloadCatalog(baseURL);
    }

    private static void downloadCatalog(final String baseURL) throws IOException, DocumentException {
        final SAXReader reader = new SAXReader();

        log.info("Downloading RDF data...");

        final HttpClientContext context = HttpClientContext.create();
        final CloseableHttpClient client = HttpClients.createDefault();

        HttpGet httpGet = new HttpGet(baseURL + "catalog.xml");
        HttpResponse response = client.execute(httpGet, context);
        Document document = reader.read(response.getEntity().getContent());

        Node node = document.selectSingleNode("//hydra:PagedCollection/hydra:lastPage");
        int lastPage = NumberUtils.toInt(StringUtils.substringAfterLast(node.getText(), "page="));

        for (int page : ProgressBar.wrap(IntStream.range(1, lastPage).boxed().collect(Collectors.toList()), "Downloading pages")) {

            httpGet = new HttpGet(baseURL + "catalog.ttl?page=" + page);
            response = client.execute(httpGet, context);

            InputStream sourceStream = response.getEntity().getContent();
            File targetFile = new File(String.format("target/%03d.ttl", page));
            FileUtils.copyInputStreamToFile(sourceStream, targetFile);

            sourceStream.close();
        }
        client.close();

    }

}
