# DCAT-AP.de Statistiken

Dieses Programm berechnet aus dem Datenbestand von GovData eine Übersicht, welche Elemente von DCAT-AP.de momentan verwendet werden.

Man muss es in zwei Schritten ausführen:

1. Herunterladen der Metadaten von GovData: `mvn exec:java -Dexec.mainClass=de.landsh.opendata.DownloadDCATCatalog`
2. Berechnen der Statistik: `mvn exec:java -Dexec.mainClass=de.landsh.opendata.DcatStatistic`

Das Ergebnis ist in der Datei `target/govdata-statistics.csv` zu finden.

Durch Anpassen der `baseURL` in der Datei `src/main/java/de/landsh/opendata/DownloadDCATCatalog.java` lassen sich auch andere Datenbestände als GovData untersuchen.