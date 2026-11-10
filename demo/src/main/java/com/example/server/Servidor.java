package com.example.server;

import java.io.EOFException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.example.common.Mensaje;

/**
 * Arranca el servidor, acepta conexiones y gestiona los hilos de los clientes.
 * El procesamiento de usuarios y mensajes se delega en {@link GestorChat}.
 *
 * @author Hodei
 * @version 1.0
 */
public class Servidor {

    /** Puerto TCP en el que escucha el servidor. */
    private static final int PUERTO = 5005;
    /** Número máximo de conexiones simultáneas, incluidas las no identificadas. */
    private static final int MAX_USERS = 5;
    /** Intervalo, en segundos, entre informes de estado. */
    private static final int INTERVALO_ESTADO_SEG = 10;
    /** Nombre del archivo de registro del servidor. */
    private static final String ARCHIVO_LOG = "servidor.log";
    /** Cerrojo que protege el contador de conexiones activas. */
    private static final Object LOCK = new Object();
    /** Cerrojo que evita escrituras simultáneas en consola y archivo de log. */
    private static final Object LOCK_LOG = new Object();
    /** Gestor compartido por los hilos de conexión. */
    private static final GestorChat GESTOR_CHAT = new GestorChat();
    /** Instante de inicio del servidor, expresado en milisegundos. */
    private static final long INICIO = System.currentTimeMillis();
    /** Número de conexiones que tienen reservada una plaza. */
    private static int conexionesActivas;
    /** Escritor del archivo de log, si se pudo abrir. */
    private static PrintWriter logArchivo;

    /** Evita crear instancias de esta clase, que solo contiene miembros estáticos. */
    private Servidor() {
    }

