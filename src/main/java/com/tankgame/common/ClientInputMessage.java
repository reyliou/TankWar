package com.tankgame.common;

public class ClientInputMessage {
    public String type = "input";
    public String playerId;
    public String playerName;
    public String roomCode;
    public String gameMode;
    public String team;
    public String tankType;
    public String text; // 新增：聊天文字
    public boolean ready;
    public boolean up;
    public boolean down;
    public boolean left;
    public boolean right;
    public boolean fire;
    public boolean shooting;
    public double aimX;
    public double aimY;
}
