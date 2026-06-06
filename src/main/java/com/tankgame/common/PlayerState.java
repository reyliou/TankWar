package com.tankgame.common;

public class PlayerState {
    public String id;
    public String name;
    public double x;
    public double y;
    public double angle; // 砲塔角度 (瞄準方向)
    public double bodyAngle; // 車身角度 (移動方向)
    public int hp;
    public int maxHp;
    public int score;
    public boolean alive;
    public boolean ai;
    public boolean ready;
    public boolean host;
    public double radius;
    public String color;
    public String team;
    public String tankType;

    public PlayerState() {
    }

    public PlayerState(String id, double x, double y, double angle, int hp) {
        this.id = id;
        this.name = id;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.hp = hp;
        this.maxHp = hp;
        this.alive = true;
        this.radius = 18;
        this.color = "#4cc9f0";
        this.team = "PLAYER";
        this.tankType = "ASSAULT";
    }
}
