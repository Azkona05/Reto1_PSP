package controller;

import java.io.IOException;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import cliente.*;

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

    private Cliente cliente;

    @FXML
    public void initialize() {

    }

    @FXML
    void conectarAlServidor(ActionEvent event) {
        String ip = txtFieldIp.getText().trim();
        String strPuerto = txtFieldPuerto.getText().trim();
        String usuario = txtFieldUsuario.getText().trim();

        if (ip.isEmpty() || strPuerto.isEmpty() || usuario.isEmpty()) {
            System.err.println("Por favor complete todos los campos.");
            return;
        }

        int puerto;
        try {
            puerto = Integer.parseInt(strPuerto);
        } catch (NumberFormatException e) {
            System.err.println("El puerto debe ser un número válido.");
            return;
        }

        new Thread(() -> {
            try {
                cliente.conectar(ip, puerto, usuario);
                System.out.println("Conexión establecida exitosamente como: " + usuario);
            } catch (IOException e) {
                System.err.println("Error al conectar: " + e.getMessage());
            }
        }).start();
    }

    @FXML
    void desconectar(ActionEvent event) {
        new Thread(() -> {
                cliente.desconectar();
                System.out.println("Cliente desconectado");
        }).start();
    }
}
