package server.model;

import java.io.Serializable;

/**
 * Mensaje que se intercambia entre el cliente y el servidor.
 * <p>
 * Implementa {@link Serializable} para poder enviarse por la red con
 * {@code ObjectOutputStream} / {@code ObjectInputStream}. Es inmutable: sus
 * campos no cambian una vez creado.
 *
 * @author Hodei Torres
 * @version 1.0
 */
public class Mensaje implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Tipos de mensaje que entiende el protocolo del chat.
     */
    public enum Tipo {
        /**
         * Identificación del usuario; el nombre va en el emisor.
         */
        LOGIN,
        /**
         * Mensaje para todos los usuarios conectados.
         */
        PUBLICO,
        /**
         * Mensaje para un único usuario (el destinatario).
         */
        PRIVADO,
        /**
         * Petición de la lista de usuarios conectados.
         */
        LISTA,
        /**
         * Aviso de que el cliente se desconecta.
         */
        SALIR,
        /**
         * Aviso o información enviada por el servidor.
         */
        SISTEMA
    }

    /**
     * Tipo de mensaje.
     */
    private final Tipo tipo;
    /**
     * Nombre del usuario que envía el mensaje.
     */
    private final String emisor;
    /**
     * Nombre del usuario destinatario (solo en mensajes privados).
     */
    private final String destinatario;
    /**
     * Contenido del mensaje.
     */
    private final String texto;

    /**
     * Crea un mensaje.
     *
     * @param tipo tipo de mensaje
     * @param emisor usuario que lo envía
     * @param destinatario usuario que lo recibe, o {@code null} si no es
     * privado
     * @param texto contenido, o {@code null} si el tipo no lo necesita
     */
    public Mensaje(Tipo tipo, String emisor, String destinatario, String texto) {
        this.tipo = tipo;
        this.emisor = emisor;
        this.destinatario = destinatario;
        this.texto = texto;
    }

    /**
     * @return tipo de mensaje
     */
    public Tipo getTipo() {
        return tipo;
    }

    /**
     * @return nombre del usuario que envía el mensaje
     */
    public String getEmisor() {
        return emisor;
    }

    /**
     * @return nombre del destinatario, o {@code null} si no es privado
     */
    public String getDestinatario() {
        return destinatario;
    }

    /**
     * @return contenido del mensaje
     */
    public String getTexto() {
        return texto;
    }

    /**
     * Devuelve una representación en texto del mensaje, útil para el log.
     *
     * @return cadena con todos los campos del mensaje
     */
    @Override
    public String toString() {
        return "Mensaje{"
                + "tipo=" + tipo
                + ", emisor='" + emisor + '\''
                + ", destinatario='" + destinatario + '\''
                + ", texto='" + texto + '\''
                + '}';
    }
}
