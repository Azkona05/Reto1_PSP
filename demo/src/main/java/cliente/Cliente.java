package cliente;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import model.Mensaje;

/**
 * Cliente del chat. Gestiona la conexión con el servidor mediante un
 * {@link Socket} y el intercambio de objetos {@link Mensaje}.
 * <p>
 * El envío de mensajes se hace desde el hilo que llama a los métodos públicos
 * (normalmente el hilo de JavaFX), mientras que la recepción se realiza en un
 * hilo independiente ({@link HiloLectura}). Los mensajes recibidos se notifican
 * a un {@link ClienteListener}.
 * <p>
 * El acceso al estado compartido ({@code conectado} y el flujo de salida) se
 * protege con {@code synchronized}, ya que lo usan varios hilos a la vez.
 *
 * @author An Azkona
 * @version 1.0
 */
public class Cliente {

    /**
     * Socket de conexión con el servidor.
     */
    private Socket socket;
    /**
     * Flujo para enviar objetos {@link Mensaje} al servidor.
     */
    private ObjectOutputStream out;
    /**
     * Flujo para recibir objetos {@link Mensaje} del servidor.
     */
    private ObjectInputStream in;
    /**
     * Hilo encargado de leer los mensajes que llegan del servidor.
     */
    private HiloLectura hiloLectura;
    /**
     * Indica si el cliente está conectado. Solo se accede dentro de bloques
     * synchronized.
     */
    private boolean conectado = false;
    /**
     * Nombre de usuario con el que se ha conectado el cliente.
     */
    private String usuario;
    /**
     * Objeto al que se notifican los mensajes recibidos y la pérdida de
     * conexión.
     */
    private ClienteListener listener;

    /**
     * Establece el objeto que será avisado cuando llegue un mensaje o se pierda
     * la conexión.
     *
     * @param listener objeto que implementa {@link ClienteListener}
     */
    public void setListener(ClienteListener listener) {
        this.listener = listener;
    }

    /**
     * Indica si el cliente está conectado al servidor.
     *
     * @return {@code true} si hay una conexión activa, {@code false} en caso
     * contrario
     */
    public synchronized boolean isConectado() {
        return conectado;
    }

    /**
     * Devuelve el nombre de usuario con el que se ha conectado.
     *
     * @return nombre de usuario
     */
    public String getUsuario() {
        return usuario;
    }

    /**
     * Inicia la conexión con el servidor en segundo plano.
     * <p>
     * No bloquea: arranca {@link HiloLectura}, que conecta y después se queda
     * leyendo. El resultado se notifica al listener mediante
     * {@link ClienteListener#conexionEstablecida(String)} o
     * {@link ClienteListener#errorConexion(String)}.
     *
     * @param host dirección IP o nombre del servidor
     * @param puerto puerto en el que escucha el servidor
     * @param usuario nombre de usuario con el que identificarse
     */
    public void conectar(String host, int puerto, String usuario) {
        this.usuario = usuario;
        hiloLectura = new HiloLectura(this, host, puerto, usuario);
        hiloLectura.start();
    }

    /**
     * Abre el socket y los flujos y envía el mensaje de LOGIN. Lo invoca
     * {@link HiloLectura} al arrancar.
     * <p>
     * Los flujos se crean en el mismo orden que en el servidor (primero el de
     * salida con {@code flush()} y luego el de entrada) para evitar que ambos
     * extremos se queden bloqueados esperando la cabecera del otro.
     *
     * @param host dirección IP o nombre del servidor
     * @param puerto puerto en el que escucha el servidor
     * @param usuario nombre de usuario con el que identificarse
     * @return flujo de entrada del que leer los mensajes del servidor
     * @throws IOException si no se puede establecer la conexión
     */
    ObjectInputStream abrirConexion(String host, int puerto, String usuario) throws IOException {
        socket = new Socket(host, puerto);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());

        synchronized (this) {
            conectado = true;
        }

        enviar(new Mensaje(Mensaje.Tipo.LOGIN, usuario, null, null));
        return in;
    }

    /**
     * Envía un mensaje público que recibirán todos los usuarios conectados.
     *
     * @param texto contenido del mensaje
     */
    public void enviarPublico(String texto) {
        enviar(new Mensaje(Mensaje.Tipo.PUBLICO, usuario, null, texto));
    }

    /**
     * Envía un mensaje privado a un único usuario.
     *
     * @param destinatario nombre del usuario que recibirá el mensaje
     * @param texto contenido del mensaje
     */
    public void enviarPrivado(String destinatario, String texto) {
        enviar(new Mensaje(Mensaje.Tipo.PRIVADO, usuario, destinatario, texto));
    }

    /**
     * Solicita al servidor la lista de usuarios conectados.
     */
    public void pedirLista() {
        enviar(new Mensaje(Mensaje.Tipo.LISTA, usuario, null, null));
    }

    /**
     * Envía un mensaje al servidor.
     * <p>
     * Es {@code synchronized} porque {@link ObjectOutputStream} no es
     * thread-safe y varios hilos podrían escribir a la vez. Si falla el envío
     * se cierran los recursos y el hilo de lectura detectará el cierre.
     *
     * @param m mensaje a enviar
     */
    private synchronized void enviar(Mensaje m) {
        if (!conectado || out == null) {
            return;
        }
        try {
            out.writeObject(m);
            out.flush();
            out.reset();
        } catch (IOException e) {
            cerrarRecursos(); // el hilo de lectura detectará el cierre
        }
    }

    /**
     * Desconecta voluntariamente del servidor: envía un mensaje SALIR y cierra
     * el socket y los flujos.
     */
    public void desconectar() {
        if (!isConectado()) {
            return;
        }
        enviar(new Mensaje(Mensaje.Tipo.SALIR, usuario, null, null));
        cerrarRecursos();
    }

    // ---- Llamados desde HiloLectura ----
    /**
     * Notifica al listener que la conexión se ha establecido.
     *
     * @param usuario nombre con el que se ha conectado
     */
    void notificarConexion(String usuario) {
        if (listener != null) {
            listener.conexionEstablecida(usuario);
        }
    }

    /**
     * Notifica al listener que no se ha podido conectar.
     *
     * @param mensajeError descripción del error
     */
    void notificarErrorConexion(String mensajeError) {
        if (listener != null) {
            listener.errorConexion(mensajeError);
        }
    }

    /**
     * Notifica al listener que ha llegado un mensaje. Lo invoca
     * {@link HiloLectura}, por lo que se ejecuta en el hilo de lectura.
     *
     * @param m mensaje recibido del servidor
     */
    void notificarMensaje(Mensaje m) {
        if (listener != null) {
            listener.mensajeRecibido(m);
        }
    }

    /**
     * Se llama cuando el hilo de lectura termina. Si el cliente seguía
     * conectado significa que no fue el usuario quien cerró, sino el servidor,
     * y se avisa al listener de la pérdida de conexión.
     */
    void lecturaTerminada() {
        boolean caidoPorServidor = isConectado(); // si seguía conectado, no cerró el usuario
        cerrarRecursos();
        if (caidoPorServidor && listener != null) {
            listener.conexionPerdida();
        }
    }

    /**
     * Marca el cliente como desconectado y cierra los flujos y el socket. Cada
     * cierre va en su propio bloque try para que, si uno falla, se intenten
     * cerrar igualmente los demás.
     */
    private synchronized void cerrarRecursos() {
        conectado = false;
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignorada) {
        }
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignorada) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignorada) {
        }
    }
}
