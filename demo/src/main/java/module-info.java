module com.example {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    opens com.example to javafx.fxml;
    opens client.controller to javafx.fxml;
    exports client to javafx.graphics;
    exports com.example;
}
