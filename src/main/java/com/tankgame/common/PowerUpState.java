package com.tankgame.common;

public class PowerUpState {
    public String id;
    public String type;
    public double x;
    public double y;

    public PowerUpState() {
    }

    public PowerUpState(String id, String type, double x, double y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
    }
}
