import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class MapEditor extends JFrame {
    private static final int MAX_UNDO_STEPS = 100;
    private static final int VIEW_WIDTH = 1280;
    private static final int VIEW_HEIGHT = 720;
    private static final int WORLD_WIDTH = TileMap.WORLD_WIDTH;
    private static final int WORLD_HEIGHT = TileMap.WORLD_HEIGHT;
    private static final int RESIZE_HANDLE_SIZE = 24;
    private static final double MIN_CANVAS_ZOOM = 0.25;
    private static final double MAX_CANVAS_ZOOM = 5.0;

    private final MapAssetRegistry assets = new MapAssetRegistry();
    private final TileMap tileMap = new TileMap();
    private final List<MapLayoutStore.Entry> entries = new ArrayList<>();
    private final Deque<EditorState> undoHistory = new ArrayDeque<>();
    private BufferedImage baseLayer;
    private JList<String> assetList;
    private MapCanvas canvas;
    private AssetPreview assetPreview;
    private JLabel assetPreviewInfo;
    private JPanel assetSidebar;
    private JSplitPane editorSplit;
    private JButton toggleAssetsButton;
    private int assetSidebarWidth = 260;
    private JSpinner xSpinner;
    private JSpinner ySpinner;
    private JSpinner widthSpinner;
    private JSpinner heightSpinner;
    private JSpinner sortSpinner;
    private JCheckBox blockingCheck;
    private JButton resizeButton;
    private int selectedIndex = -1;
    private Point dragOffset;
    private Point resizeStart;
    private int resizeStartWidth;
    private int resizeStartHeight;
    private boolean resizeMode;
    private boolean resizingSelected;
    private Point lastPanPoint;
    private boolean syncingProperties;
    private boolean gestureUndoRecorded;
    private JButton undoButton;
    private Point lastCanvasWorldPosition = new Point(WORLD_WIDTH / 2, WORLD_HEIGHT / 2);

    private static final class EditorState {
        final List<MapLayoutStore.Entry> entries;
        final int selectedIndex;

        EditorState(List<MapLayoutStore.Entry> entries, int selectedIndex) {
            this.entries = entries;
            this.selectedIndex = selectedIndex;
        }
    }

    public MapEditor() {
        setTitle("Pickle Smash Map Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(VIEW_WIDTH + 300, VIEW_HEIGHT + 80);
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null);

        entries.clear();
        List<MapLayoutStore.Entry> loaded = MapLayoutStore.load();
        if (loaded != null) {
            entries.addAll(loaded);
        } else {
            entries.addAll(MapLayoutStore.createDefaultEntries());
        }

        SwingWorker<BufferedImage, Void> baker = new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                BufferedImage image = new BufferedImage(WORLD_WIDTH, WORLD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                tileMap.draw(g, assets);
                g.dispose();
                return image;
            }

            @Override
            protected void done() {
                try {
                    baseLayer = get();
                    canvas.repaint();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MapEditor.this, "Could not bake map: " + ex.getMessage());
                }
            }
        };
        baker.execute();

        buildUi();
    }

    private void buildUi() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton saveButton = new JButton("Save");
        JButton addButton = new JButton("Add Asset");
        JButton deleteButton = new JButton("Remove");
        JButton duplicateButton = new JButton("Duplicate");
        JButton resetButton = new JButton("Reset");
        JButton exportButton = new JButton("Export PNG");
        resizeButton = new JButton();
        undoButton = new JButton("Undo (Ctrl+Z)");
        toggleAssetsButton = new JButton("Hide Assets");
        toolbar.add(saveButton);
        toolbar.add(addButton);
        toolbar.add(deleteButton);
        toolbar.add(duplicateButton);
        toolbar.add(resetButton);
        toolbar.add(resizeButton);
        toolbar.add(undoButton);
        toolbar.add(exportButton);
        toolbar.add(toggleAssetsButton);
        undoButton.setEnabled(false);
        updateResizeButton();
        add(toolbar, BorderLayout.NORTH);

        canvas = new MapCanvas();
        canvas.setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));

        assetList = new JList<>(assets.getAssetPaths().keySet().toArray(new String[0]));
        assetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assetList.setSelectedIndex(0);
        assetList.setCellRenderer(new AssetCellRenderer());
        assetList.setFixedCellHeight(52);
        JScrollPane assetScroll = new JScrollPane(assetList);
        assetScroll.setPreferredSize(new Dimension(245, 330));

        JPanel previewPanel = new JPanel(new BorderLayout(4, 4));
        previewPanel.setBorder(BorderFactory.createTitledBorder("Asset preview"));
        previewPanel.setPreferredSize(new Dimension(245, 190));
        assetPreview = new AssetPreview();
        assetPreviewInfo = new JLabel(" ");
        assetPreviewInfo.setHorizontalAlignment(JLabel.CENTER);
        previewPanel.add(assetPreview, BorderLayout.CENTER);
        previewPanel.add(assetPreviewInfo, BorderLayout.SOUTH);

        JPanel properties = new JPanel();
        properties.setBorder(BorderFactory.createTitledBorder("Selected asset"));
        properties.setPreferredSize(new Dimension(245, 225));
        xSpinner = addSpinner(properties, "X", 0, -5000, 5000);
        ySpinner = addSpinner(properties, "Y", 0, -5000, 5000);
        widthSpinner = addSpinner(properties, "Width", 100, 1, 5000);
        heightSpinner = addSpinner(properties, "Height", 100, 1, 5000);
        sortSpinner = addSpinner(properties, "Layer Y", 100, -5000, 5000);
        blockingCheck = new JCheckBox("Blocks player");

        assetSidebar = new JPanel(new BorderLayout());
        JPanel assetChooser = new JPanel(new BorderLayout(4, 4));
        assetChooser.add(assetScroll, BorderLayout.CENTER);
        assetChooser.add(previewPanel, BorderLayout.SOUTH);
        assetSidebar.add(assetChooser, BorderLayout.CENTER);
        assetSidebar.add(properties, BorderLayout.SOUTH);

        editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, assetSidebar, new JScrollPane(canvas));
        editorSplit.setDividerLocation(assetSidebarWidth);
        editorSplit.setResizeWeight(0);
        editorSplit.setOneTouchExpandable(true);
        assetSidebar.setMinimumSize(new Dimension(180, 0));
        add(editorSplit, BorderLayout.CENTER);
        editorSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            if (editorSplit.getLeftComponent() == assetSidebar && editorSplit.getDividerLocation() > 0) {
                assetSidebarWidth = editorSplit.getDividerLocation();
            }
        });

        saveButton.addActionListener(e -> saveLayout());
        addButton.addActionListener(e -> addAsset());
        deleteButton.addActionListener(e -> removeSelected());
        duplicateButton.addActionListener(e -> duplicateSelected());
        resetButton.addActionListener(e -> resetLayout());
        resizeButton.addActionListener(e -> setResizeMode(!resizeMode));
        undoButton.addActionListener(e -> undo());
        exportButton.addActionListener(e -> exportPng());
        toggleAssetsButton.addActionListener(e -> toggleAssetSidebar());

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo-map-edit");
        getRootPane().getActionMap().put("undo-map-edit", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                undo();
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("pressed SPACE"), "start-map-pan");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("released SPACE"), "stop-map-pan");
        getRootPane().getActionMap().put("start-map-pan", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                canvas.setPanMode(true);
            }
        });
        getRootPane().getActionMap().put("stop-map-pan", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                canvas.setPanMode(false);
            }
        });

        wirePropertySpinners();
        assetList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateAssetPreview();
            }
        });
        updateAssetPreview();
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestCanvasFocus();
                if (canvas.isPanMode() && SwingUtilities.isLeftMouseButton(event)) {
                    lastPanPoint = event.getPoint();
                    canvas.setPanning(true);
                    return;
                }
                Point worldPoint = canvas.toWorldPoint(event.getPoint());
                rememberCanvasPosition(worldPoint);
                gestureUndoRecorded = false;
                if (resizeMode && selectedIndex >= 0 && selectedIndex < entries.size()
                        && hitResizeHandle(entries.get(selectedIndex), worldPoint)) {
                    MapLayoutStore.Entry entry = entries.get(selectedIndex);
                    resizingSelected = true;
                    resizeStart = worldPoint;
                    resizeStartWidth = entry.width;
                    resizeStartHeight = entry.height;
                    dragOffset = null;
                    return;
                }
                resizingSelected = false;
                selectedIndex = hitTest(worldPoint.x, worldPoint.y);
                if (selectedIndex >= 0) {
                    MapLayoutStore.Entry entry = entries.get(selectedIndex);
                    dragOffset = new Point(worldPoint.x - entry.x, worldPoint.y - entry.y);
                    syncPropertySpinners(entry);
                } else {
                    clearPropertySelection();
                }
                canvas.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                lastPanPoint = null;
                canvas.setPanning(false);
                dragOffset = null;
                resizeStart = null;
                resizingSelected = false;
            }
        });
        canvas.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (lastPanPoint != null) {
                    Point current = event.getPoint();
                    canvas.panBy(current.x - lastPanPoint.x, current.y - lastPanPoint.y);
                    lastPanPoint = current;
                    rememberCanvasPosition(canvas.toWorldPoint(current));
                    return;
                }
                Point worldPoint = canvas.toWorldPoint(event.getPoint());
                rememberCanvasPosition(worldPoint);
                if (resizingSelected && selectedIndex >= 0 && resizeStart != null) {
                    resizeSelectedAsset(worldPoint);
                    return;
                }
                if (selectedIndex < 0 || dragOffset == null) return;
                MapLayoutStore.Entry entry = entries.get(selectedIndex);
                int nextX = snap(worldPoint.x - dragOffset.x);
                int nextY = snap(worldPoint.y - dragOffset.y);
                if (entry.x == nextX && entry.y == nextY) return;
                recordGestureUndo();
                entry.x = nextX;
                entry.y = nextY;
                syncPropertySpinners(entry);
                canvas.repaint();
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                rememberCanvasPosition(canvas.toWorldPoint(event.getPoint()));
            }
        });
        canvas.addMouseWheelListener(event -> {
            if (!event.isControlDown()) return;
            canvas.zoomAt(event.getPoint(), Math.pow(1.15, -event.getWheelRotation()));
            rememberCanvasPosition(canvas.toWorldPoint(event.getPoint()));
            event.consume();
        });
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_T) {
                    setResizeMode(!resizeMode);
                    return;
                }
                if (event.isControlDown() && event.getKeyCode() == KeyEvent.VK_D) {
                    duplicateSelected();
                    return;
                }
                if (selectedIndex < 0) return;
                MapLayoutStore.Entry entry = entries.get(selectedIndex);
                int step = event.isShiftDown() ? 10 : 1;
                if (event.getKeyCode() == KeyEvent.VK_DELETE || event.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    removeSelected();
                    return;
                }
                boolean moved = event.getKeyCode() == KeyEvent.VK_LEFT
                    || event.getKeyCode() == KeyEvent.VK_RIGHT
                    || event.getKeyCode() == KeyEvent.VK_UP
                    || event.getKeyCode() == KeyEvent.VK_DOWN;
                if (moved) {
                    pushUndoState();
                    if (event.getKeyCode() == KeyEvent.VK_LEFT) entry.x -= step;
                    if (event.getKeyCode() == KeyEvent.VK_RIGHT) entry.x += step;
                    if (event.getKeyCode() == KeyEvent.VK_UP) entry.y -= step;
                    if (event.getKeyCode() == KeyEvent.VK_DOWN) entry.y += step;
                }
                syncPropertySpinners(entry);
                canvas.repaint();
            }
        });
    }

    private JSpinner addSpinner(JPanel panel, String label, int value, int minimum, int maximum) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        row.add(new JLabel(label), BorderLayout.WEST);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, minimum, maximum, 1));
        spinner.setPreferredSize(new Dimension(110, 24));
        row.add(spinner, BorderLayout.EAST);
        panel.add(row);
        return spinner;
    }

    private void wirePropertySpinners() {
        xSpinner.addChangeListener(e -> updateSelectedValue());
        ySpinner.addChangeListener(e -> updateSelectedValue());
        widthSpinner.addChangeListener(e -> updateSelectedValue());
        heightSpinner.addChangeListener(e -> updateSelectedValue());
        sortSpinner.addChangeListener(e -> updateSelectedValue());
        blockingCheck.addActionListener(e -> updateSelectedValue());
        blockingCheck.setSelected(false);
    }

    private void pushUndoState() {
        undoHistory.addLast(new EditorState(copyEntries(entries), selectedIndex));
        if (undoHistory.size() > MAX_UNDO_STEPS) {
            undoHistory.removeFirst();
        }
        undoButton.setEnabled(true);
    }

    private void recordGestureUndo() {
        if (!gestureUndoRecorded) {
            pushUndoState();
            gestureUndoRecorded = true;
        }
    }

    private void undo() {
        if (undoHistory.isEmpty()) return;
        EditorState state = undoHistory.removeLast();
        entries.clear();
        entries.addAll(copyEntries(state.entries));
        selectedIndex = state.selectedIndex >= 0 && state.selectedIndex < entries.size()
            ? state.selectedIndex : -1;
        undoButton.setEnabled(!undoHistory.isEmpty());
        if (selectedIndex >= 0) {
            syncPropertySpinners(entries.get(selectedIndex));
        } else {
            clearPropertySelection();
        }
        canvas.repaint();
    }

    private List<MapLayoutStore.Entry> copyEntries(List<MapLayoutStore.Entry> source) {
        List<MapLayoutStore.Entry> copies = new ArrayList<>(source.size());
        for (MapLayoutStore.Entry entry : source) {
            copies.add(new MapLayoutStore.Entry(
                entry.assetKey, entry.x, entry.y, entry.width, entry.height,
                entry.sortY, entry.blocking
            ));
        }
        return copies;
    }

    private void setResizeMode(boolean enabled) {
        resizeMode = enabled;
        if (resizeButton != null) {
            updateResizeButton();
        }
        canvas.repaint();
    }

    private void updateResizeButton() {
        resizeButton.setText(resizeMode ? "Resize (T): On" : "Resize (T): Off");
        resizeButton.setToolTipText(resizeMode
            ? "Drag the selected asset's yellow corner handle to resize proportionally."
            : "Press T or click to enable proportional drag-resizing.");
    }

    private boolean hitResizeHandle(MapLayoutStore.Entry entry, Point point) {
        int halfSize = RESIZE_HANDLE_SIZE / 2;
        return new Rectangle(
            entry.x + entry.width - halfSize,
            entry.y + entry.height - halfSize,
            RESIZE_HANDLE_SIZE,
            RESIZE_HANDLE_SIZE
        ).contains(point);
    }

    private void resizeSelectedAsset(Point point) {
        MapLayoutStore.Entry entry = entries.get(selectedIndex);
        double width = resizeStartWidth;
        double height = resizeStartHeight;
        double deltaX = point.x - resizeStart.x;
        double deltaY = point.y - resizeStart.y;
        double denominator = width * width + height * height;
        double scale = 1.0 + ((deltaX * width) + (deltaY * height)) / denominator;
        double minimumScale = Math.max(8.0 / width, 8.0 / height);
        scale = Math.max(minimumScale, Math.min(5000.0 / Math.max(width, height), scale));
        int nextWidth = Math.max(8, (int) Math.round(width * scale));
        int nextHeight = Math.max(8, (int) Math.round(height * scale));
        if (entry.width == nextWidth && entry.height == nextHeight) return;
        recordGestureUndo();
        entry.width = nextWidth;
        entry.height = nextHeight;
        syncPropertySpinners(entry);
        canvas.repaint();
    }

    private void updateSelectedValue() {
        if (syncingProperties || selectedIndex < 0 || selectedIndex >= entries.size()) return;
        MapLayoutStore.Entry entry = entries.get(selectedIndex);
        int x = ((Number) xSpinner.getValue()).intValue();
        int y = ((Number) ySpinner.getValue()).intValue();
        int width = ((Number) widthSpinner.getValue()).intValue();
        int height = ((Number) heightSpinner.getValue()).intValue();
        int sortY = ((Number) sortSpinner.getValue()).intValue();
        boolean blocking = blockingCheck.isSelected();
        if (entry.x == x && entry.y == y && entry.width == width && entry.height == height
                && entry.sortY == sortY && entry.blocking == blocking) {
            return;
        }
        pushUndoState();
        entry.x = x;
        entry.y = y;
        entry.width = width;
        entry.height = height;
        entry.sortY = sortY;
        entry.blocking = blocking;
        canvas.repaint();
    }

    private void syncPropertySpinners(MapLayoutStore.Entry entry) {
        syncingProperties = true;
        try {
            xSpinner.setValue(entry.x);
            ySpinner.setValue(entry.y);
            widthSpinner.setValue(entry.width);
            heightSpinner.setValue(entry.height);
            sortSpinner.setValue(entry.sortY);
            blockingCheck.setSelected(entry.blocking);
        } finally {
            syncingProperties = false;
        }
    }

    private void clearPropertySelection() {
        blockingCheck.setSelected(false);
    }

    private int hitTest(int screenX, int screenY) {
        List<MapLayoutStore.Entry> ordered = getDrawOrder();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            MapLayoutStore.Entry entry = ordered.get(i);
            if (new Rectangle(entry.x, entry.y, entry.width, entry.height).contains(screenX, screenY)) {
                return entries.indexOf(entry);
            }
        }
        return -1;
    }

    private List<MapLayoutStore.Entry> getDrawOrder() {
        List<MapLayoutStore.Entry> ordered = new ArrayList<>(entries);
        ordered.sort(java.util.Comparator.comparingInt(entry -> entry.sortY));
        return ordered;
    }

    private int snap(int value) {
        return Math.round(value / 8f) * 8;
    }

    private void rememberCanvasPosition(Point point) {
        lastCanvasWorldPosition = new Point(
            Math.max(0, Math.min(WORLD_WIDTH, point.x)),
            Math.max(0, Math.min(WORLD_HEIGHT, point.y))
        );
    }

    private void addAsset() {
        String key = assetList.getSelectedValue();
        if (key == null) return;
        BufferedImage image = assets.get(key);
        int width = Math.min(180, Math.max(32, image.getWidth()));
        int height = Math.min(180, Math.max(32, image.getHeight()));
        int x = Math.max(0, Math.min(WORLD_WIDTH - width, lastCanvasWorldPosition.x - width / 2));
        int y = Math.max(0, Math.min(WORLD_HEIGHT - height, lastCanvasWorldPosition.y - height / 2));
        MapLayoutStore.Entry entry = new MapLayoutStore.Entry(key, x, y, width, height, y + height, false);
        pushUndoState();
        entries.add(entry);
        selectedIndex = entries.size() - 1;
        syncPropertySpinners(entry);
        canvas.repaint();
    }

    private void updateAssetPreview() {
        String key = assetList.getSelectedValue();
        if (key == null) {
            assetPreview.setImage(null);
            assetPreviewInfo.setText("Select an asset to preview");
            return;
        }
        BufferedImage image = assets.get(key);
        assetPreview.setImage(image);
        assetPreviewInfo.setText(key + "  -  " + image.getWidth() + " x " + image.getHeight());
    }

    private void toggleAssetSidebar() {
        if (editorSplit.getLeftComponent() == assetSidebar) {
            int currentWidth = editorSplit.getDividerLocation();
            if (currentWidth > 0) {
                assetSidebarWidth = currentWidth;
            }
            editorSplit.setLeftComponent(null);
            toggleAssetsButton.setText("Show Assets");
            toggleAssetsButton.setToolTipText("Show the asset picker and properties panel.");
        } else {
            editorSplit.setLeftComponent(assetSidebar);
            editorSplit.setDividerLocation(assetSidebarWidth);
            toggleAssetsButton.setText("Hide Assets");
            toggleAssetsButton.setToolTipText("Hide the asset picker to expand the map view.");
        }
        editorSplit.revalidate();
        editorSplit.repaint();
    }

    private void removeSelected() {
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            pushUndoState();
            entries.remove(selectedIndex);
            selectedIndex = -1;
            clearPropertySelection();
            canvas.repaint();
        }
    }

    private void duplicateSelected() {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) return;
        MapLayoutStore.Entry source = entries.get(selectedIndex);
        MapLayoutStore.Entry copy = new MapLayoutStore.Entry(
            source.assetKey, source.x + 16, source.y + 16, source.width,
            source.height, source.sortY + 16, source.blocking
        );
        pushUndoState();
        entries.add(copy);
        selectedIndex = entries.size() - 1;
        syncPropertySpinners(copy);
        canvas.repaint();
    }

    private void resetLayout() {
        int choice = JOptionPane.showConfirmDialog(this,
            "Reset all edits to the current default map?", "Reset map",
            JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;
        pushUndoState();
        entries.clear();
        entries.addAll(MapLayoutStore.createDefaultEntries());
        selectedIndex = -1;
        canvas.repaint();
    }

    private void saveLayout() {
        try {
            MapLayoutStore.save(entries);
            JOptionPane.showMessageDialog(this,
                "Saved to:\n" + MapLayoutStore.getLayoutFile().getAbsolutePath()
                    + "\nThe open main map will refresh automatically within a second.",
                "Map saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save map: " + ex.getMessage(),
                "Save failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportPng() {
        BufferedImage image = new BufferedImage(WORLD_WIDTH, WORLD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        drawCanvas(g);
        g.dispose();
        java.io.File file = new java.io.File(System.getProperty("user.dir"), "map-preview.png");
        try {
            javax.imageio.ImageIO.write(image, "png", file);
            JOptionPane.showMessageDialog(this, "Exported " + file.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not export PNG: " + ex.getMessage());
        }
    }

    private void requestCanvasFocus() {
        canvas.requestFocusInWindow();
    }

    private void drawCanvas(Graphics2D g) {
        if (baseLayer != null) {
            g.drawImage(baseLayer, 0, 0, null);
        }
        List<MapLayoutStore.Entry> ordered = getDrawOrder();
        for (int i = 0; i < ordered.size(); i++) {
            MapLayoutStore.Entry entry = ordered.get(i);
            BufferedImage image = assets.get(entry.assetKey);
            g.drawImage(image, entry.x, entry.y, entry.width, entry.height, null);
            int originalIndex = entries.indexOf(entry);
            if (originalIndex == selectedIndex) {
                g.setColor(new Color(250, 204, 21));
                g.setStroke(new java.awt.BasicStroke(3f));
                g.drawRect(entry.x, entry.y, entry.width, entry.height);
                if (resizeMode) {
                    int handleSize = RESIZE_HANDLE_SIZE;
                    g.fillRect(
                        entry.x + entry.width - handleSize / 2,
                        entry.y + entry.height - handleSize / 2,
                        handleSize,
                        handleSize
                    );
                    g.setColor(Color.BLACK);
                    g.drawRect(
                        entry.x + entry.width - handleSize / 2,
                        entry.y + entry.height - handleSize / 2,
                        handleSize,
                        handleSize
                    );
                }
            }
        }
        g.setColor(new Color(255, 255, 255, 90));
        for (int x = 0; x <= WORLD_WIDTH; x += TileMap.TILE_SIZE) g.drawLine(x, 0, x, WORLD_HEIGHT);
        for (int y = 0; y <= WORLD_HEIGHT; y += TileMap.TILE_SIZE) g.drawLine(0, y, WORLD_WIDTH, y);
    }

    private class MapCanvas extends JPanel {
        private double zoom = 1.0;
        private double panX;
        private double panY;
        private boolean panMode;
        private boolean panning;

        MapCanvas() {
            setFocusable(true);
            setBackground(new Color(12, 20, 36));
        }

        private double getScale() {
            return Math.min(getWidth() / (double) WORLD_WIDTH, getHeight() / (double) WORLD_HEIGHT) * zoom;
        }

        private double getOffsetX(double scale) {
            return (getWidth() - WORLD_WIDTH * scale) / 2.0 + panX;
        }

        private double getOffsetY(double scale) {
            return (getHeight() - WORLD_HEIGHT * scale) / 2.0 + panY;
        }

        void zoomAt(Point screenPoint, double scaleFactor) {
            Point worldPoint = toWorldPoint(screenPoint);
            zoom = Math.max(MIN_CANVAS_ZOOM, Math.min(MAX_CANVAS_ZOOM, zoom * scaleFactor));
            double scale = getScale();
            panX = screenPoint.x - (getWidth() - WORLD_WIDTH * scale) / 2.0 - worldPoint.x * scale;
            panY = screenPoint.y - (getHeight() - WORLD_HEIGHT * scale) / 2.0 - worldPoint.y * scale;
            repaint();
        }

        boolean isPanMode() {
            return panMode;
        }

        void setPanMode(boolean panMode) {
            this.panMode = panMode;
            updatePanCursor();
        }

        void setPanning(boolean panning) {
            this.panning = panning;
            updatePanCursor();
        }

        void panBy(int deltaX, int deltaY) {
            panX += deltaX;
            panY += deltaY;
            repaint();
        }

        private void updatePanCursor() {
            setCursor(Cursor.getPredefinedCursor(
                panning ? Cursor.MOVE_CURSOR : panMode ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR
            ));
        }

        Point toWorldPoint(Point point) {
            double scale = getScale();
            if (scale <= 0) return point;
            return new Point(
                (int) Math.round((point.x - getOffsetX(scale)) / scale),
                (int) Math.round((point.y - getOffsetY(scale)) / scale)
            );
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            double scale = getScale();
            g.translate(getOffsetX(scale), getOffsetY(scale));
            g.scale(scale, scale);
            drawCanvas(g);
            g.dispose();
        }
    }

    private class AssetCellRenderer extends DefaultListCellRenderer {
        private final java.util.Map<String, ImageIcon> thumbnails = new java.util.HashMap<>();

        @Override
        public java.awt.Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            String key = value == null ? "" : value.toString();
            label.setText(key);
            label.setIcon(thumbnails.computeIfAbsent(key, assetKey -> {
                BufferedImage image = assets.get(assetKey);
                BufferedImage thumbnail = new BufferedImage(44, 44, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = thumbnail.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                double scale = Math.min(42.0 / image.getWidth(), 42.0 / image.getHeight());
                int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
                g.drawImage(image, (44 - width) / 2, (44 - height) / 2, width, height, null);
                g.dispose();
                return new ImageIcon(thumbnail);
            }));
            label.setIconTextGap(8);
            label.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
            return label;
        }
    }

    private static class AssetPreview extends JPanel {
        private static final int CHECKER_SIZE = 12;
        private BufferedImage image;

        AssetPreview() {
            setBackground(new Color(42, 48, 58));
            setPreferredSize(new Dimension(220, 135));
        }

        void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            for (int y = 0; y < getHeight(); y += CHECKER_SIZE) {
                for (int x = 0; x < getWidth(); x += CHECKER_SIZE) {
                    boolean alternate = ((x / CHECKER_SIZE) + (y / CHECKER_SIZE)) % 2 == 0;
                    g.setColor(alternate ? new Color(65, 72, 82) : new Color(47, 53, 63));
                    g.fillRect(x, y, CHECKER_SIZE, CHECKER_SIZE);
                }
            }
            if (image != null) {
                double scale = Math.min(
                    (getWidth() - 20.0) / image.getWidth(),
                    (getHeight() - 20.0) / image.getHeight()
                );
                int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
                int x = (getWidth() - width) / 2;
                int y = (getHeight() - height) / 2;
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(image, x, y, width, height, null);
            }
            g.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MapEditor().setVisible(true));
    }
}
