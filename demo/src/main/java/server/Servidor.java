package server;

import java.io.EOFException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import server.model.Mensaje;

/**
 * Servidor del chat multihilo.
 * <p>
 * Acepta conexiones en un bucle y crea un hilo ({@link ManejadorCliente}) por
 * cada cliente. Limita el número de usuarios simultáneos, reenvía mensajes
 * públicos y privados, guarda un historial y escribe un log en archivo. Además,
 * un hilo de estado muestra estadísticas periódicamente.
 * <p>
 * Todo el estado compartido es {@code static} y se protege con
 * {@code synchronized (LOCK)}.
 *
 * @author Hodei Torres
 * @version 1.0
 */
public class Servidor {

    // ---------- Configuración ----------
    private static final int PUERTO = 5005;
    private static final int MAX_USERS = 5;
    private static final int INTERVALO_ESTADO_SEG = 10;
    private static final String ARCHIVO_LOG = "servidor.log";

    // ---------- Estado compartido: tiene que ser static (no variables locales del main)
    //            para que lo vean todos los hilos. Siempre se toca dentro de synchronized (LOCK).
    private static final Object LOCK = new Object();
    private static final Map<String, ManejadorCliente> usuarios = new HashMap<>();
    private static final List<Mensaje> historial = new ArrayList<>();
    private static int conexionesActivas = 0;          // incluye a quienes aún no han puesto nombre
    private static String ultimoMensaje = "(ninguno)";

    // ---------- Log global ----------
    private static final Object LOCK_LOG = new Object();
    private static PrintWriter logArchivo;

    private static final long INICIO = System.currentTimeMillis();

    /**
     * Arranca el servidor: abre el log, inicia el hilo de estado y acepta
     * clientes indefinidamente, creando un hilo para cada uno.
     *
     * @param args argumentos de la línea de comandos (no se usan)
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
            while (true) {                                    // <- accept() DENTRO de un bucle
                Socket socketCliente = serverSocket.accept();

                if (!reservarPlaza()) {
                    rechazar(socketCliente);
                    continue;
                }
                new Thread(new ManejadorCliente(socketCliente)).start();   // un hilo por cliente
            }
        } catch (IOException e) {
            log("ERROR", "Fallo en el servidor: " + e.getMessage());
        }
    }

    //  LÍMITE DE CLIENTES
    // =====================================================================
    /**
     * Intenta reservar una plaza para un nuevo cliente.
     *
     * @return {@code true} si había plaza, {@code false} si el servidor está
     * lleno
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

    /**
     * Libera la plaza de un cliente que se ha desconectado.
     */
    private static void liberarPlaza() {
        synchronized (LOCK) {
            conexionesActivas--;
        }
    }

    /**
     * Avisa a un cliente de que el servidor está lleno y cierra su conexión.
     *
     * @param socket socket del cliente rechazado
     */
    private static void rechazar(Socket socket) {
        log("RECHAZADO", "Conexión desde " + socket.getInetAddress() + " (servidor lleno)");
        try {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(new Mensaje(Mensaje.Tipo.SISTEMA, "SERVIDOR", null, "Servidor lleno. Inténtalo más tarde."));
            out.flush();
        } catch (IOException ignorada) {
        } finally {
            cerrar(socket);
        }
    }

    //  USUARIOS Y MENSAJES
    // =====================================================================
    /**
     * Registra un usuario con su nombre si no está ya en uso.
     *
     * @param nombre nombre del usuario
     * @param cliente manejador asociado a ese usuario
     * @return {@code true} si se registró, {@code false} si el nombre ya
     * existía
     */
    private static boolean registrar(String nombre, ManejadorCliente cliente) {
        synchronized (LOCK) {
            if (usuarios.containsKey(nombre)) {
                return false;
            }
            usuarios.put(nombre, cliente);
            return true;
        }
    }

    /**
     * Elimina un usuario de la lista de conectados.
     *
     * @param nombre nombre del usuario
     */
    private static void eliminar(String nombre) {
        synchronized (LOCK) {
            usuarios.remove(nombre);
        }
    }

    /**
     * Busca el manejador de un usuario conectado.
     *
     * @param nombre nombre del usuario
     * @return su manejador, o {@code null} si no está conectado
     */
    private static ManejadorCliente buscar(String nombre) {
        synchronized (LOCK) {
            return usuarios.get(nombre);
        }
    }

