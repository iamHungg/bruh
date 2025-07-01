import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.algorithm.PathUtils;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;

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

            var player = gameMap.getCurrentPlayer();
            if (player == null || player.getHealth() <= 0) {
                System.out.println("Player is dead or not initialized.");
                return;
            }

            List<Node> avoidNodes = new ArrayList<>();
            for (Obstacle o : gameMap.getListIndestructibles()) {
                avoidNodes.add(new Node(o.x, o.y));
            }

            for (Obstacle o : gameMap.getObstaclesByTag("CAN_GO_THROUGH")) {
                avoidNodes.remove(new Node(o.x, o.y));
            }

            for (Player p : gameMap.getOtherPlayerInfo()) {
                avoidNodes.add(new Node(p.x, p.y));
            }

            List<Obstacle> chests = gameMap.getListObstacles().stream()
                    .filter(o -> o.getId().equals("CHEST"))
                    .toList();

            if (chests.isEmpty()) {
                System.out.println("No chests found.");
                return;
            }

            Obstacle nearestChest = chests.stream()
                    .min((a, b) -> Double.compare(PathUtils.distance(player, a), PathUtils.distance(player, b)))
                    .orElse(null);

            if (nearestChest == null) return;

            int px = player.x;
            int py = player.y;
            int cx = nearestChest.x;
            int cy = nearestChest.y;

            if (px == cx && py == cy) {
                hero.pickupItem();
                return;
            }

            if (Math.abs(px - cx) + Math.abs(py - cy) == 1) {
                String dir = "";
                if (px == cx && py == cy - 1) dir = "u";
                else if (px == cx && py == cy + 1) dir = "d";
                else if (py == cy && px == cx - 1) dir = "r";
                else if (py == cy && px == cx + 1) dir = "l";

                System.out.println("Attacking chest in direction: " + dir);
                hero.attack(dir);
                return;
            }

            String path = PathUtils.getShortestPath(gameMap, avoidNodes, player, nearestChest, false);
            if (path == null) {
                System.out.println("No path to CHEST.");
                return;
            }

            if (path.isEmpty()) {
                hero.pickupItem();
            } else {
                hero.move(path);
            }

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}