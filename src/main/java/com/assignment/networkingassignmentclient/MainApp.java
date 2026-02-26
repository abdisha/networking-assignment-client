package com.assignment.networkingassignmentclient;

import com.assignment.networkingassignmentclient.call.VideoEngine;
import com.github.sarxos.webcam.Webcam;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.stream.Stream;

public class MainApp extends Application {
    private Socket socket;
    private PrintWriter out;
    private String currentClientIdentifier;
    private final VideoEngine videoEngine = new VideoEngine(7000);

    private String currentUsername="";
    private String currentPartnerIp;
    private volatile boolean isCalling = false;

    private final ObservableList<String> onlineUsers = FXCollections.observableArrayList();
    private final TextArea chatArea = new TextArea();
    private final TextField inputField = new TextField();
    private final ImageView remoteVideoView = new ImageView();
    private final Label selectedUserLabel = new Label("No user selected");
    private final Button callBtn = new Button("Call");
    private final Button hangUpBtn = new Button("Hang Up");

    @Override
    public void start(Stage primaryStage) {

        Pair<String, String> details = promptForConnectionDetails();
        if (details == null) {
            Platform.exit();
            return;
        }

        String serverIp = details.getKey();
        String username = details.getValue();
        currentUsername = username;
        chatArea.setEditable(false);
        remoteVideoView.setFitWidth(320);
        remoteVideoView.setPreserveRatio(true);

        // Setup User List
        ListView<String> userListView = new ListView<>(onlineUsers);
        userListView.setPrefWidth(200);
        userListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedUserLabel.setText("Selected: " + newVal);
                callBtn.setDisable(false);
            }
        });

        // --- 2. Button Logic ---
        callBtn.setDisable(true);
        callBtn.setOnAction(e -> {
            String target = userListView.getSelectionModel().getSelectedItem();
            if (target != null) {
                out.println("VIDEO_INVITE:" + target);
                chatArea.appendText("Calling " + target + "...\n");
            }
        });

        hangUpBtn.setOnAction(e -> {
            if (currentPartnerIp != null) {
                out.println("VIDEO_HANGUP:" + currentPartnerIp);
                chatArea.appendText("Hanging up...\n");
                stopCall();
            }
        });

        inputField.setOnAction(e -> {
            String selected = userListView.getSelectionModel().getSelectedItem();
            String msg = inputField.getText();
            if (selected != null) {
                out.println("PRIVATE_MSG:" + selected + ":" + msg);
            } else {
                out.println(msg);
            }
            inputField.clear();
        });

        VBox chatPane = new VBox(10, new Label("Messaging"), chatArea, inputField);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        VBox videoPane = new VBox(10, new Label("Video Stream"), remoteVideoView,
                new HBox(10, selectedUserLabel, callBtn, hangUpBtn));
        videoPane.setAlignment(Pos.CENTER);

        HBox mainLayout = new HBox(15, new VBox(10, new Label("Online Users"), userListView), chatPane, videoPane);
        mainLayout.setPadding(new Insets(15));

        connect(username,serverIp);

        primaryStage.setTitle("TCP/UDP Multimedia Client");
        primaryStage.setScene(new Scene(mainLayout, 950, 500));
        primaryStage.show();
    }

    private void connect(String username,String serverIpAddress) {
        new Thread(() -> {
            try {
                socket = new Socket(serverIpAddress, 65432);
                out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out.println("LOGIN:" + username);
                // Identify ourselves to filter out our own name from the list
                currentClientIdentifier = socket.getLocalAddress().toString().substring(1) + ":" + socket.getLocalPort();

                Platform.runLater(() -> chatArea.appendText("System: Connected to server.\n"));

                String line;
                while ((line = in.readLine()) != null) {
                    handleIncomingMessage(line);
                }
            } catch (IOException e) {
                Platform.runLater(() -> chatArea.appendText("System: Connection failed.\n"));
            }
        }).start();
    }

    private void handleIncomingMessage(String msg) {
        System.out.println("User: "+currentUsername+"Incoming Message: "+msg);
        Platform.runLater(() -> {
            System.out.println(msg);
            if (msg.startsWith("USER_LIST:")) {
                // Filter the list so we don't see ourselves
                List<String> users = Stream.of(msg.substring(10).split(","))
                        .filter(u -> !u.contains(currentUsername))
                        .toList();
                onlineUsers.setAll(users);
            }
            else if (msg.startsWith("VIDEO_PROMPT:")) {
                String caller = msg.split(":")[1] + ":" + msg.split(":")[2];
                handleInboundCall(caller);
            }
            else if (msg.startsWith("VIDEO_RESULT:")) {
                String[] parts = msg.split(":");
                String partner = parts[1] + ":" + parts[2];
                String status = parts[3];

                if (status.equals("ACCEPT")) {
                    chatArea.appendText("System: Call accepted. Starting video...\n");
                    startCall(partner); // <--- THIS TRIGGERS THE SENDING LOOP
                } else {
                    chatArea.appendText("System: Call rejected.\n");
                }
            }
            else if (msg.equals("VIDEO_TERMINATED")) {
                stopCall();
            }
            else {
                chatArea.appendText(msg + "\n");
                chatArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }

    private void handleInboundCall(String callerIp) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Inbound call from " + callerIp, ButtonType.YES, ButtonType.NO);
        alert.setTitle("Incoming Video Call");
        alert.setHeaderText(null);
        alert.showAndWait().ifPresent(response -> {
            String status = (response == ButtonType.YES) ? "ACCEPT" : "REJECT";
            out.println("VIDEO_RESPONSE:" + callerIp + ":" + status);
            if (status.equals("ACCEPT")) startCall(callerIp);
        });
    }

    private void startCall(String partnerIp) {
        if (isCalling) return;

        // Ensure the IP is clean (remove any potential leading slashes)
        final String cleanPartnerIp = partnerIp.replace("/", "");
        this.currentPartnerIp = cleanPartnerIp;
        System.out.println("current partner ip"+currentPartnerIp);
        this.isCalling = true;

        // A. Start Receiver
        videoEngine.startReceiving((data, len) -> {
            try {
                Image img = new Image(new ByteArrayInputStream(data, 0, len));
                Platform.runLater(() -> remoteVideoView.setImage(img));
            } catch (Exception e) {
                System.err.println("Image Error: " + e.getMessage());
            }
        });

        // B. Start Sender
        new Thread(() -> {
            Webcam webcam = Webcam.getDefault();
            if (webcam == null) return;

            try {
                webcam.setViewSize(new Dimension(320, 240));
                webcam.open();

                while (isCalling) {
                    BufferedImage frame = webcam.getImage();
                    // Check isCalling again inside the loop to prevent "Socket Closed"
                    if (frame != null && isCalling) {
                        byte[] packetData = videoEngine.compress(frame, 0.3f);
                        videoEngine.send(packetData, cleanPartnerIp);
                    }
                    Thread.sleep(50);
                }
            } catch (Exception e) {
                System.out.println("Webcam Thread Error: " + e.getMessage());
            } finally {
                if (webcam.isOpen()) webcam.close();
            }
        }).start();
    }

    private void stopCall() {
        isCalling = false;
        new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            videoEngine.close(); // 3. Now safe to close the socket
        }).start();

        Platform.runLater(() -> {
            remoteVideoView.setImage(null);
            currentPartnerIp = null;
        });
    }
    private Pair<String, String> promptForConnectionDetails() {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Login");

        ButtonType loginButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField ip = new TextField("192.168.1.3");
        TextField username = new TextField("User" + (int)(Math.random()*100));

        grid.add(new Label("Server IP:"), 0, 0);
        grid.add(ip, 1, 0);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(username, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new Pair<>(ip.getText(), username.getText());
            }
            return null;
        });

        return dialog.showAndWait().orElse(null);
    }


    @Override
    public void stop() throws Exception {
        stopCall();
        if (socket != null) socket.close();
        super.stop();
    }

    public static void main(String[] args) { launch(args); }
}