package com.assignment.networkingassignmentclient;

import com.assignment.networkingassignmentclient.call.VideoEngine;
import com.github.sarxos.webcam.Webcam;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;


import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.stream.Stream;

public class MainApp extends Application implements ClientListener {
    private Socket socket;
    private PrintWriter out;
    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    private final TextArea chatArea = new TextArea();
    private String currentClient;
    private final Label selectedUser = new Label();
    private final Button callBtn = new Button();

    private VideoEngine videoEngine = new VideoEngine(7000);
    private boolean isCalling = false;

    @Override
    public void start(Stage primaryStage) {
        ListView<String> userListView = new ListView<>(onlineUsers);
        userListView.setPrefWidth(150);

        selectedUser.setStyle("-fx-font-weight: bold;");
        selectedUser.setStyle("-fx-alignment: center;");
        selectedUser.setVisible(false);
        callBtn.setText("Call");
        callBtn.setVisible(false);

        userListView.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    if(newValue!=null){
                        selectedUser.setVisible(true);
                        callBtn.setVisible(true);
                        selectedUser.setText("User: "+newValue);
                    }else {
                        selectedUser.setVisible(false);
                        callBtn.setVisible(false);

                    }

                }
        );

        TextField input = new TextField();

        input.setOnAction(e -> {
            String selected = userListView.getSelectionModel().getSelectedItem();
            String msg = input.getText();
            if (selected != null){
                out.println("PRIVATE_MSG:" + selected + ":" + msg);
                System.out.println("PRIVATE_MSG:" + selected + ":" + msg);
            }
            else {
                out.println(msg);
            }
            input.clear();
        });

        chatArea.setEditable(false);
        HBox body = new HBox(10, new VBox(5, new Label("Chat"),
                    new HBox(5, selectedUser, callBtn)
                , chatArea, input),
                new VBox(5, new Label("Online (Select to Whisper)"), userListView));
        body.setPadding(new Insets(10));
        connect();

        primaryStage.setTitle("TCP Client");
        primaryStage.setScene(new Scene(body, 650, 400));
        primaryStage.show();
    }

    private void connect() {
        new Thread(() -> {
            try {
                socket = new Socket("127.0.0.1", 65432);
                System.out.println("Connected to server");
                chatArea.appendText("Connected to server\n");
                chatArea.setScrollTop(Double.MAX_VALUE);
                currentClient = socket.getLocalAddress().toString().substring(1) + ":" + socket.getLocalPort();
                out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String line;
                while ((line = in.readLine()) != null) {
                    final String msg = line;
                    Platform.runLater(() -> {
                        if (msg.startsWith("USER_LIST:")) {
                            System.out.println("current client: "+currentClient);
                            List<String> connectedUser = Stream.of(msg.substring(10).split(",")).filter(u->!u.endsWith(currentClient)).toList();
                            onlineUsers.setAll(connectedUser);
                        } else {
                            chatArea.appendText(msg + "\n");
                            chatArea.setScrollTop(Double.MAX_VALUE);
                        }
                    });
                }
            } catch (IOException e) { Platform.runLater(() -> chatArea.appendText("Connection failed.\n")); }
        }).start();
    }

    @Override
    public void onMessageReceived(String message) {
        Platform.runLater(() -> {
            if (message.startsWith("VIDEO_PROMPT:")) {
                String callerIp = message.split(":")[1];
                showCallAlert(callerIp);
            } else if (message.startsWith("VIDEO_RESULT:")) {
                String[] parts = message.split(":");
                if (parts[2].equals("ACCEPT")) {
                    startVideoCall(parts[1]); // parts[1] is the other person's IP
                }
            }

        });
    }
    private void showCallAlert(String callerIp) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Inbound call from " + callerIp, ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            String status = (response == ButtonType.YES) ? "ACCEPT" : "REJECT";
            out.println("VIDEO_RESPONSE:" + callerIp + ":" + status);
            if (status.equals("ACCEPT")) startVideoCall(callerIp);
        });
    }
    // Inside ClientApp.java
    private void startCall(String partnerIp) {
        this.currentPartnerIp = partnerIp;
        this.isCalling = true;
        videoEngine.startReceiving((data, len) -> {
            Image img = new Image(new ByteArrayInputStream(data, 0, len));
            Platform.runLater(() -> remoteVideo.setImage(img));
        });

        new Thread(() -> {
            Webcam webcam = Webcam.getDefault();
            webcam.setViewSize(new Dimension(320, 240));
            webcam.open();
            while (isCalling) {
                try {
                    byte[] data = videoEngine.compress(webcam.getImage(), 0.3f);
                    videoEngine.send(data, partnerIp);
                    Thread.sleep(50);
                } catch (Exception e) { break; }
            }
            webcam.close();
        }).start();
    }

    private void stopCall() {
        isCalling = false;
        videoEngine.close();
        Platform.runLater(() -> remoteVideo.setImage(null));
    }

    @Override public void onStatusUpdate(String status) {}
    @Override public void stop() throws Exception { if (socket != null) socket.close(); super.stop(); }
}
