module com.assignment.networkingassignmentclient {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.assignment.networkingassignmentclient to javafx.fxml;
    exports com.assignment.networkingassignmentclient;
}