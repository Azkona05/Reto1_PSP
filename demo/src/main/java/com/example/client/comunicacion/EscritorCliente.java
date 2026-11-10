package com.example.client.comunicacion;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import com.example.common.Mensaje;

/**
 * Serializa y envía los mensajes del cliente en un único hilo.
 * <p>
 * Al fallar una escritura, delega el cierre de la conexión a su propietario.
 */
final class EscritorCliente {

    private final ObjectOutputStream salida;
    private final ExecutorService ejecutor;
    private final Runnable alFallar;

    /**
     * Crea un escritor asociado al flujo de salida de una conexión.
     *
     * @param salida flujo por el que se escriben los mensajes
     * @param alFallar acción para cerrar la conexión cuando falla una escritura
     */
    EscritorCliente(ObjectOutputStream salida, Runnable alFallar) {
        this.salida = salida;
        this.alFallar = alFallar;
        this.ejecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread hilo = new Thread(r, "hilo-escritura-cliente");
                hilo.setDaemon(true);
                return hilo;
            }
        });
    }

    /**
     * Encola un mensaje para mantener el orden de escritura.
     *
     * @param mensaje mensaje que se enviará
     */
    void enviar(Mensaje mensaje) {
        ejecutor.execute(() -> escribir(mensaje));
    }

    /**
     * Encola un último mensaje y programa el cierre de la conexión al terminar.
     *
     * @param mensaje último mensaje que se enviará
     * @param alTerminar acción de cierre
     */
    void enviarAntesDeCerrar(Mensaje mensaje, Runnable alTerminar) {
        ejecutor.execute(() -> {
            escribir(mensaje);
            alTerminar.run();
        });
        ejecutor.shutdown();
    }

    /** Detiene el escritor y descarta las escrituras pendientes. */
    void cerrar() {
        ejecutor.shutdownNow();
    }

    /**
     * Escribe y vacía un mensaje desde el hilo dedicado.
     *
     * @param mensaje mensaje que se escribirá
     */
    private void escribir(Mensaje mensaje) {
        try {
            salida.writeObject(mensaje);
            salida.flush();
            salida.reset();
        } catch (IOException e) {
            alFallar.run();
        }
    }
}