    /**
     * Devuelve una copia de la lista de clientes para poder recorrerla fuera
     * del bloqueo.
     *
     * @return copia de los manejadores conectados
     */
    private static List<ManejadorCliente> copiaClientes() {
        synchronized (LOCK) {
            return new ArrayList<>(usuarios.values());
        }
    }

    /**
     * @return texto con el número y los nombres de los usuarios conectados
     */
    private static String listaUsuarios() {
        synchronized (LOCK) {
            return "Conectados (" + usuarios.size() + "): " + String.join(", ", usuarios.keySet());
        }
    }

    /**
     * Guarda un mensaje en el historial y lo marca como último mensaje.
     *
     * @param m mensaje a guardar
     */
    private static void guardarMensaje(Mensaje m) {
        synchronized (LOCK) {
            historial.add(m);
            ultimoMensaje = m.getEmisor() + ": " + m.getTexto();
        }
    }

    /**
     * Envía un mensaje a todos los clientes excepto a uno. Se envía fuera del
     * lock, sobre una copia, para que un cliente lento no bloquee a los demás.
     *
     * @param m mensaje a difundir
     * @param excluir cliente que no debe recibirlo (normalmente el emisor)
     */
    private static void difundir(Mensaje m, ManejadorCliente excluir) {
        for (ManejadorCliente c : copiaClientes()) {
            if (c != excluir) {
                c.enviar(m);
            }
        }
    }

    /**
     * Crea un mensaje de tipo SISTEMA enviado por el servidor.
     *
     * @param texto contenido del aviso
     * @return mensaje de sistema
     */
    private static Mensaje sistema(String texto) {
        return new Mensaje(Mensaje.Tipo.SISTEMA, "SERVIDOR", null, texto);
    }

    //  LOG Y ESTADO
    // =====================================================================
    /**
     * Escribe una línea con fecha y tipo en la consola y en el archivo de log.
     *
     * @param tipo categoría del evento (ENTRADA, SALIDA, ERROR...)
     * @param texto descripción del evento
     */
    private static void log(String tipo, String texto) {
        String hora = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String linea = "[" + hora + "] [" + tipo + "] " + texto;
        synchronized (LOCK_LOG) {
            System.out.println(linea);
            if (logArchivo != null) {
                logArchivo.println(linea);
            }
        }
    }

