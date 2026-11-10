package com.example.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.common.Mensaje;

/**
 * Gestiona los usuarios conectados y procesa los mensajes recibidos por el
 * servidor, incluyendo su distribución a los destinatarios correspondientes.
 *
 * @author Hodei
 * @version 1.0
 */

public class GestorChat {

    /** Cerrojo para proteger el estado compartido de usuarios e historial. */
    private final Object lock = new Object();
    /** Clientes identificados, indexados por su nombre de usuario. */
    private final Map<String, Servidor.ManejadorCliente> usuarios = new HashMap<>();
    /** Historial de mensajes públicos y privados procesados. */
    private final List<Mensaje> historial = new ArrayList<>();
    /** Texto del mensaje más reciente, usado en el informe de estado. */
    private String ultimoMensaje = "(ninguno)";
    /** Instante de recepción del mensaje más reciente. */
    private LocalDateTime timestampUltimoMensaje;

    /** Crea un gestor con estado independiente para un servidor. */
    public GestorChat() {
    }

    /**
     * Lee y valida el primer mensaje del cliente y, si es un LOGIN válido,
     * registra al usuario y le da la bienvenida.
     *
     * @param cliente manejador de la conexión que se identifica
     * @param entrada flujo del que se lee el mensaje de identificación
     * @return {@code true} si el cliente se identificó correctamente
     * @throws IOException si ocurre un error al leer o escribir en la conexión
     * @throws ClassNotFoundException si el flujo contiene una clase no disponible
     */
    boolean identificar(Servidor.ManejadorCliente cliente, ObjectInputStream entrada)
            throws IOException, ClassNotFoundException {
        Mensaje mensaje = (Mensaje) entrada.readObject();
        if (mensaje.getTipo() != Mensaje.Tipo.LOGIN) {
            enviar(cliente, new Mensaje(Mensaje.Tipo.LOGIN_ERROR, "SERVIDOR", null,
                    "Debes identificarte primero (LOGIN)."));
            return false;
        }

        String nombre = mensaje.getEmisor() == null ? "" : mensaje.getEmisor().trim();
        if (nombre.isEmpty() || nombre.contains(" ")) {
            enviar(cliente, new Mensaje(Mensaje.Tipo.LOGIN_ERROR, "SERVIDOR", null,
                    "Nombre no válido (sin espacios). Prueba otro."));
            return false;
        }
        if (!registrar(nombre, cliente)) {
            enviar(cliente, new Mensaje(Mensaje.Tipo.LOGIN_ERROR, "SERVIDOR", null,
                    "El nombre '" + nombre + "' ya está en uso. Prueba otro."));
            Servidor.log("RECHAZADO", "Nombre de usuario ya en uso: " + nombre);
            return false;
        }

        cliente.setNombre(nombre);
        enviar(cliente, new Mensaje(Mensaje.Tipo.LOGIN_OK, "SERVIDOR", null, null));
        Servidor.log("ENTRADA", nombre + " se ha conectado desde " + cliente.getDireccion());
        difundir(sistema(nombre + " se ha unido al chat"), cliente);
        enviar(cliente, sistema("Bienvenido, " + nombre + ". " + listaUsuarios()));
        return true;
    }

    /**
     * Procesa un mensaje recibido y devuelve false cuando el cliente solicita
     * desconectarse.
     *
     * @param cliente manejador del cliente que envió el mensaje
     * @param mensaje mensaje recibido
     * @return {@code false} si el cliente solicitó salir; {@code true} si debe
     *         continuar conectado
     */
    boolean procesar(Servidor.ManejadorCliente cliente, Mensaje mensaje) {
        switch (mensaje.getTipo()) {
            case PUBLICO:
                Mensaje publico = new Mensaje(Mensaje.Tipo.PUBLICO, cliente.getNombre(), null, mensaje.getTexto());
                guardarMensaje(publico);
                Servidor.log("PUBLICO", publico.toString());
                difundir(publico, cliente);
                return true;

            case PRIVADO:
                Servidor.ManejadorCliente receptor = buscar(mensaje.getDestinatario());
                if (receptor == null) {
                    enviar(cliente, sistema("El usuario '" + mensaje.getDestinatario() + "' no está conectado."));
                    return true;
                }
                Mensaje privado = new Mensaje(Mensaje.Tipo.PRIVADO, cliente.getNombre(),
                        mensaje.getDestinatario(), mensaje.getTexto());
                guardarMensaje(privado);
                Servidor.log("PRIVADO", cliente.getNombre() + " -> " + mensaje.getDestinatario()
                        + ": " + mensaje.getTexto());
                enviar(receptor, privado);
                enviar(cliente, sistema("Mensaje privado enviado a " + mensaje.getDestinatario()));
                return true;

            case LISTA:
                enviar(cliente, sistema(listaUsuarios()));
                return true;

            case SALIR:
                return false;

            default:
                enviar(cliente, sistema("Tipo de mensaje no válido."));
                return true;
        }
    }

