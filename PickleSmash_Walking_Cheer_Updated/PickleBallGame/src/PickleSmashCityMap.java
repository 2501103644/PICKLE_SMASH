import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * ============================================================================
 * PICKLE SMASH — WALKABLE TILE-BASED TOWN MAP (JAVA 2D / SWING)
 * ============================================================================
 * Features:
 * - 64 x 36 world made from separate 32-pixel ground, road, and water layers
 * - Connected training, Barangay, residential, plaza, city, church, farm, and
 * provincial districts using the supplied map asset pack
 * - Walkable 4-directional Pickleball Athlete (WASD / Arrow Keys, Shift to Jog)
 * - Smooth Zoomed-In Follow Camera (Mouse Wheel or +/- to zoom, M for Full Map)
 * - Interactive Landmark Inspection ([E] or [SPACE]) and F12 PNG Export
 *
 * Compile & Run:
 * See the project README for the full javac and Main launch commands.
 * ============================================================================
 */
public class PickleSmashCityMap extends JPanel
        implements Runnable, KeyListener, MouseWheelListener, MouseMotionListener, MouseListener {

    // ------------------------------------------------------------------------
    // 1. HD WORLD & VIEWPORT CONSTANTS (16:9 FULL HD ASPECT RATIO)
    // ------------------------------------------------------------------------
    public static final int WORLD_WIDTH = TileMap.WORLD_WIDTH;
    public static final int WORLD_HEIGHT = TileMap.WORLD_HEIGHT;
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;

    // Pre-rendered terrain, roads, and river tiles
    private BufferedImage worldBaseCache;
    private final TileMap tileMap = new TileMap();
    private final MapAssetRegistry mapAssets;
    private long mapLayoutModified = Long.MIN_VALUE;
    private long mapLayoutLength = -1;
    private long lastMapLayoutCheck;
    private BufferedImage basePlayerIdleSprite;
    private BufferedImage playerIdleSprite;
    private BufferedImage[] basePlayerWalkSprites;
    private BufferedImage[] playerWalkSprites;
    private BufferedImage[] playerWalkUpSprites;
    private BufferedImage[] playerWalkDownSprites;

    // Game loop state
    private Thread gameThread;
    private volatile boolean running = false;
    private double animTime = 0.0;
    private Runnable exitAction;
    private final Rectangle backButtonBounds = new Rectangle(16, 16, 118, 46);
    private boolean backButtonHovered = false;

    // ------------------------------------------------------------------------
    // 2. CAMERA STATE (SMOOTH ZOOM-IN & CHARACTER TRACKING)
    // ------------------------------------------------------------------------
    private double camX = 620;
    private double camY = 300;
    private double camZoom = 1.75; // Default zoomed-in close-up scale
    private double targetZoom = 1.75;
    private double savedZoom = 1.75;
    private boolean fullMapOverview = false;

    // Optional mouse drag panning when in Full Map Overview
    private boolean isDraggingCam = false;
    private int lastMouseX, lastMouseY;

    // ------------------------------------------------------------------------
    // 3. WALKABLE CHARACTER STATE
    // ------------------------------------------------------------------------
    private double playerX = 620;
    private double playerY = 300;
    private int playerDir = 0; // 0=Down, 1=Up, 2=Left, 3=Right
    private double walkAnimTimer = 0.0;
    private boolean isMoving = false;
    private boolean isJogging = false;

    // Input flags
    private boolean keyUp, keyDown, keyLeft, keyRight, keyShift;
    private boolean debugEnabled;

    // ------------------------------------------------------------------------
    // 4. WORLD ENTITIES, COLLISION & INTERACTIVE LANDMARKS
    // ------------------------------------------------------------------------
    private final List<Rectangle2D.Double> solidColliders = new ArrayList<>();
    private final List<WorldProp> depthSortedProps = new ArrayList<>();
    private final List<Landmark> landmarks = new ArrayList<>();
    private final List<PinBanner> pinBanners = new ArrayList<>();

    private Landmark nearbyLandmark = null;
    private Landmark activeDialogue = null;
    private String toastMessage = null;
    private double toastTimer = 0.0;

    // Helper data structures
    private static class Landmark {
        final String title;
        final String zoneTag;
        final String description;
        final double interactX;
        final double interactY;
        final double radius;

        Landmark(String title, String zoneTag, String description, double x, double y, double r) {
            this.title = title;
            this.zoneTag = zoneTag;
            this.description = description;
            this.interactX = x;
            this.interactY = y;
            this.radius = r;
        }
    }

    private static class PinBanner {
        final String label;
        final double x;
        final double y;

        PinBanner(String label, double x, double y) {
            this.label = label;
            this.x = x;
            this.y = y;
        }
    }

    @FunctionalInterface
    private interface PropRenderer {
        void draw(Graphics2D g, double time);
    }

    private static class WorldProp {
        final double sortY;
        final PropRenderer renderer;

        WorldProp(double sortY, PropRenderer renderer) {
            this.sortY = sortY;
            this.renderer = renderer;
        }
    }

    // ------------------------------------------------------------------------
    // 5. CONSTRUCTOR & INITIALIZATION
    // ------------------------------------------------------------------------
    public PickleSmashCityMap() {
        setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
        setBackground(new Color(12, 20, 36));
        setFocusable(true);
        addKeyListener(this);
        addMouseWheelListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);

        mapAssets = new MapAssetRegistry();
        loadPlayerSprites();
        buildWorldData();
        bakeStaticHDWorldLayer();

        camX = playerX;
        camY = playerY;
    }

    private void loadPlayerSprites() {
        BufferedImage spriteSheet = AssetLoader.loadImage("spritesheet");
        if (spriteSheet == null) {
            throw new IllegalStateException("Could not load assets/spritesheet.png");
        }

        BufferedImage[][] spriteGrid = AssetLoader.extractSpriteGrid(
                AssetLoader.stripWhiteBackground(spriteSheet, 22), 3, 3);
        if (spriteGrid == null || spriteGrid[0][0] == null) {
            throw new IllegalStateException("Could not extract the Home screen idle character sprite.");
        }
        basePlayerIdleSprite = spriteGrid[0][0];

        basePlayerWalkSprites = new BufferedImage[4];
        for (int i = 0; i < basePlayerWalkSprites.length; i++) {
            basePlayerWalkSprites[i] = AssetLoader.loadImage("walk_red_" + i);
            if (basePlayerWalkSprites[i] == null) {
                throw new IllegalStateException("Could not load assets/walk_red_" + i + ".png");
            }
        }
        setPlayerJerseyColor(new Color(220, 38, 38));
    }

    public void setPlayerJerseyColor(Color jerseyColor) {
        if (jerseyColor == null) {
            throw new IllegalArgumentException("Jersey color cannot be null.");
        }
        playerIdleSprite = AssetLoader.tintRedJersey(basePlayerIdleSprite, jerseyColor);
        playerWalkSprites = new BufferedImage[basePlayerWalkSprites.length];
        for (int i = 0; i < basePlayerWalkSprites.length; i++) {
            playerWalkSprites[i] = AssetLoader.tintRedJersey(basePlayerWalkSprites[i], jerseyColor);
        }
        playerWalkUpSprites = createVerticalWalkSprites(jerseyColor, true);
        playerWalkDownSprites = createVerticalWalkSprites(jerseyColor, false);
    }

    private BufferedImage[] createVerticalWalkSprites(Color jerseyColor, boolean facingAway) {
        BufferedImage[] frames = new BufferedImage[4];
        int[] legSwing = { -4, 0, 4, 0 };
        int[] armSwing = { 4, 0, -4, 0 };
        int[] bob = { 0, -1, 0, -1 };

        for (int i = 0; i < frames.length; i++) {
            BufferedImage image = new BufferedImage(48, 96, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawVerticalWalkFrame(g, jerseyColor, facingAway, legSwing[i], armSwing[i], bob[i]);
            g.dispose();
            frames[i] = image;
        }
        return frames;
    }

    private void drawVerticalWalkFrame(
            Graphics2D g, Color jerseyColor, boolean facingAway, int legSwing, int armSwing, int bob) {
        Color outline = new Color(30, 27, 45);
        Color skin = new Color(254, 215, 170);
        Color hair = new Color(30, 27, 75);
        Color shorts = new Color(30, 41, 59);

        // Paddle and swinging arms sit behind the torso.
        int paddleX = facingAway ? 3 : 35;
        g.setColor(outline);
        g.fillRoundRect(paddleX + 3, 47 + bob, 4, 16, 2, 2);
        g.setColor(new Color(56, 189, 248));
        g.fillRoundRect(paddleX, 37 + bob, 10, 15, 5, 5);
        g.setColor(new Color(250, 204, 21));
        g.fillRoundRect(paddleX + 2, 39 + bob, 6, 10, 3, 3);

        int leftArmX = 7 + armSwing;
        int rightArmX = 31 - armSwing;
        g.setColor(outline);
        g.fillRoundRect(leftArmX, 35 + bob, 10, 22, 5, 5);
        g.fillRoundRect(rightArmX, 35 + bob, 10, 22, 5, 5);
        g.setColor(jerseyColor);
        g.fillRoundRect(leftArmX + 2, 36 + bob, 7, 11, 4, 4);
        g.fillRoundRect(rightArmX + 1, 36 + bob, 7, 11, 4, 4);
        g.setColor(skin);
        g.fillRoundRect(leftArmX + 2, 46 + bob, 7, 10, 4, 4);
        g.fillRoundRect(rightArmX + 1, 46 + bob, 7, 10, 4, 4);

        int leftLegX = 15 + legSwing;
        int rightLegX = 25 - legSwing;
        int leftLift = legSwing > 0 ? 2 : 0;
        int rightLift = legSwing < 0 ? 2 : 0;
        g.setColor(outline);
        g.fillRoundRect(leftLegX - 1, 72 + bob - leftLift, 10, 16 + leftLift, 4, 4);
        g.fillRoundRect(rightLegX - 1, 72 + bob - rightLift, 10, 16 + rightLift, 4, 4);
        g.setColor(skin);
        g.fillRoundRect(leftLegX + 1, 73 + bob - leftLift, 6, 12 + leftLift, 3, 3);
        g.fillRoundRect(rightLegX + 1, 73 + bob - rightLift, 6, 12 + rightLift, 3, 3);

        g.setColor(outline);
        g.fillRoundRect(leftLegX - 4, 84 + bob - leftLift, 14, 9, 5, 5);
        g.fillRoundRect(rightLegX - 2, 84 + bob - rightLift, 14, 9, 5, 5);
        g.setColor(Color.WHITE);
        g.fillRoundRect(leftLegX - 3, 85 + bob - leftLift, 12, 6, 4, 4);
        g.fillRoundRect(rightLegX - 1, 85 + bob - rightLift, 12, 6, 4, 4);
        g.setColor(jerseyColor);
        g.fillRect(leftLegX, 86 + bob - leftLift, 5, 2);
        g.fillRect(rightLegX + 2, 86 + bob - rightLift, 5, 2);

        g.setColor(outline);
        g.fillRoundRect(13, 63 + bob, 22, 15, 5, 5);
        g.setColor(shorts);
        g.fillRoundRect(15, 64 + bob, 18, 12, 4, 4);

        g.setColor(outline);
        g.fillRoundRect(10, 30 + bob, 28, 38, 9, 9);
        g.setColor(jerseyColor);
        g.fillRoundRect(12, 32 + bob, 24, 34, 8, 8);
        g.setColor(new Color(255, 255, 255, 225));
        g.fillRect(15, 44 + bob, 18, 3);
        if (facingAway) {
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            g.drawString("7", 21, 59 + bob);
        }

        g.setColor(outline);
        g.fillOval(9, 4 + bob, 30, 31);
        if (facingAway) {
            g.setColor(hair);
            g.fillOval(11, 5 + bob, 26, 28);
            g.setColor(jerseyColor);
            g.fillRect(11, 17 + bob, 26, 5);
        } else {
            g.setColor(skin);
            g.fillOval(12, 9 + bob, 24, 24);
            g.setColor(hair);
            g.fillArc(10, 4 + bob, 28, 24, 0, 180);
            g.setColor(jerseyColor);
            g.fillRect(11, 16 + bob, 26, 5);
            g.setColor(outline);
            g.fillRect(17, 23 + bob, 3, 3);
            g.fillRect(28, 23 + bob, 3, 3);
        }
    }

    /**
     * Configures all buildings, trees, collisions, pins, and interactive landmarks
     * to match the exact layout of the Pickle Smash town & city map.
     */
    private void buildWorldData() {
        solidColliders.clear();
        depthSortedProps.clear();
        landmarks.clear();
        pinBanners.clear();

        addMapSpriteProp("training_building", 180, 58, 369, 171, 229);
        addBuildingCollision(292, 105, 150, 88, 52);
        landmarks.add(new Landmark(
                "TRAINING CAMPUS", "TRAINING DISTRICT",
                "The town's pickleball training campus.", 500, 268, 100));

        addMapSpriteProp("barangay_hall", 750, 58, 430, 193, 251);
        addBuildingCollision(827, 108, 274, 100, 56);
        pinBanners.add(new PinBanner("BARANGAY LEVEL", 985, 115));
        addWavingFlagProp(782, 118, 74);
        landmarks.add(new Landmark(
                "BARANGAY HALL", "BARANGAY LEVEL",
                "The barangay hall and nearby homes.", 1070, 270, 100));

        addMapSpriteProp("house_red_roof", 1325, 97, 122, 180, 277);
        addBuildingCollision(1350, 199, 72, 64, 34);
        addMapSpriteProp("house_blue_roof", 1490, 113, 122, 150, 263);
        addBuildingCollision(1517, 195, 68, 51, 34);
        addMapSpriteProp("office_tower", 1620, 105, 112, 150, 255);
        addBuildingCollision(1645, 190, 62, 52, 30);
        addMapSpriteProp("house_red_roof", 1740, 116, 105, 155, 271);
        addBuildingCollision(1762, 205, 59, 49, 30);
        pinBanners.add(new PinBanner("RESIDENTIAL STRIP", 1560, 115));

        addMapSpriteProp("sari_sari_shop", 180, 423, 156, 154, 577);
        addBuildingCollision(215, 487, 86, 74, 34);
        addMapSpriteProp("house_red_roof", 357, 418, 110, 164, 582);
        addBuildingCollision(380, 510, 62, 60, 32);
        addMapSpriteProp("house_blue_roof", 493, 434, 112, 138, 572);
        addBuildingCollision(516, 505, 66, 52, 32);
        addMapSpriteProp("street_sign", 292, 515, 38, 42, 557);
        pinBanners.add(new PinBanner("SARI-SARI STORE", 425, 385));
        landmarks.add(new Landmark(
                "SARI-SARI SHOP", "MARKET DISTRICT",
                "A neighborhood stop for snacks and refreshments between pickleball matches.", 293, 558, 96));

        addFountainProp(745, 342, 246, 252, 594);
        addSolidBox(850, 444, 38, 38);
        addMapSpriteProp("bench", 770, 542, 62, 32, 574);
        addMapSpriteProp("bench", 904, 542, 62, 32, 574);
        addMapSpriteProp("street_lamp", 728, 410, 22, 72, 482);
        addMapSpriteProp("street_lamp", 981, 410, 22, 72, 482);
        pinBanners.add(new PinBanner("FOUNTAIN PLAZA", 975, 385));
        landmarks.add(new Landmark(
                "CENTRAL FOUNTAIN PLAZA", "TOWN SQUARE",
                "A paved town square linking the neighborhoods and the main road network.", 880, 580, 112));

        addMapSpriteProp("city_level_complex", 1325, 340, 365, 236, 576);
        addMapSpriteProp("office_tower", 1710, 378, 104, 170, 548);
        addBuildingCollision(1375, 423, 250, 104, 66);
        addBuildingCollision(1731, 465, 57, 57, 30);
        landmarks.add(new Landmark(
                "CITY SPORTS COMPLEX", "CITY LEVEL",
                "The modern sports district, connected to the rest of town by paved streets.", 1640, 572, 112));

        addMapSpriteProp("house_red_roof", 180, 704, 122, 180, 884);
        addBuildingCollision(207, 808, 68, 61, 34);
        addMapSpriteProp("house_blue_roof", 285, 735, 122, 150, 885);
        addBuildingCollision(313, 818, 67, 52, 34);
        addMapSpriteProp("house_red_roof", 468, 710, 122, 180, 890);
        addBuildingCollision(495, 814, 68, 61, 34);
        addMapSpriteProp("palm_tree", 558, 780, 74, 101, 881);
        pinBanners.add(new PinBanner("RESIDENTIAL HOMES", 425, 700));

        addMapSpriteProp("church", 730, 692, 172, 189, 881);
        addBuildingCollision(782, 773, 70, 90, 38);
        addMapSpriteProp("wooden_house", 1000, 716, 179, 155, 871);
        addBuildingCollision(1040, 788, 95, 62, 42);
        addMapSpriteProp("crop_rows", 988, 874, 216, 142, 1016);
        pinBanners.add(new PinBanner("CHURCH & FARM", 970, 700));
        landmarks.add(new Landmark(
                "VILLAGE CHURCH", "CHURCH DISTRICT",
                "A small village church beside traditional homes and walking paths.", 862, 880, 100));
        landmarks.add(new Landmark(
                "COMMUNITY FARM", "AGRICULTURAL GROVE",
                "Vegetable plots and a wooden farmhouse sit along the southern town road.", 1178, 972, 100));

        addMapSpriteProp("provincial_pavilion", 1310, 724, 485, 209, 933);
        addSolidBox(1374, 810, 22, 88);
        addSolidBox(1720, 810, 22, 88);
        addMapSpriteProp("bench", 1390, 945, 66, 34, 976);
        addMapSpriteProp("street_lamp", 1764, 812, 22, 72, 884);
        pinBanners.add(new PinBanner("PROVINCIAL LEVEL", 1575, 700));
        addWavingFlagProp(1438, 730, 70);
        addWavingFlagProp(1748, 730, 70);
        landmarks.add(new Landmark(
                "PROVINCIAL SPORTS PAVILION", "PROVINCIAL LEVEL",
                "The open-air provincial pickleball pavilion and its nearby community grounds.", 1620, 980, 115));

        addTreeProp(1120, 555, "tree_large", 74, 108);
        addTreeProp(560, 918, "tree_large", 77, 112);
        addTreeProp(1170, 920, "tree_large", 76, 110);
        addTreeProp(1815, 980, "palm_tree", 72, 98);
        applyMapLayoutOverride();
    }

    private void applyMapLayoutOverride() {
        java.util.List<MapLayoutStore.Entry> layout = MapLayoutStore.load();
        if (layout == null) {
            return;
        }

        depthSortedProps.clear();
        solidColliders.clear();
        for (MapLayoutStore.Entry entry : layout) {
            if (entry.blocking) {
                double radians = Math.toRadians(entry.rotationDegrees);
                double colliderWidth = Math.abs(entry.width * Math.cos(radians))
                        + Math.abs(entry.height * Math.sin(radians));
                double colliderHeight = Math.abs(entry.width * Math.sin(radians))
                        + Math.abs(entry.height * Math.cos(radians));
                double centerX = entry.x + entry.width / 2.0;
                double centerY = entry.y + entry.height / 2.0;
                addSolidBox(centerX - colliderWidth / 2.0, centerY - colliderHeight / 2.0,
                        colliderWidth, colliderHeight);
            }
            final MapLayoutStore.Entry prop = entry;
            depthSortedProps.add(new WorldProp(
                    prop.sortY,
                    (g, time) -> {
                        BufferedImage sprite = mapAssets.get(prop.assetKey);
                        Graphics2D rotatedGraphics = (Graphics2D) g.create();
                        rotatedGraphics.rotate(Math.toRadians(prop.rotationDegrees),
                                prop.x + prop.width / 2.0, prop.y + prop.height / 2.0);
                        rotatedGraphics.drawImage(sprite, prop.x, prop.y, prop.width, prop.height, null);
                        if ("fountain".equals(prop.assetKey)) {
                            drawFountainEffect(rotatedGraphics, time,
                                    prop.x, prop.y, prop.width, prop.height);
                        }
                        rotatedGraphics.dispose();
                    }));
        }
        java.io.File layoutFile = MapLayoutStore.getLayoutFile();
        mapLayoutModified = layoutFile.isFile() ? layoutFile.lastModified() : Long.MIN_VALUE;
        mapLayoutLength = layoutFile.isFile() ? layoutFile.length() : -1;
        repaint();
    }

    private void checkForMapLayoutChanges(long now) {
        if (now - lastMapLayoutCheck < 500_000_000L) {
            return;
        }
        lastMapLayoutCheck = now;
        java.io.File layoutFile = MapLayoutStore.getLayoutFile();
        long modified = layoutFile.isFile() ? layoutFile.lastModified() : Long.MIN_VALUE;
        long length = layoutFile.isFile() ? layoutFile.length() : -1;
        if (modified == mapLayoutModified && length == mapLayoutLength) {
            return;
        }
        mapLayoutModified = modified;
        mapLayoutLength = length;
        if (modified != Long.MIN_VALUE) {
            SwingUtilities.invokeLater(this::applyMapLayoutOverride);
        }
    }

    private void addSolidBox(double x, double y, double w, double h) {
        solidColliders.add(new Rectangle2D.Double(x, y, w, h));
    }

    private void addMapSpriteProp(String assetKey, int x, int y, int width, int height, double sortY) {
        BufferedImage sprite = mapAssets.get(assetKey);
        depthSortedProps.add(new WorldProp(
                sortY, (g, t) -> g.drawImage(sprite, x, y, width, height, null)));
    }

    private void addFountainProp(int x, int y, int width, int height, double sortY) {
        BufferedImage sprite = mapAssets.get("fountain");
        depthSortedProps.add(new WorldProp(
                sortY, (g, time) -> {
                    g.drawImage(sprite, x, y, width, height, null);
                    drawFountainEffect(g, time, x, y, width, height);
                }));
    }

    private void drawFountainEffect(Graphics2D g, double time,
                                    int x, int y, int width, int height) {
        Graphics2D water = (Graphics2D) g.create();
        water.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double centerX = x + width * 0.50;
        double topY = y + height * 0.39;
        double basinTopY = y + height * 0.55;
        double basinBottomY = y + height * 0.63;
        double sideSpread = width * 0.13;

        water.setStroke(new BasicStroke(Math.max(2f, (float) (width * 0.018f)),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        water.setColor(new Color(125, 225, 255, 230));
        water.drawLine((int) centerX, (int) topY, (int) centerX, (int) basinTopY);

        double phase = (time * 1.7) % 1.0;
        for (int i = 0; i < 9; i++) {
            double offsetPhase = (phase + i * 0.117) % 1.0;
            double side = (i % 2 == 0 ? -1.0 : 1.0) * sideSpread * (0.45 + (i % 4) * 0.17);
            double startX = centerX + side * 0.18;
            double startY = topY + 4;
            double endX = centerX + side;
            double endY = basinTopY - height * 0.04;
            double t = offsetPhase;
            double dropletX = startX + (endX - startX) * t;
            double dropletY = startY + (endY - startY) * t;
            double arc = Math.sin(t * Math.PI) * height * 0.025;
            dropletY -= arc;

            water.setColor(new Color(166, 236, 255, 210));
            water.fillOval((int) (dropletX - 2.5), (int) (dropletY - 2.5), 5, 5);
        }

        for (int i = 0; i < 3; i++) {
            double ripple = (phase + i / 3.0) % 1.0;
            int rippleWidth = (int) (width * (0.12 + ripple * 0.16));
            int rippleHeight = (int) (height * (0.018 + ripple * 0.018));
            water.setColor(new Color(210, 248, 255, (int) (170 * (1.0 - ripple))));
            water.setStroke(new BasicStroke(2f));
            water.drawOval((int) centerX - rippleWidth / 2,
                    (int) basinBottomY - rippleHeight / 2,
                    rippleWidth, rippleHeight);
        }

        water.dispose();
    }

    private void addWavingFlagProp(int x, int y, int width) {
        int height = Math.max(20, width / 2);
        depthSortedProps.add(new WorldProp(y + height + 6, (g, time) -> {
            Graphics2D flag = (Graphics2D) g.create();
            flag.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int poleWidth = Math.max(2, width / 35);
            int clothX = x + poleWidth;
            double wave = Math.sin(time * 4.0) * width * 0.035;
            flag.setColor(new Color(226, 232, 240));
            flag.setStroke(new BasicStroke(poleWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            flag.drawLine(x, y - 3, x, y + height + 6);

            Path2D upperHalf = new Path2D.Double();
            upperHalf.moveTo(clothX, y);
            upperHalf.curveTo(clothX + width * 0.35, y + wave,
                    clothX + width * 0.68, y - wave, clothX + width, y + wave * 0.3);
            upperHalf.lineTo(clothX + width, y + height / 2.0 + wave * 0.3);
            upperHalf.curveTo(clothX + width * 0.68, y + height / 2.0 - wave,
                    clothX + width * 0.35, y + height / 2.0 + wave, clothX, y + height / 2.0);
            upperHalf.closePath();
            flag.setColor(new Color(0, 56, 168));
            flag.fill(upperHalf);

            Path2D lowerHalf = new Path2D.Double();
            lowerHalf.moveTo(clothX, y + height / 2.0);
            lowerHalf.curveTo(clothX + width * 0.35, y + height / 2.0 + wave,
                    clothX + width * 0.68, y + height / 2.0 - wave,
                    clothX + width, y + height / 2.0 + wave * 0.3);
            lowerHalf.lineTo(clothX + width, y + height + wave * 0.3);
            lowerHalf.curveTo(clothX + width * 0.68, y + height - wave,
                    clothX + width * 0.35, y + height + wave, clothX, y + height);
            lowerHalf.closePath();
            flag.setColor(new Color(206, 17, 38));
            flag.fill(lowerHalf);

            Polygon triangle = new Polygon(
                    new int[] { clothX, clothX + width / 3, clothX + width / 3 },
                    new int[] { y + height / 2, y + (int) (height * 0.16), y + (int) (height * 0.84) },
                    3);
            flag.setColor(Color.WHITE);
            flag.fillPolygon(triangle);
            flag.setColor(new Color(252, 209, 22));
            flag.fillOval(clothX + width / 9, y + height / 2 - height / 10,
                    Math.max(3, width / 7), Math.max(3, height / 5));
            flag.dispose();
        }));
    }

    private void addBuildingCollision(double x, double y, double width, double height, double doorGap) {
        double wallHeight = Math.max(12, height - 20);
        double sideWidth = Math.max(0, (width - doorGap) / 2);
        addSolidBox(x, y, width, wallHeight);
        addSolidBox(x, y + wallHeight, sideWidth, height - wallHeight);
        addSolidBox(x + width - sideWidth, y + wallHeight, sideWidth, height - wallHeight);
    }

    private void addTreeProp(int centerX, int baseY, String assetKey, int width, int height) {
        addSolidBox(centerX - 11, baseY - 15, 22, 16);
        addMapSpriteProp(assetKey, centerX - width / 2, baseY - height, width, height, baseY);
    }

    // ------------------------------------------------------------------------
    // 6. STATIC TILE LAYER BAKER
    // ------------------------------------------------------------------------
    private void bakeStaticHDWorldLayer() {
        worldBaseCache = new BufferedImage(WORLD_WIDTH, WORLD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = worldBaseCache.createGraphics();
        enablePixelArtRendering(g);
        tileMap.draw(g, mapAssets);
        g.dispose();
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // 7. WALKABLE CHARACTER RENDERER (HOME SCREEN SPRITES)
    // ------------------------------------------------------------------------
    private void drawWalkableCharacter(Graphics2D g, double x, double y) {
        int px = (int) Math.round(x);
        int py = (int) Math.round(y);
        int frame = isMoving ? (int) (walkAnimTimer * 8) % playerWalkSprites.length : 0;
        BufferedImage sprite;
        if (playerDir == 1) {
            sprite = isMoving ? playerWalkUpSprites[frame] : playerWalkUpSprites[0];
        } else if (playerDir == 0) {
            sprite = isMoving ? playerWalkDownSprites[frame] : playerWalkDownSprites[0];
        } else {
            sprite = isMoving ? playerWalkSprites[frame] : playerIdleSprite;
        }
        int spriteHeight = 88;
        int spriteWidth = Math.max(1, (int) Math.round(
                spriteHeight * (double) sprite.getWidth() / sprite.getHeight()));

        g.setColor(new Color(15, 23, 42, 110));
        g.fillOval(px - spriteWidth / 3, py - 7, spriteWidth * 2 / 3, 12);

        int left = px - spriteWidth / 2;
        int top = py - spriteHeight;
        if (playerDir == 2) {
            g.drawImage(sprite, left + spriteWidth, top, -spriteWidth, spriteHeight, null);
        } else {
            g.drawImage(sprite, left, top, spriteWidth, spriteHeight, null);
        }
    }

    // ------------------------------------------------------------------------
    // 8. BLUE LOCATION PIN BANNERS (MATCHING REFERENCE IMAGE)
    // ------------------------------------------------------------------------
    private void drawPinBanner(Graphics2D g, PinBanner pin) {
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        int textW = fm.stringWidth(pin.label);
        int boxW = textW + 38;
        int boxH = 26;
        int bx = (int) (pin.x - boxW / 2.0);
        int by = (int) (pin.y - boxH / 2.0);

        // Drop shadow
        g.setColor(new Color(15, 23, 42, 140));
        g.fillRoundRect(bx + 2, by + 3, boxW, boxH, 10, 10);

        // Blue gradient badge
        GradientPaint badgeGrad = new GradientPaint(bx, by, new Color(37, 99, 235), bx, by + boxH,
                new Color(29, 78, 216));
        g.setPaint(badgeGrad);
        g.fillRoundRect(bx, by, boxW, boxH, 10, 10);

        g.setColor(new Color(15, 23, 42));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(bx, by, boxW, boxH, 10, 10);
        g.setColor(new Color(147, 197, 253));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(bx + 2, by + 2, boxW - 4, boxH - 4, 8, 8);

        // Yellow Teardrop Map Pin Icon on the left
        int px = bx + 14;
        int py = by + 12;
        g.setColor(new Color(250, 204, 21));
        g.fillOval(px - 6, py - 8, 12, 12);
        Polygon pinTip = new Polygon(new int[] { px - 5, px + 5, px }, new int[] { py, py, py + 7 }, 3);
        g.fillPolygon(pinTip);
        g.setColor(new Color(15, 23, 42));
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(px - 6, py - 8, 12, 12);
        g.fillOval(px - 2, py - 4, 4, 4);

        // Crisp White Label Text
        g.setColor(new Color(15, 23, 42));
        g.drawString(pin.label, bx + 27, by + 17);
        g.setColor(Color.WHITE);
        g.drawString(pin.label, bx + 26, by + 16);
    }

    // ------------------------------------------------------------------------
    // 9. GAME LOOP, MOVEMENT, COLLISION & CAMERA FOLLOW UPDATE
    // ------------------------------------------------------------------------
    public void start() {
        if (running)
            return;
        running = true;
        gameThread = new Thread(this, "PickleSmashTownMapLoop");
        gameThread.start();
    }

    public void stop() {
        running = false;
        if (gameThread != null)
            gameThread.interrupt();
    }

    public void setExitAction(Runnable exitAction) {
        this.exitAction = exitAction;
    }

    private void returnToHome() {
        if (exitAction != null) {
            exitAction.run();
            return;
        }

        java.awt.Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            window.dispose();
        }
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            double dt = Math.min(0.05, (now - lastTime) / 1_000_000_000.0);
            lastTime = now;

            checkForMapLayoutChanges(now);
            update(dt);
            repaint();

            try {
                Thread.sleep(16); // ~60 FPS smooth loop
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void update(double dt) {
        animTime += dt;

        if (toastTimer > 0) {
            toastTimer -= dt;
            if (toastTimer <= 0)
                toastMessage = null;
        }

        // 1. Character Movement (paused while reading landmark dialogue)
        double mx = 0, my = 0;
        if (activeDialogue == null) {
            if (keyUp)
                my -= 1;
            if (keyDown)
                my += 1;
            if (keyLeft)
                mx -= 1;
            if (keyRight)
                mx += 1;
        }

        isMoving = (mx != 0 || my != 0);
        isJogging = keyShift;

        if (isMoving) {
            double len = Math.hypot(mx, my);
            mx /= len;
            my /= len;

            if (Math.abs(mx) > Math.abs(my)) {
                playerDir = (mx < 0) ? 2 : 3;
            } else {
                playerDir = (my < 0) ? 1 : 0;
            }

            double speed = isJogging ? 340.0 : 210.0;
            moveWithCollision(mx * speed * dt, my * speed * dt);
            walkAnimTimer += dt * (isJogging ? 1.5 : 1.0);
        }

        // 2. Smooth Zoom Interpolation & Character Follow Camera
        camZoom += (targetZoom - camZoom) * (1.0 - Math.exp(-10.0 * dt));

        if (!fullMapOverview && !isDraggingCam) {
            double followLerp = 1.0 - Math.exp(-8.5 * dt);
            camX += (playerX - camX) * followLerp;
            camY += (playerY - camY) * followLerp;
        }

        // Clamp camera inside world bounds
        double halfViewW = (getWidth() / camZoom) * 0.5;
        double halfViewH = (getHeight() / camZoom) * 0.5;
        if (halfViewW * 2 >= WORLD_WIDTH) {
            camX = WORLD_WIDTH * 0.5;
        } else {
            camX = Math.max(halfViewW, Math.min(WORLD_WIDTH - halfViewW, camX));
        }
        if (halfViewH * 2 >= WORLD_HEIGHT) {
            camY = WORLD_HEIGHT * 0.5;
        } else {
            camY = Math.max(halfViewH, Math.min(WORLD_HEIGHT - halfViewH, camY));
        }

        // 3. Detect Nearest Landmark for [E] Inspection
        Landmark best = null;
        double bestDist = Double.MAX_VALUE;
        for (Landmark lm : landmarks) {
            double d = Math.hypot(playerX - lm.interactX, playerY - lm.interactY);
            if (d <= lm.radius && d < bestDist) {
                best = lm;
                bestDist = d;
            }
        }
        nearbyLandmark = best;
    }

    private double getMinimumZoom() {
        int viewportWidth = getWidth() > 0 ? getWidth() : WINDOW_WIDTH;
        int viewportHeight = getHeight() > 0 ? getHeight() : WINDOW_HEIGHT;
        return Math.max((double) viewportWidth / WORLD_WIDTH, (double) viewportHeight / WORLD_HEIGHT);
    }

    private void moveWithCollision(double dx, double dy) {
        if (dx != 0) {
            if (!checkCollision(playerX + dx, playerY)) {
                playerX += dx;
            } else {
                // Gentle corner sliding
                for (double nudge = 4; nudge <= 14; nudge += 4) {
                    if (!checkCollision(playerX + dx, playerY - nudge)) {
                        playerY -= 2;
                        playerX += dx * 0.7;
                        break;
                    }
                    if (!checkCollision(playerX + dx, playerY + nudge)) {
                        playerY += 2;
                        playerX += dx * 0.7;
                        break;
                    }
                }
            }
        }
        if (dy != 0) {
            if (!checkCollision(playerX, playerY + dy)) {
                playerY += dy;
            } else {
                for (double nudge = 4; nudge <= 14; nudge += 4) {
                    if (!checkCollision(playerX - nudge, playerY + dy)) {
                        playerX -= 2;
                        playerY += dy * 0.7;
                        break;
                    }
                    if (!checkCollision(playerX + nudge, playerY + dy)) {
                        playerX += 2;
                        playerY += dy * 0.7;
                        break;
                    }
                }
            }
        }
    }

    private boolean checkCollision(double px, double py) {
        Rectangle2D.Double footBox = new Rectangle2D.Double(px - 12, py - 10, 24, 14);
        if (tileMap.blocks(footBox))
            return true;
        for (Rectangle2D.Double box : solidColliders) {
            if (footBox.intersects(box))
                return true;
        }
        return false;
    }

    private String getCurrentDistrictName() {
        if (playerY < 320) {
            if (playerX < 640)
                return "TRAINING CAMPUS";
            if (playerX < 1280)
                return "BARANGAY LEVEL";
            return "RESIDENTIAL STRIP";
        }
        if (playerY < 640) {
            if (playerX < 640)
                return "MARKET & SARI-SARI";
            if (playerX < 1280)
                return "FOUNTAIN PLAZA";
            if (playerX < 1920)
                return "CITY LEVEL";
            return "RIVER FEATURE";
        }
        if (playerX < 640)
            return "RESIDENTIAL HOMES";
        if (playerX < 1280)
            return "CHURCH & FARM";
        if (playerX < 1920)
            return "PROVINCIAL LEVEL";
        return "RIVER FEATURE";
    }

    // ------------------------------------------------------------------------
    // 10. RENDER PIPELINE (HD WORLD + CAMERA + HUD + MINIMAP)
    // ------------------------------------------------------------------------
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        enableHighQuality(g);

        int vw = getWidth();
        int vh = getHeight();

        // 1. Apply Camera Transform (Zoom In & Follow Character)
        Graphics2D worldG = (Graphics2D) g.create();
        worldG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        worldG.translate(vw * 0.5, vh * 0.5);
        worldG.scale(camZoom, camZoom);
        worldG.translate(-camX, -camY);

        renderCompleteWorldScene(worldG, true);
        worldG.dispose();

        // 2. Render Crisp Screen-Space HUD Overlay
        drawScreenHUD(g, vw, vh);
    }

    private void renderCompleteWorldScene(Graphics2D g, boolean includePlayer) {
        // Draw the tile layers baked at native world resolution.
        g.drawImage(worldBaseCache, 0, 0, null);

        // Depth-sort the map props, then keep the player visible above them.
        List<WorldProp> frameList = new ArrayList<>(depthSortedProps);
        frameList.sort(Comparator.comparingDouble(p -> p.sortY));

        for (WorldProp prop : frameList) {
            prop.renderer.draw(g, animTime);
        }

        if (includePlayer) {
            drawWalkableCharacter(g, playerX, playerY);
        }

        if (fullMapOverview) {
            for (PinBanner pin : pinBanners) {
                drawPinBanner(g, pin);
            }
        }

        if (debugEnabled) {
            tileMap.drawDebug(g);
            g.setColor(new Color(255, 40, 40, 210));
            g.setStroke(new BasicStroke(2f));
            for (Rectangle2D.Double collider : solidColliders) {
                g.draw(collider);
            }
            drawDistrictBounds(g);
        }
    }

    private void drawDistrictBounds(Graphics2D g) {
        String[] labels = {
                "TRAINING", "BARANGAY", "RESIDENTIAL", "MARKET", "PLAZA",
                "CITY", "HOMES", "CHURCH / FARM", "PROVINCIAL", "RIVER"
        };
        Rectangle[] bounds = {
                new Rectangle(0, 0, 640, 320), new Rectangle(640, 0, 640, 320),
                new Rectangle(1280, 0, 640, 320), new Rectangle(0, 320, 640, 320),
                new Rectangle(640, 320, 640, 320), new Rectangle(1280, 320, 640, 320),
                new Rectangle(0, 640, 640, 512), new Rectangle(640, 640, 640, 512),
                new Rectangle(1280, 640, 640, 512), new Rectangle(1920, 640, 128, 512)
        };
        g.setColor(new Color(255, 210, 45, 220));
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < bounds.length; i++) {
            g.draw(bounds[i]);
            g.drawString(labels[i], bounds[i].x + 8, bounds[i].y + 18);
        }
    }

    private void drawScreenHUD(Graphics2D g, int vw, int vh) {
        g.setColor(backButtonHovered
                ? new Color(37, 99, 235, 245)
                : new Color(15, 23, 42, 230));
        g.fillRoundRect(backButtonBounds.x, backButtonBounds.y,
                backButtonBounds.width, backButtonBounds.height, 12, 12);
        g.setColor(new Color(56, 189, 248));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(backButtonBounds.x, backButtonBounds.y,
                backButtonBounds.width, backButtonBounds.height, 12, 12);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("\u25C0  BACK", backButtonBounds.x + 16, backButtonBounds.y + 30);

        if (!fullMapOverview) {
            // Top-Right Live Mini-Map Radar
            int radarW = 224;
            int radarH = 126;
            int rx = vw - radarW - 16;
            int ry = 16;
            g.setColor(new Color(15, 23, 42, 230));
            g.fillRoundRect(rx - 6, ry - 6, radarW + 12, radarH + 30, 12, 12);
            g.setColor(new Color(148, 163, 184));
            g.drawRoundRect(rx - 6, ry - 6, radarW + 12, radarH + 30, 12, 12);

            g.drawImage(worldBaseCache, rx, ry, radarW, radarH, null);

            // Camera viewport rectangle on radar
            double viewWorldW = vw / camZoom;
            double viewWorldH = vh / camZoom;
            int vRectX = rx + (int) (((camX - viewWorldW * 0.5) / WORLD_WIDTH) * radarW);
            int vRectY = ry + (int) (((camY - viewWorldH * 0.5) / WORLD_HEIGHT) * radarH);
            int vRectW = (int) ((viewWorldW / WORLD_WIDTH) * radarW);
            int vRectH = (int) ((viewWorldH / WORLD_HEIGHT) * radarH);
            g.setColor(new Color(255, 255, 255, 190));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(vRectX, vRectY, vRectW, vRectH);

            // Player dot on radar
            int dotX = rx + (int) ((playerX / WORLD_WIDTH) * radarW);
            int dotY = ry + (int) ((playerY / WORLD_HEIGHT) * radarH);
            g.setColor(new Color(239, 68, 68));
            g.fillOval(dotX - 4, dotY - 4, 8, 8);
            g.setColor(Color.WHITE);
            g.drawOval(dotX - 4, dotY - 4, 8, 8);

            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            drawCenteredString(g, "WORLD RADAR [M: FULL MAP]", rx + radarW / 2, ry + radarH + 16);

            // District & Zoom Status Card, positioned immediately left of the radar
            int statusW = 330;
            int statusH = 86;
            int statusX = Math.max(16, rx - statusW - 10);
            int statusY = ry;
            g.setColor(new Color(15, 23, 42, 220));
            g.fillRoundRect(statusX, statusY, statusW, statusH, 14, 14);
            g.setColor(new Color(56, 189, 248));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(statusX, statusY, statusW, statusH, 14, 14);

            g.setColor(new Color(250, 204, 21));
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.drawString("PICKLE SMASH — TOWN MAP", statusX + 14, statusY + 22);

            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.drawString(getCurrentDistrictName(), statusX + 14, statusY + 46);

            g.setColor(new Color(148, 163, 184));
            g.setFont(new Font("Monospaced", Font.BOLD, 12));
            g.drawString(String.format("POS: (%d, %d)  |  ZOOM: %.2fx",
                    (int) playerX, (int) playerY, camZoom), statusX + 14, statusY + 69);
        }

        if (!fullMapOverview) {
            // Bottom Controls Bar
            String controlsText = "WASD/Arrows: Walk  |  Shift: Jog  |  Scroll or +/-: Zoom  |  M: Full Map  |  F3: Debug  |  1-4: Warp  |  F12: Export";
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            int barW = fm.stringWidth(controlsText) + 32;
            g.setColor(new Color(15, 23, 42, 210));
            g.fillRoundRect((vw - barW) / 2, vh - 40, barW, 28, 10, 10);
            g.setColor(new Color(203, 213, 225));
            drawCenteredString(g, controlsText, vw / 2, vh - 21);

            if (nearbyLandmark != null && activeDialogue == null) {
                String prompt = "[E] INSPECT " + nearbyLandmark.title;
                g.setFont(new Font("SansSerif", Font.BOLD, 14));
                int pw = g.getFontMetrics().stringWidth(prompt) + 36;
                g.setColor(new Color(15, 23, 42, 235));
                g.fillRoundRect((vw - pw) / 2, vh - 92, pw, 38, 12, 12);
                g.setColor(new Color(250, 204, 21));
                g.setStroke(new BasicStroke(2.5f));
                g.drawRoundRect((vw - pw) / 2, vh - 92, pw, 38, 12, 12);
                drawCenteredString(g, prompt, vw / 2, vh - 68);
            }
        }

        // Active Landmark RPG Dialogue Box
        if (activeDialogue != null) {
            int dw = Math.min(720, vw - 48);
            int dh = 135;
            int dx = (vw - dw) / 2;
            int dy = vh - dh - 52;

            g.setColor(new Color(15, 23, 42, 245));
            g.fillRoundRect(dx, dy, dw, dh, 16, 16);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(3f));
            g.drawRoundRect(dx, dy, dw, dh, 16, 16);

            g.setColor(new Color(250, 204, 21));
            g.setFont(new Font("SansSerif", Font.BOLD, 17));
            g.drawString(activeDialogue.title, dx + 22, dy + 32);

            g.setColor(new Color(56, 189, 248));
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString(activeDialogue.zoneTag + "   •   Press [E] or [ESC] to Close", dx + 22, dy + 52);

            g.setColor(new Color(226, 232, 240));
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            drawWrappedText(g, activeDialogue.description, dx + 22, dy + 80, dw - 44, 20);
        }

        // Toast Notification (e.g. Exported HD PNG)
        if (toastMessage != null) {
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            int tw = g.getFontMetrics().stringWidth(toastMessage) + 34;
            g.setColor(new Color(20, 83, 45, 240));
            g.fillRoundRect((vw - tw) / 2, 24, tw, 36, 12, 12);
            g.setColor(new Color(74, 222, 128));
            g.drawRoundRect((vw - tw) / 2, 24, tw, 36, 12, 12);
            drawCenteredString(g, toastMessage, vw / 2, 47);
        }
    }

    private void exportFullResolutionPNG() {
        try {
            BufferedImage fullHD = new BufferedImage(WORLD_WIDTH, WORLD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = fullHD.createGraphics();
            enableHighQuality(g);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            renderCompleteWorldScene(g, false);
            g.dispose();

            File out = new File("PickleSmash_HD_Map.png");
            ImageIO.write(fullHD, "PNG", out);
            toastMessage = "SAVED 2048x1152 MAP TO: " + out.getName();
            toastTimer = 3.5;
        } catch (Exception ex) {
            toastMessage = "EXPORT FAILED: " + ex.getMessage();
            toastTimer = 3.5;
        }
    }

    // ------------------------------------------------------------------------
    // 11. GRAPHICS & TEXT HELPERS
    // ------------------------------------------------------------------------
    private static void enableHighQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private static void enablePixelArtRendering(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    }

    private static void drawCenteredString(Graphics2D g, String text, int cx, int y) {
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, cx - w / 2, y);
    }

    private static void drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int curY = y;
        for (String w : words) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (fm.stringWidth(test) > maxWidth) {
                g.drawString(line.toString(), x, curY);
                line = new StringBuilder(w);
                curY += lineHeight;
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) {
            g.drawString(line.toString(), x, curY);
        }
    }

    // ------------------------------------------------------------------------
    // 12. KEYBOARD & MOUSE INPUT HANDLERS
    // ------------------------------------------------------------------------
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP)
            keyUp = true;
        if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN)
            keyDown = true;
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT)
            keyLeft = true;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT)
            keyRight = true;
        if (code == KeyEvent.VK_SHIFT)
            keyShift = true;

        if (code == KeyEvent.VK_EQUALS || code == KeyEvent.VK_ADD) {
            fullMapOverview = false;
            targetZoom = Math.min(3.6, targetZoom + 0.25);
            savedZoom = targetZoom;
        } else if (code == KeyEvent.VK_MINUS || code == KeyEvent.VK_SUBTRACT) {
            fullMapOverview = false;
            targetZoom = Math.max(getMinimumZoom(), targetZoom - 0.25);
            savedZoom = targetZoom;
        } else if (code == KeyEvent.VK_M) {
            fullMapOverview = !fullMapOverview;
            if (fullMapOverview) {
                savedZoom = targetZoom;
                targetZoom = Math.max(0.4,
                        Math.min((double) getWidth() / WORLD_WIDTH, (double) getHeight() / WORLD_HEIGHT));
            } else {
                targetZoom = Math.max(1.4, savedZoom);
            }
        } else if (code == KeyEvent.VK_F3) {
            debugEnabled = !debugEnabled;
        } else if (code == KeyEvent.VK_E || code == KeyEvent.VK_SPACE) {
            if (activeDialogue != null) {
                activeDialogue = null;
            } else if (nearbyLandmark != null) {
                activeDialogue = nearbyLandmark;
            }
        } else if (code == KeyEvent.VK_ESCAPE) {
            if (activeDialogue != null) {
                activeDialogue = null;
            } else {
                returnToHome();
            }
        } else if (code == KeyEvent.VK_1) {
            warpPlayer(620, 300, "TRAINING CAMPUS");
        } else if (code == KeyEvent.VK_2) {
            warpPlayer(1070, 270, "BARANGAY LEVEL");
        } else if (code == KeyEvent.VK_3) {
            warpPlayer(1640, 572, "CITY LEVEL");
        } else if (code == KeyEvent.VK_4) {
            warpPlayer(1620, 980, "PROVINCIAL LEVEL");
        } else if (code == KeyEvent.VK_F12) {
            exportFullResolutionPNG();
        }
    }

    private void warpPlayer(double x, double y, String label) {
        playerX = x;
        playerY = y;
        fullMapOverview = false;
        targetZoom = 1.85;
        toastMessage = "WARPED TO: " + label;
        toastTimer = 2.0;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP)
            keyUp = false;
        if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN)
            keyDown = false;
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT)
            keyLeft = false;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT)
            keyRight = false;
        if (code == KeyEvent.VK_SHIFT)
            keyShift = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        fullMapOverview = false;
        double delta = (e.getWheelRotation() < 0) ? 0.22 : -0.22;
        targetZoom = Math.max(getMinimumZoom(), Math.min(3.6, targetZoom + delta));
        savedZoom = targetZoom;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        if (SwingUtilities.isLeftMouseButton(e)
                && backButtonBounds.contains(e.getPoint())) {
            returnToHome();
            return;
        }
        if (SwingUtilities.isRightMouseButton(e) || fullMapOverview) {
            isDraggingCam = true;
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        isDraggingCam = false;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (isDraggingCam) {
            double dx = (e.getX() - lastMouseX) / camZoom;
            double dy = (e.getY() - lastMouseY) / camZoom;
            camX -= dx;
            camY -= dy;
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        boolean hovered = backButtonBounds.contains(e.getPoint());
        if (hovered != backButtonHovered) {
            backButtonHovered = hovered;
            setCursor(hovered ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
            repaint();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    // ------------------------------------------------------------------------
    // 13. MAIN ENTRY POINT
    // ------------------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Pickle Smash — Town Map");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            PickleSmashCityMap mapPanel = new PickleSmashCityMap();
            frame.setContentPane(mapPanel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            mapPanel.requestFocusInWindow();
            mapPanel.start();
        });
    }
}