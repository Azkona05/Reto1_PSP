module com.example {
    requires javafx.controls;
    requires javafx.fxml;
    opens com.example to javafx.fxml;
    opens controller to javafx.fxml;
    exports com.example;
}
