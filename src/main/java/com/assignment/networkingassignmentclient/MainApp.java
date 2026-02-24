package com.assignment.networkingassignmentclient;

import com.assignment.networkingassignmentclient.networking.TcpClient;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
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

    @Override public void onMessageReceived(String message) {} // Handled in thread
    @Override public void onStatusUpdate(String status) {}
    @Override public void stop() throws Exception { if (socket != null) socket.close(); super.stop(); }
}
