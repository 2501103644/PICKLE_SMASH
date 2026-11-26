import javax.swing.SwingUtilities;
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::launchGame);
    }

    private static void launchGame() {
        PickleBallHomeScreen game = new PickleBallHomeScreen();
        game.setVisible(true);
    }
}
