package com.tankgame.server;

import java.io.PrintWriter;
import java.net.Socket;

public class ClientConnection {
    public final String playerId;
    public final PrintWriter out;
    public final Socket socket;
    public long lastMessageTime;

    public ClientConnection(String playerId, PrintWriter out, Socket socket) {
        this.playerId = playerId;
        this.out = out;
        this.socket = socket;
        this.lastMessageTime = System.currentTimeMillis();
    }
}
