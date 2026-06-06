package com.tankgame.client;

import com.tankgame.common.BulletState;
import com.tankgame.common.EffectState;
import com.tankgame.common.PlayerState;
import com.tankgame.common.PowerUpState;
import com.tankgame.common.ServerMessage;
import com.tankgame.common.WallState;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class TankClientApp extends Application {
    private static final double WIDTH = 1000;
    private static final double HEIGHT = 700;

    private final List<PlayerState> players = new CopyOnWriteArrayList<>();
    private final List<BulletState> bullets = new CopyOnWriteArrayList<>();
    private final List<WallState> walls = new CopyOnWriteArrayList<>();
    private final List<PowerUpState> powerUps = new CopyOnWriteArrayList<>();
    private final List<EffectState> effects = new CopyOnWriteArrayList<>();
    private final InputState inputState = new InputState();
    private final SoundManager sound = new SoundManager();
    private final Set<String> heardEffectIds = new HashSet<>();

    private Image tankBodyImage;
    private Image tankHeadImage;
    private final Map<String, Image> tankBodyImages = new HashMap<>();
    private final Map<String, Image> tankHeadImages = new HashMap<>();
    private Image bulletImage;
    private Image shot1Image;
    private Image shot2Image;
    private Image healImage;
    private Image speedImage;
    private Image rapidImage;
    private Image reticleImage;
    private Image backgroundImage;
    private Image explosionImage;
    private Image flameImage;
    private Image barrel1Image;
    private Image barrel2Image;
    private Image barrel3Image;

    private final List<Image> explosionFrames = new ArrayList<>();
    private final List<Image> flashFrames = new ArrayList<>();
    private final List<Image> flameFrames = new ArrayList<>();

    private NetworkManager network;
    private String phase = "MENU";
    private String winnerId;
    private String winnerTeam;
    private String roomCode = "ROOM1";
    private String gameMode = "PVE";
    private String serverMessage = "";
    private AnimationTimer gameLoop;
    private StackPane gameRoot;
    private VBox pauseOverlay;
    private VBox roomOverlay;
    
    // 聊天系統組件
    private VBox chatHistory;
    private TextField chatInputField;
    private VBox chatContainer;
    private boolean isChatting;
    private List<List<int[]>> currentAiPaths; // 新增：保存當前收到的 AI 路徑

    private Label roomTitleLabel;
    private Label roomStatusLabel;
    private TextFlow roomPlayersFlow;
    private Button readyButton;
    private Button redButton;
    private Button blueButton;
    private HBox teamButtons;
    private boolean paused;
    private boolean selectedReady;
    private String selectedTeam = "PLAYER";
    private String selectedTankType = "ASSAULT";
    private long lastInputSentNanos;
    private long gameStartedNanos;
    private String announcedWinnerId;

    private String savedName;
    private String savedHost;
    private String savedPort;
    private String savedRoom = "ROOM1";
    private String savedMode = "PVE";
    private String savedTankType = "ASSAULT";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        loadImages();
        createChatUI();
        savedName = defaultName();
        savedHost = defaultHost();
        savedPort = defaultPort();
        stage.setTitle("坦克車戰爭");
        stage.setResizable(false);
        stage.setFullScreenExitHint("");
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        
        // 播放開場動畫，結束後進入大廳
        showIntroCinematic(stage);
        stage.show();
    }

    private void showIntroCinematic(Stage stage) {
        File videoFile = new File("assets/videos/坦克爭霸開場.mp4");
        if (!videoFile.exists()) {
            stage.setScene(createLobbyScene(stage));
            return;
        }

        Media media = new Media(videoFile.toURI().toString());
        MediaPlayer mediaPlayer = new MediaPlayer(media);
        MediaView mediaView = new MediaView(mediaPlayer);

        // 自動縮放影片以填滿視窗
        mediaView.fitWidthProperty().bind(stage.widthProperty());
        mediaView.fitHeightProperty().bind(stage.heightProperty());
        mediaView.setPreserveRatio(true);

        StackPane root = new StackPane(mediaView);
        root.setStyle("-fx-background-color: black;");
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        
        // 點擊或按鍵可跳過
        scene.setOnKeyPressed(e -> {
            mediaPlayer.stop();
            stage.setScene(createLobbyScene(stage));
        });
        scene.setOnMouseClicked(e -> {
            mediaPlayer.stop();
            stage.setScene(createLobbyScene(stage));
        });

        // 影片播放完畢後自動進入大廳
        mediaPlayer.setOnEndOfMedia(() -> {
            Platform.runLater(() -> {
                mediaPlayer.stop();
                stage.setScene(createLobbyScene(stage));
            });
        });

        stage.setScene(scene);
        mediaPlayer.play();
    }

    private void createChatUI() {
        chatHistory = new VBox(4);
        chatHistory.setAlignment(Pos.BOTTOM_LEFT);
        chatHistory.setMouseTransparent(true);
        chatHistory.setMaxWidth(400);

        chatInputField = new TextField();
        chatInputField.setPromptText("輸入訊息並按 Enter...");
        chatInputField.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white; -fx-font-family: 'Microsoft JhengHei'; -fx-border-color: #ffca3a; -fx-border-radius: 4;");
        chatInputField.setVisible(false);
        chatInputField.setManaged(false);

        chatInputField.setOnAction(e -> {
            String text = chatInputField.getText();
            if (network != null && !text.isBlank()) {
                network.sendChat(text);
            }
            chatInputField.setText("");
            stopChatting();
        });

        chatContainer = new VBox(10, chatHistory, chatInputField);
        chatContainer.setAlignment(Pos.BOTTOM_LEFT);
        chatContainer.setPadding(new Insets(0, 0, 140, 20)); // 位置調高一點避免擋到血量
        chatContainer.setPickOnBounds(false);
        chatContainer.setMouseTransparent(false);
    }

    private void startChatting() {
        if (isChatting) return;
        isChatting = true;
        chatInputField.setVisible(true);
        chatInputField.setManaged(true);
        chatInputField.requestFocus();
        resetInput();
    }

    private void stopChatting() {
        isChatting = false;
        chatInputField.setVisible(false);
        chatInputField.setManaged(false);
        // 交回焦點給場景
    }

    private void addChatMessage(String sender, String text) {
        Platform.runLater(() -> {
            Text nameTxt = new Text(sender + ": ");
            nameTxt.setFill(Color.web("#ffca3a"));
            nameTxt.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 14));
            
            Text contentTxt = new Text(text);
            contentTxt.setFill(Color.WHITE);
            contentTxt.setFont(Font.font("Microsoft JhengHei", FontWeight.NORMAL, 14));
            
            TextFlow row = new TextFlow(nameTxt, contentTxt);
            row.setStyle("-fx-background-color: rgba(0,0,0,0.4); -fx-background-radius: 4;");
            row.setPadding(new Insets(3, 8, 3, 8));
            
            chatHistory.getChildren().add(row);
            if (chatHistory.getChildren().size() > 10) {
                chatHistory.getChildren().remove(0);
            }
            
            // 自動淡出
            javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(javafx.util.Duration.seconds(3), row);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setDelay(javafx.util.Duration.seconds(6));
            fade.setOnFinished(e -> chatHistory.getChildren().remove(row));
            fade.play();
        });
    }

    private Scene createLobbyScene(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPrefSize(WIDTH, HEIGHT);
        root.setStyle("-fx-background-color: #10151b;");

        VBox panel = new VBox(16);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(34));
        panel.setMaxWidth(500);
        panel.setStyle("-fx-background-color: rgba(9,12,16,0.92); -fx-border-color: #ffca3a; -fx-border-width: 2; -fx-background-radius: 10; -fx-border-radius: 10;");

        Label title = new Label("坦克車戰爭");
        title.setStyle("-fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 50px; -fx-font-weight: 900; -fx-text-fill: #f8f7f0;");
        Label subtitle = new Label("房間大廳：選擇 PVE 或 PVP 後加入房間");
        subtitle.setStyle("-fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 17px; -fx-font-weight: 700; -fx-text-fill: #ffca3a;");

        TextField nameField = lobbyField(savedName);
        TextField hostField = lobbyField(savedHost);
        TextField portField = lobbyField(savedPort);
        TextField roomField = lobbyField(savedRoom);
        ComboBox<String> modeBox = new ComboBox<>();
        modeBox.getItems().addAll("PVE 合作打 AI", "PVP 紅藍隊對戰");
        modeBox.setValue("PVP".equals(savedMode) ? "PVP 紅藍隊對戰" : "PVE 合作打 AI");
        modeBox.setPrefWidth(250);
        modeBox.setStyle("-fx-background-color: #1b2430; -fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 14px;");
        ComboBox<String> tankBox = new ComboBox<>();
        tankBox.getItems().addAll("突擊坦克｜平均好上手", "輕型偵查車｜高速快射", "重型坦克｜高血高傷", "狙擊坦克｜高速長射程");
        tankBox.setValue(tankChoiceLabel(savedTankType));
        tankBox.setPrefWidth(250);
        tankBox.setStyle("-fx-background-color: #1b2430; -fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 14px;");

        Label status = new Label("同機測試請輸入 127.0.0.1；雙電腦請輸入伺服器電腦的 LAN IP。");
        status.setWrapText(true);
        status.setAlignment(Pos.CENTER);
        status.setStyle("-fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 12px; -fx-text-fill: #9fb3c8;");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.setAlignment(Pos.CENTER);
        addLobbyRow(form, 0, "名稱", nameField);
        addLobbyRow(form, 1, "主機 IP", hostField);
        addLobbyRow(form, 2, "Port", portField);
        addLobbyRow(form, 3, "房間代碼", roomField);
        addLobbyRow(form, 4, "模式", modeBox);
        addLobbyRow(form, 5, "戰車", tankBox);

        Button join = new Button("加入房間");
        join.setPrefWidth(210);
        join.setPrefHeight(44);
        join.setStyle("-fx-background-color: #ffca3a; -fx-text-fill: #15191f; -fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 15px; -fx-font-weight: 900;");
        join.setOnAction(event -> joinRoom(stage, nameField, hostField, portField, roomField, modeBox, tankBox, status, join));

        Label hint = new Label("PVE：所有玩家合作打 AI。PVP：請分成紅隊與藍隊，全部準備後開始。\n按 F11 可切換全螢幕模式。");
        hint.setWrapText(true);
        hint.setAlignment(Pos.CENTER);
        hint.setStyle("-fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 12px; -fx-text-fill: #b8c0cc;");

        panel.getChildren().addAll(title, subtitle, form, join, status, hint);

        Canvas decor = new Canvas(WIDTH, HEIGHT);
        drawLobbyBackdrop(decor.getGraphicsContext2D());
        root.setCenter(new StackPane(decor, panel, chatContainer));
        
        Group scalingGroup = new Group(root);
        StackPane rootContainer = new StackPane(scalingGroup);
        rootContainer.setStyle("-fx-background-color: #10151b;");
        
        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(rootContainer, WIDTH, HEIGHT);
        } else {
            scene.setRoot(rootContainer);
        }
        
        // 縮放邏輯
        root.scaleXProperty().bind(Bindings.min(scene.widthProperty().divide(WIDTH), scene.heightProperty().divide(HEIGHT)));
        root.scaleYProperty().bind(root.scaleXProperty());
        
        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();
            if (code == KeyCode.T && !isChatting) {
                startChatting();
                event.consume();
                return;
            }
            if (isChatting) {
                if (code == KeyCode.ESCAPE) stopChatting();
                return;
            }
            if (code == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
            }
        });
        return scene;
    }

    private TextField lobbyField(String value) {
        TextField field = new TextField(value);
        field.setPrefWidth(250);
        field.setStyle("-fx-background-color: #1b2430; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #728094; -fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 14px; -fx-background-radius: 6;");
        return field;
    }

    private void addLobbyRow(GridPane form, int row, String labelText, javafx.scene.Node field) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #b8c0cc;");
        form.add(label, 0, row);
        form.add(field, 1, row);
    }

    private void joinRoom(Stage stage, TextField nameField, TextField hostField, TextField portField, TextField roomField,
                          ComboBox<String> modeBox, ComboBox<String> tankBox, Label status, Button join) {
        savedName = nameField.getText().trim().isEmpty() ? defaultName() : nameField.getText().trim();
        savedHost = hostField.getText().trim().isEmpty() ? "127.0.0.1" : hostField.getText().trim();
        savedRoom = roomField.getText().trim().isEmpty() ? "ROOM1" : roomField.getText().trim().toUpperCase();
        savedMode = modeBox.getValue() != null && modeBox.getValue().startsWith("PVP") ? "PVP" : "PVE";
        savedTankType = tankTypeFromChoice(tankBox.getValue());
        savedPort = portField.getText().trim().isEmpty() ? "7788" : portField.getText().trim();

        int port;
        try {
            port = Integer.parseInt(savedPort);
        } catch (NumberFormatException e) {
            status.setText("Port 必須是數字，例如 7788。");
            return;
        }

        selectedReady = false;
        selectedTeam = "PVP".equals(savedMode) ? "RED" : "PLAYER";
        selectedTankType = savedTankType;
        join.setDisable(true);
        status.setText("正在連線到 " + savedHost + ":" + port + " ...");
        Thread connector = new Thread(() -> {
            try {
                NetworkManager manager = new NetworkManager(savedHost, port, savedName, savedRoom, savedMode, selectedTeam, selectedTankType);
                manager.connect(this::onServerMessage);
                Platform.runLater(() -> openGameScene(stage, manager));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    join.setDisable(false);
                    status.setText("連線失敗，請確認 IP、Port、server 是否已啟動，以及 Windows 防火牆。");
                });
            }
        }, "lobby-connector");
        connector.setDaemon(true);
        connector.start();
    }

    private void openGameScene(Stage stage, NetworkManager manager) {
        network = manager;
        paused = false;
        phase = "ROOM_WAIT";
        resetInput();
        clearWorld();
        heardEffectIds.clear();
        announcedWinnerId = null;

        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gameRoot = new StackPane(canvas);
        roomOverlay = createRoomOverlay(stage);
        pauseOverlay = createPauseOverlay(stage);
        pauseOverlay.setVisible(false);
        gameRoot.getChildren().addAll(roomOverlay, pauseOverlay, chatContainer);
        
        Group scalingGroup = new Group(gameRoot);
        StackPane rootContainer = new StackPane(scalingGroup);
        rootContainer.setStyle("-fx-background-color: #05070a;"); // 背景填黑

        Scene scene = stage.getScene();
        scene.setRoot(rootContainer);
        
        // 自動縮放邏輯：保持比例並置中
        gameRoot.scaleXProperty().bind(Bindings.min(scene.widthProperty().divide(WIDTH), scene.heightProperty().divide(HEIGHT)));
        gameRoot.scaleYProperty().bind(gameRoot.scaleXProperty());

        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();
            
            // 聊天模式切換
            if (code == KeyCode.T && !isChatting) {
                startChatting();
                event.consume();
                return;
            }
            if (isChatting) {
                if (code == KeyCode.ESCAPE) stopChatting();
                return;
            }

            if (code == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                event.consume();
                return;
            }
            if (code == KeyCode.ESCAPE) {
                togglePause();
                event.consume();
                return;
            }
            if (code == KeyCode.W) inputState.up = true;
            if (code == KeyCode.S) inputState.down = true;
            if (code == KeyCode.A) inputState.left = true;
            if (code == KeyCode.D) inputState.right = true;
            if (code == KeyCode.SPACE) inputState.fire = true;
        });
        scene.setOnKeyReleased(event -> {
            KeyCode code = event.getCode();
            if (code == KeyCode.W) inputState.up = false;
            if (code == KeyCode.S) inputState.down = false;
            if (code == KeyCode.A) inputState.left = false;
            if (code == KeyCode.D) inputState.right = false;
            if (code == KeyCode.SPACE) inputState.fire = false;
        });

        // 將滑鼠監聽器移到 gameRoot 上，這樣 JavaFX 會自動幫我們轉換縮放後的座標
        gameRoot.setOnMouseMoved(event -> {
            inputState.aimX = event.getX();
            inputState.aimY = event.getY();
        });
        gameRoot.setOnMouseDragged(event -> {
            inputState.aimX = event.getX();
            inputState.aimY = event.getY();
        });
        gameRoot.setOnMousePressed(event -> inputState.shooting = true);
        gameRoot.setOnMouseReleased(event -> inputState.shooting = false);

        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // 倒數 3 秒內不發送移動與射擊指令
                boolean countingDown = gameStartedNanos > 0 && (now - gameStartedNanos) < 3_000_000_000L;
                
                if (!paused && "COMBAT".equals(phase) && !countingDown && now - lastInputSentNanos >= 50_000_000L) {
                    network.sendInput(
                            inputState.up,
                            inputState.down,
                            inputState.left,
                            inputState.right,
                            inputState.fire,
                            inputState.shooting || inputState.fire,
                            inputState.aimX,
                            inputState.aimY
                    );
                    lastInputSentNanos = now;
                }
                render(gc, network.getPlayerId(), now);
            }
        };
        gameLoop.start();
        gameStartedNanos = 0;
        updateRoomOverlay();

        canvas.requestFocus();
    }

    private VBox createRoomOverlay(Stage stage) {
        VBox panel = new VBox(16);
        panel.setAlignment(Pos.CENTER);
        panel.setMaxWidth(560);
        panel.setPadding(new Insets(30));
        panel.setStyle("-fx-background-color: rgba(5,7,10,0.90); -fx-border-color: #ffca3a; -fx-border-width: 2; -fx-background-radius: 10; -fx-border-radius: 10;");

        roomTitleLabel = new Label("房間等待中");
        roomTitleLabel.setStyle("-fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 32px; -fx-font-weight: 900; -fx-text-fill: #f8f7f0;");
        roomStatusLabel = new Label("等待 server 狀態...");
        roomStatusLabel.setWrapText(true);
        roomStatusLabel.setAlignment(Pos.CENTER);
        roomStatusLabel.setStyle("-fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 14px; -fx-text-fill: #b8c0cc;");
        roomPlayersFlow = new TextFlow();
        roomPlayersFlow.setMaxWidth(480);
        roomPlayersFlow.setTextAlignment(TextAlignment.CENTER);
        roomPlayersFlow.setLineSpacing(6);

        redButton = menuButton("加入紅隊");
        redButton.setOnAction(event -> {
            selectedTeam = "RED";
            selectedReady = false;
            sendRoomUpdate();
        });
        blueButton = menuButton("加入藍隊");
        blueButton.setOnAction(event -> {
            selectedTeam = "BLUE";
            selectedReady = false;
            sendRoomUpdate();
        });
        teamButtons = new HBox(12, redButton, blueButton);
        teamButtons.setAlignment(Pos.CENTER);

        readyButton = menuButton("準備");
        readyButton.setOnAction(event -> {
            selectedReady = !selectedReady;
            sendRoomUpdate();
        });
        Button lobby = menuButton("返回大廳");
        lobby.setOnAction(event -> returnToLobby(stage));

        Label hint = new Label("所有真人玩家都準備後會自動開始。打完一場會回到這個房間，不用重新輸入 IP。");
        hint.setWrapText(true);
        hint.setAlignment(Pos.CENTER);
        hint.setStyle("-fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 12px; -fx-text-fill: #9fb3c8;");

        panel.getChildren().addAll(roomTitleLabel, roomStatusLabel, roomPlayersFlow, teamButtons, readyButton, lobby, hint);
        return panel;
    }

    private VBox createPauseOverlay(Stage stage) {
        VBox panel = new VBox(18);
        panel.setAlignment(Pos.CENTER);
        panel.setMaxWidth(370);
        panel.setPadding(new Insets(30));
        panel.setStyle("-fx-background-color: rgba(5,7,10,0.88); -fx-border-color: #ffca3a; -fx-border-width: 2; -fx-background-radius: 10; -fx-border-radius: 10;");

        Label title = new Label("遊戲暫停");
        title.setStyle("-fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 34px; -fx-font-weight: 900; -fx-text-fill: #f8f7f0;");
        Label hint = new Label("暫停只影響本機選單，其他玩家仍會繼續遊戲。");
        hint.setWrapText(true);
        hint.setAlignment(Pos.CENTER);
        hint.setStyle("-fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 13px; -fx-text-fill: #b8c0cc;");

        Button resume = menuButton("繼續遊戲");
        resume.setOnAction(event -> setPaused(false));
        Button lobby = menuButton("返回大廳");
        lobby.setOnAction(event -> returnToLobby(stage));

        panel.getChildren().addAll(title, hint, resume, lobby);
        return panel;
    }

    private Button menuButton(String text) {
        Button button = new Button(text);
        button.setPrefWidth(190);
        button.setPrefHeight(40);
        button.setStyle("-fx-background-color: #ffca3a; -fx-text-fill: #15191f; -fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 15px; -fx-font-weight: 900;");
        return button;
    }

    private void sendRoomUpdate() {
        if (network != null) {
            network.sendRoomUpdate(selectedTeam, selectedReady);
        }
        updateRoomOverlay();
    }

    private void updateRoomOverlay() {
        if (roomOverlay == null) {
            return;
        }
        boolean waiting = "ROOM_WAIT".equals(phase);
        roomOverlay.setVisible(waiting);
        if (!waiting) {
            return;
        }
        roomTitleLabel.setText("房間 " + roomCode + "｜" + modeLabel(gameMode));
        roomStatusLabel.setText(serverMessage == null || serverMessage.isBlank() ? "等待玩家準備" : serverMessage);
        PlayerState self = findPlayer(network == null ? null : network.getPlayerId());
        if (self != null) {
            selectedTeam = self.team == null ? selectedTeam : self.team;
            selectedReady = self.ready;
        }
        teamButtons.setVisible("PVP".equals(gameMode));
        teamButtons.setManaged("PVP".equals(gameMode));
        redButton.setText("RED".equals(selectedTeam) ? "紅隊 ✓" : "加入紅隊");
        blueButton.setText("BLUE".equals(selectedTeam) ? "藍隊 ✓" : "加入藍隊");
        readyButton.setText(selectedReady ? "取消準備" : "準備");
        updateRoomPlayersFlow();
    }

    private void updateRoomPlayersFlow() {
        roomPlayersFlow.getChildren().clear();
        if (players.isEmpty()) {
            roomPlayersFlow.getChildren().add(roomText("尚未收到房間玩家資料", Color.WHITE, FontWeight.BOLD));
            return;
        }
        boolean first = true;
        for (PlayerState player : players) {
            if (player.ai) {
                continue;
            }
            if (!first) {
                roomPlayersFlow.getChildren().add(roomText("\n", Color.WHITE, FontWeight.NORMAL));
            }
            first = false;
            if (player.host) {
                roomPlayersFlow.getChildren().add(roomText("房主 ", Color.web("#ffca3a"), FontWeight.BOLD));
            }
            Color teamColor = teamColor(player.team);
            roomPlayersFlow.getChildren().add(roomText(teamLabel(player.team) + "｜", teamColor, FontWeight.EXTRA_BOLD));
            roomPlayersFlow.getChildren().add(roomText(player.name == null ? player.id : player.name, teamColor, FontWeight.EXTRA_BOLD));
            roomPlayersFlow.getChildren().add(roomText("｜" + tankTypeLabel(player.tankType), Color.web("#ffca3a"), FontWeight.BOLD));
            roomPlayersFlow.getChildren().add(roomText(player.ready ? "：已準備" : "：未準備", player.ready ? Color.web("#9ef01a") : Color.web("#b8c0cc"), FontWeight.BOLD));
        }
        if (first) {
            roomPlayersFlow.getChildren().add(roomText("等待玩家加入房間", Color.WHITE, FontWeight.BOLD));
        }
    }

    private Text roomText(String value, Color color, FontWeight weight) {
        Text text = new Text(value);
        text.setFill(color);
        text.setFont(Font.font("Microsoft JhengHei", weight, 15));
        return text;
    }

    private void togglePause() {
        setPaused(!paused);
    }

    private void setPaused(boolean value) {
        paused = value;
        if (pauseOverlay != null) {
            pauseOverlay.setVisible(paused);
        }
        if (paused) {
            resetInput();
        }
    }

    private void returnToLobby(Stage stage) {
        if (gameLoop != null) {
            gameLoop.stop();
            gameLoop = null;
        }
        if (network != null) {
            network.close();
            network = null;
        }
        clearWorld();
        resetInput();
        paused = false;
        stage.setScene(createLobbyScene(stage));
    }

    private void onServerMessage(ServerMessage msg) {
        if (msg == null) {
            return;
        }
        if ("chat".equals(msg.type)) {
            addChatMessage(msg.chatSender, msg.chatText);
            return;
        }
        if ("state".equals(msg.type)) {
            Platform.runLater(() -> {
                replace(players, msg.players);
                replace(bullets, msg.bullets);
                replace(walls, msg.walls);
                replace(powerUps, msg.powerUps);
                replace(effects, msg.effects);
                playNewEffectSounds(msg.effects);
                phase = msg.phase == null ? "COMBAT" : msg.phase;
                winnerId = msg.winnerId;
                winnerTeam = msg.winnerTeam;
                roomCode = msg.roomCode == null ? roomCode : msg.roomCode;
                gameMode = msg.gameMode == null ? gameMode : msg.gameMode;
                serverMessage = msg.message == null ? "" : msg.message;
                currentAiPaths = msg.aiPaths; // 更新 AI 路徑資料
                if ("COMBAT".equals(phase) && gameStartedNanos == 0) {
                    gameStartedNanos = System.nanoTime();
                    selectedReady = true;
                }
                if ("ROOM_WAIT".equals(phase)) {
                    gameStartedNanos = 0;
                    resetInput();
                }
                if (!"ROUND_OVER".equals(phase)) {
                    announcedWinnerId = null;
                }
                updateRoomOverlay();
                playRoundResultIfNeeded(network == null ? null : network.getPlayerId());
            });
        }
    }

    private void playNewEffectSounds(List<EffectState> incomingEffects) {
        if (incomingEffects == null) {
            return;
        }
        for (EffectState effect : incomingEffects) {
            if (effect.id == null || !heardEffectIds.add(effect.id)) {
                continue;
            }
            if ("MUZZLE".equals(effect.type)) {
                sound.shoot();
            } else if ("HIT".equals(effect.type) || "SPARK".equals(effect.type)) {
                sound.hit();
            } else if ("PICKUP".equals(effect.type)) {
                sound.pickup();
            } else if ("BOOM".equals(effect.type)) {
                sound.boom();
            }
        }
        if (heardEffectIds.size() > 300) {
            heardEffectIds.clear();
        }
    }

    private void playRoundResultIfNeeded(String selfId) {
        if (!"ROUND_OVER".equals(phase) || announcedWinnerId != null) {
            return;
        }
        announcedWinnerId = winnerTeam == null ? winnerId : winnerTeam;
        PlayerState self = findPlayer(selfId);
        boolean won = self != null && self.team != null && self.team.equals(winnerTeam);
        if (winnerTeam == null && winnerId != null) {
            won = winnerId.equals(selfId);
        }
        sound.roundResult(won);
    }

    private <T> void replace(List<T> target, List<T> source) {
        target.clear();
        if (source != null) {
            target.addAll(new ArrayList<>(source));
        }
    }

    private void render(GraphicsContext gc, String selfId, long now) {
        drawBackground(gc);
        drawArena(gc);
        drawWalls(gc);
        drawPowerUps(gc, now);
        drawBullets(gc);
        drawPlayers(gc, selfId);
        drawAiPaths(gc); // 新增：繪製尋路 Debug 線條
        drawEffects(gc);
        drawHud(gc, selfId);
        drawReticle(gc);
        drawStartHint(gc, now);
        if ("ROOM_WAIT".equals(phase)) {
            drawWaitingShade(gc);
        }
        if ("ROUND_OVER".equals(phase)) {
            drawRoundOver(gc, selfId);
        }
    }

    private void drawLobbyBackdrop(GraphicsContext gc) {
        drawBackground(gc);
        drawArena(gc);
        gc.setFill(Color.web("#06080a", 0.42));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        if (tankBodyImage != null && tankHeadImage != null) {
            drawCenteredImage(gc, tankBodyImage, 165, 500, 72, 110, -0.7, 0.42);
            drawCenteredImage(gc, tankHeadImage, 165, 500, 54, 96, -0.7, 0.58);
            drawCenteredImage(gc, tankBodyImage, 835, 190, 72, 110, 2.35, 0.35);
            drawCenteredImage(gc, tankHeadImage, 835, 190, 54, 96, 2.35, 0.50);
        }
    }

    private void drawWaitingShade(GraphicsContext gc) {
        gc.setFill(Color.web("#050608", 0.42));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
    }

    private void drawBackground(GraphicsContext gc) {
        gc.setFill(Color.web("#15191f"));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        gc.setFill(Color.web("#0f1318"));
        for (int x = 0; x < WIDTH; x += 40) {
            gc.fillRect(x, 0, 1, HEIGHT);
        }
        for (int y = 0; y < HEIGHT; y += 40) {
            gc.fillRect(0, y, WIDTH, 1);
        }
    }

    private void drawArena(GraphicsContext gc) {
        if (backgroundImage != null) {
            gc.drawImage(backgroundImage, 24, 24, WIDTH - 48, HEIGHT - 48);
            return;
        }
        gc.setFill(Color.web("#21452d"));
        gc.fillRect(24, 24, WIDTH - 48, HEIGHT - 48);
        gc.setFill(Color.web("#2f5b39"));
        for (int y = 42; y < HEIGHT - 36; y += 48) {
            for (int x = 42; x < WIDTH - 36; x += 48) {
                if ((x + y) % 96 == 0) {
                    gc.fillRect(x, y, 18, 2);
                    gc.fillRect(x + 8, y + 8, 2, 16);
                }
            }
        }
    }

    private void drawWalls(GraphicsContext gc) {
        for (WallState wall : walls) {
            // 如果牆壁尺寸較小，將其繪製為油桶裝飾
            if (wall.w <= 50 && wall.h <= 50 && barrel1Image != null) {
                int hash = (int)(wall.x * 31 + wall.y);
                int type = Math.abs(hash % 3);
                Image barrel = (type == 0) ? barrel1Image : (type == 1 ? barrel2Image : barrel3Image);
                // 置中繪製油桶
                gc.drawImage(barrel, wall.x + (wall.w - 38)/2, wall.y + (wall.h - 38)/2, 38, 38);
                continue;
            }
            gc.setFill(Color.web("#41464f"));
            gc.fillRoundRect(wall.x, wall.y, wall.w, wall.h, 8, 8);
            gc.setStroke(Color.web("#747b86"));
            gc.setLineWidth(2);
            gc.strokeRoundRect(wall.x + 2, wall.y + 2, wall.w - 4, wall.h - 4, 6, 6);
        }
    }

    private void drawPowerUps(GraphicsContext gc, long now) {
        double bob = Math.sin(now / 180_000_000.0) * 2.5;
        for (PowerUpState p : powerUps) {
            gc.setFill(Color.web("#ffffff", 0.18));
            gc.fillOval(p.x - 20, p.y - 20 + bob, 40, 40);
            Image powerImage = powerImage(p.type);
            if (powerImage != null) {
                drawCenteredImage(gc, powerImage, p.x, p.y + bob, 32, 32, 0, 1);
            } else {
                gc.setFill(powerColor(p.type));
                gc.fillRoundRect(p.x - 13, p.y - 13 + bob, 26, 26, 7, 7);
            }
        }
    }

    private Color powerColor(String type) {
        if ("SPEED".equals(type)) return Color.web("#ffca3a");
        if ("HEAL".equals(type)) return Color.web("#2ec4b6");
        return Color.web("#fb5607");
    }

    private Image powerImage(String type) {
        if ("HEAL".equals(type)) return healImage;
        if ("SPEED".equals(type)) return speedImage;
        if ("RAPID".equals(type)) return rapidImage;
        return null;
    }

    private void drawBullets(GraphicsContext gc) {
        for (BulletState b : bullets) {
            double angle = Math.atan2(b.vy, b.vx);
            
            // 繪製砲彈尾流 (Wake) - 再次放大兩倍 (48x120)
            if (flameImage != null) {
                drawCenteredImage(gc, flameImage, b.x - Math.cos(angle) * 15, b.y - Math.sin(angle) * 15, 48, 120, angle + Math.PI / 2, 0.5);
            }
            
            gc.setStroke(Color.web("#ffb703", 0.30));
            gc.setLineWidth(4);
            gc.strokeLine(b.x - Math.cos(angle) * 18, b.y - Math.sin(angle) * 18, b.x, b.y);
            if (bulletImage != null) {
                // 砲彈再次放大兩倍 (28x104)
                drawCenteredImage(gc, bulletImage, b.x, b.y, 28, 104, angle + Math.PI / 2, 1);
            } else {
                gc.setFill(Color.web("#fff3b0"));
                gc.fillOval(b.x - 4, b.y - 4, 8, 8);
            }
        }
    }

    private void drawReticle(GraphicsContext gc) {
        if (!"COMBAT".equals(phase) || paused) {
            return;
        }
        double x = inputState.aimX;
        double y = inputState.aimY;
        if (reticleImage != null) {
            drawCenteredImage(gc, reticleImage, x, y, 38, 38, 0, 0.82);
            return;
        }
        gc.setStroke(Color.web("#050608", 0.90));
        gc.setLineWidth(3);
        gc.strokeOval(x - 12, y - 12, 24, 24);
        gc.strokeLine(x - 20, y, x - 8, y);
        gc.strokeLine(x + 8, y, x + 20, y);
        gc.strokeLine(x, y - 20, x, y - 8);
        gc.strokeLine(x, y + 8, x, y + 20);
        gc.setStroke(Color.web("#ffca3a", 0.82));
        gc.setLineWidth(1.5);
        gc.strokeOval(x - 12, y - 12, 24, 24);
    }

    private void drawPlayers(GraphicsContext gc, String selfId) {
        for (PlayerState p : players) {
            if (!p.alive) {
                drawWreck(gc, p);
                continue;
            }
            boolean self = p.id != null && p.id.equals(selfId);
            boolean hitFlash = isNearFreshEffect(p, "HIT", 45) || isNearFreshEffect(p, "BOOM", 60);
            Color ring = Color.web(self ? "#ffca3a" : p.color == null ? "#4cc9f0" : p.color, self ? 0.34 : 0.22);
            gc.setFill(ring);
            gc.fillOval(p.x - 27, p.y - 27, 54, 54);
            Image body = tankBodyFor(p.tankType);
            Image head = tankHeadFor(p.tankType);
            if (body != null && head != null) {
                double scale = "HEAVY".equals(p.tankType) ? 1.15 : "SCOUT".equals(p.tankType) ? 0.92 : 1.0;
                // 車身使用 bodyAngle (移動方向)
                drawCenteredImage(gc, body, p.x, p.y, 39 * scale, 58 * scale, p.bodyAngle + Math.PI / 2, 1);
                // 砲塔使用 angle (瞄準方向)
                drawCenteredImage(gc, head, p.x, p.y, 31 * scale, 55 * scale, p.angle + Math.PI / 2, 1);
            } else {
                drawFallbackTank(gc, p, self);
            }
            if (hitFlash) {
                gc.setFill(Color.web("#ffffff", 0.42));
                gc.fillOval(p.x - 25, p.y - 25, 50, 50);
            }
            drawHealthBar(gc, p);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 11));
            gc.setStroke(Color.web("#050608", 0.82));
            gc.setLineWidth(3);
            gc.strokeText(displayName(p), p.x, p.y - 31);
            gc.setFill(teamColor(p.team));
            gc.fillText(displayName(p), p.x, p.y - 31);
        }
    }

    private String displayName(PlayerState p) {
        return (p.name == null ? p.id : p.name) + (p.ai ? "" : " " + teamLabel(p.team));
    }

    private void drawFallbackTank(GraphicsContext gc, PlayerState p, boolean self) {
        gc.save();
        gc.translate(p.x, p.y);
        
        // 車身部分旋轉
        gc.save();
        gc.rotate(Math.toDegrees(p.bodyAngle));
        gc.setFill(Color.web(p.color == null ? "#4cc9f0" : p.color));
        gc.fillRoundRect(-19, -15, 38, 30, 9, 9);
        gc.restore();
        
        // 砲塔部分旋轉 (瞄準方向)
        gc.rotate(Math.toDegrees(p.angle));
        gc.setFill(Color.web(self ? "#fff1a8" : "#232933"));
        gc.fillRoundRect(-6, -10, 35, 20, 7, 7);
        gc.setFill(Color.web("#1c222b"));
        gc.fillRoundRect(8, -4, 29, 8, 4, 4);
        
        gc.restore();
    }

    private void drawWreck(GraphicsContext gc, PlayerState p) {
        gc.save();
        gc.translate(p.x, p.y);
        gc.rotate(Math.toDegrees(p.bodyAngle));
        gc.setFill(Color.web("#191c21"));
        gc.fillRoundRect(-18, -14, 36, 28, 8, 8);
        gc.setStroke(Color.web("#3b414c"));
        gc.setLineWidth(4);
        gc.strokeLine(-15, -12, 15, 12);
        gc.strokeLine(15, -12, -15, 12);
        gc.restore();
    }

    private void drawHealthBar(GraphicsContext gc, PlayerState p) {
        double w = 42;
        double pct = p.maxHp <= 0 ? 0 : p.hp / (double) p.maxHp;
        gc.setFill(Color.web("#07090c", 0.75));
        gc.fillRoundRect(p.x - w / 2, p.y + 25, w, 6, 3, 3);
        gc.setFill(pct > 0.55 ? Color.web("#3ddc84") : pct > 0.25 ? Color.web("#ffca3a") : Color.web("#ef476f"));
        gc.fillRoundRect(p.x - w / 2, p.y + 25, w * pct, 6, 3, 3);
    }

    private void drawAiPaths(GraphicsContext gc) {
        if (currentAiPaths == null || currentAiPaths.isEmpty()) return;
        
        gc.setStroke(Color.web("#9ef01a", 0.4));
        gc.setLineWidth(2);
        
        for (List<int[]> path : currentAiPaths) {
            if (path == null || path.size() < 2) continue;
            
            gc.beginPath();
            // GRID_SIZE 20, 偏移 10 到格子中心
            gc.moveTo(path.get(0)[0] * 20 + 10, path.get(0)[1] * 20 + 10);
            for (int i = 1; i < path.size(); i++) {
                gc.lineTo(path.get(i)[0] * 20 + 10, path.get(i)[1] * 20 + 10);
            }
            gc.stroke();
            
            // 繪製路徑點
            for (int[] node : path) {
                gc.setFill(Color.web("#9ef01a", 0.6));
                gc.fillOval(node[0] * 20 + 8, node[1] * 20 + 8, 4, 4);
            }
        }
    }

    private void drawEffects(GraphicsContext gc) {
        for (EffectState e : effects) {
            double alpha = Math.min(1.0, Math.max(0.12, e.lifeTicks / 28.0));
            
            if ("MUZZLE".equals(e.type)) {
                // 開火動畫播放 - 使用 EffectState 帶入的角度
                Image frame = getAnimationFrame(flashFrames, e.lifeTicks, 6);
                if (frame != null) {
                    drawCenteredImage(gc, frame, e.x, e.y, 48, 136, e.angle + Math.PI / 2, alpha);
                }
                
            } else if ("HIT".equals(e.type) || "SPARK".equals(e.type)) {
                // 擊中火花播放 - 使用 EffectState 帶入的角度
                Image frame = getAnimationFrame(explosionFrames, e.lifeTicks, 14);
                if (frame != null) {
                    drawCenteredImage(gc, frame, e.x, e.y, 96, 160, e.angle + Math.PI / 2, alpha);
                }
                
            } else if ("BOOM".equals(e.type)) {
                // 爆炸大動畫播放 (爆炸通常不需特定旋轉，可隨機或固定)
                Image frame = getAnimationFrame(explosionFrames, e.lifeTicks, 28);
                if (frame != null) {
                    drawCenteredImage(gc, frame, e.x, e.y, 360, 360, e.angle, alpha);
                } else {
                    gc.setFill(Color.web("#fb5607", alpha * 0.28));
                    gc.fillOval(e.x - e.radius, e.y - e.radius, e.radius * 2, e.radius * 2);
                    gc.setStroke(Color.web("#ffca3a", alpha));
                    gc.setLineWidth(4);
                    gc.strokeOval(e.x - e.radius * 0.65, e.y - e.radius * 0.65, e.radius * 1.3, e.radius * 1.3);
                }
            } else {
                gc.setStroke(Color.web("#fff3b0", alpha));
                gc.setLineWidth(3);
                gc.strokeOval(e.x - e.radius / 2, e.y - e.radius / 2, e.radius, e.radius);
            }
            
            if ("PICKUP".equals(e.type) && e.label != null) {
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                gc.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 17));
                gc.setFill(Color.web("#ffca3a", alpha));
                gc.fillText(e.label, e.x, e.y - 32 - (24 - e.lifeTicks));
            }
        }
    }

    private Image getAnimationFrame(List<Image> frames, int currentLife, int maxLife) {
        if (frames.isEmpty()) return null;
        // 根據剩餘生命計算應該播放哪一幀 (倒著播或正著播，這裡假設 maxLife 是起始點)
        int totalFrames = frames.size();
        int frameIndex = (maxLife - currentLife) * totalFrames / maxLife;
        frameIndex = Math.max(0, Math.min(totalFrames - 1, frameIndex));
        return frames.get(frameIndex);
    }

    private boolean isNearFreshEffect(PlayerState player, String type, double distance) {
        for (EffectState effect : effects) {
            if (type.equals(effect.type) && effect.lifeTicks > 6 && Math.hypot(player.x - effect.x, player.y - effect.y) <= distance) {
                return true;
            }
        }
        return false;
    }

    private void drawHud(GraphicsContext gc, String selfId) {
        PlayerState self = findPlayer(selfId);
        long humans = players.stream().filter(p -> !p.ai).count();
        long alive = players.stream().filter(p -> p.alive).count();
        gc.setFill(Color.web("#07090c", 0.72));
        gc.fillRoundRect(32, 32, 420, 72, 8, 8);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);
        gc.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 18));
        gc.setFill(Color.WHITE);
        gc.fillText(self == null ? "房間中" : self.name, 48, 42);
        gc.setFont(Font.font("Microsoft JhengHei", FontWeight.NORMAL, 13));
        gc.setFill(Color.web("#b8c0cc"));
        String hp = self == null ? "--" : self.hp + "/" + self.maxHp;
        gc.fillText("房間 " + roomCode + "｜" + modeLabel(gameMode) + "｜" + teamLabel(self == null ? selectedTeam : self.team), 48, 67);
        gc.fillText("血量 " + hp + "    真人 " + humans + "    存活 " + alive + "    擊破 " + (self == null ? 0 : self.score), 48, 86);

        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 14));
        gc.setFill(Color.web("#ffca3a"));
        gc.fillText("坦克車戰爭", WIDTH - 34, 36);
    }

    private void drawRoundOver(GraphicsContext gc, String selfId) {
        PlayerState self = findPlayer(selfId);
        boolean won = self != null && self.team != null && self.team.equals(winnerTeam);
        if (winnerTeam == null && winnerId != null) {
            won = winnerId.equals(selfId);
        }
        gc.setFill(Color.web("#050608", 0.62));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFont(Font.font("Microsoft JhengHei", FontWeight.EXTRA_BOLD, 48));
        gc.setFill(won ? Color.web("#ffca3a") : Color.web("#ef476f"));
        gc.fillText(won ? "勝利" : "失敗", WIDTH / 2, HEIGHT / 2 - 30);
        gc.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 21));
        gc.setFill(Color.WHITE);
        gc.fillText(teamLabel(winnerTeam) + " 獲勝", WIDTH / 2, HEIGHT / 2 + 28);
        gc.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 17));
        gc.setFill(Color.web("#b8c0cc"));
        gc.fillText("你的擊破數：" + (self == null ? 0 : self.score) + "    稍後會回到房間準備畫面", WIDTH / 2, HEIGHT / 2 + 72);
    }

    private void drawStartHint(GraphicsContext gc, long now) {
        if (gameStartedNanos == 0 || !"COMBAT".equals(phase)) {
            return;
        }
        
        long elapsedNanos = now - gameStartedNanos;
        if (elapsedNanos > 4_000_000_000L) {
            return;
        }

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);

        if (elapsedNanos < 3_000_000_000L) {
            // 3, 2, 1 倒數
            int secondsLeft = 3 - (int)(elapsedNanos / 1_000_000_000L);
            
            // 加上半透明遮罩
            gc.setFill(Color.web("#000000", 0.3));
            gc.fillRect(0, 0, WIDTH, HEIGHT);
            
            // 倒數文字動畫效果 (縮放感)
            double pulse = 1.0 + 0.2 * Math.sin((elapsedNanos % 1_000_000_000L) / 1_000_000_000.0 * Math.PI);
            gc.setFont(Font.font("Microsoft JhengHei", FontWeight.EXTRA_BOLD, 120 * pulse));
            gc.setFill(Color.web("#ffca3a"));
            gc.fillText(String.valueOf(secondsLeft), WIDTH / 2, HEIGHT / 2);
            
            gc.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 20));
            gc.setFill(Color.WHITE);
            gc.fillText("準備戰鬥！ (按 T 聊天)", WIDTH / 2, HEIGHT / 2 + 100);
        } else {
            // GO!
            double alpha = 1.0 - (elapsedNanos - 3_000_000_000L) / 1_000_000_000.0;
            gc.setFont(Font.font("Microsoft JhengHei", FontWeight.EXTRA_BOLD, 100));
            gc.setFill(Color.web("#9ef01a", alpha));
            gc.fillText("GO!", WIDTH / 2, HEIGHT / 2);
        }
    }

    private PlayerState findPlayer(String id) {
        if (id == null) {
            return null;
        }
        for (PlayerState player : players) {
            if (id.equals(player.id)) {
                return player;
            }
        }
        return null;
    }

    private String teamLabel(String team) {
        if ("RED".equals(team)) return "紅隊";
        if ("BLUE".equals(team)) return "藍隊";
        if ("ENEMY".equals(team)) return "敵方";
        return "玩家隊";
    }

    private String tankTypeLabel(String type) {
        if ("SCOUT".equals(type)) return "輕型偵查車";
        if ("HEAVY".equals(type)) return "重型坦克";
        if ("SNIPER".equals(type)) return "狙擊坦克";
        return "突擊坦克";
    }

    private String tankChoiceLabel(String type) {
        if ("SCOUT".equals(type)) return "輕型偵查車｜高速快射";
        if ("HEAVY".equals(type)) return "重型坦克｜高血高傷";
        if ("SNIPER".equals(type)) return "狙擊坦克｜高速長射程";
        return "突擊坦克｜平均好上手";
    }

    private String tankTypeFromChoice(String choice) {
        if (choice == null) return "ASSAULT";
        if (choice.startsWith("輕型")) return "SCOUT";
        if (choice.startsWith("重型")) return "HEAVY";
        if (choice.startsWith("狙擊")) return "SNIPER";
        return "ASSAULT";
    }

    private Color teamColor(String team) {
        if ("RED".equals(team)) return Color.web("#ff4d5e");
        if ("BLUE".equals(team)) return Color.web("#4cc9f0");
        if ("ENEMY".equals(team)) return Color.web("#8ecae6");
        return Color.web("#ffca3a");
    }

    private String modeLabel(String mode) {
        return "PVP".equals(mode) ? "PVP 紅藍對戰" : "PVE 合作打 AI";
    }

    private void loadImages() {
        backgroundImage = loadImage("Item_Etc/Background_Sample.png", WIDTH, HEIGHT);
        
        tankBodyImage = loadImage("Tank/M1_Bot.png", 90, 140);
        tankHeadImage = loadImage("Tank/M1_Top.png", 70, 125);
        
        loadTankVariant("ASSAULT", "Tank/M1_Bot.png", "Tank/M1_Top.png");
        loadTankVariant("SCOUT", "Tank/L21_Bot.png", "Tank/L21_Top.png");
        loadTankVariant("HEAVY", "Tank/KV1_Bot.png", "Tank/KV1_Top.png");
        loadTankVariant("SNIPER", "Tank/PT1_Bot.png", "Tank/PT1_Top.png");
        
        bulletImage = loadImage("Effects/Granade_Shell.png", 0, 0);
        
        // 載入開火動畫 Flash_A_01 ~ 05 (原始尺寸)
        loadSequence(flashFrames, "Effects/Flash_A_0", 1, 5, 0, 0);
        shot1Image = flashFrames.isEmpty() ? null : flashFrames.get(0);
        
        // 載入擊中/爆炸序列 Explosion_A ~ H (原始尺寸)
        loadSequence(explosionFrames, "Effects/Explosion_", 'A', 'H', 0, 0);
        explosionImage = explosionFrames.isEmpty() ? null : explosionFrames.get(0);
        
        // 載入尾流序列 Flame_A ~ H (原始尺寸)
        loadSequence(flameFrames, "Effects/Flame_", 'A', 'H', 0, 0);
        flameImage = flameFrames.isEmpty() ? null : flameFrames.get(0);
        
        healImage = loadImage("Item_Etc/thumb_item_Fix_1.png", 40, 40);
        speedImage = loadImage("Item_Etc/thumb_item_Booster.png", 40, 40);
        rapidImage = loadImage("Item_Etc/thumb_item_Speed.png", 40, 40);
        reticleImage = loadImage("Effects/+Black.png", 0, 0); // 載入原始尺寸
        
        barrel1Image = loadImage("Item_Etc/prop_Barrel_1.png", 0, 0);
        barrel2Image = loadImage("Item_Etc/prop_Barrel_2.png", 0, 0);
        barrel3Image = loadImage("Item_Etc/prop_Barrel_3.png", 0, 0);
    }

    private void loadSequence(List<Image> target, String prefix, int start, int end, double w, double h) {
        target.clear();
        for (int i = start; i <= end; i++) {
            Image img = loadImage(prefix + i + ".png", w, h);
            if (img != null) target.add(img);
        }
    }

    private void loadSequence(List<Image> target, String prefix, char start, char end, double w, double h) {
        target.clear();
        for (char i = start; i <= end; i++) {
            Image img = loadImage(prefix + i + ".png", w, h);
            if (img != null) target.add(img);
        }
    }

    private void loadTankVariant(String type, String bodyName, String headName) {
        Image body = loadImage(bodyName, 90, 140);
        Image head = loadImage(headName, 70, 125);
        if (body != null) {
            tankBodyImages.put(type, body);
        }
        if (head != null) {
            tankHeadImages.put(type, head);
        }
    }

    private Image tankBodyFor(String type) {
        Image image = tankBodyImages.get(type == null ? "ASSAULT" : type);
        return image == null ? tankBodyImage : image;
    }

    private Image tankHeadFor(String type) {
        Image image = tankHeadImages.get(type == null ? "ASSAULT" : type);
        return image == null ? tankHeadImage : image;
    }

    private Image firstImage(double requestedWidth, double requestedHeight, String... names) {
        for (String name : names) {
            Image image = loadImage(name, requestedWidth, requestedHeight);
            if (image != null) {
                return image;
            }
        }
        return null;
    }

    private Image loadImage(String path, double requestedWidth, double requestedHeight) {
        File file = new File("assets/images", path);
        if (!file.exists()) {
            return null;
        }
        Image image = new Image(file.toURI().toString(), requestedWidth, requestedHeight, true, true);
        return image.isError() ? null : image;
    }

    private void drawCenteredImage(GraphicsContext gc, Image image, double x, double y, double w, double h, double angle, double alpha) {
        if (image == null) {
            return;
        }
        
        double imgW = image.getWidth();
        double imgH = image.getHeight();
        
        // 自動等比例縮放邏輯
        double scale = Math.min(w / imgW, h / imgH);
        // 使用 Math.ceil 並增加 0.5 像素緩衝，防止在邊界處因浮點數誤差被裁切
        double drawW = Math.ceil(imgW * scale) + 0.5;
        double drawH = Math.ceil(imgH * scale) + 0.5;

        gc.save();
        gc.setGlobalAlpha(alpha);
        gc.translate(x, y);
        gc.rotate(Math.toDegrees(angle));
        // 使用整數座標繪製，減少模糊與裁切感
        gc.drawImage(image, -drawW / 2, -drawH / 2, drawW, drawH);
        gc.restore();
    }

    private String defaultName() {
        return "玩家" + (int) (Math.random() * 90 + 10);
    }

    private String defaultHost() {
        String prop = System.getProperty("tank.host");
        if (prop != null && !prop.isBlank()) return prop;
        String env = System.getenv("TANK_HOST");
        if (env != null && !env.isBlank()) return env;
        return "127.0.0.1";
    }

    private String defaultPort() {
        String prop = System.getProperty("tank.port");
        if (prop != null && !prop.isBlank()) return prop;
        String env = System.getenv("TANK_PORT");
        if (env != null && !env.isBlank()) return env;
        return "7788";
    }

    private static class InputState {
        boolean up;
        boolean down;
        boolean left;
        boolean right;
        boolean fire;
        boolean shooting;
        double aimX = WIDTH / 2;
        double aimY = HEIGHT / 2;
    }

    private void clearWorld() {
        players.clear();
        bullets.clear();
        walls.clear();
        powerUps.clear();
        effects.clear();
        heardEffectIds.clear();
        phase = "MENU";
        winnerId = null;
        winnerTeam = null;
        announcedWinnerId = null;
        serverMessage = "";
        gameStartedNanos = 0;
    }

    private void resetInput() {
        inputState.up = false;
        inputState.down = false;
        inputState.left = false;
        inputState.right = false;
        inputState.fire = false;
        inputState.shooting = false;
        inputState.aimX = WIDTH / 2;
        inputState.aimY = HEIGHT / 2;
    }
}
