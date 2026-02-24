package com.assignment.networkingassignmentclient;

public interface ClientListener {
    void onMessageReceived(String message);
    void onStatusUpdate(String status);
}