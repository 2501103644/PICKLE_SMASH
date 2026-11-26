import javax.swing.SwingUtilities;
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            if (args.length > 0 && ("--editor".equalsIgnoreCase(args[0]) || "--map-editor".equalsIgnoreCase(args[0]))) {
                new MapEditor().setVisible(true);
            } else {
                launchGame();
            }
        });
    }

    private static void launchGame() {
        PickleBallHomeScreen game = new PickleBallHomeScreen();
        game.setVisible(true);
    }
}
