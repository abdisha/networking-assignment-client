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
    private DatagramSocket udpSocket;
    private int port;
    private volatile boolean running;

    public VideoEngine(int port) {
        this.port = port;
    }

    // COMPRESSION LOGIC: Converts BufferedImage to a small JPEG byte array
    public byte[] compressFrame(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality); // 0.1 to 1.0 (0.3 is good for speed)

        writer.setOutput(ImageIO.createImageOutputStream(baos));
        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();

        return baos.toByteArray();
    }

    public void sendPacket(byte[] data, String targetIP) {
        try {
            // Remove any leading slashes from IP string
            String cleanIP = targetIP.split(":")[0].replace("/", "");
            InetAddress address = InetAddress.getByName(cleanIP);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            udpSocket.send(packet);
        } catch (IOException e) {
            System.err.println("UDP Send Error: " + e.getMessage());
        }
    }

    public void startReceiving(VideoFrameListener listener) {
        running = true;
        new Thread(() -> {
            try {
                if (udpSocket == null || udpSocket.isClosed()) {
                    udpSocket = new DatagramSocket(port);
                }
                byte[] buffer = new byte[65507]; // Max UDP size
                while (running) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    listener.onFrameReceived(packet.getData(), packet.getLength());
                }
            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }).start();
    }

    public void stop() {
        running = false;
        if (udpSocket != null) udpSocket.close();
    }
}

interface VideoFrameListener {
    void onFrameReceived(byte[] data, int length);
}