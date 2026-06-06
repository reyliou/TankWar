package com.tankgame.common;

public class EffectState {
    public String id;
    public String type;
    public double x;
    public double y;
    public double radius;
    public double angle; // 新增：特效旋轉角度
    public int lifeTicks;
    public String label;

    public EffectState() {
    }

    public EffectState(String id, String type, double x, double y, double radius, int lifeTicks) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.lifeTicks = lifeTicks;
    }

    public EffectState(String id, String type, double x, double y, double radius, int lifeTicks, double angle) {
        this(id, type, x, y, radius, lifeTicks);
        this.angle = angle;
    }

    public EffectState(String id, String type, double x, double y, double radius, int lifeTicks, String label) {
        this(id, type, x, y, radius, lifeTicks);
        this.label = label;
    }
}
