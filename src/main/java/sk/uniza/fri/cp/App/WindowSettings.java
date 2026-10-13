package sk.uniza.fri.cp.App;

import javafx.beans.value.ChangeListener;
import javafx.scene.control.SplitPane;
import javafx.stage.Window;

import java.util.prefs.Preferences;

/**
 * Zapamätanie polohy a veľkosti okien aplikácie medzi spusteniami. Ukladá sa do
 * Preferences užívateľa - pri ďalšom spustení sa okno obnoví na poslednú geometriu.
 *
 * @author Claude
 */
public final class WindowSettings {

    private static final Preferences PREFS = Preferences.userNodeForPackage(WindowSettings.class);

    private WindowSettings() {
    }

    /**
     * Obnovenie poslednej polohy a veľkosti okna. Ak zapamätané nastavenie neexistuje
     * (prvé spustenie) alebo je neprípustné (zmena displeja), okno si ponechá
     * predvolenú geometriu.
     *
     * @param key            Identifikátor okna (napr. "cpu", "schematic").
     * @param window         Okno, ktorého geometria sa má obnoviť.
     * @param defaultWidth   Predvolená šírka, ak sa okno neobnoví.
     * @param defaultHeight  Predvolená výška, ak sa okno neobnoví.
     */
    public static void restore(String key, Window window, double defaultWidth, double defaultHeight) {
        double x = PREFS.getDouble(key + ".x", Double.NaN);
        double y = PREFS.getDouble(key + ".y", Double.NaN);
        double width = PREFS.getDouble(key + ".width", Double.NaN);
        double height = PREFS.getDouble(key + ".height", Double.NaN);

        if (!isUsable(width) || !isUsable(height) || !isReasonablePosition(x) || !isReasonablePosition(y)) {
            window.setWidth(defaultWidth);
            window.setHeight(defaultHeight);
            return;
        }

        window.setX(x);
        window.setY(y);
        window.setWidth(width);
        window.setHeight(height);
    }

    /**
     * Sledovanie zmien polohy a veľkosti okna - každá zmena sa okamžite zapamätá,
     * takže pri ukončení programu sa nepotrebuje nič dopisovať.
     */
    public static void track(String key, Window window) {
        ChangeListener<Number> saver = (obs, oldValue, newValue) -> save(key, window);
        window.xProperty().addListener(saver);
        window.yProperty().addListener(saver);
        window.widthProperty().addListener(saver);
        window.heightProperty().addListener(saver);
    }

    private static void save(String key, Window window) {
        PREFS.putDouble(key + ".x", window.getX());
        PREFS.putDouble(key + ".y", window.getY());
        PREFS.putDouble(key + ".width", window.getWidth());
        PREFS.putDouble(key + ".height", window.getHeight());
        try {
            PREFS.flush();
        } catch (Exception e) {
            // zhzanie nezapisovania neovplyvní beh aplikácie
        }
    }

    /** Rozmer okna: konečný a dostatočne veľký na použitie. */
    private static boolean isUsable(double value) {
        return Double.isFinite(value) && value >= 200;
    }

    /** Pozícia okna môže byť aj záporná (druhý displej vľavo), nesmie byť ale nesúvislá. */
    private static boolean isReasonablePosition(double value) {
        return Double.isFinite(value) && value > -10000 && value < 10000;
    }

    /**
     * Obnovenie uložených pozícií oddeľovačov SplitPane (šírka paletky, výška
     * konzoly, šírka bočných panelov...). Ak nie sú zapamätané pozície pre daný
     * pane, nič sa nezmení.
     *
     * @return true, ak sa pozície obnovili zo zapamätaného stavu
     */
    public static boolean restoreDivider(String key, SplitPane pane) {
        double[] positions = parsePositions(PREFS.get(key + ".dividers", ""));
        if (positions == null || positions.length != pane.getDividers().size()) {
            return false;
        }
        pane.setDividerPositions(positions);
        return true;
    }

    /**
     * Sledovanie pozícií oddeľovačov SplitPane - každý pohyb oddeľovača sa
     * okamžite zapamätá.
     */
    public static void trackDivider(String key, SplitPane pane) {
        for (SplitPane.Divider divider : pane.getDividers()) {
            divider.positionProperty().addListener((obs, oldValue, newValue) -> savePositions(key, pane));
        }
    }

    private static void savePositions(String key, SplitPane pane) {
        StringBuilder sb = new StringBuilder();
        for (SplitPane.Divider divider : pane.getDividers()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(divider.getPosition());
        }
        PREFS.put(key + ".dividers", sb.toString());
        try {
            PREFS.flush();
        } catch (Exception e) {
            // nepodstatné
        }
    }

    private static double[] parsePositions(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String[] parts = value.split(",");
        double[] result = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                result[i] = Double.parseDouble(parts[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return result;
    }

    /** Zapamätanie jednoduchej hodnoty (napr. rozbalený/schovaný panel). */
    public static void putBoolean(String key, boolean value) {
        PREFS.putBoolean(key, value);
        try {
            PREFS.flush();
        } catch (Exception e) {
            // nepodstatné
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return PREFS.getBoolean(key, defaultValue);
    }

    public static void putDouble(String key, double value) {
        PREFS.putDouble(key, value);
        try {
            PREFS.flush();
        } catch (Exception e) {
            // nepodstatné
        }
    }

    public static double getDouble(String key, double defaultValue) {
        return PREFS.getDouble(key, defaultValue);
    }
}