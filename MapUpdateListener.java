import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MapUpdateListener implements Emitter.Listener {
    private final Hero hero;

    public MapUpdateListener(Hero hero) {
        this.hero = hero;
    }

    @Override
    public void call(Object... args) {
        try {
            if (args == null || args.length == 0) return;

            GameMap gameMap = hero.getGameMap();
            gameMap.updateOnUpdateMap(args[0]);
            Player player = gameMap.getCurrentPlayer();

            if (player == null || player.getHealth() <= 0) {
                System.out.println("[INFO] Player is dead or not available.");
                return;
            }

            // Nếu chưa có súng → tìm súng
            if (hero.getInventory().getGun() == null) {
                handleSearchForGun(gameMap, player);
                return;
            }

            // Nếu có súng → bắn địch nếu thấy, nếu không thì đi mở rương
            boolean hasShot = shootEnemiesNearby(gameMap, player);
            if (!hasShot) {
                handleFindAndOpenChest(gameMap, player);
            }

        } catch (Exception e) {
            System.err.println("[ERROR] Exception in map update: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleSearchForGun(GameMap gameMap, Player player) throws IOException {
        System.out.println("[ACTION] Searching for gun...");
        List<Node> avoidNodes = getNodesToAvoid(gameMap);
        Weapon nearestGun = getNearestGun(gameMap, player);

        if (nearestGun == null) {
            System.out.println("[WARN] No gun found.");
            return;
        }

        String path = PathUtils.getShortestPath(gameMap, avoidNodes, player, nearestGun, false);
        if (path == null) return;

        if (path.isEmpty()) {
            hero.pickupItem();
        } else {
            hero.move(path);
        }
    }

    private void handleFindAndOpenChest(GameMap gameMap, Player player) throws IOException {
        List<Obstacle> allObstacles = gameMap.getListObstacles();
        List<Node> avoidNodes = getNodesToAvoid(gameMap);

        Obstacle nearestChest = null;
        double minDist = Double.MAX_VALUE;

        for (Obstacle obs : allObstacles) {
            if (obs.getType() == ElementType.CHEST) {
                double dist = PathUtils.distance(player, obs);
                if (dist < minDist) {
                    minDist = dist;
                    nearestChest = obs;
                }
            }
        }

        if (nearestChest == null) {
            System.out.println("[INFO] No chest found.");
            return;
        }

        int px = player.x;
        int py = player.y;
        int cx = nearestChest.x;
        int cy = nearestChest.y;

        // Đứng trên rương → loot
        if (px == cx && py == cy) {
            System.out.println("[LOOT] Standing on chest → Pickup");
            hero.pickupItem();
            return;
        }

        // Đứng cạnh rương → attack theo hướng
        if (Math.abs(px - cx) + Math.abs(py - cy) == 1) {
            String dir = "";
            if (px == cx && py == cy - 1) dir = "u";
            else if (px == cx && py == cy + 1) dir = "d";
            else if (py == cy && px == cx - 1) dir = "l";
            else if (py == cy && px == cx + 1) dir = "r";

            System.out.println("[ATTACK] Attacking chest in direction: " + dir);
            hero.attack(dir);
            return;
        }

        // Chưa đến nơi → move đến rương
        String path = PathUtils.getShortestPath(gameMap, avoidNodes, player, nearestChest, false);
        if (path == null) return;

        hero.move(path);
    }

    private boolean shootEnemiesNearby(GameMap gameMap, Player player) throws IOException {
        int px = player.x;
        int py = player.y;
        int range = hero.getInventory().getGun().getRange();

        for (Player enemy : gameMap.getOtherPlayerInfo()) {
            if (enemy.getHealth() != null && enemy.getHealth() > 0) {
                int ex = enemy.x;
                int ey = enemy.y;

                if (ex == px && Math.abs(ey - py) <= range) {
                    hero.shoot(ey > py ? "d" : "u");
                    System.out.println("[SHOOT] Shot enemy vertically.");
                    return true;
                } else if (ey == py && Math.abs(ex - px) <= range) {
                    hero.shoot(ex > px ? "r" : "l");
                    System.out.println("[SHOOT] Shot enemy horizontally.");
                    return true;
                }
            }
        }
        return false;
    }

    private Weapon getNearestGun(GameMap gameMap, Player player) {
        List<Weapon> guns = gameMap.getAllGun();
        Weapon nearest = null;
        double minDist = Double.MAX_VALUE;

        for (Weapon w : guns) {
            double dist = PathUtils.distance(player, w);
            if (dist < minDist) {
                minDist = dist;
                nearest = w;
            }
        }
        return nearest;
    }

    private List<Node> getNodesToAvoid(GameMap gameMap) {
        List<Node> nodes = new ArrayList<>(gameMap.getListIndestructibles());
        nodes.removeAll(gameMap.getObstaclesByTag("CAN_GO_THROUGH"));
        nodes.addAll(gameMap.getOtherPlayerInfo());
        return nodes;
    }
}
