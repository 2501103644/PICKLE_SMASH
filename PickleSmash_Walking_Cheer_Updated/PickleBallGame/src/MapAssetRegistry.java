import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MapAssetRegistry {
    private static final Map<String, String> ASSET_PATHS;

    static {
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("training_building", "Sprites/Atlas_01/A01_Sprite_004.png");
        paths.put("barangay_hall", "Sprites/Atlas_01/A01_Sprite_001.png");
        paths.put("city_level_complex", "Sprites/Atlas_01/A01_Sprite_003.png");
        paths.put("provincial_pavilion", "provincial_government_hall.png");
        paths.put("sari_sari_shop", "Sprites/Atlas_01/A01_Sprite_007.png");
        paths.put("fountain", "Sprites/Atlas_01/A01_Sprite_008.png");
        paths.put("church", "Sprites/Atlas_03/A03_Sprite_005.png");
        paths.put("house_red_roof", "Sprites/Atlas_03/A03_Sprite_006.png");
        paths.put("house_blue_roof", "Sprites/Atlas_01/A01_Sprite_016.png");
        paths.put("wooden_house", "Sprites/Atlas_03/A03_Sprite_012.png");
        paths.put("office_tower", "Sprites/Atlas_01/A01_Sprite_015.png");
        paths.put("tree_large", "Sprites/Atlas_03/A03_Sprite_060.png");
        paths.put("palm_tree", "Sprites/Atlas_03/A03_Sprite_062.png");
        paths.put("street_lamp", "Sprites/Atlas_01/A01_Sprite_077.png");
        paths.put("bench", "Sprites/Atlas_01/A01_Sprite_105.png");
        paths.put("road_straight", "Sprites/Atlas_02/A02_Sprite_015.png");
        paths.put("road_vertical", "Sprites/Atlas_02/A02_Sprite_017.png");
        paths.put("road_corner", "Sprites/Atlas_02/A02_Sprite_018.png");
        paths.put("road_intersection", "Sprites/Atlas_02/A02_Sprite_024.png");
        paths.put("grass_tile", "Sprites/Atlas_02/A02_Sprite_001.png");
        paths.put("grass_tile_alt", "Sprites/Atlas_02/A02_Sprite_009.png");
        paths.put("sidewalk_tile", "Sprites/Atlas_02/A02_Sprite_004.png");
        paths.put("dirt_tile", "Sprites/Atlas_02/A02_Sprite_003.png");
        paths.put("water_tile", "Sprites/Atlas_03/A03_Sprite_121.png");
        paths.put("bridge", "Sprites/Atlas_03/A03_Sprite_121.png");
        paths.put("waterfall", "Sprites/Atlas_03/A03_Sprite_121.png");
        paths.put("crop_rows", "Sprites/Atlas_03/A03_Sprite_033.png");
        paths.put("street_sign", "Sprites/Atlas_01/A01_Sprite_090.png");
        paths.put("fence", "Sprites/Atlas_01/A01_Sprite_094.png");
        paths.put("fence_short", "Sprites/Atlas_01/A01_Sprite_094.png");
        paths.put("fence_shorter", "Sprites/Atlas_01/A01_Sprite_094.png");
        paths.put("fence_birds_eye", "Sprites/Atlas_03/A03_Sprite_117.png");
        paths.put("flower_patch", "Sprites/Atlas_03/A03_Sprite_079.png");
        paths.put("flower_bush", "Sprites/Atlas_03/A03_Sprite_073.png");
        paths.put("flower_planter", "Sprites/Atlas_01/A01_Sprite_147.png");
        paths.put("flower_planter_round", "Sprites/Atlas_01/A01_Sprite_159.png");
        paths.put("bush_hedge", "Sprites/Atlas_03/A03_Sprite_068.png");
        paths.put("bush_round", "Sprites/Atlas_03/A03_Sprite_069.png");
        paths.put("bush_flowering", "Sprites/Atlas_03/A03_Sprite_070.png");
        paths.put("bush_small", "Sprites/Atlas_03/A03_Sprite_071.png");
        paths.put("bush_wide", "Sprites/Atlas_03/A03_Sprite_072.png");
        paths.put("bush_long", "Sprites/Atlas_03/A03_Sprite_074.png");
        paths.put("bush_low", "Sprites/Atlas_03/A03_Sprite_076.png");
        paths.put("bush_long_flowering", "Sprites/Atlas_03/A03_Sprite_077.png");
        ASSET_PATHS = Collections.unmodifiableMap(paths);
    }

    private final Map<String, BufferedImage> images = new LinkedHashMap<>();

    public MapAssetRegistry() {
        Map<String, BufferedImage> loadedByPath = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : ASSET_PATHS.entrySet()) {
            String resource = "provincial_pavilion".equals(entry.getKey())
                ? "provincial_government_hall"
                : "map/" + entry.getValue();
            BufferedImage image = loadedByPath.get(resource);
            if (image == null) {
                image = AssetLoader.loadImage(resource);
                if (image == null) {
                    System.err.println("[MapAssetRegistry] Missing assets/" + resource
                        + "; using a visible placeholder for '" + entry.getKey() + "'.");
                    image = createMissingAssetPlaceholder(entry.getKey());
                }
                loadedByPath.put(resource, image);
            }
            if ("fence_short".equals(entry.getKey()) || "fence_shorter".equals(entry.getKey())) {
                int targetWidth = "fence_short".equals(entry.getKey()) ? 50 : 32;
                int width = Math.min(targetWidth, image.getWidth());
                int x = Math.min(30, image.getWidth() - width);
                image = image.getSubimage(x, 0, width, image.getHeight());
            }
            images.put(entry.getKey(), image);
        }
    }

    public BufferedImage get(String key) {
        BufferedImage image = images.get(key);
        if (image == null) {
            throw new IllegalArgumentException("Unknown map asset key: " + key);
        }
        return image;
    }

    public Map<String, String> getAssetPaths() {
        return ASSET_PATHS;
    }

    private static BufferedImage createMissingAssetPlaceholder(String key) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(190, 30, 65));
        g.fillRect(0, 0, 64, 64);
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, 63, 63);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 9));
        g.drawString("MISSING", 4, 29);
        g.drawString(key.length() > 10 ? key.substring(0, 10) : key, 4, 43);
        g.dispose();
        return image;
    }
}
