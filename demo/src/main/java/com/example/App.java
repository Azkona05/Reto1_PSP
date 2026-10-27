package com.example;

import java.io.IOException;

import controller.VentanaPrincipalController;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * JavaFX App
 * <p>
 * Punto de entrada de la aplicación cliente del chat. Carga la ventana
 * principal desde el FXML y se asegura de cerrar la conexión al salir.
 *
 * @author Azkona05, Hodei Torres, Joel Mamani
 * @version 1.0
 */
public class App extends Application {

    /**
     * Carga la interfaz, registra el cierre de la conexión al cerrar la ventana
     * y la muestra.
     *
     * @param stage ventana principal proporcionada por JavaFX
     * @throws IOException si no se puede cargar el archivo FXML
     */
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("VentanaPrincipal.fxml"));
        Parent root = fxmlLoader.load();

        // Necesitamos el controller para cerrar la conexión al salir
        final VentanaPrincipalController controller = fxmlLoader.getController();
        stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                controller.cerrar();
            }
        });

        Scene scene = new Scene(root, 800, 600);
        stage.setTitle("Chat");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Método principal: lanza la aplicación JavaFX.
     *
     * @param args argumentos de la línea de comandos (no se usan)
     */
    public static void main(String[] args) {
        launch();
    }
}
