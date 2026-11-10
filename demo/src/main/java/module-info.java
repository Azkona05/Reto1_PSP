/**
 * Chat cliente-servidor con sockets e hilos.
 * <p>
 * Contiene el cliente JavaFX ({@code com.example}, {@code client.controller},
 * {@code client.model}) y el servidor multihilo ({@code server},
 * {@code server.model}).
 */
module com.example {
    requires javafx.controls;
    requires javafx.fxml;
    opens com.example to javafx.fxml;
    requires javafx.graphics;
    opens client.controller to javafx.fxml;
    exports com.example;
}