    /**
     * Quita al cliente de los usuarios conectados y avisa al resto si había
     * completado el inicio de sesión.
     *
     * @param cliente manejador de la conexión que termina
     */
    void desconectar(Servidor.ManejadorCliente cliente) {
        String nombre = cliente.getNombre();
        if (nombre == null) {
            return;
        }
        synchronized (lock) {
            usuarios.remove(nombre, cliente);
        }
        Servidor.log("SALIDA", nombre + " se ha desconectado");
        difundir(sistema(nombre + " ha salido del chat"), cliente);
    }

    /**
     * Obtiene una instantánea coherente de las estadísticas del procesador.
     *
     * @return número de usuarios, mensajes procesados, último mensaje y su timestamp
     */
    Estado obtenerEstado() {
        synchronized (lock) {
            return new Estado(usuarios.size(), historial.size(), ultimoMensaje, timestampUltimoMensaje);
        }
    }

    /**
     * Registra al cliente si el nombre todavía no está en uso.
     *
     * @param nombre nombre que se quiere registrar
     * @param cliente manejador asociado al usuario
     * @return {@code true} si se registró; {@code false} si ya existía
     */
    private boolean registrar(String nombre, Servidor.ManejadorCliente cliente) {
        synchronized (lock) {
            if (usuarios.containsKey(nombre)) {
                return false;
            }
            usuarios.put(nombre, cliente);
            return true;
        }
    }

    /**
     * Busca un cliente conectado por nombre.
     *
     * @param nombre nombre del usuario que se busca
     * @return manejador del usuario, o {@code null} si no está conectado
     */
    private Servidor.ManejadorCliente buscar(String nombre) {
        synchronized (lock) {
            return usuarios.get(nombre);
        }
    }

    /**
     * Copia los manejadores conectados para recorrerlos sin mantener el
     * cerrojo durante los envíos.
     *
     * @return copia de los manejadores de los usuarios conectados
     */
    private List<Servidor.ManejadorCliente> copiaUsuarios() {
        synchronized (lock) {
            return new ArrayList<>(usuarios.values());
        }
    }

    /**
     * Construye la representación de los usuarios conectados.
     *
     * @return cantidad y nombres de los usuarios conectados
     */
    private String listaUsuarios() {
        synchronized (lock) {
            return "Conectados (" + usuarios.size() + "): " + String.join(", ", usuarios.keySet());
        }
    }

    /**
     * Añade un mensaje al historial y actualiza el texto del último mensaje.
     *
     * @param mensaje mensaje que se guarda
     */
    private void guardarMensaje(Mensaje mensaje) {
        synchronized (lock) {
            historial.add(mensaje);
            ultimoMensaje = mensaje.getEmisor() + ": " + mensaje.getTexto();
            timestampUltimoMensaje = LocalDateTime.now();
        }
    }

    /**
     * Envía un mensaje a todos los usuarios salvo al cliente indicado.
     *
     * @param mensaje mensaje que se distribuye
     * @param excluir cliente que no debe recibirlo, o {@code null} si no se
     *         excluye ninguno
     */
    private void difundir(Mensaje mensaje, Servidor.ManejadorCliente excluir) {
        for (Servidor.ManejadorCliente cliente : copiaUsuarios()) {
            if (cliente != excluir) {
                enviar(cliente, mensaje);
            }
        }
    }

    /**
     * Delega el envío al manejador del destinatario.
     *
     * @param cliente destinatario
     * @param mensaje mensaje que se enviará
     */
    private void enviar(Servidor.ManejadorCliente cliente, Mensaje mensaje) {
        cliente.enviar(mensaje);
    }

    /**
     * Crea un mensaje informativo generado por el servidor.
     *
     * @param texto contenido del mensaje
     * @return mensaje de tipo {@link Mensaje.Tipo#SISTEMA}
     */
    private Mensaje sistema(String texto) {
        return new Mensaje(Mensaje.Tipo.SISTEMA, "SERVIDOR", null, texto);
    }

    /** Instantánea de las estadísticas mostradas por el hilo de estado. */
    static final class Estado {
        /** Número de usuarios identificados y conectados. */
        final int conectados;
        /** Cantidad de mensajes guardados en el historial. */
        final int totalMensajes;
        /** Emisor y texto del mensaje más reciente. */
        final String ultimoMensaje;
        /** Instante de recepción del mensaje más reciente, o {@code null} si no hay mensajes. */
        final LocalDateTime timestampUltimoMensaje;

        /**
         * Crea una instantánea de estado.
         *
         * @param conectados número de usuarios conectados
         * @param totalMensajes número de mensajes del historial
         * @param ultimoMensaje descripción del último mensaje
         * @param timestampUltimoMensaje instante del último mensaje, o {@code null} si no hay mensajes
         */
        Estado(int conectados, int totalMensajes, String ultimoMensaje, LocalDateTime timestampUltimoMensaje) {
            this.conectados = conectados;
            this.totalMensajes = totalMensajes;
            this.ultimoMensaje = ultimoMensaje;
            this.timestampUltimoMensaje = timestampUltimoMensaje;
        }
    }
}