    /**
     * Arranca un hilo daemon que cada cierto tiempo muestra los usuarios
     * conectados, el tiempo activo, el total de mensajes y el último mensaje.
     */
    private static void iniciarHiloEstado() {
        Thread estado = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(INTERVALO_ESTADO_SEG * 1000L);
                } catch (InterruptedException e) {
                    return;
                }
                int conectados, total;
                String ultimo;
                synchronized (LOCK) {
                    conectados = usuarios.size();
                    total = historial.size();
                    ultimo = ultimoMensaje;
                }
                long seg = (System.currentTimeMillis() - INICIO) / 1000;
                System.out.println("----- ESTADO -----");
                System.out.println("Usuarios conectados: " + conectados + "/" + MAX_USERS);
                System.out.println("Tiempo activo:       " + String.format("%02d:%02d:%02d", seg / 3600, (seg % 3600) / 60, seg % 60));
                System.out.println("Mensajes totales:    " + total);
                System.out.println("Último mensaje:      " + ultimo);
                System.out.println("------------------");
            }
        });
        estado.setDaemon(true);
        estado.start();
    }

    /**
     * Cierra un socket ignorando posibles errores.
     *
     * @param socket socket a cerrar
     */
    private static void cerrar(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignorada) {
        }
    }

    //  HILO DE CADA CLIENTE
    // =====================================================================
    /**
     * Hilo que atiende a un único cliente: lo identifica, recibe sus mensajes y
     * los procesa hasta que se desconecta.
     */
    private static class ManejadorCliente implements Runnable {

        /**
         * Socket del cliente.
         */
        private final Socket socket;
        /**
         * Flujo para enviar mensajes al cliente.
         */
        private ObjectOutputStream out;
        /**
         * Flujo para recibir mensajes del cliente.
         */
        private ObjectInputStream in;
        /**
         * Nombre del usuario, o {@code null} hasta que se identifica.
         */
        private String nombre = null;

        /**
         * Crea el manejador para un cliente.
         *
         * @param socket socket del cliente
         */
        ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        /**
         * Envía un mensaje a este cliente. ObjectOutputStream no es
         * thread-safe: varios hilos le escriben, así que se sincroniza.
         *
         * @param m mensaje a enviar
         */
        synchronized void enviar(Mensaje m) {
            if (out == null) {
                return;
            }
            try {
                out.writeObject(m);
                out.flush();
                out.reset();      // evita que el stream acumule referencias y consuma memoria
            } catch (IOException e) {
                log("ERROR", "Fallo al enviar a " + nombre + ": " + e.getMessage());
                cerrar(socket);   // el hilo del cliente saldrá de readObject y hará la limpieza
            }
        }

        /**
         * Crea los flujos, identifica al usuario y procesa sus mensajes hasta
         * que se desconecta. Al terminar, lo elimina y libera su plaza.
         */
        @Override
        public void run() {
            try {
                // Orden importante: primero el Output (con flush) y luego el Input.
                // El cliente debe hacer lo mismo o ambos se quedarán esperando la cabecera del otro.
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                if (!identificar()) {
                    return;
                }

                log("ENTRADA", nombre + " se ha conectado desde " + socket.getInetAddress());
                difundir(sistema(nombre + " se ha unido al chat"), this);
                enviar(sistema("Bienvenido, " + nombre + ". " + listaUsuarios()));

                boolean seguir = true;
                while (seguir) {
                    Mensaje m = (Mensaje) in.readObject();
                    seguir = procesar(m);
                }
            } catch (EOFException e) {
                // el cliente cerró la conexión sin enviar SALIR: es una desconexión normal
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                log("ERROR", "Comunicación con " + (nombre != null ? nombre : "cliente sin nombre")
                        + " interrumpida: " + e.getMessage());
            } finally {
                if (nombre != null) {
                    eliminar(nombre);
                    log("SALIDA", nombre + " se ha desconectado");
                    difundir(sistema(nombre + " ha salido del chat"), this);
                }
                liberarPlaza();
                cerrar(socket);
            }
        }

        /**
         * El primer mensaje del cliente debe ser un LOGIN con el nombre en
         * 'emisor'. Repite hasta recibir un nombre válido y libre.
         *
         * @return {@code true} cuando el usuario queda registrado
         * @throws IOException si falla la comunicación
         * @throws ClassNotFoundException si llega un objeto desconocido
         */
        private boolean identificar() throws IOException, ClassNotFoundException {
            while (true) {
                Mensaje m = (Mensaje) in.readObject();
                if (m.getTipo() != Mensaje.Tipo.LOGIN) {
                    enviar(sistema("Debes identificarte primero (LOGIN)."));
                    continue;
                }
                String n = m.getEmisor() == null ? "" : m.getEmisor().trim();
                if (n.isEmpty() || n.contains(" ")) {
                    enviar(sistema("Nombre no válido (sin espacios). Prueba otro."));
                } else if (!registrar(n, this)) {
                    enviar(sistema("El nombre '" + n + "' ya está en uso. Prueba otro."));
                } else {
                    nombre = n;
                    return true;
                }
            }
        }

        /**
         * Procesa un mensaje del cliente según su tipo. Devuelve false cuando
         * el cliente quiere salir.
         *
         * @param m mensaje recibido
         * @return {@code false} si el cliente quiere salir, {@code true} en
         * otro caso
         */
        private boolean procesar(Mensaje m) {
            switch (m.getTipo()) {
                case PUBLICO:
                    Mensaje publico = new Mensaje(Mensaje.Tipo.PUBLICO, nombre, null, m.getTexto());
                    guardarMensaje(publico);
                    log("PUBLICO", publico.toString());
                    difundir(publico, this);
                    return true;

                case PRIVADO:
                    ManejadorCliente receptor = buscar(m.getDestinatario());
                    if (receptor == null) {
                        enviar(sistema("El usuario '" + m.getDestinatario() + "' no está conectado."));
                        return true;
                    }
                    Mensaje privado = new Mensaje(Mensaje.Tipo.PRIVADO, nombre, m.getDestinatario(), m.getTexto());
                    guardarMensaje(privado);
                    log("PRIVADO", nombre + " -> " + m.getDestinatario() + ": " + m.getTexto());
                    receptor.enviar(privado);
                    enviar(sistema("Mensaje privado enviado a " + m.getDestinatario()));
                    return true;

                case LISTA:
                    enviar(sistema(listaUsuarios()));
                    return true;

                case SALIR:
                    return false;

                default:
                    enviar(sistema("Tipo de mensaje no válido."));
                    return true;
            }
        }
    }
}
