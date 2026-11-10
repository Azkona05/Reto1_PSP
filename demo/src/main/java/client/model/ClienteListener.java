package client.model;

import server.model.Mensaje;

/**
 * Interfaz que permite al {@link Cliente} avisar de los eventos de la conexión
 * sin depender de una clase concreta (patrón Observer).
 * <p>
 * Los métodos se invocan desde {@link HiloLectura}, no desde el hilo de la
 * interfaz gráfica. Si quien la implementa modifica componentes de JavaFX, debe
 * hacerlo mediante {@code Platform.runLater}.
 *
 * @author Azkona05
 * @version 1.0
 */
public interface ClienteListener {

    /**
     * Se invoca cada vez que llega un mensaje del servidor.
     *
     * @param m mensaje recibido
     */
    void mensajeRecibido(Mensaje m);

    /**
     * Se invoca cuando la conexión con el servidor se pierde sin que el usuario
     * se haya desconectado.
     */
    void conexionPerdida();

    /**
     * Se invoca desde {@link HiloLectura} cuando la conexión se ha establecido
     * correctamente.
     *
     * @param usuario nombre con el que se ha conectado
     */
    void conexionEstablecida(String usuario);

    /**
     * Se invoca desde {@link HiloLectura} cuando no se ha podido conectar.
     *
     * @param mensajeError descripción del error
     */
    void errorConexion(String mensajeError);
}
