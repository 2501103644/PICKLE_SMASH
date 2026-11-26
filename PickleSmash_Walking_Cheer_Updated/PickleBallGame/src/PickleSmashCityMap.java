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
 * PICKLE SMASH — HD 2D CITY & TOWN MAP (JAVA 2D / SWING)
 * ============================================================================
 * Features:
 * - High-Resolution 3200 x 1800 HD World with anti-aliased multi-layered art
 * - Faithfully recreates all districts from your map:
 *     1. TRAINING (Coastal Beach, Pier, Gym & Outdoor Court)
 *     2. BARANGAY LEVEL (Barangay Hall, Waving PH Flag & Court)
 *     3. SARI-SARI & Residential Neighborhood
 *     4. CENTRAL FOUNTAIN PLAZA (4-way crosswalk roundabout park)
 *     5. CITY LEVEL (Domed Pickleball Arena with Crossed Paddles & Towers)
 *     6. VILLAGE CHAPEL & Nipa Farmhouse with Crop Rows
 *     7. PROVINCIAL LEVEL (Covered Green-Roof Court, Windmill, Waterfall & Bridge)
 * - Walkable 4-directional Pickleball Athlete (WASD / Arrow Keys, Shift to Jog)
 * - Smooth Zoomed-In Follow Camera (Mouse Wheel or +/- to zoom, M for Full Map)
 * - Interactive Landmark Inspection ([E] or [SPACE]) & F12 HD PNG Export
 *
 * Compile & Run:
 *   javac PickleSmashCityMap.java
 *   java PickleSmashCityMap
 * ============================================================================
 */
public class PickleSmashCityMap extends JPanel implements Runnable, KeyListener, MouseWheelListener, MouseMotionListener, MouseListener {

    // ------------------------------------------------------------------------
    // 1. HD WORLD & VIEWPORT CONSTANTS (16:9 FULL HD ASPECT RATIO)
    // ------------------------------------------------------------------------
    public static final int WORLD_WIDTH = 3200;
    public static final int WORLD_HEIGHT = 1800;
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;

    private static final int CLUBHOUSE = 0;
    private static final int BARANGAY_HALL = 1;
    private static final int PICKLEBALL_ARENA = 2;
    private static final int OFFICE_BLOCK = 3;
    private static final int RED_ROOF_SHOP = 4;
    private static final int SARI_SARI_STORE = 5;
    private static final int GREEN_ROOF_SHOP = 6;
    private static final int CHURCH = 7;
    private static final int BLUE_HOUSE = 8;
    private static final int RED_HOUSE = 9;
    private static final int[][] BUILDING_SPRITE_BOUNDS = {
        {6, 11, 450, 245},
        {465, 7, 373, 244},
        {865, 25, 366, 227},
        {7, 270, 405, 240},
        {416, 280, 240, 230},
        {663, 272, 275, 238},
        {941, 280, 282, 232},
        {699, 513, 325, 235},
        {13, 541, 334, 205},
        {369, 543, 309, 204}
    };

    // Pre-rendered static HD ground, roads, mountains, beach & river layer
    private BufferedImage worldBaseCache;
    private BufferedImage[] buildingSprites;
    private BufferedImage grassTexture;
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

    // ------------------------------------------------------------------------
    // 2. CAMERA STATE (SMOOTH ZOOM-IN & CHARACTER TRACKING)
    // ------------------------------------------------------------------------
    private double camX = 680;
    private double camY = 580;
    private double camZoom = 1.75;        // Default zoomed-in close-up scale
    private double targetZoom = 1.75;
    private double savedZoom = 1.75;
    private boolean fullMapOverview = false;

    // Optional mouse drag panning when in Full Map Overview
    private boolean isDraggingCam = false;
    private int lastMouseX, lastMouseY;

    // ------------------------------------------------------------------------
    // 3. WALKABLE CHARACTER STATE
    // ------------------------------------------------------------------------
    private double playerX = 680;
    private double playerY = 615;
    private int playerDir = 0;            // 0=Down, 1=Up, 2=Left, 3=Right
    private double walkAnimTimer = 0.0;
    private boolean isMoving = false;
    private boolean isJogging = false;

    // Input flags
    private boolean keyUp, keyDown, keyLeft, keyRight, keyShift;

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

        loadGrassTexture();
        loadBuildingSprites();
        loadPlayerSprites();
        buildWorldData();
        bakeStaticHDWorldLayer();

