module work.archaic.culpa {
    requires work.archaic.service.catalog;
    exports work.archaic.culpa;
    provides work.archaic.service.logging.v03.Log with work.archaic.culpa.Culpa;
}
