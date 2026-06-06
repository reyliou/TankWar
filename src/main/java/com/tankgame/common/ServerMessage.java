package com.tankgame.common;

import java.util.List;

public class ServerMessage {
    public String type;
    public String playerId;
    public String roomCode;
    public String gameMode;
    public String phase;
    public String winnerId;
    public String winnerTeam;
    public String message;
    public String chatSender; // 新增：聊天發送者
    public String chatText;   // 新增：聊天內容
    public List<List<int[]>> aiPaths; // 新增：AI 尋路路徑 (Debug 用)
    public List<PlayerState> players;
    public List<BulletState> bullets;
    public List<WallState> walls;
    public List<PowerUpState> powerUps;
    public List<EffectState> effects;

    public static ServerMessage welcome(String playerId) {
        ServerMessage m = new ServerMessage();
        m.type = "welcome";
        m.playerId = playerId;
        return m;
    }

    public static ServerMessage chat(String sender, String text) {
        ServerMessage m = new ServerMessage();
        m.type = "chat";
        m.chatSender = sender;
        m.chatText = text;
        return m;
    }

    public static ServerMessage state(
            List<PlayerState> players,
            List<BulletState> bullets,
            List<WallState> walls,
            List<PowerUpState> powerUps,
            List<EffectState> effects,
            String phase,
            String winnerId
    ) {
        return state(players, bullets, walls, powerUps, effects, phase, winnerId, null, null, null, null);
    }

    public static ServerMessage state(
            List<PlayerState> players,
            List<BulletState> bullets,
            List<WallState> walls,
            List<PowerUpState> powerUps,
            List<EffectState> effects,
            String phase,
            String winnerId,
            String winnerTeam,
            String roomCode,
            String gameMode,
            String message
    ) {
        ServerMessage m = new ServerMessage();
        m.type = "state";
        m.players = players;
        m.bullets = bullets;
        m.walls = walls;
        m.powerUps = powerUps;
        m.effects = effects;
        m.phase = phase;
        m.winnerId = winnerId;
        m.winnerTeam = winnerTeam;
        m.roomCode = roomCode;
        m.gameMode = gameMode;
        m.message = message;
        return m;
    }
}