        camX = playerX;
        camY = playerY;
    }

    private void loadGrassTexture() {
        grassTexture = AssetLoader.loadImage("grass_texture");
        if (grassTexture == null) {
            throw new IllegalStateException("Could not load assets/grass_texture.png");
        }
    }

    private void loadBuildingSprites() {
        BufferedImage sheet = AssetLoader.loadImage("city_buildings_sheet");
        if (sheet == null) {
            throw new IllegalStateException("Could not load assets/city_buildings_sheet.png");
        }

        buildingSprites = new BufferedImage[BUILDING_SPRITE_BOUNDS.length];
        for (int i = 0; i < BUILDING_SPRITE_BOUNDS.length; i++) {
            int[] bounds = BUILDING_SPRITE_BOUNDS[i];
            if (bounds[0] + bounds[2] > sheet.getWidth()
                    || bounds[1] + bounds[3] > sheet.getHeight()) {
                throw new IllegalStateException("Building sprite " + i + " exceeds the source sheet bounds.");
            }
            buildingSprites[i] = sheet.getSubimage(bounds[0], bounds[1], bounds[2], bounds[3]);
        }
    }

    private void loadPlayerSprites() {
        BufferedImage spriteSheet = AssetLoader.loadImage("spritesheet");
        if (spriteSheet == null) {
            throw new IllegalStateException("Could not load assets/spritesheet.png");
        }

        BufferedImage[][] spriteGrid = AssetLoader.extractSpriteGrid(
            AssetLoader.stripWhiteBackground(spriteSheet, 22), 3, 3
        );
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
        int[] legSwing = {-4, 0, 4, 0};
        int[] armSwing = {4, 0, -4, 0};
        int[] bob = {0, -1, 0, -1};

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
     * Configures all buildings, courts, trees, collisions, pins, and interactive landmarks
     * to match the exact layout of the Pickle Smash town & city map.
     */
    private void buildWorldData() {
        solidColliders.clear();
        depthSortedProps.clear();
        landmarks.clear();
        pinBanners.clear();

        // Outer world boundary walls
        solidColliders.add(new Rectangle2D.Double(-40, 0, 40, WORLD_HEIGHT));
        solidColliders.add(new Rectangle2D.Double(WORLD_WIDTH, 0, 40, WORLD_HEIGHT));
        solidColliders.add(new Rectangle2D.Double(0, -40, WORLD_WIDTH, 40));
        solidColliders.add(new Rectangle2D.Double(0, WORLD_HEIGHT, WORLD_WIDTH, 40));

        // Top-Left Ocean & Beach water barrier
        Polygon oceanPoly = new Polygon();
        oceanPoly.addPoint(0, 0);
        oceanPoly.addPoint(560, 0);
        oceanPoly.addPoint(0, 290);
        solidColliders.add(new Rectangle2D.Double(0, 0, 260, 175));

        // Top-Right Mountain Cliff barrier
        solidColliders.add(new Rectangle2D.Double(2080, 0, 1120, 155));

        // Bottom-Right Waterfall & River water barrier (walkable across Wooden Bridge at y=1505..1565)
        solidColliders.add(new Rectangle2D.Double(2950, 1280, 250, 220));
        solidColliders.add(new Rectangle2D.Double(2890, 1575, 310, 225));

        // --------------------------------------------------------------------
        // DISTRICT 1: TRAINING (Top-Left)
        // --------------------------------------------------------------------
        addSolidBox(405, 195, 550, 145); // Training Gym Building
        addCourtFenceWithSouthGate(375, 175, 610, 360, 680);
        depthSortedProps.add(new WorldProp(530, (g, t) -> drawBuildingSprite(g, CLUBHOUSE, 365, 170, 630, 360)));
        depthSortedProps.add(new WorldProp(340, (g, t) -> drawPalmTree(g, 155, 340, t)));

        pinBanners.add(new PinBanner("TRAINING", 680, 155));
        landmarks.add(new Landmark(
                "TRAINING CENTER & COURT",
                "TRAINING DISTRICT",
                "Welcome to the Pickle Smash Training Academy! Practice third-shot drops, kitchen dinks, and serve accuracy before entering Barangay matches.",
                680, 548, 95
        ));

        // --------------------------------------------------------------------
        // DISTRICT 2: BARANGAY LEVEL (Top-Center)
        // --------------------------------------------------------------------
        addSolidBox(1420, 195, 455, 165); // Barangay Hall
        addSolidBox(1155, 200, 175, 115); // Left Barangay House 1
        addSolidBox(1160, 385, 175, 115); // Left Barangay House 2
        addCourtFenceWithSouthGate(1390, 175, 585, 360, 1650);

        depthSortedProps.add(new WorldProp(325, (g, t) -> drawBuildingSprite(g, BLUE_HOUSE, 1125, 180, 235, 145)));
        depthSortedProps.add(new WorldProp(500, (g, t) -> drawBuildingSprite(g, BLUE_HOUSE, 1125, 355, 235, 145)));
        depthSortedProps.add(new WorldProp(410, (g, t) -> drawBuildingSprite(g, BARANGAY_HALL, 1430, 150, 455, 260)));
        depthSortedProps.add(new WorldProp(530, (g, t) -> drawPickleballCourt(g, 1485, 420, 330, 110, false)));
        depthSortedProps.add(new WorldProp(536, (g, t) -> drawPicketFenceEnclosure(g, 1390, 175, 585, 360, 1650)));

        pinBanners.add(new PinBanner("BARANGAY LEVEL", 1650, 148));
        landmarks.add(new Landmark(
                "BARANGAY HALL & COMMUNITY COURT",
                "BARANGAY LEVEL",
                "The heart of the neighborhood! Sign up here for weekend Barangay Fiesta Doubles tournaments and climb the local ladder.",
                1650, 548, 95
        ));

        // --------------------------------------------------------------------
        // TOP-RIGHT TOWNHOUSES & COTTAGES
        // --------------------------------------------------------------------
        addSolidBox(2075, 140, 150, 165);
        addSolidBox(2245, 215, 175, 165);
        addSolidBox(2435, 200, 175, 180);
        addSolidBox(2625, 255, 195, 150);
        addSolidBox(2885, 300, 225, 190);

        depthSortedProps.add(new WorldProp(280, (g, t) -> drawBuildingSprite(g, BLUE_HOUSE, 2050, 130, 195, 150)));
        depthSortedProps.add(new WorldProp(355, (g, t) -> drawBuildingSprite(g, BLUE_HOUSE, 2225, 205, 205, 150)));
        depthSortedProps.add(new WorldProp(365, (g, t) -> drawBuildingSprite(g, RED_HOUSE, 2415, 190, 210, 155)));
        depthSortedProps.add(new WorldProp(405, (g, t) -> drawBuildingSprite(g, GREEN_ROOF_SHOP, 2625, 245, 195, 160)));
        depthSortedProps.add(new WorldProp(485, (g, t) -> drawBuildingSprite(g, SARI_SARI_STORE, 2885, 300, 225, 190)));

        // --------------------------------------------------------------------
        // MIDDLE-LEFT: SARI-SARI STORE & RESIDENTIAL BLOCK
        // --------------------------------------------------------------------
        addSolidBox(245, 745, 240, 145); // Red Cottage
        addSolidBox(525, 745, 200, 158); // Sari-Sari Store
        addSolidBox(730, 665, 215, 230); // Blue 2-Story House

        depthSortedProps.add(new WorldProp(895, (g, t) -> drawBuildingSprite(g, RED_HOUSE, 245, 730, 240, 165)));
        depthSortedProps.add(new WorldProp(900, (g, t) -> drawBuildingSprite(g, RED_ROOF_SHOP, 525, 730, 200, 173)));
        depthSortedProps.add(new WorldProp(900, (g, t) -> drawBuildingSprite(g, BLUE_HOUSE, 705, 655, 260, 245)));

        landmarks.add(new Landmark(
                "SARI-SARI STORE",
                "NEIGHBORHOOD BLOCK",
                "Ice-cold calamansi juice, pandesal, and energy snacks! Local pickleball players hang out here between sets.",
                618, 935, 90
        ));

        // --------------------------------------------------------------------
        // CENTER: CENTRAL FOUNTAIN PLAZA
        // --------------------------------------------------------------------
        addSolidBox(1375, 835, 140, 115); // Central Fountain Basin
        depthSortedProps.add(new WorldProp(915, (g, t) -> drawPlazaFountain(g, 1445, 892, t)));
        depthSortedProps.add(new WorldProp(820, (g, t) -> drawParkBench(g, 1265, 805)));
        depthSortedProps.add(new WorldProp(820, (g, t) -> drawParkBench(g, 1580, 805)));
        depthSortedProps.add(new WorldProp(1005, (g, t) -> drawParkBench(g, 1280, 990)));
        depthSortedProps.add(new WorldProp(1005, (g, t) -> drawParkBench(g, 1565, 990)));

        landmarks.add(new Landmark(
                "CENTRAL TOWN FOUNTAIN",
                "TOWN SQUARE PLAZA",
                "The central crossroads connecting Training, Barangay, City, and Provincial districts. Toss a coin for good luck on match point!",
                1445, 985, 105
        ));

        // --------------------------------------------------------------------
        // DISTRICT 3: CITY LEVEL — DOMED PICKLEBALL ARENA & SKYSCRAPERS
        // --------------------------------------------------------------------
        addSolidBox(1800, 760, 260, 115);  // Left Glass Tower Block
        addSolidBox(2080, 665, 745, 315);  // Grand Domed Arena
        addSolidBox(2845, 595, 155, 330);  // Tall Blue Skyscraper
        addSolidBox(3005, 685, 150, 240);  // Right Glass Building

        depthSortedProps.add(new WorldProp(878, (g, t) -> drawBuildingSprite(g, OFFICE_BLOCK, 1780, 700, 300, 178)));
        depthSortedProps.add(new WorldProp(1070, (g, t) -> drawBuildingSprite(g, PICKLEBALL_ARENA, 2080, 610, 745, 465)));
        depthSortedProps.add(new WorldProp(930, (g, t) -> drawSkyscraper(g, 2845, 575, 155, 355, new Color(14, 165, 233), false)));
        depthSortedProps.add(new WorldProp(930, (g, t) -> drawSkyscraper(g, 3005, 665, 150, 265, new Color(59, 130, 246), true)));

        pinBanners.add(new PinBanner("CITY LEVEL", 2452, 595));
        landmarks.add(new Landmark(
                "METRO CITY PICKLEBALL ARENA",
                "CITY LEVEL",
                "The iconic glass-domed Pro Circuit Arena! Compete under bright stadium spotlights against top ranked urban champions.",
                2452, 1045, 120
        ));

        // --------------------------------------------------------------------
        // BOTTOM-LEFT: NEIGHBORHOOD, CHAPEL & NIPA FARMHOUSE
        // --------------------------------------------------------------------
        addSolidBox(40, 1055, 185, 155);
        addSolidBox(25, 1235, 175, 140);
        addSolidBox(285, 1235, 185, 140);
        addSolidBox(60, 1435, 185, 165);
        addSolidBox(265, 1435, 165, 165);
        addSolidBox(445, 1455, 110, 145);

        depthSortedProps.add(new WorldProp(1215, (g, t) -> drawBuildingSprite(g, BLUE_HOUSE, 20, 1035, 225, 170)));
        depthSortedProps.add(new WorldProp(1380, (g, t) -> drawBuildingSprite(g, RED_HOUSE, 0, 1220, 220, 165)));
        depthSortedProps.add(new WorldProp(1380, (g, t) -> drawBuildingSprite(g, BLUE_HOUSE, 260, 1220, 225, 165)));
        depthSortedProps.add(new WorldProp(1605, (g, t) -> drawBuildingSprite(g, RED_HOUSE, 0, 1415, 225, 180)));
        depthSortedProps.add(new WorldProp(1605, (g, t) -> drawBuildingSprite(g, BLUE_HOUSE, 235, 1415, 230, 180)));
        depthSortedProps.add(new WorldProp(1605, (g, t) -> drawBuildingSprite(g, GREEN_ROOF_SHOP, 445, 1445, 135, 125)));

        // Village Chapel / Church
        addSolidBox(740, 1295, 200, 235);
        depthSortedProps.add(new WorldProp(1465, (g, t) -> drawBuildingSprite(g, CHURCH, 700, 1235, 280, 230)));
        landmarks.add(new Landmark(
                "SAN ISIDRO VILLAGE CHAPEL",
                "SOUTH DISTRICT",
                "Historic white stone chapel with red terracotta roofing. Bell chimes ring out across the valley every morning.",
                840, 1565, 95
        ));

        // Traditional Bahay Kubo / Wooden Farmhouse & Crop Rows
        addSolidBox(1225, 1285, 215, 140);
        addSolidBox(1100, 1360, 120, 100);
        depthSortedProps.add(new WorldProp(1445, (g, t) -> drawBuildingSprite(g, RED_HOUSE, 1205, 1265, 245, 180)));
        depthSortedProps.add(new WorldProp(1460, (g, t) -> drawBuildingSprite(g, RED_ROOF_SHOP, 1085, 1335, 145, 125)));
        landmarks.add(new Landmark(
                "HERITAGE FARMSTEAD",
                "AGRICULTURAL GROVE",
                "Lush organic vegetable rows tended beside a classic Philippine wooden farmhouse.",
                1330, 1445, 90
        ));

        // --------------------------------------------------------------------
        // DISTRICT 4: PROVINCIAL LEVEL — COVERED COURT, WINDMILL & WATERFALL
        // --------------------------------------------------------------------
        addSolidBox(1640, 1310, 930, 135); // Covered Court Back Stands / Roof Pillars
        addCourtFenceWithSouthGate(1610, 1280, 1265, 365, 2105);

        depthSortedProps.add(new WorldProp(1450, (g, t) -> drawPickleballCourt(g, 1790, 1475, 630, 115, true)));
        depthSortedProps.add(new WorldProp(1455, (g, t) -> drawProvincialCoveredCourt(g, 1640, 1285, 930, 315)));
        depthSortedProps.add(new WorldProp(1465, (g, t) -> drawWaterTower(g, 2645, 1265)));
        depthSortedProps.add(new WorldProp(1475, (g, t) -> drawSpinningWindmill(g, 2790, 1300, t)));
        depthSortedProps.add(new WorldProp(1650, (g, t) -> drawPicketFenceEnclosure(g, 1610, 1280, 1265, 365, 2105)));

        pinBanners.add(new PinBanner("PROVINCIAL LEVEL", 2105, 1255));
        landmarks.add(new Landmark(
                "PROVINCIAL COVERED COURT STADIUM",
                "PROVINCIAL LEVEL",
                "The grandest regional tournament pavilion! Rain or shine, championship finals echo beneath this massive teal-green roof.",
                2105, 1665, 120
        ));
        landmarks.add(new Landmark(
                "CRYSTAL WATERFALL & WOODEN BRIDGE",
                "PROVINCIAL FALLS",
                "A refreshing mountain waterfall feeding the valley stream. Cross the arched wooden footbridge for scenic views!",
                2965, 1535, 105
        ));

        // --------------------------------------------------------------------
        // SCENIC TREES & STREETLIGHTS AROUND THE TOWN
        // --------------------------------------------------------------------
        int[][] treeCoords = {
                {85, 420}, {970, 880},
                {1225, 960}, {1345, 1030}, {1545, 1030}, {1670, 960}, {1955, 1020}, {2765, 1020},
                {975, 1265},
                {1655, 1605}, {1965, 1605}, {2255, 1605}, {2545, 1605}, {2845, 1615},
                {215, 1745}, {515, 1745}, {1945, 1745}, {2445, 1745}
        };
        for (int[] tc : treeCoords) {
            final int tx = tc[0], ty = tc[1];
            addSolidBox(tx - 18, ty - 14, 36, 22);
            depthSortedProps.add(new WorldProp(ty + 8, (g, t) -> drawLushTree(g, tx, ty, 1.0)));
        }
    }

    private void addSolidBox(double x, double y, double w, double h) {
        solidColliders.add(new Rectangle2D.Double(x, y, w, h));
    }

    private void drawBuildingSprite(Graphics2D g, int spriteIndex, int x, int y, int width, int height) {
        BufferedImage sprite = buildingSprites[spriteIndex];
        double scale = Math.min((double) width / sprite.getWidth(), (double) height / sprite.getHeight());
        int drawWidth = (int) Math.round(sprite.getWidth() * scale);
        int drawHeight = (int) Math.round(sprite.getHeight() * scale);
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + height - drawHeight;
        g.drawImage(sprite, drawX, drawY, drawWidth, drawHeight, null);
    }

    private void addCourtFenceWithSouthGate(double x, double y, double w, double h, double gateCenterX) {
        double thick = 14;
        double gateHalf = 55;
        solidColliders.add(new Rectangle2D.Double(x, y, w, thick)); // North
        solidColliders.add(new Rectangle2D.Double(x, y, thick, h)); // West
        solidColliders.add(new Rectangle2D.Double(x + w - thick, y, thick, h)); // East
        solidColliders.add(new Rectangle2D.Double(x, y + h - thick, (gateCenterX - gateHalf) - x, thick)); // South-Left
        solidColliders.add(new Rectangle2D.Double(gateCenterX + gateHalf, y + h - thick, (x + w) - (gateCenterX + gateHalf), thick)); // South-Right
    }

    // ------------------------------------------------------------------------
    // 6. HIGH-RESOLUTION STATIC WORLD LAYER BAKER (3200 x 1800)
    // ------------------------------------------------------------------------
    private void bakeStaticHDWorldLayer() {
        worldBaseCache = new BufferedImage(WORLD_WIDTH, WORLD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = worldBaseCache.createGraphics();
        enableHighQuality(g);

        // 1. Tile the supplied grass texture beneath the town's other terrain.
        for (int y = 0; y < WORLD_HEIGHT; y += grassTexture.getHeight()) {
            for (int x = 0; x < WORLD_WIDTH; x += grassTexture.getWidth()) {
                g.drawImage(grassTexture, x, y, null);
            }
        }

        // 2. Top-Left Coastal Ocean & Golden Beach
        g.setColor(new Color(245, 214, 146));
        Polygon sandBeach = new Polygon(new int[]{0, 760, 0}, new int[]{0, 0, 390}, 3);
        g.fillPolygon(sandBeach);
        g.setColor(new Color(224, 184, 112));
        g.setStroke(new BasicStroke(6f));
        g.drawLine(0, 390, 760, 0);

        GradientPaint seaGrad = new GradientPaint(0, 0, new Color(14, 165, 233), 500, 260, new Color(56, 189, 248));
        g.setPaint(seaGrad);
        Polygon seaPoly = new Polygon(new int[]{0, 620, 0}, new int[]{0, 0, 315}, 3);
        g.fillPolygon(seaPoly);

        // Wooden Pier on Beach
        drawWoodenPier(g, 285, 70);

        // 3. Top-Right Scenic Sky & Mountain Range
        GradientPaint skyGrad = new GradientPaint(2150, 0, new Color(56, 189, 248), 2150, 200, new Color(186, 230, 253));
        g.setPaint(skyGrad);
        g.fillRect(2160, 0, 1040, 210);
        drawMountainPeak(g, 2420, 210, 520, 185, new Color(34, 139, 94), new Color(74, 222, 128));
        drawMountainPeak(g, 2740, 210, 560, 165, new Color(22, 120, 82), new Color(52, 211, 153));
        drawMountainPeak(g, 3040, 210, 420, 145, new Color(20, 105, 74), new Color(45, 212, 191));

        // 4. Paved Asphalt Road Network with Tan Curbs & Sidewalks
        drawAsphaltRoadNetwork(g);

        // 5. Village Crop Garden Rows (Bottom-Left)
        drawCropGarden(g, 1065, 1445, 445, 145);

        // 6. Top Scenic Forest Canopy Border
        for (int fx = 780; fx <= 2250; fx += 95) {
            if (fx > 1050 && fx < 1980) continue; // Keep clear above Barangay
            drawLushTree(g, fx, 68, 1.15);
        }
        drawLushTree(g, 1340, 62, 1.1);
        drawLushTree(g, 1985, 65, 1.15);

        g.dispose();
    }

    private void drawAsphaltRoadNetwork(Graphics2D g) {
        // Road centerlines & paths
        List<int[]> roadSegments = new ArrayList<>();
        // [x1, y1, x2, y2]
        roadSegments.add(new int[]{120, 620, 3140, 620});   // Upper Main Avenue
        roadSegments.add(new int[]{120, 620, 120, 1030});    // Far-Left Loop
        roadSegments.add(new int[]{120, 1030, 1100, 1030});  // Mid-Left Horizontal
        roadSegments.add(new int[]{1100, 240, 1100, 1170});  // West-Central Vertical
        roadSegments.add(new int[]{1790, 620, 1790, 1170});  // East-Central Vertical
        roadSegments.add(new int[]{1100, 1170, 3080, 1170}); // Mid-Right Horizontal
        roadSegments.add(new int[]{610, 620, 610, 1670});    // South-West Vertical Avenue
        roadSegments.add(new int[]{610, 1670, 1570, 1670});  // Bottom Boulevard

        // 1. Warm tan sidewalk border
        g.setStroke(new BasicStroke(112f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(235, 206, 158));
        for (int[] s : roadSegments) g.drawLine(s[0], s[1], s[2], s[3]);

        // 2. Dark curb outline
        g.setStroke(new BasicStroke(92f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(51, 65, 85));
        for (int[] s : roadSegments) g.drawLine(s[0], s[1], s[2], s[3]);

        // 3. Smooth HD Asphalt Surface
        g.setStroke(new BasicStroke(84f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(82, 92, 106));
        for (int[] s : roadSegments) g.drawLine(s[0], s[1], s[2], s[3]);

        // 4. White Dashed Lane Dividers
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{24f, 20f}, 0f));
        g.setColor(new Color(241, 245, 249, 210));
        for (int[] s : roadSegments) g.drawLine(s[0], s[1], s[2], s[3]);

        // 5. Crisp Zebra Crosswalks at Intersections
        g.setStroke(new BasicStroke(1f));
        drawCrosswalk(g, 680, 584, true);
        drawCrosswalk(g, 1015, 584, false);
        drawCrosswalk(g, 1650, 584, true);
        drawCrosswalk(g, 2015, 455, false);
        drawCrosswalk(g, 85, 760, false);
        drawCrosswalk(g, 1064, 895, false);
        drawCrosswalk(g, 1754, 895, false);
        drawCrosswalk(g, 1445, 1134, true);
        drawCrosswalk(g, 2452, 1134, true);
        drawCrosswalk(g, 574, 1165, false);
        drawCrosswalk(g, 574, 1515, false);
        drawCrosswalk(g, 840, 1634, true);

        // City Arena Parking Bays
        g.setColor(new Color(82, 92, 106));
        g.fillRoundRect(1895, 985, 175, 95, 12, 12);
        g.fillRoundRect(2845, 945, 265, 135, 12, 12);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3f));
        for (int px = 1925; px <= 2040; px += 38) g.drawLine(px, 995, px, 1065);
        for (int px = 2880; px <= 3070; px += 46) g.drawLine(px, 960, px, 1065);
    }

    private void drawCrosswalk(Graphics2D g, int x, int y, boolean horizontalRoad) {
        g.setColor(new Color(248, 250, 252));
        if (horizontalRoad) {
            for (int i = 0; i < 6; i++) g.fillRect(x - 36 + i * 13, y, 7, 72);
        } else {
            for (int i = 0; i < 6; i++) g.fillRect(x, y - 36 + i * 13, 72, 7);
        }
    }

    private void drawWoodenPier(Graphics2D g, int x, int y) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(Math.toRadians(28));
        g.setColor(new Color(120, 53, 15));
        g.fillRoundRect(0, 0, 175, 58, 8, 8);
        g.setColor(new Color(180, 83, 9));
        for (int px = 6; px < 168; px += 14) {
            g.fillRect(px, 4, 11, 50);
        }
        g.setColor(new Color(69, 26, 3));
        for (int px = 10; px < 170; px += 48) {
            g.fillRect(px, -4, 8, 10);
            g.fillRect(px, 52, 8, 10);
        }
        g.setTransform(old);
    }

    private void drawMountainPeak(Graphics2D g, int cx, int baseY, int width, int height, Color shadowCol, Color sunlitCol) {
        Polygon leftHalf = new Polygon(new int[]{cx - width / 2, cx, cx}, new int[]{baseY, baseY - height, baseY}, 3);
        Polygon rightHalf = new Polygon(new int[]{cx, cx + width / 2, cx}, new int[]{baseY - height, baseY, baseY}, 3);
        g.setColor(shadowCol);
        g.fillPolygon(leftHalf);
        g.setColor(sunlitCol);
        g.fillPolygon(rightHalf);
        g.setColor(new Color(20, 83, 45));
        g.setStroke(new BasicStroke(4f));
        g.drawLine(cx - width / 2, baseY, cx, baseY - height);
        g.drawLine(cx, baseY - height, cx + width / 2, baseY);
    }

    private void drawCropGarden(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(146, 102, 58));
        g.fillRoundRect(x, y, w, h, 14, 14);
        g.setColor(new Color(101, 67, 33));
        g.setStroke(new BasicStroke(4f));
        g.drawRoundRect(x, y, w, h, 14, 14);

        for (int col = 0; col < 6; col++) {
            int rx = x + 22 + col * 68;
            g.setColor(new Color(92, 60, 30));
            g.fillRoundRect(rx, y + 14, 44, h - 28, 10, 10);
            for (int ry = y + 22; ry < y + h - 26; ry += 22) {
                g.setColor(new Color(34, 197, 94));
                g.fillOval(rx + 6, ry, 16, 16);
                g.fillOval(rx + 22, ry + 2, 16, 16);
                g.setColor(new Color(187, 247, 208));
                g.fillOval(rx + 10, ry + 3, 6, 6);
            }
        }
    }

    // ------------------------------------------------------------------------
    // 7. HD ARCHITECTURAL & NATURE RENDERERS
    // ------------------------------------------------------------------------
    private void drawPickleballCourt(Graphics2D g, int x, int y, int w, int h, boolean sheltered) {
        if (!sheltered) {
            g.setColor(new Color(226, 232, 240));
            g.fillRoundRect(x - 12, y - 10, w + 24, h + 20, 12, 12);
        }
        // Bright tournament blue surface
        g.setColor(new Color(29, 118, 226));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(37, 138, 245));
        g.fillRect(x + 14, y + 10, w - 28, h - 20);

        // Crisp white court lines
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3f));
        g.drawRect(x + 14, y + 10, w - 28, h - 20);

        int midX = x + w / 2;
        int midY = y + h / 2;
        int kitchenOffset = (int) (w * 0.16);
        g.drawLine(midX - kitchenOffset, y + 10, midX - kitchenOffset, y + h - 10);
        g.drawLine(midX + kitchenOffset, y + 10, midX + kitchenOffset, y + h - 10);
        g.drawLine(x + 14, midY, midX - kitchenOffset, midY);
        g.drawLine(midX + kitchenOffset, midY, x + w - 14, midY);

        // Center Net
        g.setColor(new Color(15, 23, 42));
        g.fillRect(midX - 3, y + 4, 6, h - 8);
        g.setColor(Color.WHITE);
        g.fillRect(midX - 1, y + 4, 3, h - 8);
    }

    private void drawPicketFenceEnclosure(Graphics2D g, int x, int y, int w, int h, int gateCenterX) {
        g.setColor(new Color(248, 250, 252));
        g.setStroke(new BasicStroke(3f));
        int gateHalf = 52;
        // Bottom fence left & right of entrance gate
        for (int fx = x; fx < gateCenterX - gateHalf; fx += 14) {
            g.fillRect(fx, y + h - 18, 7, 20);
        }
        for (int fx = gateCenterX + gateHalf; fx < x + w; fx += 14) {
            g.fillRect(fx, y + h - 18, 7, 20);
        }
        g.fillRect(x, y + h - 12, (gateCenterX - gateHalf) - x, 4);
        g.fillRect(gateCenterX + gateHalf, y + h - 12, (x + w) - (gateCenterX + gateHalf), 4);
    }

    private void drawTrainingGym(Graphics2D g, int x, int y, int w, int h) {
        // Ground Shadow
        g.setColor(new Color(15, 23, 42, 65));
        g.fillRoundRect(x + 10, y + h - 14, w - 16, 22, 12, 12);

        // Walls
        g.setColor(new Color(241, 245, 249));
        g.fillRect(x + 18, y + 75, w - 36, h - 85);
        g.setColor(new Color(148, 163, 184));
        g.fillRect(x + 18, y + h - 28, w - 36, 18);

        // Windows & Double Doors
        for (int wx : new int[]{x + 48, x + 125, x + w - 185, x + w - 108}) {
            g.setColor(new Color(30, 58, 138));
            g.fillRoundRect(wx, y + 98, 56, 36, 6, 6);
            g.setColor(new Color(56, 189, 248));
            g.fillRect(wx + 4, y + 102, 48, 28);
        }
        g.setColor(new Color(120, 53, 15));
        g.fillRect(x + w / 2 - 42, y + 95, 84, 70);
        g.setColor(new Color(180, 83, 9));
        g.fillRect(x + w / 2 - 36, y + 100, 34, 65);
        g.fillRect(x + w / 2 + 2, y + 100, 34, 65);

        // Iconic Blue Trapezoid Roof
        Polygon roof = new Polygon(
                new int[]{x, x + 55, x + w - 55, x + w},
                new int[]{y + 82, y + 10, y + 10, y + 82},
                4
        );
        GradientPaint roofGrad = new GradientPaint(x, y + 10, new Color(37, 99, 235), x, y + 82, new Color(29, 78, 216));
        g.setPaint(roofGrad);
        g.fillPolygon(roof);
        g.setColor(new Color(15, 23, 42));
        g.setStroke(new BasicStroke(3.5f));
        g.drawPolygon(roof);
    }

    private void drawBarangayHall(Graphics2D g, int x, int y, int w, int h, double time) {
        g.setColor(new Color(15, 23, 42, 65));
        g.fillRoundRect(x + 8, y + h - 14, w - 12, 22, 12, 12);

        // Cream & Terracotta Two-Story Facade
        g.setColor(new Color(254, 243, 199));
        g.fillRect(x + 18, y + 62, w - 36, h - 72);
        g.setColor(new Color(251, 146, 60));
        g.fillRect(x + 18, y + 118, w - 36, 8);

        // Red Terracotta Roof
        Polygon roof = new Polygon(
                new int[]{x, x + 45, x + w - 45, x + w},
                new int[]{y + 66, y + 8, y + 8, y + 66},
                4
        );
        g.setColor(new Color(220, 38, 38));
        g.fillPolygon(roof);
        g.setColor(new Color(127, 29, 29));
        g.setStroke(new BasicStroke(3.5f));
        g.drawPolygon(roof);

        // "BARANGAY" Signboard
        g.setColor(new Color(254, 249, 195));
        g.fillRoundRect(x + w / 2 - 82, y + 70, 164, 28, 6, 6);
        g.setColor(new Color(120, 53, 15));
        g.drawRoundRect(x + w / 2 - 82, y + 70, 164, 28, 6, 6);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        drawCenteredString(g, "BARANGAY", x + w / 2, y + 89);

        // Windows & Entrance Doors
        for (int wx : new int[]{x + 42, x + 98, x + w - 142, x + w - 86}) {
            g.setColor(new Color(56, 189, 248));
            g.fillRect(wx, y + 74, 42, 34);
            g.fillRect(wx, y + 132, 42, 36);
            g.setColor(new Color(120, 53, 15));
            g.drawRect(wx, y + 74, 42, 34);
            g.drawRect(wx, y + 132, 42, 36);
        }
        g.setColor(new Color(120, 53, 15));
        g.fillRect(x + w / 2 - 36, y + 124, 72, 56);

        // Flagpole & Waving Philippine Flag
        int poleX = x + w + 24;
        g.setColor(new Color(226, 232, 240));
        g.fillRect(poleX, y + 25, 6, 165);
        g.setColor(new Color(250, 204, 21));
        g.fillOval(poleX - 3, y + 18, 12, 12);

        int wave = (int) Math.round(Math.sin(time * 5.0) * 4);
        g.setColor(new Color(29, 78, 216));
        g.fillRect(poleX + 6, y + 30, 68, 19);
        g.setColor(new Color(220, 38, 38));
        g.fillRect(poleX + 6, y + 49 + wave / 2, 68, 19);
        Polygon whiteTri = new Polygon(
                new int[]{poleX + 6, poleX + 34, poleX + 6},
                new int[]{y + 30, y + 49, y + 68},
                3
        );
        g.setColor(Color.WHITE);
        g.fillPolygon(whiteTri);
        g.setColor(new Color(250, 204, 21));
        g.fillOval(poleX + 12, y + 45, 8, 8);
    }

    private void drawSariSariStore(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(254, 215, 170));
        g.fillRect(x + 12, y + 52, w - 24, h - 60);

        // Orange Roof
        Polygon roof = new Polygon(new int[]{x, x + 26, x + w - 26, x + w}, new int[]{y + 56, y + 12, y + 12, y + 56}, 4);
        g.setColor(new Color(234, 88, 12));
        g.fillPolygon(roof);
        g.setColor(new Color(124, 45, 18));
        g.setStroke(new BasicStroke(3f));
        g.drawPolygon(roof);

        // "SARI-SARI" Sign
        g.setColor(Color.WHITE);
        g.fillRoundRect(x + 22, y + 60, w - 44, 24, 6, 6);
        g.setColor(new Color(15, 23, 42));
        g.drawRoundRect(x + 22, y + 60, w - 44, 24, 6, 6);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        drawCenteredString(g, "SARI-SARI", x + w / 2, y + 77);

        // Red & White Striped Awning
        int stripeW = (w - 32) / 8;
        for (int i = 0; i < 8; i++) {
            g.setColor(i % 2 == 0 ? new Color(220, 38, 38) : Color.WHITE);
            g.fillRect(x + 16 + i * stripeW, y + 88, stripeW, 18);
        }

        // Open Storefront Counter & Colorful Jars/Snacks
        g.setColor(new Color(30, 41, 59));
        g.fillRect(x + 20, y + 106, w - 40, 38);
        Color[] snackCols = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.ORANGE, Color.MAGENTA};
        for (int i = 0; i < 9; i++) {
            g.setColor(snackCols[i % snackCols.length]);
            g.fillOval(x + 26 + i * 14, y + 112, 10, 12);
            g.fillRect(x + 26 + i * 14, y + 128, 10, 12);
        }
        g.setColor(new Color(180, 83, 9));
        g.fillRect(x + 16, y + 144, w - 32, 12);
    }

    private void drawCozyHouse(Graphics2D g, int x, int y, int w, int h, Color roofCol, Color wallCol) {
        g.setColor(new Color(15, 23, 42, 55));
        g.fillRoundRect(x + 8, y + h - 12, w - 14, 18, 10, 10);
        g.setColor(wallCol);
        g.fillRect(x + 14, y + 48, w - 28, h - 56);
        Polygon roof = new Polygon(new int[]{x, x + w / 2, x + w}, new int[]{y + 52, y + 8, y + 52}, 3);
        g.setColor(roofCol);
        g.fillPolygon(roof);
        g.setColor(new Color(15, 23, 42));
        g.setStroke(new BasicStroke(3f));
        g.drawPolygon(roof);
        g.drawRect(x + 14, y + 48, w - 28, h - 56);

        g.setColor(new Color(56, 189, 248));
        g.fillRect(x + 28, y + 66, 32, 30);
        g.fillRect(x + w - 60, y + 66, 32, 30);
        g.setColor(new Color(146, 64, 14));
        g.fillRect(x + w / 2 - 16, y + h - 54, 32, 46);
    }

    private void drawTownhouse(Graphics2D g, int x, int y, int w, int h, Color roofCol, Color wallCol) {
        g.setColor(wallCol);
        g.fillRect(x + 12, y + 45, w - 24, h - 52);
        Polygon roof = new Polygon(new int[]{x, x + 28, x + w - 28, x + w}, new int[]{y + 48, y + 8, y + 8, y + 48}, 4);
        g.setColor(roofCol);
        g.fillPolygon(roof);
        g.setColor(new Color(15, 23, 42));
        g.setStroke(new BasicStroke(3f));
        g.drawPolygon(roof);
        g.drawRect(x + 12, y + 45, w - 24, h - 52);

        for (int row = 0; row < 2; row++) {
            int wy = y + 64 + row * 52;
            for (int wx = x + 26; wx < x + w - 50; wx += 44) {
                g.setColor(new Color(224, 242, 254));
                g.fillRect(wx, wy, 30, 34);
                g.setColor(new Color(30, 58, 138));
                g.drawRect(wx, wy, 30, 34);
            }
        }
        g.setColor(new Color(120, 53, 15));
        g.fillRect(x + w / 2 - 18, y + h - 48, 36, 41);
    }

    private void drawPlazaFountain(Graphics2D g, int cx, int cy, double time) {
        // Paved Circular Plaza Floor
        g.setColor(new Color(253, 230, 138));
        g.fillOval(cx - 175, cy - 135, 350, 270);
        g.setColor(new Color(217, 119, 6));
        g.setStroke(new BasicStroke(4f));
        g.drawOval(cx - 175, cy - 135, 350, 270);

        // Stone Fountain Basin
        g.setColor(new Color(148, 163, 184));
        g.fillOval(cx - 72, cy - 52, 144, 104);
        g.setColor(new Color(14, 165, 233));
        g.fillOval(cx - 60, cy - 42, 120, 84);

        // Animated Ripples & Water Spout
        double pulse = (Math.sin(time * 4.5) + 1.0) * 0.5;
        g.setColor(new Color(224, 242, 254, 180));
        g.setStroke(new BasicStroke(2.5f));
        int rw = (int) (46 + pulse * 44);
        int rh = (int) (30 + pulse * 30);
        g.drawOval(cx - rw / 2, cy - rh / 2, rw, rh);

        g.setColor(new Color(203, 213, 225));
        g.fillRect(cx - 14, cy - 26, 28, 32);
        g.setColor(new Color(125, 211, 252));
        g.fillOval(cx - 24, cy - 46 - (int) (pulse * 6), 48, 28);
        g.setColor(Color.WHITE);
        g.fillOval(cx - 8, cy - 58 - (int) (pulse * 8), 16, 26);
    }

    private void drawParkBench(Graphics2D g, int x, int y) {
        g.setColor(new Color(146, 64, 14));
        g.fillRoundRect(x, y, 64, 22, 6, 6);
        g.setColor(new Color(217, 119, 6));
        g.fillRect(x + 4, y + 3, 56, 7);
        g.fillRect(x + 4, y + 12, 56, 6);
        g.setColor(new Color(30, 41, 59));
        g.fillRect(x + 6, y + 18, 6, 10);
        g.fillRect(x + 52, y + 18, 6, 10);
    }

    private void drawCityPickleballArena(Graphics2D g, int x, int y, int w, int h) {
        // Plaza apron
        g.setColor(new Color(226, 232, 240));
        g.fillRoundRect(x - 16, y + 25, w + 32, h - 10, 22, 22);

        // Grand Silver Arch Dome
        Arc2D.Double dome = new Arc2D.Double(x, y, w, 240, 0, 180, Arc2D.PIE);
        GradientPaint domeGrad = new GradientPaint(x, y, new Color(248, 250, 252), x, y + 120, new Color(148, 163, 184));
        g.setPaint(domeGrad);
        g.fill(dome);
        g.setColor(new Color(30, 41, 59));
        g.setStroke(new BasicStroke(4f));
        g.draw(dome);

        // Blue Glass Curtain Wall Facade
        g.setColor(new Color(29, 78, 216));
        g.fillRect(x + 28, y + 115, w - 56, h - 140);
        g.setColor(new Color(56, 189, 248));
        for (int gx = x + 44; gx < x + w - 70; gx += 58) {
            g.fillRect(gx, y + 128, 46, h - 168);
        }

        // Giant Center Emblem: Crossed Blue Pickleball Paddles & Yellow Ball
        int cx = x + w / 2;
        int cy = y + 195;
        g.setColor(new Color(15, 23, 42, 110));
        g.fillOval(cx - 105, cy - 85, 210, 170);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(cx, cy);
        // Left tilted paddle
        g2.rotate(Math.toRadians(-35));
        g2.setColor(new Color(30, 58, 138));
        g2.fillRoundRect(-38, -75, 76, 98, 28, 28);
        g2.setColor(new Color(15, 23, 42));
        g2.fillRect(-10, 22, 20, 48);
        g2.rotate(Math.toRadians(70));
        // Right tilted paddle
        g2.setColor(new Color(37, 99, 235));
        g2.fillRoundRect(-38, -75, 76, 98, 28, 28);
        g2.setColor(new Color(15, 23, 42));
        g2.fillRect(-10, 22, 20, 48);
        g2.dispose();

        // Bright Yellow Perforated Pickleball in center
        g.setColor(new Color(250, 204, 21));
        g.fillOval(cx - 32, cy - 58, 64, 64);
        g.setColor(new Color(15, 23, 42));
        g.setStroke(new BasicStroke(3f));
        g.drawOval(cx - 32, cy - 58, 64, 64);
        for (int hx = -14; hx <= 14; hx += 14) {
            for (int hy = -40; hy <= -12; hy += 14) {
                g.fillOval(cx + hx - 3, cy + hy - 3, 6, 6);
            }
        }

        // Grand Arena Entrance Steps
        g.setColor(new Color(203, 213, 225));
        g.fillRect(cx - 95, y + h - 32, 190, 32);
        g.setColor(new Color(100, 116, 139));
        g.drawLine(cx - 95, y + h - 22, cx + 95, y + h - 22);
        g.drawLine(cx - 95, y + h - 11, cx + 95, y + h - 11);
    }

    private void drawSkyscraper(Graphics2D g, int x, int y, int w, int h, Color glassCol, boolean hasBanner) {
        g.setColor(new Color(30, 58, 138));
        g.fillRect(x, y, w, h);
        g.setColor(glassCol);
        g.fillRect(x + 8, y + 10, w - 16, h - 18);

        for (int wy = y + 22; wy < y + h - 28; wy += 32) {
            for (int wx = x + 16; wx < x + w - 26; wx += 30) {
                g.setColor(new Color(186, 230, 253));
                g.fillRect(wx, wy, 20, 22);
                g.setColor(new Color(30, 64, 175));
                g.drawRect(wx, wy, 20, 22);
            }
        }
        if (hasBanner) {
            g.setColor(new Color(59, 130, 246));
            g.fillRect(x + 24, y + 65, w - 48, 120);
            g.setColor(new Color(250, 204, 21));
            g.fillOval(x + w / 2 - 14, y + 95, 28, 28);
        }
    }

    private void drawVillageChurch(Graphics2D g, int x, int y, int w, int h) {
        // Main White Chapel Body
        g.setColor(new Color(248, 250, 252));
        g.fillRect(x + 16, y + 135, w - 32, h - 145);
        Polygon mainRoof = new Polygon(new int[]{x, x + w / 2, x + w}, new int[]{y + 140, y + 85, y + 140}, 3);
        g.setColor(new Color(220, 38, 38));
        g.fillPolygon(mainRoof);

        // Central Bell Steeple & Cross
        int sx = x + w / 2 - 36;
        g.setColor(Color.WHITE);
        g.fillRect(sx, y + 70, 72, 130);
        Polygon steepleRoof = new Polygon(new int[]{sx - 8, x + w / 2, sx + 80}, new int[]{y + 72, y + 24, y + 72}, 3);
        g.setColor(new Color(220, 38, 38));
        g.fillPolygon(steepleRoof);

        // White Cross on Top
        g.setColor(Color.WHITE);
        g.fillRect(x + w / 2 - 4, y, 8, 28);
        g.fillRect(x + w / 2 - 12, y + 8, 24, 7);

        // Arched Stained Glass & Wooden Doors
        g.setColor(new Color(56, 189, 248));
        g.fillArc(x + w / 2 - 16, y + 92, 32, 38, 0, 180);
        g.fillRect(x + w / 2 - 16, y + 111, 32, 22);

        g.setColor(new Color(120, 53, 15));
        g.fillArc(x + w / 2 - 22, y + h - 85, 44, 44, 0, 180);
        g.fillRect(x + w / 2 - 22, y + h - 63, 44, 53);
    }

    private void drawWoodenFarmhouse(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(180, 83, 9));
        g.fillRect(x + 18, y + 65, w - 36, h - 75);
        Polygon roof = new Polygon(new int[]{x, x + 36, x + w - 36, x + w}, new int[]{y + 70, y + 12, y + 12, y + 70}, 4);
        g.setColor(new Color(217, 119, 6));
        g.fillPolygon(roof);
        g.setColor(new Color(120, 53, 15));
        g.setStroke(new BasicStroke(3f));
        g.drawPolygon(roof);
        g.fillRect(x + w / 2 - 20, y + h - 65, 40, 55);
        g.setColor(new Color(254, 243, 199));
        g.fillRect(x + 36, y + 88, 36, 32);
        g.fillRect(x + w - 72, y + 88, 36, 32);
    }

    private void drawProvincialCoveredCourt(Graphics2D g, int x, int y, int w, int h) {
        // Concrete Pavilion Floor
        g.setColor(new Color(226, 232, 240));
        g.fillRoundRect(x + 25, y + 125, w - 50, h - 125, 16, 16);

        // Support Columns
        g.setColor(new Color(241, 245, 249));
        for (int px : new int[]{x + 48, x + 235, x + 465, x + 695, x + w - 68}) {
            g.fillRect(px, y + 115, 22, 160);
            g.setColor(new Color(100, 116, 139));
            g.drawRect(px, y + 115, 22, 160);
            g.setColor(new Color(241, 245, 249));
        }

        // Grand Teal-Green Covered Roof
        Polygon roof = new Polygon(
                new int[]{x, x + 165, x + w - 165, x + w},
                new int[]{y + 125, y + 14, y + 14, y + 125},
                4
        );
        GradientPaint roofGrad = new GradientPaint(x, y + 14, new Color(16, 185, 129), x, y + 125, new Color(5, 150, 105));
        g.setPaint(roofGrad);
        g.fillPolygon(roof);
        g.setColor(new Color(6, 78, 59));
        g.setStroke(new BasicStroke(4f));
        g.drawPolygon(roof);
        g.setColor(new Color(167, 243, 208));
        g.fillRect(x + 165, y + 10, w - 330, 10);
    }

    private void drawWaterTower(Graphics2D g, int x, int y) {
        g.setColor(new Color(120, 53, 15));
        g.setStroke(new BasicStroke(5f));
        g.drawLine(x + 16, y + 75, x + 4, y + 195);
        g.drawLine(x + 64, y + 75, x + 76, y + 195);
        g.drawLine(x + 10, y + 135, x + 70, y + 135);
        g.setColor(new Color(226, 232, 240));
        g.fillRoundRect(x + 8, y + 25, 64, 56, 12, 12);
        Polygon cap = new Polygon(new int[]{x + 4, x + 40, x + 76}, new int[]{y + 28, y, y + 28}, 3);
        g.setColor(new Color(100, 116, 139));
        g.fillPolygon(cap);
    }

    private void drawSpinningWindmill(Graphics2D g, int cx, int cy, double time) {
        g.setColor(new Color(146, 64, 14));
        g.setStroke(new BasicStroke(6f));
        g.drawLine(cx - 12, cy + 45, cx - 26, cy + 165);
        g.drawLine(cx + 12, cy + 45, cx + 26, cy + 165);
        g.drawLine(cx - 20, cy + 105, cx + 20, cy + 105);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(cx, cy + 40);
        g2.rotate(time * 1.4);
        g2.setColor(new Color(248, 250, 252));
        for (int i = 0; i < 6; i++) {
            g2.rotate(Math.PI / 3.0);
            g2.fillRoundRect(-5, -52, 10, 48, 4, 4);
            g2.setColor(new Color(220, 38, 38));
            g2.fillRect(-4, -48, 8, 12);
            g2.setColor(new Color(248, 250, 252));
        }
        g2.setColor(new Color(220, 38, 38));
        g2.fillOval(-10, -10, 20, 20);
        g2.dispose();
    }

    private void drawLushTree(Graphics2D g, int cx, int baseY, double scale) {
        int s = (int) (46 * scale);
        g.setColor(new Color(15, 23, 42, 60));
        g.fillOval(cx - s, baseY - 10, s * 2, 20);

        g.setColor(new Color(120, 53, 15));
        g.fillRect(cx - (int) (9 * scale), baseY - (int) (34 * scale), (int) (18 * scale), (int) (34 * scale));

        g.setColor(new Color(21, 128, 61));
        g.fillOval(cx - s, baseY - (int) (88 * scale), s * 2, (int) (68 * scale));
        g.setColor(new Color(34, 197, 94));
        g.fillOval(cx - (int) (s * 0.85), baseY - (int) (96 * scale), (int) (s * 1.7), (int) (62 * scale));
        g.setColor(new Color(134, 239, 172));
        g.fillOval(cx - (int) (s * 0.45), baseY - (int) (88 * scale), (int) (s * 0.75), (int) (32 * scale));
    }

    private void drawPalmTree(Graphics2D g, int cx, int baseY, double time) {
        g.setColor(new Color(120, 53, 15));
        g.fillRect(cx - 10, baseY - 125, 20, 125);
        int sway = (int) (Math.sin(time * 2.2) * 5);
        g.setColor(new Color(22, 163, 74));
        g.fillArc(cx - 75 + sway, baseY - 155, 150, 55, 0, 180);
        g.fillArc(cx - 65 + sway, baseY - 170, 130, 50, 0, 180);
        g.setColor(new Color(74, 222, 128));
        g.fillArc(cx - 45 + sway, baseY - 178, 90, 40, 0, 180);
    }

    // ------------------------------------------------------------------------
    // 8. ANIMATED WATERFALL, RIVER, WOODEN BRIDGE & WAVES
    // ------------------------------------------------------------------------
    private void drawAnimatedWaterFeatures(Graphics2D g, double time) {
        // 1. Ocean Wave Foam (Top-Left)
        int waveOffset = (int) (Math.sin(time * 2.6) * 8);
        g.setColor(new Color(255, 255, 255, 210));
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(0, 315 + waveOffset, 620 + waveOffset, 0);

        // 2. Waterfall & River (Bottom-Right)
        // Rocky Cliff
        g.setColor(new Color(100, 116, 139));
        g.fillRoundRect(2985, 1275, 215, 235, 28, 28);
        g.setColor(new Color(71, 85, 105));
        g.fillRoundRect(3005, 1310, 195, 195, 20, 20);

        // Cascading Waterfall Stream
        GradientPaint fallGrad = new GradientPaint(3060, 1315, new Color(56, 189, 248), 3060, 1500, new Color(224, 242, 254));
        g.setPaint(fallGrad);
        g.fillRect(3045, 1315, 95, 190);

        g.setColor(Color.WHITE);
        int fallShift = (int) ((time * 160) % 28);
        for (int fy = 1320 + fallShift; fy < 1495; fy += 28) {
            g.fillRect(3058, fy, 10, 14);
            g.fillRect(3088, fy + 6, 12, 14);
            g.fillRect(3118, fy, 8, 14);
        }

        // Winding Blue River
        Polygon river = new Polygon(
                new int[]{3015, 3200, 3200, 2835},
                new int[]{1485, 1485, 1800, 1800},
                4
        );
        g.setColor(new Color(14, 165, 233));
        g.fillPolygon(river);

        // Splash Foam at Waterfall Base
        int splashR = 16 + (int) (Math.sin(time * 8.0) * 4);
        g.setColor(Color.WHITE);
        g.fillOval(3035, 1485, 115, splashR + 12);

        // 3. Arched Wooden Footbridge spanning the river
        int bx = 2905, by = 1500, bw = 215, bh = 62;
        g.setColor(new Color(120, 53, 15));
        g.fillRoundRect(bx, by, bw, bh, 14, 14);
        g.setColor(new Color(180, 83, 9));
        for (int px = bx + 10; px < bx + bw - 14; px += 16) {
            g.fillRect(px, by + 6, 12, bh - 12);
        }
        g.setColor(new Color(69, 26, 3));
        g.setStroke(new BasicStroke(5f));
        g.drawRoundRect(bx, by, bw, bh, 14, 14);
    }

    // ------------------------------------------------------------------------
    // 9. WALKABLE CHARACTER RENDERER (HOME SCREEN SPRITES)
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
            spriteHeight * (double) sprite.getWidth() / sprite.getHeight()
        ));

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
    // 10. BLUE LOCATION PIN BANNERS (MATCHING REFERENCE IMAGE)
    // ------------------------------------------------------------------------
    private void drawPinBanner(Graphics2D g, PinBanner pin) {
        g.setFont(new Font("SansSerif", Font.BOLD, 24));
        FontMetrics fm = g.getFontMetrics();
        int textW = fm.stringWidth(pin.label);
        int boxW = textW + 78;
        int boxH = 52;
        int bx = (int) (pin.x - boxW / 2.0);
        int by = (int) (pin.y - boxH / 2.0);

        // Drop shadow
        g.setColor(new Color(15, 23, 42, 140));
        g.fillRoundRect(bx + 4, by + 6, boxW, boxH, 16, 16);

        // Blue gradient badge
        GradientPaint badgeGrad = new GradientPaint(bx, by, new Color(37, 99, 235), bx, by + boxH, new Color(29, 78, 216));
        g.setPaint(badgeGrad);
        g.fillRoundRect(bx, by, boxW, boxH, 16, 16);

        g.setColor(new Color(15, 23, 42));
        g.setStroke(new BasicStroke(3.5f));
        g.drawRoundRect(bx, by, boxW, boxH, 16, 16);
        g.setColor(new Color(147, 197, 253));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(bx + 3, by + 3, boxW - 6, boxH - 6, 12, 12);

        // Yellow Teardrop Map Pin Icon on the left
        int px = bx + 28;
        int py = by + 23;
        g.setColor(new Color(250, 204, 21));
        g.fillOval(px - 12, py - 15, 24, 24);
        Polygon pinTip = new Polygon(new int[]{px - 10, px + 10, px}, new int[]{py + 1, py + 1, py + 18}, 3);
        g.fillPolygon(pinTip);
        g.setColor(new Color(15, 23, 42));
        g.setStroke(new BasicStroke(2.5f));
        g.drawOval(px - 12, py - 15, 24, 24);
        g.fillOval(px - 4, py - 7, 8, 8);

        // Crisp White Label Text
        g.setColor(new Color(15, 23, 42));
        g.drawString(pin.label, bx + 54, by + 36);
        g.setColor(Color.WHITE);
        g.drawString(pin.label, bx + 52, by + 34);
    }

    // ------------------------------------------------------------------------
    // 11. GAME LOOP, MOVEMENT, COLLISION & CAMERA FOLLOW UPDATE
    // ------------------------------------------------------------------------
    public void start() {
        if (running) return;
        running = true;
        gameThread = new Thread(this, "PickleSmashHDMapLoop");
        gameThread.start();
    }

    public void stop() {
        running = false;
        if (gameThread != null) gameThread.interrupt();
    }

    public void setExitAction(Runnable exitAction) {
        this.exitAction = exitAction;
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            double dt = Math.min(0.05, (now - lastTime) / 1_000_000_000.0);
            lastTime = now;

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
            if (toastTimer <= 0) toastMessage = null;
        }

        // 1. Character Movement (paused while reading landmark dialogue)
        double mx = 0, my = 0;
        if (activeDialogue == null) {
            if (keyUp) my -= 1;
            if (keyDown) my += 1;
            if (keyLeft) mx -= 1;
            if (keyRight) mx += 1;
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

    private void moveWithCollision(double dx, double dy) {
        if (dx != 0) {
            if (!checkCollision(playerX + dx, playerY)) {
                playerX += dx;
            } else {
                // Gentle corner sliding
                for (double nudge = 4; nudge <= 14; nudge += 4) {
                    if (!checkCollision(playerX + dx, playerY - nudge)) { playerY -= 2; playerX += dx * 0.7; break; }
                    if (!checkCollision(playerX + dx, playerY + nudge)) { playerY += 2; playerX += dx * 0.7; break; }
                }
            }
        }
        if (dy != 0) {
            if (!checkCollision(playerX, playerY + dy)) {
                playerY += dy;
            } else {
                for (double nudge = 4; nudge <= 14; nudge += 4) {
                    if (!checkCollision(playerX - nudge, playerY + dy)) { playerX -= 2; playerY += dy * 0.7; break; }
                    if (!checkCollision(playerX + nudge, playerY + dy)) { playerX += 2; playerY += dy * 0.7; break; }
                }
            }
        }
    }

    private boolean checkCollision(double px, double py) {
        Rectangle2D.Double footBox = new Rectangle2D.Double(px - 12, py - 10, 24, 14);
        for (Rectangle2D.Double box : solidColliders) {
            if (footBox.intersects(box)) return true;
        }
        return false;
    }

    private String getCurrentDistrictName() {
        if (playerX < 1060 && playerY < 620) return "TRAINING DISTRICT";
        if (playerX >= 1060 && playerX < 2040 && playerY < 620) return "BARANGAY LEVEL";
        if (playerX >= 1840 && playerY >= 560 && playerY < 1180) return "CITY LEVEL";
        if (playerX >= 1580 && playerY >= 1180) return "PROVINCIAL LEVEL";
        if (playerX >= 1140 && playerX < 1760 && playerY >= 660 && playerY < 1140) return "CENTRAL FOUNTAIN PLAZA";
        return "TOWN NEIGHBORHOOD";
    }

    // ------------------------------------------------------------------------
    // 12. RENDER PIPELINE (HD WORLD + CAMERA + HUD + MINIMAP)
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
        worldG.translate(vw * 0.5, vh * 0.5);
        worldG.scale(camZoom, camZoom);
        worldG.translate(-camX, -camY);

        renderCompleteWorldScene(worldG, true);
        worldG.dispose();

        // 2. Render Crisp Screen-Space HUD Overlay
        drawScreenHUD(g, vw, vh);
    }

    private void renderCompleteWorldScene(Graphics2D g, boolean includePlayer) {
        // Draw pre-baked 3200x1800 static terrain/roads layer
        g.drawImage(worldBaseCache, 0, 0, null);

        // Draw animated waterfall, river, bridge & ocean waves
        drawAnimatedWaterFeatures(g, animTime);

        // Depth-sort all buildings, courts, trees & character by Y coordinate
        List<WorldProp> frameList = new ArrayList<>(depthSortedProps);
        if (includePlayer) {
            frameList.add(new WorldProp(playerY, (pg, t) -> drawWalkableCharacter(pg, playerX, playerY)));
        }
        frameList.sort(Comparator.comparingDouble(p -> p.sortY));

        for (WorldProp prop : frameList) {
            prop.renderer.draw(g, animTime);
        }

        // Draw floating blue district pin banners
        for (PinBanner pin : pinBanners) {
            drawPinBanner(g, pin);
        }
    }

    private void drawScreenHUD(Graphics2D g, int vw, int vh) {
        // Top-Left District & Zoom Status Card
        g.setColor(new Color(15, 23, 42, 220));
        g.fillRoundRect(16, 16, 330, 86, 14, 14);
        g.setColor(new Color(56, 189, 248));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(16, 16, 330, 86, 14, 14);

        g.setColor(new Color(250, 204, 21));
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.drawString("PICKLE SMASH — HD 2D WORLD", 30, 38);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString(getCurrentDistrictName(), 30, 62);

        g.setColor(new Color(148, 163, 184));
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        g.drawString(String.format("POS: (%d, %d)  |  ZOOM: %.2fx", (int) playerX, (int) playerY, camZoom), 30, 85);

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

        // Bottom Controls Bar
        String controlsText = "WASD/Arrows: Walk  |  Shift: Jog  |  Scroll or +/-: Zoom  |  M: Full Map  |  1-4: Warp  |  F12: Save HD PNG";
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        int barW = fm.stringWidth(controlsText) + 32;
        g.setColor(new Color(15, 23, 42, 210));
        g.fillRoundRect((vw - barW) / 2, vh - 40, barW, 28, 10, 10);
        g.setColor(new Color(203, 213, 225));
        drawCenteredString(g, controlsText, vw / 2, vh - 21);

        // Nearby Landmark Prompt
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
            renderCompleteWorldScene(g, false);
            g.dispose();

            File out = new File("PickleSmash_HD_Map.png");
            ImageIO.write(fullHD, "PNG", out);
            toastMessage = "SAVED FULL HD 3200x1800 MAP TO: " + out.getName();
            toastTimer = 3.5;
        } catch (Exception ex) {
            toastMessage = "EXPORT FAILED: " + ex.getMessage();
            toastTimer = 3.5;
        }
    }

    // ------------------------------------------------------------------------
    // 13. GRAPHICS & TEXT HELPERS
    // ------------------------------------------------------------------------
    private static void enableHighQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
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
    // 14. KEYBOARD & MOUSE INPUT HANDLERS
    // ------------------------------------------------------------------------
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) keyUp = true;
        if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) keyDown = true;
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) keyLeft = true;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) keyRight = true;
        if (code == KeyEvent.VK_SHIFT) keyShift = true;

        if (code == KeyEvent.VK_EQUALS || code == KeyEvent.VK_ADD) {
            fullMapOverview = false;
            targetZoom = Math.min(3.6, targetZoom + 0.25);
            savedZoom = targetZoom;
        } else if (code == KeyEvent.VK_MINUS || code == KeyEvent.VK_SUBTRACT) {
            fullMapOverview = false;
            targetZoom = Math.max(0.45, targetZoom - 0.25);
            savedZoom = targetZoom;
        } else if (code == KeyEvent.VK_M) {
            fullMapOverview = !fullMapOverview;
            if (fullMapOverview) {
                savedZoom = targetZoom;
                targetZoom = Math.max(0.4, Math.min((double) getWidth() / WORLD_WIDTH, (double) getHeight() / WORLD_HEIGHT));
            } else {
                targetZoom = Math.max(1.4, savedZoom);
            }
        } else if (code == KeyEvent.VK_E || code == KeyEvent.VK_SPACE) {
            if (activeDialogue != null) {
                activeDialogue = null;
            } else if (nearbyLandmark != null) {
                activeDialogue = nearbyLandmark;
            }
        } else if (code == KeyEvent.VK_ESCAPE) {
            if (activeDialogue != null) {
                activeDialogue = null;
            } else if (exitAction != null) {
                exitAction.run();
            }
        } else if (code == KeyEvent.VK_1) {
            warpPlayer(680, 615, "TRAINING DISTRICT");
        } else if (code == KeyEvent.VK_2) {
            warpPlayer(1650, 615, "BARANGAY LEVEL");
        } else if (code == KeyEvent.VK_3) {
            warpPlayer(2452, 1165, "CITY LEVEL");
        } else if (code == KeyEvent.VK_4) {
            warpPlayer(2105, 1675, "PROVINCIAL LEVEL");
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
        if (code == KeyEvent.VK_W || code == KeyEvent.VK_UP) keyUp = false;
        if (code == KeyEvent.VK_S || code == KeyEvent.VK_DOWN) keyDown = false;
        if (code == KeyEvent.VK_A || code == KeyEvent.VK_LEFT) keyLeft = false;
        if (code == KeyEvent.VK_D || code == KeyEvent.VK_RIGHT) keyRight = false;
        if (code == KeyEvent.VK_SHIFT) keyShift = false;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        fullMapOverview = false;
        double delta = (e.getWheelRotation() < 0) ? 0.22 : -0.22;
        targetZoom = Math.max(0.45, Math.min(3.6, targetZoom + delta));
        savedZoom = targetZoom;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
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

    @Override public void mouseMoved(MouseEvent e) {}
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    // ------------------------------------------------------------------------
    // 15. MAIN ENTRY POINT
    // ------------------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Pickle Smash — HD 2D City & Town Map");
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