    /**
     * Inicializa el registro, inicia el hilo de estado y comienza a aceptar
     * conexiones de clientes. Se crea un hilo independiente para cada conexión
     * que obtiene una plaza.
     *
     * @param args argumentos de línea de comandos (no se utilizan)
     */
    public static void main(String[] args) {
        try {
            logArchivo = new PrintWriter(new FileWriter(ARCHIVO_LOG, true), true);
        } catch (IOException e) {
            System.err.println("No se pudo abrir el log: " + e.getMessage());
        }

        iniciarHiloEstado();
        log("SERVIDOR", "Escuchando por el puerto " + PUERTO + " (máx. " + MAX_USERS + " usuarios)");

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket socketCliente = serverSocket.accept();
                if (!reservarPlaza()) {
                    rechazar(socketCliente);
                    continue;
                }
                new Thread(new ManejadorCliente(socketCliente)).start();
            }
        } catch (IOException e) {
            log("ERROR", "Fallo en el servidor: " + e.getMessage());
        } finally {
            cerrarLog();
        }
    }

    /**
     * Reserva una plaza para una conexión nueva si no se ha alcanzado el
     * máximo permitido.
     *
     * @return {@code true} si se reservó la plaza; {@code false} si el servidor
     *         está lleno
     */
    private static boolean reservarPlaza() {
        synchronized (LOCK) {
            if (conexionesActivas >= MAX_USERS) {
                return false;
            }
            conexionesActivas++;
            return true;
        }
    }

    /** Libera la plaza reservada por un cliente al terminar su hilo. */
    private static void liberarPlaza() {
        synchronized (LOCK) {
            conexionesActivas--;
        }
    }

    /**
     * Envía al socket un error de servidor lleno y después lo cierra.
     *
     * @param socket socket de la conexión rechazada
     */
    private static void rechazar(Socket socket) {
        log("RECHAZADO", "Conexión desde " + socket.getInetAddress() + " (servidor lleno)");
        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(new Mensaje(Mensaje.Tipo.LOGIN_ERROR, "SERVIDOR", null,
                    "Servidor lleno. Inténtalo más tarde."));
            out.flush();
        } catch (IOException ignorada) {
        } finally {
            cerrar(socket);
        }
    }

    /**
     * Escribe un evento con fecha en la consola y, si está disponible, en el
     * archivo de log.
     *
     * @param tipo categoría del evento
     * @param texto descripción del evento
     */
    static void log(String tipo, String texto) {
        String hora = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String linea = "[" + hora + "] [" + tipo + "] " + texto;
        synchronized (LOCK_LOG) {
            System.out.println(linea);
            if (logArchivo != null) {
                logArchivo.println(linea);
            }
        }
    }

    /** Inicia el hilo daemon que muestra periódicamente las estadísticas. */
    private static void iniciarHiloEstado() {
        Thread estado = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(INTERVALO_ESTADO_SEG * 1000L);
                } catch (InterruptedException e) {
                    return;
                }
                GestorChat.Estado datos = GESTOR_CHAT.obtenerEstado();
                long seg = (System.currentTimeMillis() - INICIO) / 1000;
                System.out.println("----- ESTADO -----\n"
                        + "Usuarios conectados: " + datos.conectados + "/" + MAX_USERS + "\n"
                        + "Tiempo activo:       "
                        + String.format("%02d:%02d:%02d", seg / 3600, (seg % 3600) / 60, seg % 60) + "\n"
                        + "Mensajes totales:    " + datos.totalMensajes + "\n"
                        + "Último mensaje:      " + datos.ultimoMensaje + "\n"
                        + "------------------");
            }
        });
        estado.setDaemon(true);
        estado.start();
    }

    /** Cierra el archivo de log si se había abierto. */
    private static void cerrarLog() {
        synchronized (LOCK_LOG) {
            if (logArchivo != null) {
                logArchivo.close();
                logArchivo = null;
            }
        }
    }

    /**
     * Cierra un socket.
     *
     * @param socket socket que se va a cerrar
     */
    private static void cerrar(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignorada) {
        }
    }

    /**
     * Atiende la conexión de un cliente: prepara los flujos, delega la
     * identificación y el procesamiento de mensajes a {@link GestorChat} y
     * libera los recursos al terminar.
     */
    static class ManejadorCliente implements Runnable {

        /** Socket asociado a este cliente. */
        private final Socket socket;
        /** Flujo de salida usado para enviar objetos al cliente. */
        private ObjectOutputStream out;
        /** Flujo de entrada usado para recibir objetos del cliente. */
        private ObjectInputStream in;
        /** Nombre del cliente, o {@code null} antes de identificarse. */
        private String nombre;

        /**
         * Crea el manejador para un socket aceptado por el servidor.
         *
         * @param socket conexión del cliente
         */
        ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        /**
         * Envía un mensaje al cliente. El método es sincronizado porque el
         * flujo puede ser utilizado por varios hilos.
         *
         * @param mensaje mensaje que se enviará
         */
        synchronized void enviar(Mensaje mensaje) {
            if (out == null) {
                return;
            }
            try {
                out.writeObject(mensaje);
                out.flush();
                out.reset();
            } catch (IOException e) {
                log("ERROR", "Fallo al enviar a " + nombre + ": " + e.getMessage());
                cerrar(socket);
            }
        }

        /**
         * Establece el nombre tras completar correctamente el inicio de sesión.
         *
         * @param nombre nombre identificado del cliente
         */
        void setNombre(String nombre) {
            this.nombre = nombre;
        }

        /**
         * Devuelve el nombre del cliente.
         *
         * @return nombre identificado, o {@code null} si aún no se ha
         *         identificado
         */
        String getNombre() {
            return nombre;
        }

        /**
         * Devuelve la dirección remota del socket.
         *
         * @return dirección IP del cliente
         */
        String getDireccion() {
            return socket.getInetAddress().toString();
        }

        /**
         * Ejecuta el ciclo de comunicación del cliente y limpia la conexión
         * cuando termina.
         */
        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                if (!GESTOR_CHAT.identificar(this, in)) {
                    return;
                }

                boolean seguir = true;
                while (seguir) {
                    Mensaje mensaje = (Mensaje) in.readObject();
                    seguir = GESTOR_CHAT.procesar(this, mensaje);
                }
            } catch (EOFException e) {
                // Desconexión normal si el cliente cierra el socket sin enviar SALIR.
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                log("ERROR", "Comunicación con " + (nombre != null ? nombre : "cliente sin nombre")
                        + " interrumpida: " + e.getMessage());
            } finally {
                GESTOR_CHAT.desconectar(this);
                liberarPlaza();
                cerrar(socket);
            }
        }
    }
}
