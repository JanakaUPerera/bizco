module com.bizco.client {
    requires com.bizco.common;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires java.net.http;
    requires javafx.controls;
    requires javafx.fxml;
    requires org.controlsfx.controls;

    exports com.bizco.client;
}
