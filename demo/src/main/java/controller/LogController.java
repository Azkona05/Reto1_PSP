package controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LogController {
    private String ruta = "./src/main/resources/data/log.log";

    public LogController() {
        if (!new java.io.File(ruta).exists()) {
            try {
                new java.io.File(ruta).createNewFile();
            } catch (IOException e) {
                System.err.println("Error al crear el archivo: " + e.getMessage());
            }
        }
    }

    public String getRuta() {
        return ruta;
    }

    public void setRuta(String ruta) {
        this.ruta = ruta;
    }

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
