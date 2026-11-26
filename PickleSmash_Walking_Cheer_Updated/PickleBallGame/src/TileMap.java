import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class TileMap {
    public static final int COLUMNS = 64;
    public static final int ROWS = 36;
    public static final int TILE_SIZE = 32;
    public static final int WORLD_WIDTH = COLUMNS * TILE_SIZE;
    public static final int WORLD_HEIGHT = ROWS * TILE_SIZE;

    private static final byte GRASS = 0;
    private static final byte DIRT = 1;
    private static final byte PAVED = 2;
    private final byte[][] ground = new byte[ROWS][COLUMNS];
    private final boolean[][] roads = new boolean[ROWS][COLUMNS];
    private final boolean[][] water = new boolean[ROWS][COLUMNS];
    private final boolean[][] bridge = new boolean[ROWS][COLUMNS];
    private final List<int[]> horizontalRoads = new ArrayList<>();
    private final List<int[]> verticalRoads = new ArrayList<>();

    public TileMap() {
        buildGroundLayer();
        buildRoadLayer();
        buildRiverLayer();
    }

    public void draw(Graphics2D g, MapAssetRegistry assets) {
        BufferedImage grass = cropTileBorder(assets.get("grass_tile"));
        BufferedImage alternateGrass = cropTileBorder(assets.get("grass_tile_alt"));
        BufferedImage dirt = cropTileBorder(assets.get("dirt_tile"));
        BufferedImage pavement = cropTileBorder(assets.get("sidewalk_tile"));

        g.setColor(new Color(91, 156, 65));
        g.fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int x = column * TILE_SIZE;
                int y = row * TILE_SIZE;
                if (water[row][column]) {
                    drawWaterCell(g, x, y, row, column);
                } else if (ground[row][column] == DIRT) {
                    g.drawImage(dirt, x, y, TILE_SIZE, TILE_SIZE, null);
                } else if (ground[row][column] == PAVED) {
                    g.drawImage(pavement, x, y, TILE_SIZE, TILE_SIZE, null);
                } else {
                    BufferedImage tile = ((row * 17 + column * 31) % 7 == 0)
                        ? alternateGrass : grass;
                    g.drawImage(tile, x, y, TILE_SIZE, TILE_SIZE, null);
                }
            }
        }

        drawSidewalks(g, pavement);
        drawRoads(g);
        drawBridgeApproaches(g);
    }

    public boolean blocks(Rectangle2D bounds) {
        int left = (int) Math.floor(bounds.getMinX() / TILE_SIZE);
        int right = (int) Math.floor((bounds.getMaxX() - 0.001) / TILE_SIZE);
        int top = (int) Math.floor(bounds.getMinY() / TILE_SIZE);
        int bottom = (int) Math.floor((bounds.getMaxY() - 0.001) / TILE_SIZE);
        for (int row = top; row <= bottom; row++) {
            for (int column = left; column <= right; column++) {
                if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS
                        || (water[row][column] && !bridge[row][column])) {
                    return true;
                }
            }
        }
        return false;
    }

    public void drawDebug(Graphics2D g) {
        g.setColor(new Color(255, 40, 40, 90));
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                if (water[row][column] && !bridge[row][column]) {
                    g.fillRect(column * TILE_SIZE, row * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                    g.drawRect(column * TILE_SIZE, row * TILE_SIZE, TILE_SIZE, TILE_SIZE);
                }
            }
        }
        g.setColor(new Color(255, 255, 255, 70));
        g.setStroke(new BasicStroke(1f));
        for (int column = 0; column <= COLUMNS; column++) {
            int x = column * TILE_SIZE;
            g.drawLine(x, 0, x, WORLD_HEIGHT);
        }
        for (int row = 0; row <= ROWS; row++) {
            int y = row * TILE_SIZE;
            g.drawLine(0, y, WORLD_WIDTH, y);
        }
    }

    private void buildGroundLayer() {
        setGroundRect(22, 11, 16, 8, PAVED);
        setGroundRect(21, 21, 18, 12, DIRT);
        setGroundRect(40, 21, 18, 12, DIRT);
        setGroundRect(2, 21, 17, 12, PAVED);

        setGroundRect(6, 12, 12, 6, DIRT);
        setGroundRect(22, 12, 16, 6, PAVED);
        setGroundRect(42, 12, 15, 6, DIRT);
    }

    private void buildRoadLayer() {
        addHorizontalRoad(0, 10, COLUMNS - 1);
        addHorizontalRoad(2, 20, COLUMNS - 3);
        addHorizontalRoad(0, 33, 59);

        addVerticalRoad(4, 3, 34);
        addVerticalRoad(21, 3, 34);
        addVerticalRoad(39, 3, 34);
        addVerticalRoad(59, 3, 34);

    }

    private void buildRiverLayer() {
        for (int row = 22; row <= 34; row++) {
            for (int column = 60; column < COLUMNS; column++) {
                water[row][column] = true;
            }
        }
        for (int column = 60; column < COLUMNS; column++) {
            bridge[28][column] = true;
        }
    }

    private void addHorizontalRoad(int startColumn, int centerRow, int endColumn) {
        horizontalRoads.add(new int[]{startColumn, centerRow, endColumn});
        for (int column = startColumn; column <= endColumn; column++) {
            setRoad(column, centerRow - 1);
            setRoad(column, centerRow);
        }
    }

    private void addVerticalRoad(int centerColumn, int startRow, int endRow) {
        verticalRoads.add(new int[]{centerColumn, startRow, endRow});
        for (int row = startRow; row <= endRow; row++) {
            setRoad(centerColumn - 1, row);
            setRoad(centerColumn, row);
        }
    }

    private void setRoad(int column, int row) {
        if (column >= 0 && column < COLUMNS && row >= 0 && row < ROWS) {
            roads[row][column] = true;
        }
    }

    private void setGroundRect(int column, int row, int width, int height, byte type) {
        for (int y = row; y < row + height; y++) {
            for (int x = column; x < column + width; x++) {
                if (x >= 0 && x < COLUMNS && y >= 0 && y < ROWS) {
                    ground[y][x] = type;
                }
            }
        }
    }

    private void drawSidewalks(Graphics2D g, BufferedImage pavement) {
        boolean[][] sidewalk = new boolean[ROWS][COLUMNS];
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                if (!roads[row][column]) {
                    continue;
                }
                for (int y = row - 1; y <= row + 1; y++) {
                    for (int x = column - 1; x <= column + 1; x++) {
                        if (x >= 0 && x < COLUMNS && y >= 0 && y < ROWS && !roads[y][x]
                                && !water[y][x]) {
                            sidewalk[y][x] = true;
                        }
                    }
                }
            }
        }
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                if (sidewalk[row][column]) {
                    g.drawImage(pavement, column * TILE_SIZE, row * TILE_SIZE, TILE_SIZE, TILE_SIZE, null);
                }
            }
        }
    }

    private void drawRoads(Graphics2D g) {
        g.setStroke(new BasicStroke(70f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(Color.BLACK);
        for (int[] road : horizontalRoads) {
            int y = (road[1] * TILE_SIZE) + TILE_SIZE / 2;
            g.drawLine(road[0] * TILE_SIZE, y, (road[2] + 1) * TILE_SIZE, y);
        }
        for (int[] road : verticalRoads) {
            int x = (road[0] * TILE_SIZE) + TILE_SIZE / 2;
            g.drawLine(x, road[1] * TILE_SIZE, x, (road[2] + 1) * TILE_SIZE);
        }

        g.setStroke(new BasicStroke(62f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.setColor(new Color(72, 81, 93));
        for (int[] road : horizontalRoads) {
            int y = (road[1] * TILE_SIZE) + TILE_SIZE / 2;
            g.drawLine(road[0] * TILE_SIZE, y, (road[2] + 1) * TILE_SIZE, y);
        }
        for (int[] road : verticalRoads) {
            int x = (road[0] * TILE_SIZE) + TILE_SIZE / 2;
            g.drawLine(x, road[1] * TILE_SIZE, x, (road[2] + 1) * TILE_SIZE);
        }

        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[]{16f, 16f}, 0f));
        g.setColor(new Color(235, 239, 242));
        for (int[] road : horizontalRoads) {
            int y = (road[1] * TILE_SIZE) + TILE_SIZE / 2;
            g.drawLine(road[0] * TILE_SIZE, y, (road[2] + 1) * TILE_SIZE, y);
        }
        for (int[] road : verticalRoads) {
            int x = (road[0] * TILE_SIZE) + TILE_SIZE / 2;
            g.drawLine(x, road[1] * TILE_SIZE, x, (road[2] + 1) * TILE_SIZE);
        }
    }

    private void drawBridgeApproaches(Graphics2D g) {
        int y = 28 * TILE_SIZE;
        g.setColor(new Color(153, 111, 69));
        g.fillRect(60 * TILE_SIZE, y + 5, 4 * TILE_SIZE, TILE_SIZE - 10);
        g.setColor(new Color(82, 50, 27));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(60 * TILE_SIZE, y + 4, WORLD_WIDTH, y + 4);
        g.drawLine(60 * TILE_SIZE, y + TILE_SIZE - 4, WORLD_WIDTH, y + TILE_SIZE - 4);
    }

    private void drawWaterCell(Graphics2D g, int x, int y, int row, int column) {
        g.setColor(new Color(21, 135, 205));
        g.fillRect(x, y, TILE_SIZE, TILE_SIZE);
        g.setColor(new Color(74, 190, 239, 180));
        int waveX = x + ((column * 11 + row * 5) % 21) + 4;
        int waveY = y + ((row * 7 + column * 3) % 23) + 5;
        g.drawLine(waveX, waveY, Math.min(x + TILE_SIZE - 3, waveX + 8), waveY);
    }

    private BufferedImage cropTileBorder(BufferedImage tile) {
        int inset = Math.min(16, Math.min(tile.getWidth(), tile.getHeight()) / 4);
        int width = Math.max(1, tile.getWidth() - inset * 2);
        int height = Math.max(1, tile.getHeight() - inset * 2);
        return tile.getSubimage(inset, inset, width, height);
    }
}
