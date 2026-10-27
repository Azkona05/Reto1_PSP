package controller;

import cliente.Cliente;
import cliente.ClienteListener;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import model.Mensaje;

/**
 * Controlador de la ventana principal del chat.
 * <p>
 * Gestiona los botones de conectar, desconectar y enviar, y muestra los
 * mensajes recibidos. Implementa {@link ClienteListener} para que el
 * {@link Cliente} le avise cuando llega un mensaje o se pierde la conexión.
 * <p>
 * Las operaciones que pueden bloquear (conectar) se hacen en otro hilo, y los
 * cambios en la interfaz que vienen de otros hilos se pasan al hilo de JavaFX
 * con {@link Platform#runLater(Runnable)}.
 *
 * @author An Azkona
 * @version 1.0
 */
public class VentanaPrincipalController implements ClienteListener {

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
    private Label mensajeConectado;
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
    private VBox contenedorMensajes;

    /**
     * Cliente que gestiona la conexión con el servidor.
     */
    private Cliente cliente;

    /**
     * Lo llama JavaFX al cargar el FXML. Crea el cliente, se registra como
     * listener y deja la interfaz en estado desconectado.
     */
    @FXML
    public void initialize() {
        cliente = new Cliente();
        cliente.setListener(this);
        estadoDesconectado();
    }

    // ---------- Callbacks de ClienteListener (se ejecutan en HiloLectura) ----------
    /**
     * Muestra en pantalla un mensaje recibido del servidor.
     *
     * @param m mensaje recibido
     */
    @Override
    public void mensajeRecibido(final Mensaje m) {
        // Hay que pasar al hilo de JavaFX para tocar la interfaz
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                agregarMensaje(formatear(m));
            }
        });
    }

    /**
     * Informa de que se ha perdido la conexión y vuelve al estado desconectado.
     */
    @Override
    public void conexionPerdida() {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                agregarMensaje("** Conexión perdida con el servidor **");
                estadoDesconectado();
            }
        });
    }

    /**
     * Actualiza la interfaz cuando el cliente consigue conectar.
     *
     * @param usuario nombre con el que se ha conectado
     */
    @Override
    public void conexionEstablecida(final String usuario) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                estadoConectado(usuario);
                agregarMensaje("Conectado como: " + usuario);
            }
        });
    }

    /**
     * Muestra el error cuando el cliente no consigue conectar.
     *
     * @param mensajeError descripción del error
     */
    @Override
    public void errorConexion(final String mensajeError) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                agregarMensaje("Error al conectar: " + mensajeError);
                estadoDesconectado();
            }
        });
    }

    // ---------- Acciones de los botones ----------
    /**
     * Valida los campos IP, puerto y usuario y conecta con el servidor en un
     * hilo aparte para no congelar la interfaz.
     *
     * @param event evento del botón Conectar
     */
    @FXML
    void conectarAlServidor(ActionEvent event) {
        final String ip = txtFieldIp.getText().trim();
        String strPuerto = txtFieldPuerto.getText().trim();
        final String usuario = txtFieldUsuario.getText().trim();

        if (ip.isEmpty() || strPuerto.isEmpty() || usuario.isEmpty()) {
            agregarMensaje("Por favor complete todos los campos.");
            return;
        }

        final int puerto;
        try {
            puerto = Integer.parseInt(strPuerto);
        } catch (NumberFormatException e) {
            agregarMensaje("El puerto debe ser un número válido.");
            return;
        }

        btnConectar.setDisable(true);
        // No bloquea: conecta en HiloLectura y el resultado llega por
        // conexionEstablecida() o errorConexion()
        cliente.conectar(ip, puerto, usuario);
    }

    /**
     * Desconecta del servidor a petición del usuario.
     *
     * @param event evento del botón Desconectar
     */
    @FXML
    void desconectar(ActionEvent event) {
        cliente.desconectar();
        estadoDesconectado();
        agregarMensaje("Cliente desconectado");
    }

    /**
     * Envía el texto escrito como mensaje público o, si está marcada la casilla
     * Privado, como mensaje privado al destinatario indicado.
     *
     * @param event evento del botón Enviar
     */
    @FXML
    void enviarMensaje(ActionEvent event) {
        String texto = txtFieldMensaje.getText().trim();
        if (texto.isEmpty() || !cliente.isConectado()) {
            return;
        }

        if (chckBoxPrivado.isSelected()) {
            String dest = txtFieldDestinatario.getText().trim();
            if (dest.isEmpty()) {
                agregarMensaje("Indica un destinatario para el mensaje privado.");
                return;
            }
            cliente.enviarPrivado(dest, texto);
            agregarMensaje("[Privado para " + dest + "] " + texto);
        } else {
            cliente.enviarPublico(texto);
            agregarMensaje("Yo: " + texto); // el servidor no te reenvía tus públicos
        }
        txtFieldMensaje.clear();
    }

    /**
     * Cierra la conexión. Se llama desde {@code App} al cerrar la ventana.
     */
    public void cerrar() {
        cliente.desconectar();
    }

    // ---------- Utilidades de interfaz ----------
    /**
     * Convierte un mensaje en el texto que se mostrará según su tipo.
     *
     * @param m mensaje a formatear
     * @return texto listo para mostrar
     */
    private String formatear(Mensaje m) {
        switch (m.getTipo()) {
            case PUBLICO:
                return m.getEmisor() + ": " + m.getTexto();
            case PRIVADO:
                return "[Privado de " + m.getEmisor() + "] " + m.getTexto();
            case SISTEMA:
                return "** " + m.getTexto() + " **";
            default:
                return m.getTexto();
        }
    }

    /**
     * Añade una línea de texto al contenedor de mensajes. Debe llamarse desde
     * el hilo de JavaFX.
     *
     * @param texto texto a mostrar
     */
    private void agregarMensaje(String texto) {
        Label lbl = new Label(texto);
        lbl.setWrapText(true);
        contenedorMensajes.getChildren().add(lbl);
    }

    /**
     * Activa o desactiva los controles para el estado conectado.
     *
     * @param usuario nombre del usuario conectado
     */
    private void estadoConectado(String usuario) {
        btnConectar.setDisable(true);
        btnDesconectar.setDisable(false);
        btnEnviar.setDisable(false);
        txtFieldMensaje.setDisable(false);
        mensajeConectado.setText("Conectado como " + usuario);
    }

    /**
     * Activa o desactiva los controles para el estado desconectado.
     */
    private void estadoDesconectado() {
        btnConectar.setDisable(false);
        btnDesconectar.setDisable(true);
        btnEnviar.setDisable(true);
        txtFieldMensaje.setDisable(true);
        mensajeConectado.setText("Desconectado");
    }
}
