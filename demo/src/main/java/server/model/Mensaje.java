package server.model;

import java.io.Serializable;

public class Mensaje implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Tipo {
        LOGIN,
        PUBLICO,
        PRIVADO,
        LISTA,
        SALIR,
        SISTEMA
    }

    private final Tipo tipo;
    private final String emisor;
    private final String destinatario;
    private final String texto;

    public Mensaje(Tipo tipo, String emisor, String destinatario, String texto) {
        this.tipo = tipo;
        this.emisor = emisor;
        this.destinatario = destinatario;
        this.texto = texto;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getEmisor() {
        return emisor;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getTexto() {
        return texto;
    }

    @Override
    public String toString() {
        return "Mensaje{" +
                "tipo=" + tipo +
                ", emisor='" + emisor + '\'' +
                ", destinatario='" + destinatario + '\'' +
                ", texto='" + texto + '\'' +
                '}';
    }
}
