package client.model;

import java.io.IOException;
import java.io.ObjectInputStream;

import server.model.Mensaje;

/**
 * Hilo del cliente que conecta con el servidor y después escucha continuamente
 * los mensajes que envía.
 * <p>
 * Funciona en dos fases:
 * <ol>
 * <li><b>Conexión:</b> abre el socket y envía el LOGIN. Puede tardar si el
 * servidor no responde, por eso se hace en este hilo y no en el de JavaFX. Al
 * terminar avisa de si se ha conectado o no.</li>
 * <li><b>Lectura:</b> se queda bloqueado en
 * {@link ObjectInputStream#readObject()} hasta que llega un {@link Mensaje} y
 * se lo pasa al {@link Cliente}. Cuando el socket se cierra (por el usuario o
 * por el servidor) sale del bucle.</li>
 * </ol>
 *
 * @author Azkona05, Hodei Torres, Joel Mamani
 * @version 1.0
 */
public class HiloLectura extends Thread {

    /**
     * Cliente que gestiona la conexión y al que se notifican los eventos.
     */
    private final Cliente cliente;
    /**
     * Dirección IP o nombre del servidor.
     */
    private final String host;
    /**
     * Puerto en el que escucha el servidor.
     */
    private final int puerto;
    /**
     * Nombre de usuario con el que identificarse.
     */
    private final String usuario;

    /**
     * Crea el hilo.
     *
     * @param cliente cliente que gestiona la conexión
     * @param host dirección IP o nombre del servidor
     * @param puerto puerto del servidor
     * @param usuario nombre de usuario
     */
    public HiloLectura(Cliente cliente, String host, int puerto, String usuario) {
        super("hilo-lectura-cliente");
        this.cliente = cliente;
        this.host = host;
        this.puerto = puerto;
        this.usuario = usuario;
    }

    /**
     * Conecta con el servidor y, si lo consigue, lee mensajes hasta que se
     * cierra la conexión. Al terminar, siempre llama a
     * {@link Cliente#lecturaTerminada()}.
     */
    @Override
    public void run() {
        // ---- Fase 1: conexión ----
        ObjectInputStream in;
        try {
            in = cliente.abrirConexion(host, puerto, usuario);
        } catch (IOException e) {
            cliente.notificarErrorConexion(e.getMessage());
            cliente.lecturaTerminada(); // cierra lo que se haya llegado a abrir
            return;
        }
        cliente.notificarConexion(usuario);

        // ---- Fase 2: lectura ----
        try {
            while (true) {
                Mensaje m = (Mensaje) in.readObject(); // se bloquea hasta que llega algo
                cliente.notificarMensaje(m);
            }
        } catch (IOException | ClassNotFoundException e) {
            // socket cerrado (por nosotros o por el servidor)
        } finally {
            cliente.lecturaTerminada();
        }
    }
}
