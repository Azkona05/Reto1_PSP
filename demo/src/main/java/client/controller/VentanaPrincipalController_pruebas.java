package client.controller;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import server.model.Mensaje;

public class VentanaPrincipalController_pruebas {

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
    private TextArea txtAreaChat;

    private volatile Socket socket;
    private volatile ObjectOutputStream out;
    private String nombreUsuario;

    @FXML
    private void initialize() {
        btnDesconectar.setDisable(true);
        btnEnviar.setDisable(true);
        lblPara.setDisable(true);
        txtFieldDestinatario.setDisable(true);
        chckBoxPrivado.setOnAction(event -> {
            boolean privado = chckBoxPrivado.isSelected();
            lblPara.setDisable(!privado);
            txtFieldDestinatario.setDisable(!privado);
        });
        txtFieldMensaje.setOnAction(event -> handleEnviar());
    }

    @FXML
    private void handleConectar() {
        String host = txtFieldIp.getText().trim();
        if (host.isEmpty()) {
            host = "localhost";
        }

        int puerto;
        try {
            puerto = Integer.parseInt(txtFieldPuerto.getText().trim());
            if (puerto < 1 || puerto > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            mostrar("[ERROR] Introduce un puerto válido entre 1 y 65535.");
            return;
        }

        String usuario = txtFieldUsuario.getText().trim();
        if (usuario.isEmpty() || usuario.contains(" ")) {
            mostrar("[ERROR] El nombre de usuario no puede estar vacío ni contener espacios.");
            return;
        }

        btnConectar.setDisable(true);
        mostrar("[SISTEMA] Conectando con " + host + ":" + puerto + "...");
        conectarEnSegundoPlano(host, puerto, usuario);
    }

    private void conectarEnSegundoPlano(String host, int puerto, String usuario) {
        Thread conexion = new Thread(() -> {
            Socket nuevoSocket = null;
            try {
                nuevoSocket = new Socket(host, puerto);
                ObjectOutputStream nuevoOut = new ObjectOutputStream(nuevoSocket.getOutputStream());
                nuevoOut.flush();
                ObjectInputStream nuevoIn = new ObjectInputStream(nuevoSocket.getInputStream());

                nuevoOut.writeObject(new Mensaje(Mensaje.Tipo.LOGIN, usuario, null, null));
                nuevoOut.flush();
                nuevoOut.reset();

                Socket socketConectado = nuevoSocket;
                socket = socketConectado;
                out = nuevoOut;
                nombreUsuario = usuario;

                Platform.runLater(() -> {
                    if (socket == socketConectado) {
                        btnDesconectar.setDisable(false);
                        btnEnviar.setDisable(false);
                        txtFieldIp.setDisable(true);
                        txtFieldPuerto.setDisable(true);
                        txtFieldUsuario.setDisable(true);
                        mostrar("[SISTEMA] Conectado como " + usuario + ".");
                    }
                });

                Thread receptor = new Thread(
                        () -> recibirMensajes(socketConectado, nuevoIn),
                        "cliente-receptor-" + usuario);
                receptor.setDaemon(true);
                receptor.start();
            } catch (IOException e) {
                cerrarSocket(nuevoSocket);
                Platform.runLater(() -> {
                    btnConectar.setDisable(false);
                    mostrar("[ERROR] No se pudo conectar: " + e.getMessage());
                });
            }
        }, "cliente-conexion");
        conexion.setDaemon(true);
        conexion.start();
    }

    @FXML
    private void handleEnviar() {
        String texto = txtFieldMensaje.getText().trim();
        if (texto.isEmpty()) {
            return;
        }

        Mensaje mensaje;
        if (chckBoxPrivado.isSelected()) {
            String destinatario = txtFieldDestinatario.getText().trim();
            if (destinatario.isEmpty()) {
                mostrar("[ERROR] Escribe el nombre del destinatario.");
                return;
            }
            mensaje = new Mensaje(Mensaje.Tipo.PRIVADO, nombreUsuario, destinatario, texto);
        } else {
            mensaje = new Mensaje(Mensaje.Tipo.PUBLICO, nombreUsuario, null, texto);
        }

        try {
            enviar(mensaje);
            mostrar(chckBoxPrivado.isSelected()
                    ? "[PRIVADO a " + mensaje.getDestinatario() + "] " + texto
                    : "[PÚBLICO enviado] " + texto);
            txtFieldMensaje.clear();
        } catch (IOException e) {
            mostrar("[ERROR] No se pudo enviar el mensaje: " + e.getMessage());
        }
    }

    @FXML
    private void handleDesconectar() {
        Socket socketActual = socket;
        ObjectOutputStream salida = out;
        socket = null;
        out = null;

        if (socketActual != null) {
            try {
                if (salida != null) {
                    salida.writeObject(new Mensaje(Mensaje.Tipo.SALIR, nombreUsuario, null, null));
                    salida.flush();
                }
            } catch (IOException e) {
                mostrar("[ERROR] No se pudo enviar la desconexión: " + e.getMessage());
            } finally {
                cerrarSocket(socketActual);
            }
        }
        actualizarDesconexion();
        mostrar("[SISTEMA] Desconectado.");
    }

    private synchronized void enviar(Mensaje mensaje) throws IOException {
        ObjectOutputStream salida = out;
        if (salida == null) {
            throw new IOException("No hay conexión con el servidor.");
        }
        salida.writeObject(mensaje);
        salida.flush();
        salida.reset();
    }

    private void recibirMensajes(Socket socketConectado, ObjectInputStream entrada) {
        boolean respuestaLoginRecibida = false;
        try {
            while (true) {
                Mensaje mensaje = (Mensaje) entrada.readObject();
                if (!respuestaLoginRecibida && mensaje.getTipo() == Mensaje.Tipo.SISTEMA) {
                    respuestaLoginRecibida = true;
                    String texto = mensaje.getTexto();
                    boolean errorLogin = texto != null
                            && (texto.startsWith("Nombre no válido")
                            || texto.startsWith("El nombre '")
                            || texto.startsWith("Servidor lleno."));
                    if (errorLogin) {
                        Platform.runLater(() -> {
                            mostrarMensaje(mensaje);
                            finalizarConexion(socketConectado, "No se pudo iniciar sesión.");
                        });
                        return;
                    }
                }
                Platform.runLater(() -> mostrarMensaje(mensaje));
            }
        } catch (EOFException e) {
            Platform.runLater(() -> finalizarConexion(socketConectado, "El servidor ha cerrado la conexión."));
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            Platform.runLater(() -> finalizarConexion(
                    socketConectado, "Error recibiendo mensajes: " + e.getMessage()));
        }
    }

    private void mostrarMensaje(Mensaje mensaje) {
        switch (mensaje.getTipo()) {
            case PUBLICO:
                mostrar("[PÚBLICO] " + mensaje.getEmisor() + ": " + mensaje.getTexto());
                break;
            case PRIVADO:
                mostrar("[PRIVADO] " + mensaje.getEmisor() + ": " + mensaje.getTexto());
                break;
            case SISTEMA:
                mostrar("[SERVIDOR] " + mensaje.getTexto());
                break;
            default:
                mostrar("[MENSAJE] " + mensaje);
                break;
        }
    }

    private void finalizarConexion(Socket socketConectado, String aviso) {
        if (socket != socketConectado) {
            return;
        }
        socket = null;
        out = null;
        cerrarSocket(socketConectado);
        actualizarDesconexion();
        mostrar("[SISTEMA] " + aviso);
    }

    private void actualizarDesconexion() {
        btnConectar.setDisable(false);
        btnDesconectar.setDisable(true);
        btnEnviar.setDisable(true);
        txtFieldIp.setDisable(false);
        txtFieldPuerto.setDisable(false);
        txtFieldUsuario.setDisable(false);
    }

    private void mostrar(String texto) {
        txtAreaChat.appendText(texto + System.lineSeparator());
    }

    private void cerrarSocket(Socket socketACerrar) {
        if (socketACerrar != null) {
            try {
                socketACerrar.close();
            } catch (IOException e) {
                Platform.runLater(() -> mostrar("[ERROR] No se pudo cerrar el socket: " + e.getMessage()));
            }
        }
    }
}
