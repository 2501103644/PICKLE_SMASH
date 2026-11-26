import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class PickleBallHomeScreen extends JFrame {

    // Virtual 16:9 pixel canvas scaled up 4x for razor-sharp retro pixels
    public static final int VIRTUAL_WIDTH = 320;
    public static final int VIRTUAL_HEIGHT = 180;
    public static final int SCALE = 4;
    private final GameCanvas canvas;

    public PickleBallHomeScreen() {
        setTitle("PICKLE SMASH - THE ULTIMATE PADDLE CHALLENGE");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        canvas = new GameCanvas();
        setContentPane(canvas);
        pack();
        setLocationRelativeTo(null);
    }

    private void showCityMap(PickleSmashCityMap cityMap) {
        setTitle("Pickle Smash — City & Town Map");
        setContentPane(cityMap);
        pack();
        setLocationRelativeTo(null);
        validate();
        cityMap.start();
        SwingUtilities.invokeLater(cityMap::requestFocusInWindow);
    }

    private void showHomeScreen() {
        setTitle("PICKLE SMASH - THE ULTIMATE PADDLE CHALLENGE");
        setContentPane(canvas);
        pack();
        setLocationRelativeTo(null);
        validate();
        SwingUtilities.invokeLater(canvas::requestFocusInWindow);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PickleBallHomeScreen game = new PickleBallHomeScreen();
            game.setVisible(true);
        });
    }

    // =========================================================================
    // 1. ENCAPSULATED RETRO PALETTE, STATES & AVATAR PRESETS
    // =========================================================================
    static final class Palette {
        static final Color SKY_TOP = new Color(24, 48, 89);
        static final Color SKY_MID = new Color(41, 84, 145);
        static final Color SKY_HORIZON = new Color(86, 152, 204);
        static final Color COURT_OUTER = new Color(36, 102, 74);
        static final Color COURT_INNER = new Color(56, 142, 98);
        static final Color COURT_LINE = new Color(241, 245, 249);
        static final Color COURT_SHADOW = new Color(18, 56, 40, 165);
        static final Color NET_DARK = new Color(24, 34, 52);

        // Beveled 16-bit button colors
        static final Color BTN_OUTLINE = new Color(15, 23, 42);
        static final Color BTN_ACTIVE_BG = new Color(250, 204, 21);
        static final Color BTN_ACTIVE_HI = new Color(254, 249, 195);
        static final Color BTN_ACTIVE_SH = new Color(202, 138, 4);
        static final Color BTN_IDLE_BG = new Color(22, 33, 56);
        static final Color BTN_IDLE_HI = new Color(71, 85, 105);
        static final Color BTN_IDLE_SH = new Color(12, 19, 34);
        static final Color GOLD_TRIM = new Color(234, 179, 8);

        static final Color BALL_YELLOW = new Color(250, 204, 21);
        static final Color BALL_LIGHT = new Color(254, 240, 138);
        static final Color BALL_SHADOW = new Color(202, 138, 4);
        static final Color BALL_HOLE = new Color(113, 63, 18);

        static final Color MODAL_BG = new Color(15, 23, 42, 240);
        static final Color TEXT_WHITE = new Color(248, 250, 252);
        static final Color TEXT_MUTED = new Color(148, 163, 184);
        static final Color RIVAL_CYAN = new Color(14, 165, 233);
    }

    enum GameState {
        INTRO,
        GENDER,
        HOME,
        PLAYING,
        MAP,
        AVATAR,
        CREDITS
    }

    enum Gender {
        MALE("MALE"),
        FEMALE("FEMALE");

        final String label;

        Gender(String label) {
            this.label = label;
        }
    }

    enum MenuAction {
        PLAY("PLAY"),
        AVATAR("AVATAR"),
        CREDITS("CREDITS"),
        EXIT("EXIT");

        final String label;

        MenuAction(String label) {
            this.label = label;
        }
    }

    enum AvatarPreset {
        PADDLE_PRO("PADDLE PRO", "BALANCED SPEED AND CONTROL", new Color(220, 38, 38)),
        SPIN_MASTER("SPIN MASTER", "HIGH TOPSPIN AND SLICE", new Color(14, 165, 233)),
        SMASH_KING("SMASH KING", "HEAVY NET POWER SHOTS", new Color(168, 85, 247));

        final String name;
        final String trait;
        final Color jerseyColor;

        AvatarPreset(String name, String trait, Color jerseyColor) {
            this.name = name;
            this.trait = trait;
            this.jerseyColor = jerseyColor;
        }
    }

    // =========================================================================
    // 2. CRISP 5x7 BITMAP PIXEL FONT RENDERER (ZERO BLUR AT 320x180)
    // =========================================================================
    static final class PixelFont {
        private static final Map<Character, int[]> GLYPHS = new HashMap<>();

        static {
            GLYPHS.put('A', new int[] { 0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001 });
            GLYPHS.put('B', new int[] { 0b11110, 0b10001, 0b10001, 0b11110, 0b10001, 0b10001, 0b11110 });
            GLYPHS.put('C', new int[] { 0b01111, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b01111 });
            GLYPHS.put('D', new int[] { 0b11110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11110 });
            GLYPHS.put('E', new int[] { 0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111 });
            GLYPHS.put('F', new int[] { 0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000 });
            GLYPHS.put('G', new int[] { 0b01111, 0b10000, 0b10000, 0b10111, 0b10001, 0b10001, 0b01111 });
            GLYPHS.put('H', new int[] { 0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001 });
            GLYPHS.put('I', new int[] { 0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b11111 });
            GLYPHS.put('J', new int[] { 0b00111, 0b00010, 0b00010, 0b00010, 0b10010, 0b10010, 0b01100 });
            GLYPHS.put('K', new int[] { 0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001 });
            GLYPHS.put('L', new int[] { 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111 });
            GLYPHS.put('M', new int[] { 0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001 });
            GLYPHS.put('N', new int[] { 0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001, 0b10001 });
            GLYPHS.put('O', new int[] { 0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110 });
            GLYPHS.put('P', new int[] { 0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000 });
            GLYPHS.put('Q', new int[] { 0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10010, 0b01101 });
            GLYPHS.put('R', new int[] { 0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001 });
            GLYPHS.put('S', new int[] { 0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110 });
            GLYPHS.put('T', new int[] { 0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100 });
            GLYPHS.put('U', new int[] { 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110 });
            GLYPHS.put('V', new int[] { 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100 });
            GLYPHS.put('W', new int[] { 0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b11011, 0b10001 });
            GLYPHS.put('X', new int[] { 0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001 });
            GLYPHS.put('Y', new int[] { 0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100 });
            GLYPHS.put('Z', new int[] { 0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111 });

            GLYPHS.put('0', new int[] { 0b01110, 0b10011, 0b10101, 0b10101, 0b11001, 0b10001, 0b01110 });
            GLYPHS.put('1', new int[] { 0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110 });
            GLYPHS.put('2', new int[] { 0b01110, 0b10001, 0b00001, 0b00110, 0b01000, 0b10000, 0b11111 });
            GLYPHS.put('3', new int[] { 0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110 });
            GLYPHS.put('4', new int[] { 0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010 });
            GLYPHS.put('5', new int[] { 0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110 });
            GLYPHS.put('6', new int[] { 0b01110, 0b10000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110 });
            GLYPHS.put('7', new int[] { 0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000 });
            GLYPHS.put('8', new int[] { 0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110 });
            GLYPHS.put('9', new int[] { 0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00001, 0b01110 });

            GLYPHS.put('-', new int[] { 0b00000, 0b00000, 0b00000, 0b01110, 0b00000, 0b00000, 0b00000 });
            GLYPHS.put(':', new int[] { 0b00000, 0b00100, 0b00100, 0b00000, 0b00100, 0b00100, 0b00000 });
            GLYPHS.put('.', new int[] { 0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00110, 0b00110 });
            GLYPHS.put('!', new int[] { 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00000, 0b00100 });
            GLYPHS.put('/', new int[] { 0b00001, 0b00010, 0b00010, 0b00100, 0b01000, 0b01000, 0b10000 });
            GLYPHS.put('>', new int[] { 0b10000, 0b01000, 0b00100, 0b00010, 0b00100, 0b01000, 0b10000 });
            GLYPHS.put('<', new int[] { 0b00001, 0b00010, 0b00100, 0b01000, 0b00100, 0b00010, 0b00001 });
            GLYPHS.put(' ', new int[] { 0, 0, 0, 0, 0, 0, 0 });
        }

        public static int getWidth(String text, int scale, int spacing) {
            if (text == null || text.isEmpty())
                return 0;
            return text.length() * (5 * scale + spacing) - spacing;
        }

        public static void draw(Graphics2D g, String text, int x, int y, int scale, int spacing, Color color) {
            g.setColor(color);
            String upper = text.toUpperCase();
            int cursorX = x;
            for (int i = 0; i < upper.length(); i++) {
                int[] rows = GLYPHS.getOrDefault(upper.charAt(i), GLYPHS.get(' '));
                for (int r = 0; r < 7; r++) {
                    int rowBits = rows[r];
                    for (int c = 0; c < 5; c++) {
                        if ((rowBits & (1 << (4 - c))) != 0) {
                            g.fillRect(cursorX + c * scale, y + r * scale, scale, scale);
                        }
                    }
                }
                cursorX += (5 * scale) + spacing;
            }
        }

        public static void drawOutlined(
                Graphics2D g, String text, int x, int y, int scale, int spacing,
                Color fill, Color outline) {
            draw(g, text, x - 1, y, scale, spacing, outline);
            draw(g, text, x + 1, y, scale, spacing, outline);
            draw(g, text, x, y - 1, scale, spacing, outline);
            draw(g, text, x, y + 1, scale, spacing, outline);
            draw(g, text, x + 1, y + 1, scale, spacing, outline);
            draw(g, text, x, y, scale, spacing, fill);
        }

        public static void drawCentered(Graphics2D g, String text, int centerX, int y, int scale, int spacing,
                Color color) {
            int w = getWidth(text, scale, spacing);
            draw(g, text, centerX - (w / 2), y, scale, spacing, color);
        }
    }

    // =========================================================================
    // 3. DATA STRUCTURES & ALGORITHMS (DSA)
    // =========================================================================

    /**
     * Generic Circular Doubly Linked List for O(1) wrap-around navigation.
     */
    static class CircularMenu<T> {
        private static class Node<T> {
            T data;
            Node<T> next;
            Node<T> prev;

            Node(T data) {
                this.data = data;
            }
        }

        private Node<T> head;
        private Node<T> current;
        private int size = 0;

        public void add(T item) {
            Node<T> newNode = new Node<>(item);
            if (head == null) {
                head = newNode;
                head.next = head;
                head.prev = head;
                current = head;
            } else {
                Node<T> tail = head.prev;
                tail.next = newNode;
                newNode.prev = tail;
                newNode.next = head;
                head.prev = newNode;
            }
            size++;
        }

        public void moveNext() {
            if (current != null)
                current = current.next;
        }

        public void movePrev() {
            if (current != null)
                current = current.prev;
        }

        public T getCurrent() {
            return current != null ? current.data : null;
        }

        public void select(T item) {
            if (head == null)
                return;
            Node<T> temp = head;
            for (int i = 0; i < size; i++) {
                if (temp.data.equals(item)) {
                    current = temp;
                    return;
                }
                temp = temp.next;
            }
        }

        public List<T> toList() {
            List<T> items = new ArrayList<>(size);
            if (head == null)
                return items;
            Node<T> temp = head;
            for (int i = 0; i < size; i++) {
                items.add(temp.data);
                temp = temp.next;
            }
            return items;
        }
    }

    /**
     * Cyclic Sprite Animator managing idle/shuffle frame loops and one-shot paddle
     * swings.
     */
    /**
     * Idle is the default pose. Ready appears only while the ball approaches.
     * The swing pose is a one-shot triggered only after paddle contact.
     */
    static class SpriteAnimator {
        private final BufferedImage idleSprite;
        private final BufferedImage readySprite;
        private final BufferedImage swingSprite;
        private final BufferedImage[] walkSprites;
        private double swingTimer = 0;
        private double walkFrameTimer = 0;
        private int walkFrameIndex = 0;
        private boolean ready = false;
        private boolean walking = false;

        public SpriteAnimator(
                BufferedImage idleSprite, BufferedImage readySprite,
                BufferedImage swingSprite, BufferedImage[] walkSprites) {
            this.idleSprite = idleSprite;
            this.readySprite = readySprite;
            this.swingSprite = swingSprite;
            this.walkSprites = walkSprites;
        }

        public void triggerSwing(double duration) {
            swingTimer = Math.max(swingTimer, duration);
            ready = false;
        }

        public void setReady(boolean value) {
            ready = value;
        }

        public void setWalking(boolean value) {
            walking = value;
            if (!value) {
                walkFrameTimer = 0;
                walkFrameIndex = 0;
            }
        }

        public void update(double dt) {
            if (swingTimer > 0)
                swingTimer = Math.max(0, swingTimer - dt);
            if (walking && walkSprites != null && walkSprites.length > 0) {
                walkFrameTimer += dt;
                // Four-frame walk cycle: a complete footstep loop takes 0.48 s.
                while (walkFrameTimer >= 0.12) {
                    walkFrameTimer -= 0.12;
                    walkFrameIndex = (walkFrameIndex + 1) % walkSprites.length;
                }
            }
        }

        public BufferedImage getCurrentSprite() {
            // Action priority: confirmed hit > prepare > walk > idle.
            if (swingTimer > 0 && swingSprite != null)
                return swingSprite;
            if (ready && readySprite != null)
                return readySprite;
            if (walking && walkSprites != null && walkSprites.length > 0) {
                BufferedImage frame = walkSprites[walkFrameIndex];
                if (frame != null)
                    return frame;
            }
            return idleSprite;
        }

        public void reset() {
            swingTimer = 0;
            ready = false;
            walking = false;
            walkFrameTimer = 0;
            walkFrameIndex = 0;
        }
    }

    // 4. OBJECT-ORIENTED GAME ENTITIES (ABSTRACTION & POLYMORPHISM)
    // =========================================================================

    static abstract class PixelEntity {
        protected double x, y;
        protected double vx, vy;

        public PixelEntity(double x, double y, double vx, double vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }

        public abstract void update(double dt);

        public abstract void render(Graphics2D g);
    }

    /**
     * 2.5D Pickleball with court bounce physics (z-elevation + gravity), ground
     * shadow,
     * and an ArrayDeque sliding-window arc trail.
     */
    static class RallyPickleball extends PixelEntity {
        private static final int[][] FALLBACK_BALL = {
                { 0, 0, 0, 1, 1, 1, 0, 0, 0 },
                { 0, 1, 1, 2, 2, 2, 2, 0, 0 },
                { 1, 2, 4, 2, 2, 4, 2, 2, 0 },
                { 1, 2, 2, 2, 2, 2, 2, 3, 0 },
                { 1, 2, 2, 4, 2, 2, 4, 3, 3 },
                { 0, 2, 4, 2, 2, 2, 2, 3, 3 },
                { 0, 2, 2, 2, 4, 2, 3, 3, 0 },
                { 0, 0, 3, 3, 3, 3, 3, 0, 0 },
                { 0, 0, 0, 3, 3, 3, 0, 0, 0 }
        };

        private static final double GRAVITY = 155.0;
        private static final double RESTITUTION = 0.68;
        private static final int MAX_TRAIL = 12;

        private double z = 12.0;
        private double vz = 58.0;
        private final Deque<Point> trail = new ArrayDeque<>(MAX_TRAIL);
        private double trailTimer = 0;
        private final BufferedImage ballSprite;

        // The ball has one receiver at a time. Its flight is gently steered
        // toward that receiver's live paddle position instead of a fixed point.
        private CourtPlayer targetPlayer;
        private double flightSpeed = 136.0;

        public RallyPickleball(double x, double y, double vx, double vy, BufferedImage ballSprite) {
            super(x, y, vx, vy);
            this.ballSprite = ballSprite;
        }

        public void reset(double startX, double startY, double startVx, double startVy) {
            x = startX;
            y = startY;
            vx = startVx;
            vy = startVy;
            z = 12.0;
            vz = 58.0;
            targetPlayer = null;
            flightSpeed = Math.max(1.0, Math.hypot(startVx, startVy));
            trail.clear();
            trailTimer = 0;
        }

        public double getScreenCenterX() {
            return x + 7.0;
        }

        public double getScreenCenterY() {
            return y - z + 8.0;
        }

        public boolean isTarget(CourtPlayer player) {
            return targetPlayer == player;
        }

        /**
         * Starts or changes the receiver. The initial velocity points toward
         * the receiver's paddle; subsequent frames gently home toward its
         * updated position, so a moving opponent can be tracked in flight.
         */
        public void launchToward(CourtPlayer target, double speed) {
            targetPlayer = target;
            flightSpeed = speed;
            aimVelocityAtTarget();
        }

        /**
         * A hit is accepted only from the player currently assigned to receive
         * the ball. Switching the target immediately prevents duplicate hits
         * and repeated swing animations from the same contact.
         */
        public boolean hitBy(CourtPlayer hitter, CourtPlayer nextTarget, double speed) {
            if (hitter == null || nextTarget == null || targetPlayer != hitter) {
                return false;
            }

            targetPlayer = nextTarget;
            flightSpeed = speed;
            z = Math.max(8.0, z);
            vz = 54.0;
            aimVelocityAtTarget();
            return true;
        }

        private void aimVelocityAtTarget() {
            if (targetPlayer == null)
                return;

            double targetX = targetPlayer.getPaddleCenterX();
            double targetGroundY = targetPlayer.getPaddleCenterY() + z;
            double dx = targetX - getScreenCenterX();
            double dy = targetGroundY - (y + 8.0);
            double distance = Math.max(1.0, Math.hypot(dx, dy));

            vx = (dx / distance) * flightSpeed;
            vy = (dy / distance) * flightSpeed;
        }

        private void steerTowardMovingTarget(double dt) {
            if (targetPlayer == null)
                return;

            double targetX = targetPlayer.getPaddleCenterX();
            // Convert the visible paddle height back to the ball's ground plane.
            double targetGroundY = targetPlayer.getPaddleCenterY() + z;
            double dx = targetX - getScreenCenterX();
            double dy = targetGroundY - (y + 8.0);
            double distance = Math.max(1.0, Math.hypot(dx, dy));

            double wantedVx = (dx / distance) * flightSpeed;
            double wantedVy = (dy / distance) * flightSpeed;

            // Smooth steering keeps the ball moving naturally rather than snapping.
            double blend = 1.0 - Math.exp(-3.2 * dt);
            vx += (wantedVx - vx) * blend;
            vy += (wantedVy - vy) * blend;
        }

        @Override
        public void update(double dt) {
            steerTowardMovingTarget(dt);
            x += vx * dt;
            y += vy * dt;

            vz -= GRAVITY * dt;
            z += vz * dt;
            if (z <= 0) {
                z = 0;
                vz = Math.abs(vz) * RESTITUTION;
                if (vz < 14.0)
                    vz = 36.0;
            }

            // The perspective court widens toward the near baseline.
            double depth = Math.max(0, Math.min(34, y - 120));
            double leftEdge = 42.0 - depth * 0.88;
            double rightEdge = 278.0 + depth * 0.88;
            if (x < leftEdge) {
                x = leftEdge;
                vx = Math.abs(vx);
            } else if (x > rightEdge - 14.0) {
                x = rightEdge - 14.0;
                vx = -Math.abs(vx);
            }

            if (y < 120) {
                y = 120;
                vy = Math.abs(vy);
            } else if (y > 154) {
                y = 154;
                vy = -Math.abs(vy);
            }

            trailTimer += dt;
            if (trailTimer >= 0.022) {
                trailTimer = 0;
                trail.addFirst(new Point(
                        (int) Math.round(x + 7),
                        (int) Math.round(y - z + 8)));
                if (trail.size() > MAX_TRAIL)
                    trail.removeLast();
            }
        }

        @Override
        public void render(Graphics2D g) {
            int groundX = (int) Math.round(x);
            int groundY = (int) Math.round(y);

            // Ground shadow
            int shadowW = Math.max(4, 10 - (int) (z / 5));
            g.setColor(Palette.COURT_SHADOW);
            g.fillRect(groundX + (10 - shadowW) / 2, groundY + 8, shadowW, 2);

            // Fading pixel arc trail
            int idx = 0;
            for (Point p : trail) {
                int size = Math.max(1, 4 - (idx / 3));
                int alpha = Math.max(20, 170 - (idx * 13));
                g.setColor(new Color(254, 240, 138, alpha));
                g.fillRect(p.x - size / 2, p.y - size / 2, size, size);
                idx++;
            }

            int drawX = groundX;
            int drawY = (int) Math.round(y - z);

            if (ballSprite != null) {
                // FIX: ball frame is 156x193, aspect 0.81. Old 10x10 forced it
                // square and ~15x smaller, then the 4x upscale pixelated it.
                // 14x17 keeps the real aspect and is big enough to see.
                g.drawImage(ballSprite, drawX, drawY, 14, 17, null);
            } else {
                for (int r = 0; r < FALLBACK_BALL.length; r++) {
                    for (int c = 0; c < FALLBACK_BALL[r].length; c++) {
                        int val = FALLBACK_BALL[r][c];
                        if (val == 0)
                            continue;
                        switch (val) {
                            case 1 -> g.setColor(Palette.BALL_LIGHT);
                            case 2 -> g.setColor(Palette.BALL_YELLOW);
                            case 3 -> g.setColor(Palette.BALL_SHADOW);
                            case 4 -> g.setColor(Palette.BALL_HOLE);
                        }
                        g.fillRect(drawX + c, drawY + r, 1, 1);
                    }
                }
            }
        }
    }

    /**
     * Animated Court Player driven by sliced PNG frames (with built-in pixel matrix
     * fallback).
     */
    static class CourtPlayer extends PixelEntity {
        private final boolean facingRight;
        private final boolean flipSpriteHorizontally;
        private final double homeX;
        private Color jerseyColor;
        private final BufferedImage baseIdleFrame;
        private final BufferedImage baseReadyFrame;
        private final BufferedImage baseSwingFrame;
        private final BufferedImage[] baseWalkFrames;
        private final RallyPickleball ball;
        private CourtPlayer opponent;
        private SpriteAnimator animator;
        private double hitCooldown = 0;

        public CourtPlayer(
                double x, double y, boolean facingRight, boolean flipSpriteHorizontally,
                Color jerseyColor, BufferedImage idleFrame, BufferedImage readyFrame,
                BufferedImage swingFrame, BufferedImage[] walkFrames, RallyPickleball ball) {
            super(x, y, 0, 0);
            this.homeX = x;
            this.facingRight = facingRight;
            this.flipSpriteHorizontally = flipSpriteHorizontally;
            this.jerseyColor = jerseyColor;
            this.baseIdleFrame = idleFrame;
            this.baseReadyFrame = readyFrame;
            this.baseSwingFrame = swingFrame;
            this.baseWalkFrames = walkFrames != null ? walkFrames.clone() : new BufferedImage[0];
            this.ball = ball;
            rebuildAnimator();
        }

        public void setOpponent(CourtPlayer opponent) {
            this.opponent = opponent;
        }

        public double getPaddleCenterX() {
            return x + (facingRight ? 18.0 : 4.0);
        }

        public double getPaddleCenterY() {
            return y + 32.0;
        }

        private void rebuildAnimator() {
            if (baseIdleFrame != null) {
                BufferedImage idle = AssetLoader.tintRedJersey(baseIdleFrame, jerseyColor);
                BufferedImage ready = baseReadyFrame != null
                        ? AssetLoader.tintRedJersey(baseReadyFrame, jerseyColor)
                        : null;
                BufferedImage swing = baseSwingFrame != null
                        ? AssetLoader.tintRedJersey(baseSwingFrame, jerseyColor)
                        : null;
                BufferedImage[] walk = new BufferedImage[baseWalkFrames.length];
                for (int i = 0; i < baseWalkFrames.length; i++) {
                    walk[i] = baseWalkFrames[i] != null
                            ? AssetLoader.tintRedJersey(baseWalkFrames[i], jerseyColor)
                            : null;
                }
                animator = new SpriteAnimator(idle, ready, swing, walk);
            } else {
                animator = null;
            }
        }

        public void setJerseyColor(Color newColor) {
            jerseyColor = newColor;
            if (baseIdleFrame != null)
                rebuildAnimator();
        }

        public void reset(double startX, double startY) {
            x = startX;
            y = startY;
            hitCooldown = 0;
            if (animator != null)
                animator.reset();
        }

        private double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }

        private double moveToward(double current, double target, double maxStep) {
            double delta = target - current;
            if (Math.abs(delta) <= maxStep)
                return target;
            return current + Math.signum(delta) * maxStep;
        }

        @Override
        public void update(double dt) {
            if (hitCooldown > 0)
                hitCooldown = Math.max(0, hitCooldown - dt);

            boolean ballComingTowardMe = facingRight ? ball.vx < 0 : ball.vx > 0;
            boolean ballAssignedToMe = ball.isTarget(this);

            // The net divides the sideways court into two halves. Only the
            // receiver moves to intercept; the other player returns to position.
            double paddleOffsetX = facingRight ? 18.0 : 4.0;
            double minX = facingRight ? 28.0 : 188.0;
            double maxX = facingRight ? 130.0 : 278.0;

            double desiredX = homeX;
            double desiredY = 108.0;
            if (ballAssignedToMe) {
                // Aim slightly ahead of the ball's current position. This gives
                // the receiver time to meet it, while the ball homes toward the
                // receiver's changing paddle location.
                double interceptLead = 0.10;
                desiredX = ball.getScreenCenterX()
                        + ball.vx * interceptLead - paddleOffsetX;
                desiredY = ball.getScreenCenterY()
                        + ball.vy * interceptLead - 32.0;
            }

            desiredX = clamp(desiredX, minX, maxX);
            // Keep the player's feet inside the blue court, not on the red apron.
            desiredY = clamp(desiredY, 98.0, 112.0);

            double previousX = x;
            double previousY = y;
            x = moveToward(x, desiredX, 112.0 * dt);
            y = moveToward(y, desiredY, 76.0 * dt);

            double movementThisFrame = Math.hypot(x - previousX, y - previousY);
            if (animator != null) {
                animator.setWalking(movementThisFrame > 0.35);
            }

            double paddleX = x + paddleOffsetX;
            double paddleY = y + 32.0;
            double contactDistance = Math.hypot(
                    ball.getScreenCenterX() - paddleX,
                    ball.getScreenCenterY() - paddleY);

            if (animator != null) {
                // Ready is only a near-contact preparation pose. It is not a swing.
                animator.setReady(
                        ballAssignedToMe && ballComingTowardMe
                                && contactDistance > 12.0 && contactDistance < 28.0);
            }

            // A swing is permitted only for the assigned receiver and only
            // inside the small paddle/ball collision radius.
            final double PADDLE_CONTACT_RADIUS = 12.0;
            boolean isContact = ballAssignedToMe
                    && ballComingTowardMe
                    && hitCooldown <= 0
                    && contactDistance <= PADDLE_CONTACT_RADIUS;

            if (isContact && opponent != null
                    && ball.hitBy(this, opponent, 138.0)) {
                if (animator != null)
                    animator.triggerSwing(0.18);
                hitCooldown = 0.24;
            }

            if (animator != null)
                animator.update(dt);
        }

        @Override
        public void render(Graphics2D g) {
            int drawY = (int) Math.round(y);
            BufferedImage sprite = (animator != null) ? animator.getCurrentSprite() : null;
            int spriteH = 48;
            int spriteW = 22;
            if (sprite != null && sprite.getHeight() > 0) {
                spriteW = Math.max(10, (int) Math.round(
                        spriteH * (double) sprite.getWidth() / sprite.getHeight()));
            }

            int centreX = (int) Math.round(x) + 12;
            int drawX = centreX - spriteW / 2;

            g.setColor(Palette.COURT_SHADOW);
            int shadowW = Math.max(14, (int) (spriteW * 0.6));
            g.fillRect(centreX - shadowW / 2, drawY + spriteH - 2, shadowW, 3);

            if (sprite != null) {
                if (flipSpriteHorizontally) {
                    g.drawImage(sprite, drawX + spriteW, drawY, -spriteW, spriteH, null);
                } else {
                    g.drawImage(sprite, drawX, drawY, spriteW, spriteH, null);
                }
            }
        }
    }

    // 5. MAIN PIXEL RENDERER & INPUT CONTROLLER
    // =========================================================================

    static class GameCanvas extends JPanel implements ActionListener {
        private final BufferedImage pixelBuffer;
        private final Timer gameLoop;
        private final List<PixelEntity> worldEntities = new ArrayList<>();
        private final CircularMenu<MenuAction> menu = new CircularMenu<>();
        private final CircularMenu<AvatarPreset> avatarMenu = new CircularMenu<>();
        private final Map<MenuAction, Runnable> actionDispatch = new HashMap<>();
        private final Map<MenuAction, Rectangle> buttonHitboxes = new HashMap<>();

        private BufferedImage courtBackground;
        private BufferedImage tintedPreview;
        private AvatarPreset tintedPreviewPreset;
        private BufferedImage[] leftPreviewFrames;
        private CourtPlayer leftPlayer;
        private CourtPlayer rightPlayer;
        private RallyPickleball rallyBall;

        private GameState currentState = GameState.INTRO;
        private Gender playerGender = Gender.MALE;
        private Gender hoveredGender;
        private final Rectangle maleGenderHitbox = new Rectangle(70, 94, 80, 20);
        private final Rectangle femaleGenderHitbox = new Rectangle(170, 94, 80, 20);
        private String matchBanner = "";
        private double matchBannerTimer = 0;
        private double pulseTimer = 0;
        private long lastNanoTime;

        public GameCanvas() {
            setPreferredSize(new Dimension(VIRTUAL_WIDTH * SCALE, VIRTUAL_HEIGHT * SCALE));
            setFocusable(true);

            // FIX: render straight into a 1280x720 buffer instead of a
            // 320x180 one. The old buffer threw away most of every asset
            // before the screen upscale could ever get it back.
            pixelBuffer = new BufferedImage(VIRTUAL_WIDTH * SCALE, VIRTUAL_HEIGHT * SCALE, BufferedImage.TYPE_INT_ARGB);

            initMenusAndDispatch();
            loadAssetsAndEntities();
            initInputListeners();
            SwingUtilities.invokeLater(this::requestFocusInWindow);

            lastNanoTime = System.nanoTime();
            gameLoop = new Timer(16, this);
            gameLoop.start();
        }

        private void loadAssetsAndEntities() {
            courtBackground = AssetLoader.loadImage("court_background");

            BufferedImage rawSheet = AssetLoader.loadImage("spritesheet");
            BufferedImage cleanSheet = AssetLoader.stripWhiteBackground(rawSheet, 22);
            BufferedImage[][] spriteGrid = AssetLoader.extractSpriteGrid(cleanSheet, 3, 3);

            BufferedImage redIdle = null;
            BufferedImage redReady = null;
            BufferedImage redSwing = null;
            BufferedImage blueIdle = null;
            BufferedImage blueReady = null;
            BufferedImage blueSwing = null;
            BufferedImage ballSprite = null;
            BufferedImage[] redWalkFrames = loadWalkFrames("red");
            BufferedImage[] blueWalkFrames = loadWalkFrames("blue");

            if (spriteGrid != null) {
                // Column 1 = idle, column 2 = ready, column 3 = swing.
                // Do NOT loop the ready/swing poses as ordinary idle frames.
                redIdle = spriteGrid[0][0];
                redReady = spriteGrid[0][1];
                redSwing = spriteGrid[0][2];
                blueIdle = spriteGrid[1][0];
                blueReady = spriteGrid[1][1];
                blueSwing = spriteGrid[1][2];
                ballSprite = spriteGrid[2][0];
            }
            leftPreviewFrames = redIdle != null
                    ? new BufferedImage[] { redIdle }
                    : null;

            rallyBall = new RallyPickleball(92, 136, 128, 3, ballSprite);

            leftPlayer = new CourtPlayer(
                    40, 108, true, false,
                    avatarMenu.getCurrent().jerseyColor,
                    redIdle, redReady, redSwing, redWalkFrames, rallyBall);

            // Flip the second row so the blue player faces left toward the net.
            rightPlayer = new CourtPlayer(
                    258, 108, false, true,
                    Palette.RIVAL_CYAN,
                    blueIdle, blueReady, blueSwing, blueWalkFrames, rallyBall);

            leftPlayer.setOpponent(rightPlayer);
            rightPlayer.setOpponent(leftPlayer);

            // The opening serve travels toward the right-side receiver.
            rallyBall.launchToward(rightPlayer, 128.0);

            // Update the ball before players so contact checks use its latest
            // position for this frame. Rendering still uses depth sorting.
            worldEntities.add(rallyBall);
            worldEntities.add(leftPlayer);
            worldEntities.add(rightPlayer);
        }

        private BufferedImage[] loadWalkFrames(String color) {
            BufferedImage[] frames = new BufferedImage[4];
            for (int i = 0; i < frames.length; i++) {
                frames[i] = AssetLoader.loadImage("walk_" + color + "_" + i);
            }
            return frames;
        }

        private void initMenusAndDispatch() {
            for (MenuAction action : MenuAction.values()) {
                menu.add(action);
            }
            for (AvatarPreset preset : AvatarPreset.values()) {
                avatarMenu.add(preset);
            }

            // O(1) Command Dispatch Table
            actionDispatch.put(MenuAction.PLAY, () -> startMatch());
            actionDispatch.put(MenuAction.AVATAR, () -> currentState = GameState.AVATAR);
            actionDispatch.put(MenuAction.CREDITS, () -> currentState = GameState.CREDITS);
            actionDispatch.put(MenuAction.EXIT, () -> System.exit(0));
        }

        private void startMatch() {
            if (leftPlayer != null)
                leftPlayer.reset(40, 108);
            if (rightPlayer != null)
                rightPlayer.reset(258, 108);
            if (rallyBall != null) {
                rallyBall.reset(92, 136, 128, 3);
                rallyBall.launchToward(rightPlayer, 128.0);
            }
            matchBanner = "";
            matchBannerTimer = 0;
            currentState = GameState.MAP;
            PickleBallHomeScreen owner = (PickleBallHomeScreen) SwingUtilities.getWindowAncestor(this);
            PickleSmashCityMap cityMap = new PickleSmashCityMap();
            cityMap.setPlayerJerseyColor(avatarMenu.getCurrent().jerseyColor);
            cityMap.setExitAction(() -> {
                cityMap.stop();
                currentState = GameState.HOME;
                owner.showHomeScreen();
            });
            owner.showCityMap(cityMap);
        }

        private void initInputListeners() {
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int code = e.getKeyCode();

                    if (currentState == GameState.INTRO) {
                        currentState = GameState.GENDER;
                        repaint();
                        return;
                    }

                    if (currentState == GameState.GENDER) {
                        switch (code) {
                            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> playerGender = Gender.MALE;
                            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> playerGender = Gender.FEMALE;
                            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> currentState = GameState.HOME;
                        }
                        repaint();
                        return;
                    }

                    if (currentState == GameState.MAP) {
                        if (code == KeyEvent.VK_ESCAPE
                                || code == KeyEvent.VK_ENTER
                                || code == KeyEvent.VK_SPACE) {
                            currentState = GameState.HOME;
                        }
                        return;
                    }

                    if (currentState == GameState.PLAYING) {
                        if (code == KeyEvent.VK_ESCAPE) {
                            currentState = GameState.HOME;
                        }
                        return;
                    }

                    if (currentState == GameState.AVATAR) {
                        switch (code) {
                            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> {
                                avatarMenu.movePrev();
                                leftPlayer.setJerseyColor(avatarMenu.getCurrent().jerseyColor);
                            }
                            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> {
                                avatarMenu.moveNext();
                                leftPlayer.setJerseyColor(avatarMenu.getCurrent().jerseyColor);
                            }
                            case KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE ->
                                currentState = GameState.HOME;
                        }
                        return;
                    }

                    if (currentState == GameState.CREDITS) {
                        switch (code) {
                            case KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE ->
                                currentState = GameState.HOME;
                        }
                        return;
                    }

                    switch (code) {
                        case KeyEvent.VK_UP, KeyEvent.VK_W -> {
                            menu.movePrev();
                            matchBannerTimer = 0;
                        }
                        case KeyEvent.VK_DOWN, KeyEvent.VK_S -> {
                            menu.moveNext();
                            matchBannerTimer = 0;
                        }
                        case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> executeSelectedAction();
                    }
                }
            });

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoveredGender != null) {
                        hoveredGender = null;
                        repaint();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    if (currentState == GameState.GENDER) {
                        int vx = e.getX() / SCALE;
                        int vy = e.getY() / SCALE;
                        Gender nextHovered = maleGenderHitbox.contains(vx, vy)
                                ? Gender.MALE
                                : femaleGenderHitbox.contains(vx, vy) ? Gender.FEMALE : null;
                        if (hoveredGender != nextHovered) {
                            hoveredGender = nextHovered;
                            repaint();
                        }
                        return;
                    }
                    if (currentState != GameState.HOME)
                        return;
                    int vx = e.getX() / SCALE;
                    int vy = e.getY() / SCALE;
                    for (Map.Entry<MenuAction, Rectangle> entry : buttonHitboxes.entrySet()) {
                        if (entry.getValue().contains(vx, vy)) {
                            menu.select(entry.getKey());
                            break;
                        }
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int vy = e.getY() / SCALE;
                    int vx = e.getX() / SCALE;

                    if (currentState == GameState.INTRO) {
                        currentState = GameState.GENDER;
                        repaint();
                        return;
                    }

                    if (currentState == GameState.GENDER) {
                        if (maleGenderHitbox.contains(vx, vy)) {
                            playerGender = Gender.MALE;
                            currentState = GameState.HOME;
                        } else if (femaleGenderHitbox.contains(vx, vy)) {
                            playerGender = Gender.FEMALE;
                            currentState = GameState.HOME;
                        }
                        repaint();
                        return;
                    }

                    if (currentState == GameState.AVATAR) {
                        if (vy >= 138) {
                            currentState = GameState.HOME;
                        } else {
                            avatarMenu.moveNext();
                            leftPlayer.setJerseyColor(avatarMenu.getCurrent().jerseyColor);
                        }
                        return;
                    }

                    if (currentState == GameState.CREDITS) {
                        currentState = GameState.HOME;
                        return;
                    }

                    for (Map.Entry<MenuAction, Rectangle> entry : buttonHitboxes.entrySet()) {
                        if (entry.getValue().contains(vx, vy)) {
                            menu.select(entry.getKey());
                            executeSelectedAction();
                            break;
                        }
                    }
                }
            };
            addMouseMotionListener(mouseHandler);
            addMouseListener(mouseHandler);
        }

        private void executeSelectedAction() {
            MenuAction selected = menu.getCurrent();
            Runnable command = actionDispatch.get(selected);
            if (command != null) {
                command.run();
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            long now = System.nanoTime();
            double dt = (now - lastNanoTime) / 1_000_000_000.0;
            lastNanoTime = now;

            pulseTimer += dt;
            if (matchBannerTimer > 0) {
                matchBannerTimer = Math.max(0, matchBannerTimer - dt);
            }

            // Keep a live court demonstration running behind the menu screens.
            // The Home screen is still being built, so the characters and ball
            // should move as soon as the application opens without pressing PLAY.
            // PLAY simply resets the same entities and continues as the match.
            double step = Math.min(dt, 0.05);
            if (currentState != GameState.MAP) {
                if (rallyBall != null)
                    rallyBall.update(step);
                if (leftPlayer != null)
                    leftPlayer.update(step);
                if (rightPlayer != null)
                    rightPlayer.update(step);
            }

            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = pixelBuffer.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            // FIX (pixelation root cause): images are now sampled with
            // BILINEAR into a 1280x720 buffer, not NEAREST into 320x180.
            // Old path: 1376px court -> 320px NEAREST (threw away ~94% of
            // pixels) -> 1280px NEAREST (blew the damage up 4x).
            // New path: 1376px court -> 1280px BILINEAR in one step (~0.93x),
            // sprites sampled once, directly to screen size.
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            g2.scale(SCALE, SCALE);

            // 1. Background layer (uses court_background.png if present, or crisp
            // procedural fallback)
            if (courtBackground != null) {
                g2.drawImage(courtBackground, 0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT, null);
            } else {
                drawProceduralFallbackCourt(g2);
            }

            // 1b. Replace the baked-in static sky clouds with animated pixel clouds.
            // Buildings, trees, spectators, and the court remain completely still.
            drawMovingClouds(g2);

            // 2. Live court demo: depth-sorted players & bouncing pickleball (also active
            // on Home)
            List<PixelEntity> renderOrder = new ArrayList<>(worldEntities);
            renderOrder.sort((a, b) -> Double.compare(a.y, b.y));
            for (PixelEntity entity : renderOrder) {
                entity.render(g2);
            }

            // 3. Keep the title on menus; gameplay gets a small unobtrusive HUD.
            if (currentState == GameState.PLAYING) {
                drawPlayingHud(g2);
            } else {
                drawTitleHeader(g2);
            }

            // 4. Active Screen UI
            switch (currentState) {
                case INTRO -> drawIntroOverlay(g2);
                case GENDER -> drawGenderOverlay(g2);
                case HOME -> drawHomeMenu(g2);
                case PLAYING -> {
                    /* The court is unobstructed during the rally. */ }
                case MAP -> {
                    /* The city map is hosted directly in the game window. */ }
                case AVATAR -> drawAvatarOverlay(g2);
                case CREDITS -> drawCreditsOverlay(g2);
            }

            g2.dispose();

            // Buffer is already 1280x720: draw it 1:1, no second upscale.
            Graphics2D screen = (Graphics2D) g;
            screen.drawImage(pixelBuffer, 0, 0, VIRTUAL_WIDTH * SCALE, VIRTUAL_HEIGHT * SCALE, null);
        }

        /**
         * Draws a small parallax cloud layer over the sky.
         * The original background contains painted clouds, so the top sky bands
         * are repainted first and then fresh pixel clouds are moved across them.
         */
        private void drawMovingClouds(Graphics2D g) {
            // Repaint only the sky area to hide the clouds baked into the PNG.
            g.setColor(new Color(1, 82, 178));
            g.fillRect(0, 0, VIRTUAL_WIDTH, 20);
            g.setColor(new Color(0, 118, 225));
            g.fillRect(0, 20, VIRTUAL_WIDTH, 22);

            drawPixelCloud(g, cloudX(0, 7.0), 7, 1);
            drawPixelCloud(g, cloudX(105, 4.5), 19, 1);
            drawPixelCloud(g, cloudX(215, 6.0), 5, 1);
        }

        private int cloudX(double startX, double speed) {
            double travel = (startX + pulseTimer * speed) % (VIRTUAL_WIDTH + 54);
            return (int) Math.round(travel) - 42;
        }

        /** A compact, blocky cloud with a pale-blue pixel shadow. */
        private void drawPixelCloud(Graphics2D g, int x, int y, int scale) {
            int u = scale;
            g.setColor(new Color(151, 218, 247));
            g.fillRect(x + 5 * u, y + 7 * u, 25 * u, 5 * u);
            g.fillRect(x + 2 * u, y + 9 * u, 32 * u, 3 * u);
            g.fillRect(x + 8 * u, y + 4 * u, 8 * u, 6 * u);
            g.fillRect(x + 14 * u, y + 1 * u, 9 * u, 9 * u);
            g.fillRect(x + 23 * u, y + 5 * u, 7 * u, 6 * u);

            g.setColor(new Color(250, 253, 255));
            g.fillRect(x + 5 * u, y + 6 * u, 25 * u, 4 * u);
            g.fillRect(x + 2 * u, y + 8 * u, 32 * u, 3 * u);
            g.fillRect(x + 8 * u, y + 3 * u, 8 * u, 6 * u);
            g.fillRect(x + 14 * u, y, 9 * u, 9 * u);
            g.fillRect(x + 23 * u, y + 4 * u, 7 * u, 6 * u);
        }

        private void drawProceduralFallbackCourt(Graphics2D g) {
            g.setColor(Palette.SKY_TOP);
            g.fillRect(0, 0, VIRTUAL_WIDTH, 28);
            g.setColor(Palette.SKY_MID);
            g.fillRect(0, 28, VIRTUAL_WIDTH, 24);
            g.setColor(Palette.SKY_HORIZON);
            g.fillRect(0, 52, VIRTUAL_WIDTH, 16);

            g.setColor(Palette.COURT_OUTER);
            g.fillRect(0, 68, VIRTUAL_WIDTH, VIRTUAL_HEIGHT - 68);

            int[] xPoints = { 42, VIRTUAL_WIDTH - 42, VIRTUAL_WIDTH - 14, 14 };
            int[] yPoints = { 78, 78, VIRTUAL_HEIGHT - 12, VIRTUAL_HEIGHT - 12 };
            g.setColor(Palette.COURT_INNER);
            g.fillPolygon(xPoints, yPoints, 4);

            g.setColor(Palette.COURT_LINE);
            g.drawPolygon(xPoints, yPoints, 4);
            g.drawLine(122, 78, 114, VIRTUAL_HEIGHT - 12);
            g.drawLine(VIRTUAL_WIDTH - 122, 78, VIRTUAL_WIDTH - 114, VIRTUAL_HEIGHT - 12);
            g.drawLine(28, 122, 118, 122);
            g.drawLine(VIRTUAL_WIDTH - 118, 122, VIRTUAL_WIDTH - 28, 122);

            int netX = VIRTUAL_WIDTH / 2;
            g.setColor(Palette.NET_DARK);
            g.fillRect(netX - 1, 74, 3, VIRTUAL_HEIGHT - 84);
            g.setColor(Palette.COURT_LINE);
            g.drawLine(netX, 74, netX, VIRTUAL_HEIGHT - 11);
        }

        private void drawTitleHeader(Graphics2D g) {
            int bobY = (int) Math.round(Math.sin(pulseTimer * 3.0) * 1.2);
            int titleY = 12 + bobY;

            String title = "PICKLE SMASH";
            int titleScale = 3;
            int titleSpacing = 2;
            int titleW = PixelFont.getWidth(title, titleScale, titleSpacing);
            int titleX = (VIRTUAL_WIDTH - titleW) / 2;

            PixelFont.draw(g, title, titleX + 2, titleY + 2, titleScale, titleSpacing, Palette.BTN_OUTLINE);
            PixelFont.drawOutlined(
                    g, title, titleX, titleY, titleScale, titleSpacing,
                    Palette.BALL_YELLOW, Palette.BTN_OUTLINE);

            // High-contrast subtitle banner
            String sub = "THE ULTIMATE PADDLE CHALLENGE";
            int subW = PixelFont.getWidth(sub, 1, 1);
            int bannerW = subW + 14;
            int bannerH = 11;
            int bannerX = (VIRTUAL_WIDTH - bannerW) / 2;
            int bannerY = titleY + 27;

            g.setColor(Palette.BTN_OUTLINE);
            g.fillRect(bannerX - 1, bannerY - 1, bannerW + 2, bannerH + 2);
            g.setColor(Palette.BTN_IDLE_BG);
            g.fillRect(bannerX, bannerY, bannerW, bannerH);
            g.setColor(Palette.GOLD_TRIM);
            g.drawRect(bannerX, bannerY, bannerW - 1, bannerH - 1);

            PixelFont.draw(g, sub, bannerX + 7, bannerY + 2, 1, 1, Palette.TEXT_WHITE);
        }

        private void drawPlayingHud(Graphics2D g) {
            int x = 6;
            int y = 5;
            g.setColor(Palette.BTN_OUTLINE);
            g.fillRect(x - 2, y - 2, 76, 12);
            g.setColor(new Color(10, 32, 65, 220));
            g.fillRect(x - 1, y - 1, 74, 10);
            PixelFont.draw(g, "MATCH IN PLAY", x + 2, y + 1, 1, 1, Palette.TEXT_WHITE);
            PixelFont.draw(g, "ESC: MENU", 257, y + 1, 1, 1, Palette.TEXT_WHITE);
        }

        private void drawHomeMenu(Graphics2D g) {
            buttonHitboxes.clear();
            List<MenuAction> items = menu.toList();
            MenuAction active = menu.getCurrent();

            int boxW = 112;
            int boxH = 16;
            int startX = (VIRTUAL_WIDTH - boxW) / 2;
            int startY = 64;
            int gap = 20;

            for (int i = 0; i < items.size(); i++) {
                MenuAction item = items.get(i);
                int y = startY + i * gap;
                boolean isSelected = (item == active);

                buttonHitboxes.put(item, new Rectangle(startX, y, boxW, boxH));
                drawBeveledButton(g, startX, y, boxW, boxH, item.label, isSelected);
            }

            // Temporary match notification banner when PLAY is clicked (disappears cleanly)
            if (matchBannerTimer > 0 && !matchBanner.isEmpty()) {
                int bw = PixelFont.getWidth(matchBanner, 1, 1) + 16;
                int bx = (VIRTUAL_WIDTH - bw) / 2;
                int by = 148;
                g.setColor(Palette.BTN_OUTLINE);
                g.fillRect(bx - 1, by - 1, bw + 2, 13);
                g.setColor(Palette.BTN_ACTIVE_BG);
                g.fillRect(bx, by, bw, 11);
                PixelFont.drawCentered(g, matchBanner, VIRTUAL_WIDTH / 2, by + 2, 1, 1, Palette.BTN_OUTLINE);
            }
        }

        private void drawBeveledButton(
                Graphics2D g, int x, int y, int w, int h, String label, boolean selected) {
            // 1. Thick outer pixel border
            g.setColor(Palette.BTN_OUTLINE);
            g.fillRect(x - 1, y - 1, w + 2, h + 2);

            // 2. Main button fill
            g.setColor(selected ? Palette.BTN_ACTIVE_BG : Palette.BTN_IDLE_BG);
            g.fillRect(x, y, w, h);

            // 3. Crisp 1px 16-bit top/left highlight & bottom/right bevel shadow
            g.setColor(selected ? Palette.BTN_ACTIVE_HI : Palette.GOLD_TRIM);
            g.fillRect(x, y, w, 1);
            g.fillRect(x, y, 1, h);

            g.setColor(selected ? Palette.BTN_ACTIVE_SH : Palette.BTN_IDLE_SH);
            g.fillRect(x, y + h - 1, w, 1);
            g.fillRect(x + w - 1, y, 1, h);

            if (!selected) {
                g.setColor(Palette.BTN_IDLE_HI);
                g.drawRect(x + 1, y + 1, w - 3, h - 3);
            }

            // 4. Pixel cursor arrow on the left for the selected option
            if (selected) {
                int cx = x + 10;
                int cy = y + 4;
                g.setColor(Palette.BTN_OUTLINE);
                g.fillRect(cx, cy, 1, 8);
                g.fillRect(cx + 1, cy + 1, 1, 6);
                g.fillRect(cx + 2, cy + 2, 1, 4);
                g.fillRect(cx + 3, cy + 3, 1, 2);
                g.fillRect(cx + 4, cy + 4, 1, 1);
            }

            // 5. Sharp bitmap label centered inside the button
            String display = selected ? "> " + label + " <" : label;
            Color textColor = selected ? Palette.BTN_OUTLINE : Palette.TEXT_WHITE;
            PixelFont.drawCentered(g, display, x + (w / 2), y + 5, 1, 1, textColor);
        }

        private void drawIntroOverlay(Graphics2D g) {
            drawModalFrame(g, "", "CLICK ANYWHERE OR PRESS ANY KEY");
            PixelFont.drawCentered(
                    g,
                    "WELCOME TO",
                    VIRTUAL_WIDTH / 2,
                    65,
                    2,
                    1,
                    Palette.BALL_YELLOW);
            PixelFont.drawCentered(
                    g,
                    "PICKLESMASH",
                    VIRTUAL_WIDTH / 2,
                    84,
                    2,
                    1,
                    Palette.BALL_YELLOW);
            PixelFont.drawCentered(
                    g,
                    "GET READY TO PLAY",
                    VIRTUAL_WIDTH / 2,
                    110,
                    1,
                    1,
                    Palette.TEXT_WHITE);
        }

        private void drawGenderOverlay(Graphics2D g) {
            drawModalFrame(g, "SELECT YOUR GENDER", "LEFT/RIGHT: SELECT  ENTER: CONTINUE");
            drawBeveledButton(
                    g,
                    maleGenderHitbox.x,
                    maleGenderHitbox.y,
                    maleGenderHitbox.width,
                    maleGenderHitbox.height,
                    Gender.MALE.label,
                    hoveredGender == Gender.MALE);
            drawBeveledButton(
                    g,
                    femaleGenderHitbox.x,
                    femaleGenderHitbox.y,
                    femaleGenderHitbox.width,
                    femaleGenderHitbox.height,
                    Gender.FEMALE.label,
                    hoveredGender == Gender.FEMALE);
        }

        private void drawAvatarOverlay(Graphics2D g) {
            drawModalFrame(g, "SELECT AVATAR", "LEFT/RIGHT: CYCLE  ENTER: DONE");
            AvatarPreset active = avatarMenu.getCurrent();

            int boxSize = 36;
            int boxX = (VIRTUAL_WIDTH - boxSize) / 2;
            int boxY = 72; // +6 with the modal (was 66)

            g.setColor(Palette.BTN_IDLE_SH);
            g.fillRect(boxX, boxY, boxSize, boxSize);
            g.setColor(Palette.GOLD_TRIM);
            g.drawRect(boxX, boxY, boxSize - 1, boxSize - 1);

            if (leftPreviewFrames != null && leftPreviewFrames[0] != null) {
                // FIX: tint once per preset change, not on every paint (was
                // rebuilding a full image 60x/second), and keep the frame's
                // real aspect (121x280 = 0.43). Old 28x30 stretched it ~2x wide.
                if (tintedPreview == null || tintedPreviewPreset != active) {
                    tintedPreview = AssetLoader.tintRedJersey(leftPreviewFrames[0], active.jerseyColor);
                    tintedPreviewPreset = active;
                }
                int previewH = 30;
                int previewW = Math.max(8,
                        (int) Math.round(previewH * (double) tintedPreview.getWidth() / tintedPreview.getHeight()));
                int previewX = boxX + (boxSize - previewW) / 2;
                g.drawImage(tintedPreview, previewX, boxY + 3, previewW, previewH, null);
            } else {
                g.setColor(active.jerseyColor);
                g.fillRect(boxX + 10, boxY + 10, 16, 16);
            }

            PixelFont.drawCentered(g, "< " + active.name + " >", VIRTUAL_WIDTH / 2, 116, 1, 1, Palette.BALL_YELLOW);
            PixelFont.drawCentered(g, active.trait, VIRTUAL_WIDTH / 2, 129, 1, 1, Palette.TEXT_WHITE);
        }

        private void drawCreditsOverlay(Graphics2D g) {
            drawModalFrame(g, "GAME CREDITS", "PRESS ENTER OR CLICK TO RETURN");
            String[] lines = {
                    "-----Members-----",
                    "FABRICANTE, CRIS HARVEY G.",
                    "   PADERNAL, REYMAR T.    ",
                    "   SALARDA, JERULD T.    "
            };
            int y = 74;
            for (String line : lines) {
                PixelFont.drawCentered(g, line, VIRTUAL_WIDTH / 2, y, 1, 1, Palette.TEXT_WHITE);
                y += 14;
            }
        }

        private void drawModalFrame(Graphics2D g, String header, String prompt) {
            drawModalFrame(g, header, prompt, 1, 1);
        }

        private void drawModalFrame(
                Graphics2D g, String header, String prompt, int headerScale, int headerSpacing) {
            // y=52 (was 46) so the modal clears the subtitle banner, which
            // sits 3px lower now that the real logo is taller than the old font title.
            int x = 44, y = 52, w = VIRTUAL_WIDTH - 88, h = 112;
            g.setColor(Palette.BTN_OUTLINE);
            g.fillRect(x - 1, y - 1, w + 2, h + 2);
            g.setColor(Palette.MODAL_BG);
            g.fillRect(x, y, w, h);
            g.setColor(Palette.GOLD_TRIM);
            g.drawRect(x, y, w - 1, h - 1);

            PixelFont.drawCentered(
                    g,
                    header,
                    VIRTUAL_WIDTH / 2,
                    y + 10,
                    headerScale,
                    headerSpacing,
                    Palette.BALL_YELLOW);
            PixelFont.drawCentered(g, prompt, VIRTUAL_WIDTH / 2, y + h - 14, 1, 1, Palette.TEXT_MUTED);
        }
    }
}
