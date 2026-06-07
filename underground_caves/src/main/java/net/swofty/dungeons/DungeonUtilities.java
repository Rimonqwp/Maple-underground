package net.swofty.dungeons;

import java.util.*;
import java.util.stream.Stream;
import java.util.logging.Logger;

public class DungeonUtilities {
    private static final Logger LOGGER = Logger.getLogger(DungeonUtilities.class.getName());

    public static Stream<Map.Entry<Integer, Integer>> loopOverDungeonRooms(DungeonsData data) {
        // Pre-calculate all entries to avoid repeated computation
        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(data.getWidth() * data.getHeight());
        for (int i = 0; i < data.getWidth() * data.getHeight(); i++) {
            entries.add(Map.entry(i % data.getWidth(), i / data.getWidth()));
        }
        return entries.stream();
    }

    public static List<int[]> getFreeNeighbours(int x, int y, SkyBlockDungeon dungeon, DungeonsData data) {
        List<int[]> freeNeighbours = new ArrayList<>();
        if (x > 0 && dungeon.getRoom(x - 1, y).getStage() == 0)
            freeNeighbours.add(new int[] { x - 1, y });
        if (y > 0 && dungeon.getRoom(x, y - 1).getStage() == 0)
            freeNeighbours.add(new int[] { x, y - 1 });
        if (x < data.getWidth() - 1 && dungeon.getRoom(x + 1, y).getStage() == 0)
            freeNeighbours.add(new int[] { x + 1, y });
        if (y < data.getHeight() - 1 && dungeon.getRoom(x, y + 1).getStage() == 0)
            freeNeighbours.add(new int[] { x, y + 1 });
        return freeNeighbours;
    }

    public static List<int[]> getAdjacentBaseRooms(int x, int y, SkyBlockDungeon dungeon, DungeonsData data) {
        List<int[]> neighbours = new ArrayList<>();
        if (x > 0 && dungeon.getRoom(x - 1, y).getRoomType() == DungeonRoomType.BASE)
            neighbours.add(new int[] { x - 1, y });
        if (y > 0 && dungeon.getRoom(x, y - 1).getRoomType() == DungeonRoomType.BASE)
            neighbours.add(new int[] { x, y - 1 });
        if (x < data.getWidth() - 1 && dungeon.getRoom(x + 1, y).getRoomType() == DungeonRoomType.BASE)
            neighbours.add(new int[] { x + 1, y });
        if (y < data.getHeight() - 1 && dungeon.getRoom(x, y + 1).getRoomType() == DungeonRoomType.BASE)
            neighbours.add(new int[] { x, y + 1 });
        return neighbours;
    }

    public static void asyncPrintPerformance(long startTime, String identifier) {
        new Thread(() -> {
            long time = System.currentTimeMillis() - startTime;
            LOGGER.info("Dungeon Performance: " + identifier + " took " + time + "ms");
        }).start();
    }

    public static List<int[]> aStar(List<int[]> alreadyVisited,
            int startX, int startY, int endX, int endY, int width, int height) {
        PriorityQueue<int[]> openSet = new PriorityQueue<>(Comparator.comparingInt(cell -> cell[2]));
        openSet.add(new int[] { startX, startY, 0 }); // Add start position to open set
        Map<String, int[]> cameFrom = new HashMap<>();
        Map<String, Integer> costSoFar = new HashMap<>();
        costSoFar.put(startX + "," + startY, 0);

        boolean[][] blockedByVisited = new boolean[width][height];
        for (int[] cell : alreadyVisited) {
            int x = cell[0];
            int y = cell[1];
            if (x >= 0 && x < width && y >= 0 && y < height) {
                blockedByVisited[x][y] = true;
            }
        }
        if (startX >= 0 && startX < width && startY >= 0 && startY < height) {
            blockedByVisited[startX][startY] = false;
        }
        if (endX >= 0 && endX < width && endY >= 0 && endY < height) {
            blockedByVisited[endX][endY] = false;
        }

        long startMs = System.currentTimeMillis();
        long nextLogMs = startMs + 2000;
        int iterations = 0;

        while (!openSet.isEmpty()) {
            int[] current = openSet.poll();
            iterations++;

            if (current[0] == endX && current[1] == endY) {
                return reconstructPath(cameFrom, startX, startY, current); // Ensure start is included in path
            }

            for (int[] neighbor : getNeighbors(current[0], current[1], width, height)) {
                // Convert neighbor to a String key to check if visited
                String neighborKey = neighbor[0] + "," + neighbor[1];
                if (blockedByVisited[neighbor[0]][neighbor[1]])
                    continue; // Skip already visited

                int newCost = costSoFar.getOrDefault(current[0] + "," + current[1], Integer.MAX_VALUE) + 1;
                if (!costSoFar.containsKey(neighborKey) || newCost < costSoFar.get(neighborKey)) {
                    costSoFar.put(neighborKey, newCost);
                    int priority = newCost + heuristic(neighbor[0], neighbor[1], endX, endY);
                    openSet.add(new int[] { neighbor[0], neighbor[1], priority });
                    cameFrom.put(neighborKey, current);
                }
            }

            long now = System.currentTimeMillis();
            if (now >= nextLogMs) {
                LOGGER.info("A* running " + (now - startMs) + "ms"
                        + " iters=" + iterations
                        + " open=" + openSet.size()
                        + " seen=" + costSoFar.size()
                        + " cur=(" + current[0] + "," + current[1] + ")"
                        + " goal=(" + endX + "," + endY + ")");
                nextLogMs = now + 2000;
            }
        }

        return new ArrayList<>(); // Return empty path if goal is not reachable
    }

    private static int heuristic(int x, int y, int endX, int endY) {
        // Using Manhattan distance as the heuristic
        return Math.abs(x - endX) + Math.abs(y - endY);
    }

    private static List<int[]> getNeighbors(int x, int y, int width, int height) {
        List<int[]> neighbors = new ArrayList<>();
        if (x > 0)
            neighbors.add(new int[] { x - 1, y });
        if (y > 0)
            neighbors.add(new int[] { x, y - 1 });
        if (x < width - 1)
            neighbors.add(new int[] { x + 1, y });
        if (y < height - 1)
            neighbors.add(new int[] { x, y + 1 });
        return neighbors;
    }

    private static List<int[]> reconstructPath(Map<String, int[]> cameFrom, int startX, int startY, int[] current) {
        LinkedList<int[]> path = new LinkedList<>();
        while (cameFrom.containsKey(current[0] + "," + current[1])) {
            path.addFirst(current);
            current = cameFrom.get(current[0] + "," + current[1]);
        }
        path.addFirst(new int[] { startX, startY });
        return path;
    }

    public static void asyncPrintDungeon(SkyBlockDungeon dungeon) {
        new Thread(() -> {
            LOGGER.info(String.valueOf(dungeon));
        }).start();
    }

    public static String center(String s, int size, char pad) {
        if (s == null || size <= s.length())
            return s;

        StringBuilder sb = new StringBuilder(size);
        sb.append(String.valueOf(pad).repeat((size - s.length()) / 2));
        sb.append(s);
        while (sb.length() < size) {
            sb.append(pad);
        }
        return sb.toString();
    }
}
