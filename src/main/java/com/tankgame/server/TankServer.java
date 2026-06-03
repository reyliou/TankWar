package com.tankgame.server;

import com.google.gson.Gson;
import com.tankgame.common.BulletState;
import com.tankgame.common.ClientInputMessage;
import com.tankgame.common.EffectState;
import com.tankgame.common.PlayerState;
import com.tankgame.common.PowerUpState;
import com.tankgame.common.ServerMessage;
import com.tankgame.common.WallState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TankServer {
    private static final int DEFAULT_PORT = 7788;
    private static final int WORLD_W = 1000;
    private static final int WORLD_H = 700;
    private static final int TICK_MS = 50;
    private static final Gson GSON = new Gson();

    private final Object worldLock = new Object();
    private final Map<String, ClientConnection> clients = new ConcurrentHashMap<>();
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> playerRooms = new ConcurrentHashMap<>();

    private static final class TankStats {
        final int maxHp;
        final double moveSpeed;
        final double bulletSpeed;
        final int damage;
        final int reloadTicks;
        final int bulletLifeTicks;

        TankStats(int maxHp, double moveSpeed, double bulletSpeed, int damage, int reloadTicks, int bulletLifeTicks) {
            this.maxHp = maxHp;
            this.moveSpeed = moveSpeed;
            this.bulletSpeed = bulletSpeed;
            this.damage = damage;
            this.reloadTicks = reloadTicks;
            this.bulletLifeTicks = bulletLifeTicks;
        }
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new TankServer().start(port);
    }

    private void start(int port) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            ScheduledExecutorService gameLoop = Executors.newSingleThreadScheduledExecutor();
            gameLoop.scheduleAtFixedRate(this::tick, 0, TICK_MS, TimeUnit.MILLISECONDS);
            System.out.println("Tank server started on port " + port);
            System.out.println("Same computer: 127.0.0.1 | LAN hint: " + localHostAddress());
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleClient(socket), "client-handler").start();
            }
        }
    }

    private String localHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
            return "use ipconfig to find this computer's IPv4 address";
        }
    }

    private void handleClient(Socket socket) {
        String playerId = UUID.randomUUID().toString().substring(0, 8);
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            synchronized (worldLock) {
                clients.put(playerId, new ClientConnection(playerId, out));
            }
            out.println(GSON.toJson(ServerMessage.welcome(playerId)));
            System.out.println("Player connected: " + playerId);

            String line;
            while ((line = in.readLine()) != null) {
                ClientInputMessage input = GSON.fromJson(line, ClientInputMessage.class);
                if (input == null) {
                    continue;
                }
                synchronized (worldLock) {
                    if ("joinRoom".equals(input.type) || "roomUpdate".equals(input.type) || "profile".equals(input.type)) {
                        joinOrUpdateRoom(playerId, input);
                    } else if ("input".equals(input.type)) {
                        GameRoom room = roomForPlayer(playerId);
                        if (room != null && "COMBAT".equals(room.phase)) {
                            room.updatePlayerProfile(playerId, input);
                            room.inputs.put(playerId, input);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Client disconnected: " + playerId);
        } finally {
            synchronized (worldLock) {
                removePlayer(playerId);
                clients.remove(playerId);
            }
        }
    }

    private void joinOrUpdateRoom(String playerId, ClientInputMessage input) {
        String code = normalizeRoomCode(input.roomCode);
        String oldCode = playerRooms.get(playerId);
        if (oldCode != null && !oldCode.equals(code)) {
            GameRoom oldRoom = rooms.get(oldCode);
            if (oldRoom != null) {
                oldRoom.removeHuman(playerId);
            }
        }

        GameRoom room = rooms.computeIfAbsent(code, key -> new GameRoom(key, normalizeMode(input.gameMode)));
        playerRooms.put(playerId, code);
        room.joinOrUpdateHuman(playerId, input);
        room.broadcastState();
    }

    private GameRoom roomForPlayer(String playerId) {
        String code = playerRooms.get(playerId);
        return code == null ? null : rooms.get(code);
    }

    private void removePlayer(String playerId) {
        GameRoom room = roomForPlayer(playerId);
        if (room != null) {
            room.removeHuman(playerId);
            room.broadcastState();
        }
        playerRooms.remove(playerId);
    }

    private String normalizeRoomCode(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "ROOM1";
        }
        String cleaned = raw.trim().toUpperCase().replaceAll("[^A-Z0-9_-]", "");
        return cleaned.isEmpty() ? "ROOM1" : cleaned.substring(0, Math.min(12, cleaned.length()));
    }

    private String normalizeMode(String raw) {
        return "PVP".equalsIgnoreCase(raw) ? "PVP" : "PVE";
    }

    private void tick() {
        synchronized (worldLock) {
            List<String> emptyRooms = new ArrayList<>();
            for (GameRoom room : rooms.values()) {
                room.tick();
                if (room.isEmpty()) {
                    emptyRooms.add(room.code);
                }
            }
            for (String code : emptyRooms) {
                rooms.remove(code);
            }
        }
    }

    private final class GameRoom {
        private final String code;
        private final String gameMode;
        private final Random random = new Random();
        private final Map<String, PlayerState> players = new ConcurrentHashMap<>();
        private final Map<String, ClientInputMessage> inputs = new ConcurrentHashMap<>();
        private final Map<String, Integer> reloadTicks = new ConcurrentHashMap<>();
        private final Map<String, Integer> speedBoostTicks = new ConcurrentHashMap<>();
        private final Map<String, Integer> rapidFireTicks = new ConcurrentHashMap<>();
        private final List<BulletState> bullets = new ArrayList<>();
        private final List<WallState> walls = new ArrayList<>();
        private final List<PowerUpState> powerUps = new ArrayList<>();
        private final List<EffectState> effects = new ArrayList<>();
        private String phase = "ROOM_WAIT";
        private String winnerId;
        private String winnerTeam;
        private String message = "請選擇隊伍並按準備";
        private int nextAiNumber = 1;
        private int powerUpSpawnTick = 0;
        private int roundOverTicks = 0;

        private GameRoom(String code, String gameMode) {
            this.code = code;
            this.gameMode = gameMode;
        }

        private boolean isEmpty() {
            return humanCount() == 0;
        }

        private void joinOrUpdateHuman(String playerId, ClientInputMessage input) {
            PlayerState player = players.get(playerId);
            boolean firstHuman = humanCount() == 0 && player == null;
            if (player == null) {
                player = createPlayer(playerId, safeName(input.playerName, playerId), false, humanColor(input.team));
                player.host = firstHuman;
                players.put(playerId, player);
            }
            updatePlayerProfile(playerId, input);
            if ("ROOM_WAIT".equals(phase)) {
                player.ready = input.ready;
            }
            if ("ROOM_WAIT".equals(phase)) {
                maybeStartRound();
            }
        }

        private void updatePlayerProfile(String playerId, ClientInputMessage input) {
            PlayerState player = players.get(playerId);
            if (player == null || player.ai) {
                return;
            }
            player.name = safeName(input.playerName, player.id);
            if ("PVE".equals(gameMode)) {
                player.team = "PLAYER";
                player.color = "#ffd166";
            } else {
                player.team = normalizeTeam(input.team);
                player.color = "BLUE".equals(player.team) ? "#4cc9f0" : "#ef476f";
            }
            player.tankType = normalizeTankType(input.tankType);
            applyTankStats(player, false);
        }

        private void removeHuman(String playerId) {
            players.remove(playerId);
            inputs.remove(playerId);
            reloadTicks.remove(playerId);
            speedBoostTicks.remove(playerId);
            rapidFireTicks.remove(playerId);
            bullets.removeIf(b -> playerId.equals(b.ownerId));
            if ("ROOM_WAIT".equals(phase)) {
                promoteHost();
            } else {
                checkWinner();
            }
        }

        private void promoteHost() {
            boolean hasHost = false;
            for (PlayerState player : players.values()) {
                if (!player.ai && player.host) {
                    hasHost = true;
                    break;
                }
            }
            if (!hasHost) {
                for (PlayerState player : players.values()) {
                    if (!player.ai) {
                        player.host = true;
                        return;
                    }
                }
            }
        }

        private void maybeStartRound() {
            List<PlayerState> humans = humans();
            if (humans.isEmpty()) {
                return;
            }
            for (PlayerState player : humans) {
                if (!player.ready) {
                    message = "等待所有玩家準備";
                    return;
                }
            }
            if ("PVP".equals(gameMode) && (!hasHumanTeam("RED") || !hasHumanTeam("BLUE"))) {
                message = "PVP 需要紅隊與藍隊各至少 1 位玩家";
                return;
            }
            startCombat();
        }

        private void startCombat() {
            bullets.clear();
            walls.clear();
            powerUps.clear();
            effects.clear();
            inputs.clear();
            reloadTicks.clear();
            speedBoostTicks.clear();
            rapidFireTicks.clear();
            powerUpSpawnTick = 0;
            roundOverTicks = 0;
            winnerId = null;
            winnerTeam = null;
            phase = "COMBAT";
            message = "戰鬥開始";
            generateWalls();

            List<PlayerState> humans = humans();
            players.clear();
            for (PlayerState human : humans) {
                PlayerState fresh = createPlayer(human.id, human.name, false, human.color);
                fresh.score = human.score;
                fresh.team = human.team;
                fresh.tankType = normalizeTankType(human.tankType);
                applyTankStats(fresh, false);
                fresh.ready = true;
                fresh.host = human.host;
                players.put(fresh.id, fresh);
                reloadTicks.put(fresh.id, 0);
            }
            if ("PVE".equals(gameMode)) {
                addPveEnemies(Math.min(4, Math.max(2, humans.size() + 1)));
            }
        }

        private void setRoomWait(String newMessage) {
            bullets.clear();
            walls.clear();
            powerUps.clear();
            effects.clear();
            inputs.clear();
            reloadTicks.clear();
            speedBoostTicks.clear();
            rapidFireTicks.clear();
            phase = "ROOM_WAIT";
            winnerId = null;
            winnerTeam = null;
            roundOverTicks = 0;
            powerUpSpawnTick = 0;
            message = newMessage;

            List<PlayerState> humans = humans();
            players.clear();
            for (PlayerState human : humans) {
                human.ai = false;
                human.alive = true;
                human.hp = 100;
                human.maxHp = 100;
                human.ready = false;
                human.radius = 18;
                human.color = "BLUE".equals(human.team) ? "#4cc9f0" : "#ef476f";
                human.tankType = normalizeTankType(human.tankType);
                applyTankStats(human, true);
                if ("PVE".equals(gameMode)) {
                    human.team = "PLAYER";
                    human.color = "#ffd166";
                }
                players.put(human.id, human);
            }
            promoteHost();
        }

        private void tick() {
            if ("ROOM_WAIT".equals(phase)) {
                broadcastState();
                return;
            }
            if ("ROUND_OVER".equals(phase)) {
                roundOverTicks++;
                updateEffects();
                broadcastState();
                if (roundOverTicks > 90) {
                    setRoomWait("回合結束，請重新準備");
                    broadcastState();
                }
                return;
            }

            updatePlayers();
            updateBullets();
            updatePowerUps();
            updateEffects();
            maybeSpawnPowerUp();
            checkWinner();
            broadcastState();
        }

        private List<PlayerState> humans() {
            List<PlayerState> humans = new ArrayList<>();
            for (PlayerState player : players.values()) {
                if (!player.ai) {
                    humans.add(player);
                }
            }
            return humans;
        }

        private long humanCount() {
            return players.values().stream().filter(p -> !p.ai).count();
        }

        private boolean hasHumanTeam(String team) {
            for (PlayerState player : players.values()) {
                if (!player.ai && team.equals(player.team)) {
                    return true;
                }
            }
            return false;
        }

        private String normalizeTeam(String raw) {
            return "BLUE".equalsIgnoreCase(raw) ? "BLUE" : "RED";
        }

        private String humanColor(String team) {
            return "BLUE".equalsIgnoreCase(team) ? "#4cc9f0" : "#ef476f";
        }

        private String normalizeTankType(String raw) {
            if ("SCOUT".equalsIgnoreCase(raw)) return "SCOUT";
            if ("HEAVY".equalsIgnoreCase(raw)) return "HEAVY";
            if ("SNIPER".equalsIgnoreCase(raw)) return "SNIPER";
            return "ASSAULT";
        }

        private String aiTankType(int index) {
            String[] types = {"ASSAULT", "SCOUT", "HEAVY", "SNIPER"};
            return types[Math.floorMod(index, types.length)];
        }

        private TankStats statsFor(String type) {
            String tank = normalizeTankType(type);
            if ("SCOUT".equals(tank)) {
                return new TankStats(75, 4.8, 12.0, 16, 7, 82);
            }
            if ("HEAVY".equals(tank)) {
                return new TankStats(150, 2.8, 10.0, 34, 15, 90);
            }
            if ("SNIPER".equals(tank)) {
                return new TankStats(90, 3.2, 15.5, 42, 20, 125);
            }
            return new TankStats(105, 3.7, 11.5, 22, 11, 90);
        }

        private void applyTankStats(PlayerState player, boolean keepHpRatio) {
            TankStats stats = statsFor(player.tankType);
            double hpRatio = player.maxHp <= 0 ? 1.0 : player.hp / (double) player.maxHp;
            player.maxHp = stats.maxHp;
            player.hp = keepHpRatio ? Math.max(1, (int) Math.round(stats.maxHp * hpRatio)) : stats.maxHp;
            player.radius = "HEAVY".equals(player.tankType) ? 21 : "SCOUT".equals(player.tankType) ? 16 : 18;
        }

        private String safeName(String raw, String fallbackId) {
            if (raw == null || raw.trim().isEmpty()) {
                return "玩家" + fallbackId.substring(0, Math.min(3, fallbackId.length()));
            }
            String trimmed = raw.trim();
            return trimmed.length() > 16 ? trimmed.substring(0, 16) : trimmed;
        }

        private void addPveEnemies(int count) {
            for (int i = 0; i < count; i++) {
                String id = "AI-" + nextAiNumber++;
                PlayerState ai = createPlayer(id, "敵方 " + id.substring(3), true, "#8ecae6");
                ai.team = "ENEMY";
                ai.tankType = aiTankType(i);
                applyTankStats(ai, false);
                players.put(id, ai);
                reloadTicks.put(id, 0);
            }
        }

        private void generateWalls() {
            walls.add(new WallState(0, 0, WORLD_W, 24));
            walls.add(new WallState(0, WORLD_H - 24, WORLD_W, 24));
            walls.add(new WallState(0, 0, 24, WORLD_H));
            walls.add(new WallState(WORLD_W - 24, 0, 24, WORLD_H));
            walls.add(new WallState(456, 260, 36, 130));
            walls.add(new WallState(520, 310, 36, 130));

            int target = 7 + random.nextInt(4);
            int placed = 0;
            for (int attempt = 0; attempt < 120 && placed < target; attempt++) {
                boolean horizontal = random.nextBoolean();
                double w = horizontal ? 90 + random.nextInt(100) : 32 + random.nextInt(18);
                double h = horizontal ? 32 + random.nextInt(18) : 90 + random.nextInt(100);
                double x = 55 + random.nextDouble() * (WORLD_W - 110 - w);
                double y = 55 + random.nextDouble() * (WORLD_H - 110 - h);
                WallState wall = new WallState(x, y, w, h);
                if (validRandomWall(wall)) {
                    walls.add(wall);
                    placed++;
                }
            }
        }

        private boolean validRandomWall(WallState candidate) {
            if (insideSpawnSafeZone(candidate)) {
                return false;
            }
            if (candidate.x > 390 && candidate.x + candidate.w < 610 && candidate.y > 230 && candidate.y + candidate.h < 470) {
                return false;
            }
            for (WallState wall : walls) {
                if (rectsOverlap(candidate, wall, 34)) {
                    return false;
                }
            }
            return true;
        }

        private boolean insideSpawnSafeZone(WallState wall) {
            double[][] spawns = {
                    {90, 90}, {WORLD_W - 90, 90}, {90, WORLD_H - 90}, {WORLD_W - 90, WORLD_H - 90},
                    {WORLD_W / 2.0, 90}, {WORLD_W / 2.0, WORLD_H - 90}
            };
            for (double[] spawn : spawns) {
                double closestX = clamp(spawn[0], wall.x, wall.x + wall.w);
                double closestY = clamp(spawn[1], wall.y, wall.y + wall.h);
                if (Math.hypot(spawn[0] - closestX, spawn[1] - closestY) < 105) {
                    return true;
                }
            }
            return false;
        }

        private boolean rectsOverlap(WallState a, WallState b, double padding) {
            return a.x - padding < b.x + b.w
                    && a.x + a.w + padding > b.x
                    && a.y - padding < b.y + b.h
                    && a.y + a.h + padding > b.y;
        }

        private PlayerState createPlayer(String id, String name, boolean ai, String color) {
            PlayerState p = new PlayerState(id, 80, 80, 0, 100);
            p.name = name;
            p.ai = ai;
            p.maxHp = 100;
            p.hp = 100;
            p.alive = true;
            p.radius = 18;
            p.color = color;
            p.tankType = normalizeTankType(p.tankType);
            applyTankStats(p, false);

            for (int attempt = 0; attempt < 200; attempt++) {
                double x = 70 + random.nextDouble() * (WORLD_W - 140);
                double y = 70 + random.nextDouble() * (WORLD_H - 140);
                if (!collidesWithWall(x, y, p.radius + 8)) {
                    p.x = x;
                    p.y = y;
                    return p;
                }
            }
            return p;
        }

        private void updatePlayers() {
            for (PlayerState player : players.values()) {
                if (!player.alive) {
                    continue;
                }
                ClientInputMessage input = player.ai ? createAiInput(player) : inputs.get(player.id);
                if (input == null) {
                    continue;
                }

                double targetAngle = Math.atan2(input.aimY - player.y, input.aimX - player.x);
                if (!Double.isNaN(targetAngle)) {
                    player.angle = targetAngle;
                }

                double dx = 0;
                double dy = 0;
                if (input.up) dy -= 1;
                if (input.down) dy += 1;
                if (input.left) dx -= 1;
                if (input.right) dx += 1;
                double len = Math.hypot(dx, dy);
                if (len > 0) {
                    TankStats stats = statsFor(player.tankType);
                    double speed = speedBoostTicks.getOrDefault(player.id, 0) > 0 ? stats.moveSpeed * 1.5 : stats.moveSpeed;
                    movePlayer(player, dx / len * speed, dy / len * speed);
                }

                decrementBoosts(player.id);
                int reload = Math.max(0, reloadTicks.getOrDefault(player.id, 0) - 1);
                reloadTicks.put(player.id, reload);
                if ((input.shooting || input.fire) && reload == 0) {
                fireBullet(player);
                    TankStats stats = statsFor(player.tankType);
                    reloadTicks.put(player.id, rapidFireTicks.getOrDefault(player.id, 0) > 0 ? Math.max(4, stats.reloadTicks / 2) : stats.reloadTicks);
                }
            }
        }

        private ClientInputMessage createAiInput(PlayerState ai) {
            PlayerState target = nearestEnemy(ai);
            ClientInputMessage input = new ClientInputMessage();
            if (target == null) {
                input.aimX = ai.x + Math.cos(ai.angle) * 100;
                input.aimY = ai.y + Math.sin(ai.angle) * 100;
                return input;
            }

            input.aimX = target.x;
            input.aimY = target.y;
            double dx = target.x - ai.x;
            double dy = target.y - ai.y;
            double distance = Math.hypot(dx, dy);
            if (distance > 180) {
                input.right = dx > 20;
                input.left = dx < -20;
                input.down = dy > 20;
                input.up = dy < -20;
            } else if (distance < 105) {
                input.right = dx < -20;
                input.left = dx > 20;
                input.down = dy < -20;
                input.up = dy > 20;
            }
            input.shooting = hasLineOfSight(ai, target);
            return input;
        }

        private PlayerState nearestEnemy(PlayerState player) {
            PlayerState best = null;
            double bestDistance = Double.MAX_VALUE;
            for (PlayerState other : players.values()) {
                if (other == player || !other.alive || sameTeam(player, other)) {
                    continue;
                }
                double distance = Math.hypot(other.x - player.x, other.y - player.y);
                if (distance < bestDistance) {
                    best = other;
                    bestDistance = distance;
                }
            }
            return best;
        }

        private boolean hasLineOfSight(PlayerState from, PlayerState to) {
            int checks = 18;
            for (int i = 1; i < checks; i++) {
                double t = i / (double) checks;
                double x = from.x + (to.x - from.x) * t;
                double y = from.y + (to.y - from.y) * t;
                if (pointInsideWall(x, y)) {
                    return false;
                }
            }
            return true;
        }

        private void movePlayer(PlayerState player, double dx, double dy) {
            double nextX = clamp(player.x + dx, 30, WORLD_W - 30);
            if (!collidesWithWall(nextX, player.y, player.radius)) {
                player.x = nextX;
            }
            double nextY = clamp(player.y + dy, 30, WORLD_H - 30);
            if (!collidesWithWall(player.x, nextY, player.radius)) {
                player.y = nextY;
            }
        }

        private void fireBullet(PlayerState player) {
            TankStats stats = statsFor(player.tankType);
            double speed = stats.bulletSpeed;
            double muzzleX = player.x + Math.cos(player.angle) * 25;
            double muzzleY = player.y + Math.sin(player.angle) * 25;
            BulletState bullet = new BulletState(
                    UUID.randomUUID().toString().substring(0, 8),
                    player.id,
                    muzzleX,
                    muzzleY,
                    Math.cos(player.angle) * speed,
                    Math.sin(player.angle) * speed,
                    stats.damage,
                    stats.bulletLifeTicks
            );
            bullets.add(bullet);
            effects.add(new EffectState(UUID.randomUUID().toString(), "MUZZLE", muzzleX, muzzleY, 12, 6));
        }

        private void updateBullets() {
            Iterator<BulletState> iterator = bullets.iterator();
            while (iterator.hasNext()) {
                BulletState bullet = iterator.next();
                bullet.x += bullet.vx;
                bullet.y += bullet.vy;
                bullet.lifeTicks--;

                if (bullet.lifeTicks <= 0 || bullet.x < 0 || bullet.x > WORLD_W || bullet.y < 0 || bullet.y > WORLD_H) {
                    iterator.remove();
                    continue;
                }
                if (pointInsideWall(bullet.x, bullet.y)) {
                    effects.add(new EffectState(UUID.randomUUID().toString(), "SPARK", bullet.x, bullet.y, 18, 10));
                    iterator.remove();
                    continue;
                }
                PlayerState hit = hitPlayer(bullet);
                if (hit != null) {
                    hit.hp = Math.max(0, hit.hp - bullet.damage);
                    effects.add(new EffectState(UUID.randomUUID().toString(), "HIT", hit.x, hit.y, 24, 14));
                    if (hit.hp == 0) {
                        hit.alive = false;
                        effects.add(new EffectState(UUID.randomUUID().toString(), "BOOM", hit.x, hit.y, 54, 28));
                        PlayerState owner = players.get(bullet.ownerId);
                        if (owner != null) {
                            owner.score++;
                        }
                    }
                    iterator.remove();
                }
            }
        }

        private PlayerState hitPlayer(BulletState bullet) {
            PlayerState owner = players.get(bullet.ownerId);
            for (PlayerState player : players.values()) {
                if (!player.alive || player.id.equals(bullet.ownerId) || sameTeam(owner, player)) {
                    continue;
                }
                if (Math.hypot(player.x - bullet.x, player.y - bullet.y) <= player.radius + 5) {
                    return player;
                }
            }
            return null;
        }

        private boolean sameTeam(PlayerState a, PlayerState b) {
            if (a == null || b == null || a.team == null || b.team == null) {
                return false;
            }
            return a.team.equals(b.team);
        }

        private void updatePowerUps() {
            Iterator<PowerUpState> iterator = powerUps.iterator();
            while (iterator.hasNext()) {
                PowerUpState powerUp = iterator.next();
                PlayerState collector = null;
                for (PlayerState player : players.values()) {
                    if (player.alive && Math.hypot(player.x - powerUp.x, player.y - powerUp.y) < player.radius + 14) {
                        collector = player;
                        break;
                    }
                }
                if (collector != null) {
                    applyPowerUp(collector, powerUp);
                    effects.add(new EffectState(UUID.randomUUID().toString(), "PICKUP", powerUp.x, powerUp.y, 26, 24, powerUpLabel(powerUp.type)));
                    iterator.remove();
                }
            }
        }

        private void applyPowerUp(PlayerState player, PowerUpState powerUp) {
            if ("HEAL".equals(powerUp.type)) {
                player.hp = Math.min(player.maxHp, player.hp + 32);
            } else if ("SPEED".equals(powerUp.type)) {
                speedBoostTicks.put(player.id, 180);
            } else if ("RAPID".equals(powerUp.type)) {
                rapidFireTicks.put(player.id, 180);
            }
        }

        private String powerUpLabel(String type) {
            if ("HEAL".equals(type)) {
                return "補血";
            }
            if ("SPEED".equals(type)) {
                return "加速";
            }
            if ("RAPID".equals(type)) {
                return "快速射擊";
            }
            return "道具";
        }

        private void maybeSpawnPowerUp() {
            powerUpSpawnTick++;
            if (powerUpSpawnTick < 110 || powerUps.size() >= 4) {
                return;
            }
            powerUpSpawnTick = 0;
            String[] types = {"HEAL", "SPEED", "RAPID"};
            for (int attempt = 0; attempt < 80; attempt++) {
                double x = 60 + random.nextDouble() * (WORLD_W - 120);
                double y = 60 + random.nextDouble() * (WORLD_H - 120);
                if (!collidesWithWall(x, y, 24)) {
                    powerUps.add(new PowerUpState(UUID.randomUUID().toString().substring(0, 8), types[random.nextInt(types.length)], x, y));
                    return;
                }
            }
        }

        private void updateEffects() {
            Iterator<EffectState> iterator = effects.iterator();
            while (iterator.hasNext()) {
                EffectState effect = iterator.next();
                effect.lifeTicks--;
                effect.radius += "BOOM".equals(effect.type) ? 2.2 : 1.0;
                if (effect.lifeTicks <= 0) {
                    iterator.remove();
                }
            }
        }

        private void decrementBoosts(String playerId) {
            int speed = Math.max(0, speedBoostTicks.getOrDefault(playerId, 0) - 1);
            int rapid = Math.max(0, rapidFireTicks.getOrDefault(playerId, 0) - 1);
            if (speed > 0) {
                speedBoostTicks.put(playerId, speed);
            } else {
                speedBoostTicks.remove(playerId);
            }
            if (rapid > 0) {
                rapidFireTicks.put(playerId, rapid);
            } else {
                rapidFireTicks.remove(playerId);
            }
        }

        private void checkWinner() {
            if ("PVE".equals(gameMode)) {
                boolean humanAlive = false;
                boolean enemyAlive = false;
                for (PlayerState player : players.values()) {
                    if (!player.alive) {
                        continue;
                    }
                    if ("ENEMY".equals(player.team)) {
                        enemyAlive = true;
                    } else if (!player.ai) {
                        humanAlive = true;
                    }
                }
                if (!humanAlive || !enemyAlive) {
                    winnerTeam = humanAlive ? "PLAYER" : "ENEMY";
                    winnerId = firstAliveId(winnerTeam);
                    phase = "ROUND_OVER";
                    roundOverTicks = 0;
                }
                return;
            }

            boolean redAlive = false;
            boolean blueAlive = false;
            for (PlayerState player : players.values()) {
                if (!player.alive) {
                    continue;
                }
                if ("RED".equals(player.team)) {
                    redAlive = true;
                } else if ("BLUE".equals(player.team)) {
                    blueAlive = true;
                }
            }
            if (!redAlive || !blueAlive) {
                winnerTeam = redAlive ? "RED" : blueAlive ? "BLUE" : null;
                winnerId = firstAliveId(winnerTeam);
                phase = "ROUND_OVER";
                roundOverTicks = 0;
            }
        }

        private String firstAliveId(String team) {
            if (team == null) {
                return null;
            }
            for (PlayerState player : players.values()) {
                if (player.alive && team.equals(player.team)) {
                    return player.id;
                }
            }
            return null;
        }

        private void broadcastState() {
            String payload = GSON.toJson(ServerMessage.state(
                    new ArrayList<>(players.values()),
                    new ArrayList<>(bullets),
                    new ArrayList<>(walls),
                    new ArrayList<>(powerUps),
                    new ArrayList<>(effects),
                    phase,
                    winnerId,
                    winnerTeam,
                    code,
                    gameMode,
                    message
            ));
            for (PlayerState player : players.values()) {
                if (player.ai) {
                    continue;
                }
                ClientConnection client = clients.get(player.id);
                if (client != null) {
                    client.out.println(payload);
                }
            }
        }

        private boolean collidesWithWall(double x, double y, double radius) {
            for (WallState wall : walls) {
                double closestX = clamp(x, wall.x, wall.x + wall.w);
                double closestY = clamp(y, wall.y, wall.y + wall.h);
                if (Math.hypot(x - closestX, y - closestY) < radius) {
                    return true;
                }
            }
            return false;
        }

        private boolean pointInsideWall(double x, double y) {
            for (WallState wall : walls) {
                if (x >= wall.x && x <= wall.x + wall.w && y >= wall.y && y <= wall.y + wall.h) {
                    return true;
                }
            }
            return false;
        }

        private double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
