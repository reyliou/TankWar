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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
        savedName = defaultName();
        savedHost = defaultHost();
        savedPort = defaultPort();
        stage.setTitle("坦克車戰爭");
        stage.setResizable(false);
        stage.setScene(createLobbyScene(stage));
        stage.show();
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

        Label hint = new Label("PVE：所有玩家合作打 AI。PVP：請分成紅隊與藍隊，全部準備後開始。");
        hint.setWrapText(true);
        hint.setAlignment(Pos.CENTER);
        hint.setStyle("-fx-font-family: Microsoft JhengHei, Verdana; -fx-font-size: 12px; -fx-text-fill: #b8c0cc;");

        panel.getChildren().addAll(title, subtitle, form, join, status, hint);

        Canvas decor = new Canvas(WIDTH, HEIGHT);
        drawLobbyBackdrop(decor.getGraphicsContext2D());
        root.setCenter(new StackPane(decor, panel));
        return new Scene(root, WIDTH, HEIGHT);
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
        gameRoot.getChildren().addAll(roomOverlay, pauseOverlay);
        Scene scene = new Scene(gameRoot, WIDTH, HEIGHT);

        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();
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
        scene.setOnMouseMoved(event -> {
            inputState.aimX = event.getX();
            inputState.aimY = event.getY();
        });
        scene.setOnMouseDragged(event -> {
            inputState.aimX = event.getX();
            inputState.aimY = event.getY();
        });
        scene.setOnMousePressed(event -> inputState.shooting = true);
        scene.setOnMouseReleased(event -> inputState.shooting = false);

        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!paused && "COMBAT".equals(phase) && now - lastInputSentNanos >= 50_000_000L) {
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

        stage.setScene(scene);
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
        drawEffects(gc);
        drawHud(gc, selfId);
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
            gc.setStroke(Color.web("#ffb703", 0.30));
            gc.setLineWidth(4);
            gc.strokeLine(b.x - Math.cos(angle) * 18, b.y - Math.sin(angle) * 18, b.x, b.y);
            if (bulletImage != null) {
                drawCenteredImage(gc, bulletImage, b.x, b.y, 7, 26, angle + Math.PI / 2, 1);
            } else {
                gc.setFill(Color.web("#fff3b0"));
                gc.fillOval(b.x - 4, b.y - 4, 8, 8);
            }
        }
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
                drawCenteredImage(gc, body, p.x, p.y, 39 * scale, 58 * scale, p.angle + Math.PI / 2, 1);
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
        gc.rotate(Math.toDegrees(p.angle));
        gc.setFill(Color.web(p.color == null ? "#4cc9f0" : p.color));
        gc.fillRoundRect(-19, -15, 38, 30, 9, 9);
        gc.setFill(Color.web(self ? "#fff1a8" : "#232933"));
        gc.fillRoundRect(-6, -10, 35, 20, 7, 7);
        gc.setFill(Color.web("#1c222b"));
        gc.fillRoundRect(8, -4, 29, 8, 4, 4);
        gc.restore();
    }

    private void drawWreck(GraphicsContext gc, PlayerState p) {
        gc.save();
        gc.translate(p.x, p.y);
        gc.rotate(Math.toDegrees(p.angle));
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

    private void drawEffects(GraphicsContext gc) {
        for (EffectState e : effects) {
            double alpha = Math.min(1.0, Math.max(0.12, e.lifeTicks / 28.0));
            if ("MUZZLE".equals(e.type) && shot1Image != null) {
                drawCenteredImage(gc, shot1Image, e.x, e.y, 12, 34, 0, alpha);
            } else if (("HIT".equals(e.type) || "SPARK".equals(e.type)) && shot2Image != null) {
                drawCenteredImage(gc, shot2Image, e.x, e.y, 24, 40, 0, alpha);
            } else if ("BOOM".equals(e.type)) {
                gc.setFill(Color.web("#fb5607", alpha * 0.28));
                gc.fillOval(e.x - e.radius, e.y - e.radius, e.radius * 2, e.radius * 2);
                gc.setStroke(Color.web("#ffca3a", alpha));
                gc.setLineWidth(4);
                gc.strokeOval(e.x - e.radius * 0.65, e.y - e.radius * 0.65, e.radius * 1.3, e.radius * 1.3);
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
        if (gameStartedNanos == 0 || now - gameStartedNanos > 2_400_000_000L || !"COMBAT".equals(phase)) {
            return;
        }
        double alpha = 1.0 - Math.max(0, (now - gameStartedNanos - 1_200_000_000L) / 1_200_000_000.0);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFont(Font.font("Microsoft JhengHei", FontWeight.EXTRA_BOLD, 42));
        gc.setFill(Color.web("#ffca3a", Math.min(1.0, alpha)));
        gc.fillText("準備開始", WIDTH / 2, HEIGHT / 2 - 18);
        gc.setFont(Font.font("Microsoft JhengHei", FontWeight.BOLD, 16));
        gc.setFill(Color.web("#ffffff", Math.min(0.88, alpha)));
        gc.fillText("WASD 移動，滑鼠瞄準，左鍵或 Space 射擊，ESC 暫停", WIDTH / 2, HEIGHT / 2 + 28);
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
        tankBodyImage = loadImage("tankbody.png", 90, 140);
        tankHeadImage = loadImage("tankhead.png", 70, 125);
        loadTankVariant("SCOUT", "tank_scout_body.png", "tank_scout_head.png");
        loadTankVariant("HEAVY", "tank_heavy_body.png", "tank_heavy_head.png");
        loadTankVariant("ASSAULT", "tank_assault_body.png", "tank_assault_head.png");
        loadTankVariant("SNIPER", "tank_sniper_body.png", "tank_sniper_head.png");
        bulletImage = loadImage("Bullet.png", 10, 40);
        shot1Image = loadImage("Shot1.png", 16, 42);
        shot2Image = loadImage("Shot2.png", 32, 48);
        healImage = firstImage(40, 40, "heal.png", "+Black.png");
        speedImage = loadImage("boost.png", 40, 40);
        rapidImage = loadImage("rapid.png", 40, 40);
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

    private Image loadImage(String name, double requestedWidth, double requestedHeight) {
        File file = new File("image", name);
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
        gc.save();
        gc.setGlobalAlpha(alpha);
        gc.translate(x, y);
        gc.rotate(Math.toDegrees(angle));
        gc.drawImage(image, -w / 2, -h / 2, w, h);
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
