package com.example.client.comunicacion;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.RejectedExecutionException;

import com.example.common.Mensaje;

/**
 * Cliente del chat. Gestiona la conexión con el servidor mediante un
 * {@link Socket} y el intercambio de objetos {@link Mensaje}.
 * <p>
 * El envío se delega a {@link EscritorCliente}, que mantiene el orden de los
 * mensajes, y la recepción se realiza en {@link LectorCliente}. Los mensajes
 * recibidos se notifican a un {@link ClienteListener}.
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
    /** Flujo de salida que se cierra junto con el resto de la conexión. */
    private ObjectOutputStream out;
    /**
     * Flujo para recibir objetos {@link Mensaje} del servidor.
     */
    private ObjectInputStream in;
    /**
     * Hilo encargado de leer los mensajes que llegan del servidor.
     */
    private LectorCliente hiloLectura;
    /** Escritor que serializa los mensajes salientes. */
    private EscritorCliente escritor;
    /**
     * Indica si el cliente está conectado. Solo se accede dentro de bloques
     * synchronized.
     */
    private boolean conectado = false;
    /**
     * Indica que la conexión está en curso, antes de la aceptación del
     * servidor.
     */
    private boolean conectando = false;
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
     * Crea un cliente desconectado. Para conectarlo hay que llamar a
     * {@link #conectar(String, int, String)}.
     */
    public Cliente() {
    }

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
     * No bloquea: arranca {@link LectorCliente}, que conecta y después se queda
     * leyendo. El resultado se notifica al listener mediante
     * {@link ClienteListener#conexionEstablecida(String)} o
     * {@link ClienteListener#errorConexion(String)}.
     *
     * @param host dirección IP o nombre del servidor
     * @param puerto puerto en el que escucha el servidor
     * @param usuario nombre de usuario con el que identificarse
     */
    public synchronized void conectar(String host, int puerto, String usuario) {
        if (conectando || conectado) {
            return;
        }
        this.usuario = usuario;
        conectando = true;
        hiloLectura = new LectorCliente(this, host, puerto, usuario);
        hiloLectura.start();
    }

    /**
     * Abre el socket y los flujos y envía el mensaje de LOGIN. Lo invoca
     * {@link LectorCliente} al arrancar.
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
    ObjectInputStream establecerConexion(String host, int puerto, String usuario) throws IOException {
        Socket nuevaSocket = new Socket();
        synchronized (this) {
            if (!conectando) {
                nuevaSocket.close();
                throw new IOException("Conexión cancelada.");
            }
            socket = nuevaSocket;
        }
        nuevaSocket.connect(new InetSocketAddress(host, puerto));
        ObjectOutputStream nuevoOut = new ObjectOutputStream(nuevaSocket.getOutputStream());
        nuevoOut.flush();
        ObjectInputStream nuevoIn = new ObjectInputStream(nuevaSocket.getInputStream());

        synchronized (this) {
            if (!conectando) {
                nuevaSocket.close();
                throw new IOException("Conexión cancelada.");
            }
            out = nuevoOut;
            in = nuevoIn;
            escritor = new EscritorCliente(nuevoOut, this::cerrarRecursos);
            escritor.enviar(new Mensaje(Mensaje.Tipo.LOGIN, usuario, null, null));
        }
        return nuevoIn;
    }

    /**
     * Marca la conexión como activa únicamente después de la confirmación del
     * servidor.
     *
     * @return {@code true} si la conexión sigue pendiente y pudo confirmarse
     */
    synchronized boolean confirmarConexion() {
        if (!conectando) {
            return false;
        }
        conectando = false;
        conectado = true;
        return true;
    }

    /**
     * Envía un mensaje público que recibirán todos los usuarios conectados.
     *
     * @param texto contenido del mensaje
     */
    public void enviarMensajePublico(String texto) {
        enviarMensaje(new Mensaje(Mensaje.Tipo.PUBLICO, usuario, null, texto));
    }

    /**
     * Envía un mensaje privado a un único usuario.
     *
     * @param destinatario nombre del usuario que recibirá el mensaje
     * @param texto contenido del mensaje
     */
    public void enviarMensajePrivado(String destinatario, String texto) {
        enviarMensaje(new Mensaje(Mensaje.Tipo.PRIVADO, usuario, destinatario, texto));
    }

    /**
     * Solicita al servidor la lista de usuarios conectados.
     */
    public void solicitarListaUsuarios() {
        enviarMensaje(new Mensaje(Mensaje.Tipo.LISTA, usuario, null, null));
    }

    /**
     * Encola un mensaje para que lo escriba el hilo dedicado. El ejecutor
     * serializa las escrituras porque {@link ObjectOutputStream} no es
     * thread-safe; si falla un envío, se cierran los recursos y el hilo de
     * lectura detectará el cierre.
     *
     * @param m mensaje que se enviará
     */
    private void enviarMensaje(Mensaje m) {
        synchronized (this) {
            if (!conectado || escritor == null) {
                return;
            }
            try {
                escritor.enviar(m);
            } catch (RejectedExecutionException e) {
                cerrarRecursos();
            }
        }
    }

    /**
     * Desconecta voluntariamente del servidor: envía un mensaje SALIR y cierra
     * el socket y los flujos.
     */
    public void desconectar() {
        synchronized (this) {
            if (conectando) {
                cerrarRecursos();
                return;
            }
            if (!conectado) {
                return;
            }
            conectado = false;
            EscritorCliente escritorActual = escritor;
            try {
                escritorActual.enviarAntesDeCerrar(
                        new Mensaje(Mensaje.Tipo.SALIR, usuario, null, null),
                        this::cerrarRecursos);
            } catch (RejectedExecutionException e) {
                cerrarRecursos();
            }
        }
    }

    // ---- Llamados desde LectorCliente ----
    /**
     * Notifica al listener que la conexión se ha establecido.
     *
     * @param usuario nombre con el que se ha conectado
     */
    void notificarConexionEstablecida(String usuario) {
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
        if (listener != null && isConectando()) {
            listener.errorConexion(mensajeError);
        }
    }

    /**
     * Indica si hay una conexión en curso que el servidor aún no ha
     * confirmado.
     *
     * @return {@code true} si se está conectando
     */
    private synchronized boolean isConectando() {
        return conectando;
    }

    /**
     * Notifica al listener que ha llegado un mensaje. Lo invoca
     * {@link LectorCliente}, por lo que se ejecuta en el hilo de lectura.
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
    void finalizarLectura() {
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
        conectando = false;
        if (escritor != null) {
            escritor.cerrar();
            escritor = null;
        }
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
