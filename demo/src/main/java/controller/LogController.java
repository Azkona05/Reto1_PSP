package controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestiona un archivo de log en el que se guardan cadenas de texto
 * serializadas.
 *
 * @author Joel Mamani
 * @version 1.0
 */
public class LogController {

    /**
     * Ruta del archivo de log.
     */
    private String ruta = "./src/main/resources/data/log.log";

    /**
     * Crea el controlador y, si el archivo de log no existe, lo crea.
     */
    public LogController() {
        if (!new java.io.File(ruta).exists()) {
            try {
                new java.io.File(ruta).createNewFile();
            } catch (IOException e) {
                System.err.println("Error al crear el archivo: " + e.getMessage());
            }
        }
    }

    /**
     * @return ruta del archivo de log
     */
    public String getRuta() {
        return ruta;
    }

    /**
     * @param ruta nueva ruta del archivo de log
     */
    public void setRuta(String ruta) {
        this.ruta = ruta;
    }

    /**
     * Añade una cadena al log. Lee todo el contenido previo, añade la nueva
     * cadena y vuelve a escribir el archivo completo.
     *
     * @param deportivo texto a añadir
     */
    public void addContent(String deportivo) {
        List<String> mensajes = getContent();
        if (mensajes == null) {
            mensajes = new ArrayList<>();
        }
        mensajes.add(deportivo);
        try (java.io.ObjectOutputStream writer = new java.io.ObjectOutputStream(new java.io.FileOutputStream(ruta))) {
            for (String contenido : mensajes) {
                writer.writeObject(contenido);
            }
        } catch (IOException e) {
            System.err.println("Error al escribir en el archivo: " + e.getMessage());
        }
    }

    /**
     * Lee todas las cadenas guardadas en el archivo de log.
     *
     * @return lista con el contenido del log (vacía si no hay nada)
     */
    private List<String> getContent() {
        List<String> mensajes = new ArrayList<>();

        try (java.io.ObjectInputStream reader = new java.io.ObjectInputStream(
                new java.io.FileInputStream(ruta))) {
            while (true) {
                try {
                    String mensaje = (String) reader.readObject();
                    mensajes.add(mensaje);
                } catch (ClassNotFoundException e) {
                    break;
                }
            }
        } catch (IOException e) {
            // Cuando termina
        }
        return mensajes;
    }

    /**
     * Prueba rápida del funcionamiento del log.
     *
     * @param args argumentos de la línea de comandos (no se usan)
     */
    public static void main(String[] args) {
        LogController logController = new LogController();
        logController.addContent("Mensaje 1");
        logController.addContent("\nMensaje 2");

        List<String> mensajes = logController.getContent();
        for (String mensaje : mensajes) {
            System.out.println(mensaje);
        }
    }
}
