package client;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Client_Prueba extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                Client_Prueba.class.getResource("/com/example/VentanaPrincipal_Pruebas.fxml"));
        Scene scene = new Scene(loader.load());
        stage.setTitle("Chat multiusuario - Cliente de prueba");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
