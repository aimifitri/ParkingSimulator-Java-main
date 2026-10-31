module com.parkwise {
    requires transitive javafx.controls;
    requires transitive javafx.graphics;
    requires java.logging;

    exports com.parkwise;
    exports com.parkwise.model;
    exports com.parkwise.view;
    exports com.parkwise.service;
    exports com.parkwise.strategy;
}
