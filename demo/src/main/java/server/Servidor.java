package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import server.model.Mensaje;

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
    private static boolean reservarPlaza() {
        synchronized (LOCK) {
            if (conexionesActivas >= MAX_USERS) {
                return false;
            }
            conexionesActivas++;
            return true;
        }
    }

    private static void liberarPlaza() {
        synchronized (LOCK) {
            conexionesActivas--;
        }
    }

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
    private static boolean registrar(String nombre, ManejadorCliente cliente) {
        synchronized (LOCK) {
            if (usuarios.containsKey(nombre)) {
                return false;
            }
            usuarios.put(nombre, cliente);
            return true;
        }
    }

    private static void eliminar(String nombre) {
        synchronized (LOCK) {
            usuarios.remove(nombre);
        }
    }

    private static ManejadorCliente buscar(String nombre) {
        synchronized (LOCK) {
            return usuarios.get(nombre);
        }
    }

    private static List<ManejadorCliente> copiaClientes() {
        synchronized (LOCK) {
            return new ArrayList<>(usuarios.values());
        }
    }

    private static String listaUsuarios() {
        synchronized (LOCK) {
            return "Conectados (" + usuarios.size() + "): " + String.join(", ", usuarios.keySet());
        }
    }

    private static void guardarMensaje(Mensaje m) {
        synchronized (LOCK) {
            historial.add(m);
            ultimoMensaje = m.getEmisor() + ": " + m.getTexto();
        }
    }

    /** Se envía fuera del lock, sobre una copia, para que un cliente lento no bloquee a los demás. */
    private static void difundir(Mensaje m, ManejadorCliente excluir) {
        for (ManejadorCliente c : copiaClientes()) {
            if (c != excluir) {
                c.enviar(m);
            }
        }
    }

    private static Mensaje sistema(String texto) {
        return new Mensaje(Mensaje.Tipo.SISTEMA, "SERVIDOR", null, texto);
    }

    //  LOG Y ESTADO
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

    private static void cerrar(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignorada) {
        }
    }

    //  HILO DE CADA CLIENTE
    private static class ManejadorCliente implements Runnable {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String nombre = null;

        ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        /** ObjectOutputStream no es thread-safe: varios hilos le escriben, así que se sincroniza. */
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

        /** El primer mensaje del cliente debe ser un LOGIN con el nombre en 'emisor'. */
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

        /** Devuelve false cuando el cliente quiere salir. */
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