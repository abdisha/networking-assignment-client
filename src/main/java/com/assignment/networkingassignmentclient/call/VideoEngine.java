package com.assignment.networkingassignmentclient.call;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.net.*;
import java.io.*;
import java.util.Iterator;

public class VideoEngine {
    private DatagramSocket socket;
    private int port;
    private boolean running;

    public VideoEngine(int port) { this.port = port; }

    public void send(byte[] data, String ip) {
        // If we've called close(), stop trying to send immediately
        if (!running || socket == null || socket.isClosed()) return;

        try {
            String cleanIp = ip.split(":")[0].replace("/", "");
            InetAddress addr = InetAddress.getByName(cleanIp);
            socket.send(new DatagramPacket(data, data.length, addr, port));
        } catch (Exception e) {
            // Log error only if we haven't intentionally closed the socket
            if (running) System.err.println("UDP Send Error: " + e.getMessage());
        }
    }

    public void startReceiving(VideoListener l) {
        running = true;
        new Thread(() -> {
            try {
                socket = new DatagramSocket(port);
                byte[] buffer = new byte[65507];
                while (running) {
                    DatagramPacket p = new DatagramPacket(buffer, buffer.length);
                    socket.receive(p);
                    l.onFrame(p.getData(), p.getLength());
                }
            } catch (Exception e) { }
        }).start();
    }

    public byte[] compress(BufferedImage img, float q) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(q);
        writer.setOutput(ImageIO.createImageOutputStream(baos));
        writer.write(null, new IIOImage(img, null, null), param);
        writer.dispose();
        return baos.toByteArray();
    }

    public void close() { running = false; if (socket != null) socket.close(); }
    public interface VideoListener { void onFrame(byte[] data, int len); }
}

interface VideoFrameListener {
    void onFrameReceived(byte[] data, int length);
}