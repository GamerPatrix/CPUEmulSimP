package sk.uniza.fri.cp.App;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Wire.Wire;

import java.util.prefs.Preferences;

/**
 * Nastavenie farieb vodičov schémy: farba vodiča v normálnom režime a farby
 * debug režimu podľa logického stavu (Z, 0, 1). Nastavenie sa ukladá do Preferences
 * užívateľa a uplatňuje sa na všetky vodiče aktuálnej plochy.
 */
public final class WireColorSettings {

    private static final Preferences PREFS = Preferences.userNodeForPackage(WireColorSettings.class);

    private static final String KEY_NORMAL = "wire.normal";
    private static final String KEY_DEBUG_Z = "wire.debugZ";
    private static final String KEY_DEBUG_LOW = "wire.debugLow";
    private static final String KEY_DEBUG_HIGH = "wire.debugHigh";

    private static final Color DEFAULT_NORMAL = Color.BLACK;
    private static final Color DEFAULT_DEBUG_Z = Color.GRAY;
    private static final Color DEFAULT_DEBUG_LOW = Color.BLUE;
    private static final Color DEFAULT_DEBUG_HIGH = Color.RED;

    private WireColorSettings() {
    }

    /**
     * Načítanie uložených farieb a ich aplikácia ako predvolených hodnôt vodičov.
     * Volá sa pri štarte aplikácie.
     */
    public static void loadAndApply() {
        Wire.setDefaultColor(loadColor(KEY_NORMAL, DEFAULT_NORMAL));
        Wire.setDebugColors(
                loadColor(KEY_DEBUG_Z, DEFAULT_DEBUG_Z),
                loadColor(KEY_DEBUG_LOW, DEFAULT_DEBUG_LOW),
                loadColor(KEY_DEBUG_HIGH, DEFAULT_DEBUG_HIGH));
    }

    /**
     * Otvorenie dialógu s nastavením farieb vodičov. Po potvrdení sa farby uložia
     * a uplatnia na všetky vodiče na ploche {@code sheet}.
     */
    public static void showEditor(SchematicSheet sheet, Window owner) {
        loadAndApply();

        ColorPicker normalPicker = picker(Wire.getDefaultColor());
        ColorPicker debugZPicker = picker(Wire.getDebugZColor());
        ColorPicker debugLowPicker = picker(Wire.getDebugLowColor());
        ColorPicker debugHighPicker = picker(Wire.getDebugHighColor());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Farby vodičov");
        dialog.setHeaderText("Nastavenie farieb vodičov na ploche schémy");
        if (owner != null) dialog.initOwner(owner);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Vodič (normálny režim):"), normalPicker);
        grid.addRow(1, new Label("Debug – stav Z:"), debugZPicker);
        grid.addRow(2, new Label("Debug – stav 0:"), debugLowPicker);
        grid.addRow(3, new Label("Debug – stav 1:"), debugHighPicker);
        GridPane.setHgrow(normalPicker, Priority.ALWAYS);

        // návrat k predvoleným farbám - len nastaví vstupné polia, potvrdí sa tlačidlom OK
        Button restoreButton = new Button("Predvolené farby");
        restoreButton.setOnAction(e -> resetToDefaults(normalPicker, debugZPicker, debugLowPicker, debugHighPicker));
        grid.addRow(4, new Label(), restoreButton);

        dialog.getDialogPane().setContent(grid);

        ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Zrušiť", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, cancel);

        dialog.showAndWait().ifPresent(button -> {
            if (button != ok) return;
            saveAndApply(sheet, normalPicker.getValue(), debugZPicker.getValue(),
                    debugLowPicker.getValue(), debugHighPicker.getValue());
        });
    }

    static void saveAndApply(SchematicSheet sheet, Color normal, Color debugZ, Color debugLow, Color debugHigh) {
        PREFS.put(KEY_NORMAL, toHex(safe(normal, DEFAULT_NORMAL)));
        PREFS.put(KEY_DEBUG_Z, toHex(safe(debugZ, DEFAULT_DEBUG_Z)));
        PREFS.put(KEY_DEBUG_LOW, toHex(safe(debugLow, DEFAULT_DEBUG_LOW)));
        PREFS.put(KEY_DEBUG_HIGH, toHex(safe(debugHigh, DEFAULT_DEBUG_HIGH)));
        try {
            PREFS.flush();
        } catch (Exception e) {
            // nepodstatné
        }

        final Color n = safe(normal, Wire.getDefaultColor());
        Wire.setDefaultColor(n);
        Wire.setDebugColors(
                safe(debugZ, Wire.getDebugZColor()),
                safe(debugLow, Wire.getDebugLowColor()),
                safe(debugHigh, Wire.getDebugHighColor()));

        if (sheet != null) {
            for (Wire wire : sheet.getWires()) {
                wire.changeColor(n);
            }
        }
    }

    /** Nastavenie všetkých vstupov na predvolené farby aplikácie. */
    private static void resetToDefaults(ColorPicker normal, ColorPicker debugZ, ColorPicker debugLow, ColorPicker debugHigh) {
        normal.setValue(DEFAULT_NORMAL);
        debugZ.setValue(DEFAULT_DEBUG_Z);
        debugLow.setValue(DEFAULT_DEBUG_LOW);
        debugHigh.setValue(DEFAULT_DEBUG_HIGH);
    }

    private static ColorPicker picker(Color color) {
        ColorPicker picker = new ColorPicker(color);
        picker.setMaxWidth(Double.MAX_VALUE);
        return picker;
    }

    private static Color safe(Color color, Color fallback) {
        return color != null ? color : fallback;
    }

    private static Color loadColor(String key, Color fallback) {
        String value = PREFS.get(key, toHex(fallback));
        try {
            return Color.valueOf(value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Hex reťazec "#rrggbb" (bez priehľadnosti). */
    private static String toHex(Color color) {
        String hex = color.toString();
        if (hex.length() == 8) {
            int alpha = Integer.parseInt(hex.substring(1, 2), 16);
            return alpha == 0xff ? hex.substring(0, 7) : hex;
        }
        return hex;
    }
}