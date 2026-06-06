package com.tankgame.client;

import com.google.gson.Gson;
import com.tankgame.common.ClientInputMessage;
import com.tankgame.common.ServerMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkManager {
    private static final Gson GSON = new Gson();
    private final String host;
    private final int port;
    private final String playerName;
    private final String roomCode;
    private final String gameMode;
    private final String tankType;
    private String team;
    private boolean ready;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String playerId;

    public NetworkManager(String host, int port, String playerName, String roomCode, String gameMode, String team, String tankType) {
        this.host = host;
        this.port = port;
        this.playerName = playerName;
        this.roomCode = roomCode;
        this.gameMode = gameMode;
        this.tankType = tankType;
        this.team = team;
    }

    public void connect(Consumer<ServerMessage> onMessage) throws Exception {
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    ServerMessage msg = GSON.fromJson(line, ServerMessage.class);
                    if (msg != null && "welcome".equals(msg.type)) {
                        playerId = msg.playerId;
                        sendJoinRoom();
                    }
                    onMessage.accept(msg);
                }
            } catch (Exception ignored) {
            }
        }, "client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void sendInput(boolean up, boolean down, boolean left, boolean right, boolean fire, boolean shooting, double aimX, double aimY) {
        if (out == null || playerId == null) {
            return;
        }
        ClientInputMessage input = new ClientInputMessage();
        input.playerId = playerId;
        input.playerName = playerName;
        input.roomCode = roomCode;
        input.gameMode = gameMode;
        input.team = team;
        input.tankType = tankType;
        input.ready = ready;
        input.up = up;
        input.down = down;
        input.left = left;
        input.right = right;
        input.fire = fire;
        input.shooting = shooting;
        input.aimX = aimX;
        input.aimY = aimY;
        out.println(GSON.toJson(input));
    }

    public void sendRoomUpdate(String newTeam, boolean newReady) {
        if (newTeam != null && !newTeam.isBlank()) {
            team = newTeam;
        }
        ready = newReady;
        sendJoinRoom();
    }

    public void sendChat(String text) {
        if (out == null || playerId == null || text == null || text.isBlank()) {
            return;
        }
        ClientInputMessage msg = new ClientInputMessage();
        msg.type = "chat";
        msg.playerId = playerId;
        msg.playerName = playerName;
        msg.roomCode = roomCode;
        msg.text = text;
        out.println(GSON.toJson(msg));
    }

    private void sendJoinRoom() {
        if (out == null || playerId == null) {
            return;
        }
        ClientInputMessage join = new ClientInputMessage();
        join.type = "joinRoom";
        join.playerId = playerId;
        join.playerName = playerName;
        join.roomCode = roomCode;
        join.gameMode = gameMode;
        join.team = team;
        join.tankType = tankType;
        join.ready = ready;
        out.println(GSON.toJson(join));
    }

    public String getPlayerId() {
        return playerId;
    }

    public void close() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
        playerId = null;
    }
}
