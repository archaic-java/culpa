module work.archaic.culpa.test {
    requires work.archaic.culpa;
    requires work.archaic.service.catalog;
    requires work.archaic.service.catalog.test;
    uses work.archaic.service.logging.v03.Log;
    exports work.archaic.culpa.test to work.archaic.minau;
}
