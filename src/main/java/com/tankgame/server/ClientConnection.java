package com.tankgame.server;

import java.io.PrintWriter;

public class ClientConnection {
    public final String playerId;
    public final PrintWriter out;

    public ClientConnection(String playerId, PrintWriter out) {
        this.playerId = playerId;
        this.out = out;
    }
}
