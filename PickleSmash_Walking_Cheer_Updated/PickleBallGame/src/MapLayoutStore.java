import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class MapLayoutStore {
    public static final String FILE_NAME = "map-layout.csv";

    private MapLayoutStore() {
    }

    public static boolean isBlockingAsset(String assetKey) {
        if (assetKey == null) return false;
        if (assetKey.startsWith("fence") || assetKey.startsWith("tree_")
                || "palm_tree".equals(assetKey) || assetKey.startsWith("bush_")
                || assetKey.startsWith("flower_") || "crop_rows".equals(assetKey)) {
            return true;
        }
        switch (assetKey) {
            case "training_building":
            case "barangay_hall":
            case "city_level_complex":
            case "provincial_pavilion":
            case "sari_sari_shop":
            case "church":
            case "house_red_roof":
            case "house_blue_roof":
            case "wooden_house":
            case "office_tower":
            case "fountain":
            case "street_lamp":
            case "street_sign":
            case "bench":
                return true;
            default:
                return false;
        }
    }

    public static final class Entry {
        public String assetKey;
        public int x;
        public int y;
        public int width;
        public int height;
        public int sortY;
        public boolean blocking;
        public int rotationDegrees;

        public Entry(String assetKey, int x, int y, int width, int height, int sortY, boolean blocking) {
            this(assetKey, x, y, width, height, sortY, blocking, 0);
        }

        public Entry(String assetKey, int x, int y, int width, int height, int sortY,
                boolean blocking, int rotationDegrees) {
            this.assetKey = assetKey;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.sortY = sortY;
            this.blocking = blocking || isBlockingAsset(assetKey);
            this.rotationDegrees = ((rotationDegrees % 360) + 360) % 360;
        }
    }

    public static File getLayoutFile() {
        File assetsDirectory = AssetLoader.getAssetsDirectory();
        if (assetsDirectory != null) {
            return new File(new File(assetsDirectory, "map"), FILE_NAME);
        }

        File userDir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        File[] candidates = {
            new File(userDir, "assets/map/" + FILE_NAME),
            new File(userDir, "PickleBallGame/assets/map/" + FILE_NAME),
            new File(userDir, "PICKLE_SMASH/PickleSmash_Walking_Cheer_Updated/PickleBallGame/assets/map/" + FILE_NAME)
        };
        for (File candidate : candidates) {
            if (candidate.exists()) {
                return candidate;
            }
        }
        File assetsDir = new File(userDir, "assets/map");
        if (!assetsDir.isDirectory()) {
            assetsDir = new File(userDir, "PickleBallGame/assets/map");
        }
        if (!assetsDir.isDirectory()) {
            assetsDir = new File(userDir, "PICKLE_SMASH/PickleSmash_Walking_Cheer_Updated/PickleBallGame/assets/map");
        }
        return new File(assetsDir, FILE_NAME);
    }

    public static List<Entry> load() {
        File file = getLayoutFile();
        if (!file.isFile()) {
            return null;
        }
        List<Entry> entries = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] values = splitCsv(line);
                if (values.length >= 7) {
                    entries.add(new Entry(
                        values[0],
                        parseInt(values[1]), parseInt(values[2]),
                        parseInt(values[3]), parseInt(values[4]),
                        parseInt(values[5]), Boolean.parseBoolean(values[6]),
                        values.length >= 8 ? parseInt(values[7]) : 0
                    ));
                }
            }
            return entries;
        } catch (IOException | RuntimeException ex) {
            System.err.println("[MapLayoutStore] Could not load " + file + ": " + ex.getMessage());
            return null;
        }
    }

    public static void save(List<Entry> entries) throws IOException {
        File file = getLayoutFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create map layout directory: " + parent);
        }
        File temporaryFile = File.createTempFile(FILE_NAME, ".tmp", parent);
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile.toPath(), StandardCharsets.UTF_8)) {
                writer.write("# assetKey,x,y,width,height,sortY,blocking,rotationDegrees\n");
                for (Entry entry : entries) {
                    writer.write(String.join(",",
                        csv(entry.assetKey),
                        Integer.toString(entry.x),
                        Integer.toString(entry.y),
                        Integer.toString(entry.width),
                        Integer.toString(entry.height),
                        Integer.toString(entry.sortY),
                        Boolean.toString(entry.blocking),
                        Integer.toString(entry.rotationDegrees)));
                    writer.newLine();
                }
            }
            try {
                Files.move(temporaryFile.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile.toPath());
        }
    }

    public static List<Entry> createDefaultEntries() {
        List<Entry> entries = new ArrayList<>();
        add(entries, "training_building", 180, 58, 369, 171, 229, true);
        add(entries, "barangay_hall", 750, 58, 430, 193, 251, true);
        add(entries, "house_red_roof", 1325, 97, 122, 180, 277, true);
        add(entries, "house_blue_roof", 1490, 113, 122, 150, 263, true);
        add(entries, "office_tower", 1620, 105, 112, 150, 255, true);
        add(entries, "house_red_roof", 1740, 116, 105, 155, 271, true);
        add(entries, "sari_sari_shop", 180, 423, 156, 154, 577, true);
        add(entries, "house_red_roof", 357, 418, 110, 164, 582, true);
        add(entries, "house_blue_roof", 493, 434, 112, 138, 572, true);
        add(entries, "street_sign", 292, 515, 38, 42, 557, false);
        add(entries, "fountain", 745, 342, 246, 252, 594, true);
        add(entries, "bench", 770, 542, 62, 32, 574, false);
        add(entries, "bench", 904, 542, 62, 32, 574, false);
        add(entries, "street_lamp", 728, 410, 22, 72, 482, false);
        add(entries, "street_lamp", 981, 410, 22, 72, 482, false);
        add(entries, "city_level_complex", 1325, 340, 365, 236, 576, true);
        add(entries, "office_tower", 1710, 378, 104, 170, 548, true);
        add(entries, "house_red_roof", 180, 704, 122, 180, 884, true);
        add(entries, "house_blue_roof", 285, 735, 122, 150, 885, true);
        add(entries, "house_red_roof", 468, 710, 122, 180, 890, true);
        add(entries, "palm_tree", 558, 780, 74, 101, 881, true);
        add(entries, "church", 730, 692, 172, 189, 881, true);
        add(entries, "wooden_house", 1000, 716, 179, 155, 871, true);
        add(entries, "crop_rows", 988, 874, 216, 142, 1016, false);
        add(entries, "provincial_pavilion", 1310, 724, 485, 209, 933, true);
        add(entries, "bench", 1390, 945, 66, 34, 976, false);
        add(entries, "street_lamp", 1764, 812, 22, 72, 884, false);
        add(entries, "tree_large", 1083, 447, 74, 108, 555, true);
        add(entries, "tree_large", 521, 806, 77, 112, 918, true);
        add(entries, "tree_large", 1132, 810, 76, 110, 920, true);
        add(entries, "palm_tree", 1779, 882, 72, 98, 980, true);
        return entries;
    }

    private static void add(List<Entry> entries, String key, int x, int y, int w, int h, int sortY, boolean blocking) {
        entries.add(new Entry(key, x, y, w, h, sortY, blocking));
    }

    private static int parseInt(String value) {
        return Integer.parseInt(value.trim());
    }

    private static String csv(String value) {
        return value == null ? "" : value.replace(",", " ");
    }

    private static String[] splitCsv(String line) {
        return line.split(",", -1);
    }
}