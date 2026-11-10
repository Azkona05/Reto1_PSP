/**
 * Chat cliente-servidor con sockets e hilos.
 * <p>
 * Contiene el cliente JavaFX, el servidor multihilo y el protocolo compartido
 * bajo el paquete raíz {@code com.example}.
 */
module com.example {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;
    opens com.example.client.controller to javafx.fxml;
    exports com.example.client;
}
