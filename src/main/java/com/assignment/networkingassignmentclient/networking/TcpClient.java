package com.assignment.networkingassignmentclient.networking;
import com.assignment.networkingassignmentclient.ClientListener;

import java.io.*;
import java.net.*;

public class TcpClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final String host;
    private final int port;
    private final ClientListener listener;
    private boolean running = false;

    public TcpClient(String host, int port, ClientListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public void connect() {
        new Thread(() -> {
            try {
                socket = new Socket(host, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                running = true;
                listener.onStatusUpdate("Connected to " + host);

                String line;
                while (running && (line = in.readLine()) != null) {
                    listener.onMessageReceived(line);
                }
            } catch (IOException e) {
                listener.onStatusUpdate("Connection Error: " + e.getMessage());
            } finally {
                stop();
            }
        }).start();
    }

    public void sendMessage(String msg) {
        if (out != null) out.println(msg);
    }

    public void stop() {
        running = false;
        try {
            if (socket != null) socket.close();
            listener.onStatusUpdate("Disconnected");
        } catch (IOException e) { e.printStackTrace(); }
    }
}