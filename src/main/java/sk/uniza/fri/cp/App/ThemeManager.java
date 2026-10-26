package sk.uniza.fri.cp.App;

import com.sun.javafx.stage.StageHelper;
import com.sun.javafx.tk.TKPulseListener;
import com.sun.javafx.tk.Toolkit;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorInput;
import javafx.scene.paint.Color;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Centrálne nastavenie vzhľadu aplikácie - svetlý a tmavý motív.
 * <p>
 * Tmavý motív nie je samostatný farebný štýl, ale <b>invertovanie farieb</b> celej scény.
 * Aplikácia používa jediný (svetlý) stylesheet a tmavá verzia je jeho negatívom, preto
 * kontrast textu a pozadia zostáva zachovaný všade - v menu, paneli nástrojov, tabuľkách,
 * schéme, editore kódu aj konzole. To je dôležité najmä pre prvky, ktoré si farby nastavujú
 * priamo do node (plocha schémy, RichTextFX editor a konzola), kde by ich stylesheet scény
 * nedokázal prebiť.
 * <p>
 * Invertovanie sa realizuje efektom {@link BlendMode#DIFFERENCE} s bielym podkladom, preto
 * platí |biela - farba pixelu|, čo je invertovaná hodnota. Efekt sa nasadzuje na koreňový
 * node scény, preto musí byť nasadený aj na vyskakovacích oknách (kontextové menu, tooltipy,
 * rolety), ktoré JavaFX vytvára samostatne a lenivo, preto ich sledujeme pri každom pulze.
 * <p>
 * Nastavenie sa ukladá do Preferences užívateľa a obnoví sa pri ďalšom spustení.
 *
 * @author Claude
 */
public final class ThemeManager {

    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);

    private static final String KEY_DARK_MODE = "ui.darkMode";

    /** Farba okna, ktorá v tmavom motíve zodpovedá invertovanému svetlému pozadiu. */
    private static final Color DARK_WINDOW_FILL = Color.web("#0d0d0d");

    private static final BooleanProperty DARK_MODE = new SimpleBooleanProperty(false);

    /**
     * Listener na pulzy, ktorý dohľadáva vyskakovacie okná. JavaFX si ho drží len slabým
     * odkazom, preto musíme instanciu vlastniť - inak by bola zmetaná garbage collectorom.
     */
    private static final TKPulseListener PULSE_LISTENER = new TKPulseListener() {
        @Override
        public void pulse() {
            if (isDark()) applyToAllWindows();
        }
    };

    private static boolean installed;

    private ThemeManager() {
    }

    /**
     * Načítanie uloženého nastavenia a prihlásenie sa k novovytváraným oknám.
     * Volá sa na FX vlákne pred vytvorením prvého okna.
     */
    public static void install() {
        DARK_MODE.set(PREFS.getBoolean(KEY_DARK_MODE, false));
        if (installed) return;
        installed = true;

        // okná sa vytvárajú počas behu (okno schémy, pamäť RAM, ...) - treba ich zachytiť
        StageHelper.getStages().addListener((ListChangeListener<Stage>) c -> {
            while (c.next()) {
                for (Stage stage : c.getAddedSubList()) {
                    Scene scene = stage.getScene();
                    if (scene != null) applyTo(scene);
                }
            }
        });

        // vyskakovacie okná (kontextové menu, tooltipy, rolety) JavaFX vytvára lenivo
        // a bez verejného oznamovania, preto ich pri pulzoch dohľadáme
        Toolkit.getToolkit().addPostSceneTkPulseListener(PULSE_LISTENER);
    }

    /** Zisti, či je aktívny tmavý motív. */
    public static boolean isDark() {
        return DARK_MODE.get();
    }

    /** Vlastnosť pre počúvanie zmeny motívu. */
    public static BooleanProperty darkModeProperty() {
        return DARK_MODE;
    }

    /** Nastaví motív a premaluje všetky okná. */
    public static void setDarkMode(boolean dark) {
        if (DARK_MODE.get() == dark) return;
        DARK_MODE.set(dark);
        PREFS.putBoolean(KEY_DARK_MODE, dark);
        try {
            PREFS.flush();
        } catch (Exception e) {
            // zlyhanie zápisu nastavenia nesmie rozhodiť aplikáciu
        }
        applyToAllWindows();
    }

    /**
     * Prihlásenie scény pod správu motívu a okamžité aplikovanie aktuálneho vzhľadu.
     *
     * @param scene scéna okna
     */
    public static void registerScene(Scene scene) {
        applyTo(scene);
    }

    /** Nasadí alebo odstráni invertovanie podľa aktuálneho motívu. */
    public static void applyTo(Scene scene) {
        if (scene == null) return;
        Node root = scene.getRoot();
        if (root == null) return;

        if (isDark()) {
            if (!(root.getEffect() instanceof Blend)) root.setEffect(createInvertEffect(root));
            // scéna kreslí svoje pozadie pod efektom koreňa, preto ho musíme stmaviť priamo
            if (!DARK_WINDOW_FILL.equals(scene.getFill())) scene.setFill(DARK_WINDOW_FILL);
        } else {
            if (root.getEffect() != null) root.setEffect(null);
            if (scene.getFill() != null) scene.setFill(null);
        }
    }

    /**
     * Premaluje scény všetkých okná, vrátane vyskakovacích (kontextové menu, tooltipy, rolety).
     * Metóda je idempotentná - mení iba scény, ktoré nemajú požadovaný stav.
     */
    private static void applyToAllWindows() {
        List<Window> windows = new ArrayList<>();
        java.util.Iterator<Window> it = Window.impl_getWindows();
        while (it != null && it.hasNext()) windows.add(it.next());
        for (Window window : windows) {
            if (!(window instanceof Stage) && !(window instanceof PopupWindow)) continue;
            applyTo(window.getScene());
        }
    }

    /**
     * Vytvorí invertujúci efekt. Biely podklad je potrebné rozmerovo zviazať s node,
     * na ktorý sa efekt vztahuje - bez veľkosti by bol prázdny a invertovanie by nemalo
     * žiadny účinok.
     */
    private static Blend createInvertEffect(Node target) {
        DoubleBinding width = Bindings.createDoubleBinding(
                () -> target.getLayoutBounds().getWidth(), target.layoutBoundsProperty());
        DoubleBinding height = Bindings.createDoubleBinding(
                () -> target.getLayoutBounds().getHeight(), target.layoutBoundsProperty());

        ColorInput white = new ColorInput(0, 0, 0, 0, Color.WHITE);
        white.widthProperty().bind(width);
        white.heightProperty().bind(height);

        return new Blend(BlendMode.DIFFERENCE, white, null);
    }
}
