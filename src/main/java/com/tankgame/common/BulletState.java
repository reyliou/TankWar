package com.tankgame.common;

public class BulletState {
    public String id;
    public String ownerId;
    public double x;
    public double y;
    public double vx;
    public double vy;
    public int damage;
    public int lifeTicks;

    public BulletState() {
    }

    public BulletState(String id, String ownerId, double x, double y, double vx, double vy, int damage, int lifeTicks) {
        this.id = id;
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.damage = damage;
        this.lifeTicks = lifeTicks;
    }
}
