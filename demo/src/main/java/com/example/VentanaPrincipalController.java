package com.example;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class VentanaPrincipalController {

    @FXML
    private HBox hBoxHeader;

    @FXML
    private Label lblIp;

    @FXML
    private TextField txtFieldIp;

    @FXML
    private Label lblPuerto;

    @FXML
    private TextField txtFieldPuerto;

    @FXML
    private Label lblUsuario;

    @FXML
    private TextField txtFieldUsuario;

    @FXML
    private Button btnConectar;

    @FXML
    private Button btnDesconectar;

    @FXML
    private HBox hBoxButton;

    @FXML
    private CheckBox chckBoxPrivado;

    @FXML
    private Label lblPara;

    @FXML
    private TextField txtFieldDestinatario;

    @FXML
    private TextField txtFieldMensaje;

    @FXML
    private Button btnEnviar;

    @FXML
    void initialize() {
        // Código que se ejecuta automáticamente al cargar la interfaz
    }

    // Ejemplo de método para asignar a un botón (ej: On Action="#handleConectar")
    @FXML
    void handleConectar(ActionEvent event) {
        String ip = txtFieldIp.getText();
        System.out.println("Conectando a: " + ip);
    }
}