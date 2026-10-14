package sk.uniza.fri.cp.App;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.stage.Window;

import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Zapamätanie polohy a veľkosti okien aplikácie medzi spusteniami. Ukladá sa do
 * Preferences užívateľa - pri ďalšom spustení sa okno obnoví na poslednú geometriu.
 *
 * @author Claude
 */
public final class WindowSettings {

    private static final Preferences PREFS = Preferences.userNodeForPackage(WindowSettings.class);

    /** Kľúče, pre ktoré už bolo sledovanie oddeľovačov nastavené (aby sa nepridávalo viackrát). */
    private static final Set<String> TRACKED_KEYS = new HashSet<>();

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
     * Uložené pozície oddeľovačov SplitPane (šírka paletky, výška konzoly, šírka
     * bočných panelov...). Pozície sa NEaplikujú - vráti ich len na zistenie
     * predvolených hodnôt. Aplikovať sa musia až po zobrazení okna metódou
     * {@link #applyDivider(String, SplitPane)}: aplikovanie pred zobrazením scény
     * spôsobí posun (skin si pozície prepočíta voči inému rozmeru), ktorý sa pri
     * každom ďalšom spustení kumuluje a rozloženie sa tak samo posúva.
     *
     * @return uložené pozície oddeľovačov alebo {@code null}, ak žiadne nie sú
     *         (vtedy si panel ponechá pozície definované v FXML)
     */
    public static double[] getSavedDividerPositions(String key, SplitPane pane) {
        double[] positions = parsePositions(PREFS.get(key + ".dividers", ""));
        if (positions == null || positions.length != pane.getDividers().size()) {
            return null;
        }
        return positions;
    }

    /**
     * Aplikovanie uložených pozícií oddeľovačov. Volajte až po tom, čo je okno s
     * panelom zobrazené (ideálne v {@code Platform.runLater} z {@code setOnShown}),
     * aby panel mal už konečný rozmer. Sledovanie zmien sa spustí až po aplikovaní
     * - inak by počiatočné rozloženie okna prepísalo zapamätanú polohu skôr, než
     * sa stihne obnoviť.
     */
    public static void applyDivider(String key, SplitPane pane) {
        double[] positions = getSavedDividerPositions(key, pane);
        if (positions != null) {
            pane.setDividerPositions(positions);
        }
        trackDivider(key, pane);
    }

    /**
     * Sledovanie pozícií oddeľovačov SplitPane - každý pohyb oddeľovača sa
     * okamžite zapamätá. Pre ten istý kľúč sa sledovanie nastaví len raz, aby sa
     * pri opakovanom zobrazení okna nepridávali ďalší poslucháči.
     */
    public static void trackDivider(String key, SplitPane pane) {
        if (!TRACKED_KEYS.add(key)) {
            return;
        }
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

    /**
     * Vypíše na konzolu pomery rozloženia častí okna - podiely jednotlivých
     * panelov daného SplitPane v percentách spolu s ich veľkosťou v pixeloch.
     *
     * @param label Textové označenie, ktoré sa vypíše pred pomermi (napr. "CPU").
     * @param pane  SplitPane, ktorého rozloženie sa má vypísať.
     */
    public static void printDividerRatios(String label, SplitPane pane) {
        if (pane == null) {
            return;
        }
        boolean horizontal = pane.getOrientation() == Orientation.HORIZONTAL;
        double total = horizontal ? pane.getWidth() : pane.getHeight();
        double[] positions = pane.getDividerPositions();

        StringBuilder sb = new StringBuilder();
        double previous = 0;
        for (int i = 0; i <= positions.length; i++) {
            double end = (i < positions.length) ? positions[i] : 1.0;
            double fraction = end - previous;
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(String.format("%.1f%%", fraction * 100.0));
            if (total > 0) {
                sb.append(String.format(" (%.0fpx)", fraction * total));
            }
            previous = end;
        }
        System.out.println("[Layout] " + label + " [" + (horizontal ? "H" : "V") + "] " + sb);
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