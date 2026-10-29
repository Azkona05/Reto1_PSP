module com.example {
    requires javafx.controls;
    requires javafx.fxml;
    opens com.example to javafx.fxml;
    requires javafx.graphics;
    opens client.controller to javafx.fxml;
    exports com.example;
}
