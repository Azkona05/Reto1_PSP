package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Cliente {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public void conectar(String host, int puerto, String usuario) throws IOException {
        // 1. Establecer conexión TCP con IP y Puerto
        this.socket = new Socket(host, puerto);
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out.println("USER:" + usuario);
    }

    public void enviarMensaje(String mensaje) {
        if (out != null) {
            out.println(mensaje);
        }
    }

    public void desconectar() {
        try {
            if (out != null)
                out.close();
            if (in != null)
                in.close();
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
