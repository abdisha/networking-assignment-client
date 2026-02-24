module com.assignment.networkingassignmentclient {
    requires java.desktop;
    requires javafx.graphics;
    requires javafx.controls;
    requires webcam.capture;


    opens com.assignment.networkingassignmentclient to javafx.fxml;
    exports com.assignment.networkingassignmentclient;
}