import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import javax.imageio.ImageIO;

public final class AssetLoader {

    private static File cachedAssetsDir = null;
    private static boolean searchedAssetsDir = false;

    private AssetLoader() {}

    /**
     * Automatically locates the 'assets' directory across nested VS Code folders,
     * compiled class output paths, and classpath resources.
     */
    public static BufferedImage loadImage(String baseName) {
        String[] extensions = {".png", ".jpg", ".jpeg", ""};
        File assetsDir = resolveAssetsDirectory();

        // 1. Load from discovered assets directory
        if (assetsDir != null) {
            for (String ext : extensions) {
                File candidate = new File(assetsDir, baseName + ext);
                if (candidate.exists() && candidate.isFile()) {
                    try {
                        BufferedImage img = ImageIO.read(candidate);
                        if (img != null) {
                            System.out.println("[AssetLoader] Loaded: " + candidate.getAbsolutePath());
                            return img;
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        // 2. Fallback: check classpath resources (/assets/<name>.png or /<name>.png)
        for (String prefix : new String[]{"/assets/", "/"}) {
            for (String ext : extensions) {
                try (InputStream is = AssetLoader.class.getResourceAsStream(prefix + baseName + ext)) {
                    if (is != null) {
                        BufferedImage img = ImageIO.read(is);
                        if (img != null) {
                            System.out.println("[AssetLoader] Loaded from classpath: " + prefix + baseName + ext);
                            return img;
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        }

        System.err.println("[AssetLoader] Missing asset '" + baseName + "' (working dir: " + System.getProperty("user.dir") + ")");
        return null;
    }

    private static File resolveAssetsDirectory() {
        if (searchedAssetsDir) {
            return cachedAssetsDir;
        }
        searchedAssetsDir = true;

        Set<File> searchRoots = new LinkedHashSet<>();

        // Working directory & parent directories
        File userDir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        searchRoots.add(userDir);
        if (userDir.getParentFile() != null) {
            searchRoots.add(userDir.getParentFile());
        }

        // Compiled .class location (e.g., .../PickleBallGame/bin or .../PickleBallGame/src)
        try {
            File codeLocation = new File(
                AssetLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getAbsoluteFile();
            File codeDir = codeLocation.isDirectory() ? codeLocation : codeLocation.getParentFile();
            if (codeDir != null) {
                searchRoots.add(codeDir);
                if (codeDir.getParentFile() != null) {
                    searchRoots.add(codeDir.getParentFile());
                }
            }
        } catch (URISyntaxException | SecurityException ignored) {
        }

        // Prefer assets in the working directory or its nested project folders.
        String[] relativeCandidates = {
            "assets",
            "PickleBallGame/assets",
            "PickleBallGame/PickleBallGame/assets"
        };
        for (String rel : relativeCandidates) {
            File dir = new File(userDir, rel);
            if (dir.exists() && dir.isDirectory()) {
                cachedAssetsDir = dir;
                return cachedAssetsDir;
            }
        }

        // Search the active working directory before considering sibling projects.
        Queue<File> queue = new ArrayDeque<>();
        Queue<Integer> depths = new ArrayDeque<>();
        queue.add(userDir);
        depths.add(0);

        while (!queue.isEmpty()) {
            File current = queue.poll();
            int depth = depths.poll();
            if ("assets".equalsIgnoreCase(current.getName()) && current.isDirectory()) {
                cachedAssetsDir = current;
                return cachedAssetsDir;
            }
            if (depth < 3) {
                File[] children = current.listFiles(File::isDirectory);
                if (children != null) {
                    for (File child : children) {
                        if (!child.getName().startsWith(".") && !"bin".equals(child.getName())) {
                            queue.add(child);
                            depths.add(depth + 1);
                        }
                    }
                }
            }
        }

        for (File root : searchRoots) {
            if (root.equals(userDir)) {
                continue;
            }
            for (String rel : relativeCandidates) {
                File dir = new File(root, rel);
                if (dir.exists() && dir.isDirectory()) {
                    cachedAssetsDir = dir;
                    return cachedAssetsDir;
                }
            }
        }

        return null;
    }

    public static File getAssetsDirectory() {
        return resolveAssetsDirectory();
    }

    /**
     * Converts near-white backdrop pixels to transparent ARGB so sprites blend cleanly over the court.
     */
    public static BufferedImage stripWhiteBackground(BufferedImage raw, int tolerance) {
        if (raw == null) return null;
        int w = raw.getWidth();
        int h = raw.getHeight();
        BufferedImage clean = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int threshold = 255 - tolerance;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = raw.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (r >= threshold && g >= threshold && b >= threshold) {
                    clean.setRGB(x, y, 0x00000000);
                } else {
                    clean.setRGB(x, y, argb | 0xFF000000);
                }
            }
        }
        return clean;
    }

    /**
     * Uses horizontal & vertical pixel projection histograms (DSA) to automatically slice
     * a sprite sheet into tightly bounded frames regardless of image resolution.
     */
    public static BufferedImage[][] extractSpriteGrid(BufferedImage transparentSheet, int expectedRows, int expectedCols) {
        if (transparentSheet == null) return null;

        int w = transparentSheet.getWidth();
        int h = transparentSheet.getHeight();

        int[] rowCounts = new int[h];
        for (int y = 0; y < h; y++) {
            int count = 0;
            for (int x = 0; x < w; x++) {
                if (((transparentSheet.getRGB(x, y) >>> 24) & 0xFF) > 20) count++;
            }
            rowCounts[y] = count;
        }
        List<int[]> rowBands = findContiguousBands(rowCounts, 4, 12, 16);
        if (rowBands.size() < expectedRows) return null;

        BufferedImage[][] grid = new BufferedImage[expectedRows][expectedCols];

        for (int r = 0; r < expectedRows; r++) {
            int y0 = rowBands.get(r)[0];
            int y1 = rowBands.get(r)[1];

            int[] colCounts = new int[w];
            for (int x = 0; x < w; x++) {
                int count = 0;
                for (int y = y0; y <= y1; y++) {
                    if (((transparentSheet.getRGB(x, y) >>> 24) & 0xFF) > 20) count++;
                }
                colCounts[x] = count;
            }

            List<int[]> colBands = findContiguousBands(colCounts, 3, 10, 20);
            if (colBands.size() < expectedCols) return null;

            for (int c = 0; c < expectedCols; c++) {
                int x0 = colBands.get(c)[0];
                int x1 = colBands.get(c)[1];
                grid[r][c] = transparentSheet.getSubimage(x0, y0, (x1 - x0) + 1, (y1 - y0) + 1);
            }
        }
        return grid;
    }

    /**
     * Dynamically swaps red jersey pixels on the player sprite when changing Avatar presets.
     */
    public static BufferedImage tintRedJersey(BufferedImage source, Color targetColor) {
        if (source == null || targetColor == null) return source;
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage tinted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) continue;

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                if (r > 130 && g < 85 && b < 85) {
                    float shade = r / 220f;
                    int nr = Math.min(255, Math.round(targetColor.getRed() * shade));
                    int ng = Math.min(255, Math.round(targetColor.getGreen() * shade));
                    int nb = Math.min(255, Math.round(targetColor.getBlue() * shade));
                    tinted.setRGB(x, y, (alpha << 24) | (nr << 16) | (ng << 8) | nb);
                } else {
                    tinted.setRGB(x, y, argb);
                }
            }
        }
        return tinted;
    }

    private static List<int[]> findContiguousBands(int[] histogram, int minPixels, int minSpan, int mergeGap) {
        List<int[]> rawBands = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] >= minPixels) {
                if (start == -1) start = i;
            } else if (start != -1) {
                rawBands.add(new int[]{start, i - 1});
                start = -1;
            }
        }
        if (start != -1) {
            rawBands.add(new int[]{start, histogram.length - 1});
        }

        List<int[]> merged = new ArrayList<>();
        for (int[] band : rawBands) {
            if (!merged.isEmpty() && (band[0] - merged.get(merged.size() - 1)[1]) <= mergeGap) {
                merged.get(merged.size() - 1)[1] = band[1];
            } else {
                merged.add(new int[]{band[0], band[1]});
            }
        }

        List<int[]> filtered = new ArrayList<>();
        for (int[] band : merged) {
            if ((band[1] - band[0] + 1) >= minSpan) {
                filtered.add(band);
            }
        }
        return filtered;
    }
}