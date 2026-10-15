package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import sk.uniza.fri.cp.SchematicSim.ContextMenuHelper;
import sk.uniza.fri.cp.SchematicSim.Pin.InputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Side;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 7-segmentový displej. Má 9 vstupov - A až G (segmenty), DP (desatinná bodka) a
 * CA (spoločná anóda), všetky na ľavej strane tela. Všetky vstupy sú aktívne v 0:
 * segment svieti, keď je na jeho vstupe logická 0 a zároveň je na CA logická 0
 * (CA = master spínanie displeja). Displej je čisto pasívny - nemá výstupy a
 * nikdy nehynie na zbernicu.
 * <p>
 * Priradenie segmentov: A = horný, B = pravý horný, C = pravý dolný, D = dolný,
 * E = ľavý dolný, F = ľavý horný, G = stredný, DP = desatinná bodka.
 * <p>
 * Pravým tlačidlom myši sa otvorí kontextové menu s výberom farby svietiacich segmentov.
 *
 * @author Claude (návrh podľa SchematicSim architektúry)
 */
public class SevenSegmentDisplay extends GateSymbol {

    private static final int GRID_WIDTH = 6;
    private static final int GRID_HEIGHT = 10;
    private static final int SEGMENT_COUNT = 8;
    private static final long CA_HOLD_MS = 100L;

    private static final Color SEGMENT_OFF = Color.rgb(220, 220, 220);
    private static final Color SEGMENT_UNPOWERED = Color.rgb(120, 120, 120);

    private static final Map<String, Color> DISPLAY_COLORS = new LinkedHashMap<>();
    static {
        DISPLAY_COLORS.put("Červená", Color.RED);
        DISPLAY_COLORS.put("Zelená", Color.GREEN);
        DISPLAY_COLORS.put("Modrá", Color.BLUE);
        DISPLAY_COLORS.put("Žltá", Color.YELLOW);
        DISPLAY_COLORS.put("Oranžová", Color.ORANGE);
    }

    private static final String[] PIN_NAMES = {"A", "B", "C", "D", "E", "F", "G", "DP", "CA"};

    // pozor: toto sa nesmie inicializovať inline - createPins()/drawBody() volá už
    // konštruktor GateSymbol, ešte pred spustením inicializátorov inštančných premenných
    private List<Pin> segmentPins;
    private Pin caPin;
    private Shape[] segmentShapes;

    private ContextMenu contextMenu;
    private ToggleGroup colorGroup;
    private volatile Color segmentColor = DISPLAY_COLORS.get("Červená");

    private boolean[] litSegments = new boolean[SEGMENT_COUNT];
    private boolean[] holdSnapshot = new boolean[SEGMENT_COUNT];
    private boolean caWasEnabled;
    private boolean caHoldActive;
    private long caHoldDeadlineMs;
    private volatile boolean powered;

    /** Konštruktor pre paletku (ItemPicker). */
    public SevenSegmentDisplay() {
        super();
    }

    @Override
    public Pane getImage() {
        Text label = new Text("7SEG");
        label.setLayoutY(label.getBoundsInLocal().getHeight());

        Image image = new Image(getClass().getResourceAsStream("/icons/7segment.png"));
        ImageView view = new ImageView(image);
        view.setLayoutY(label.getBoundsInLocal().getHeight() + 5);

        label.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (image.getWidth() - label.getBoundsInLocal().getWidth()) / 2.0,
                image.widthProperty(), label.boundsInLocalProperty()));

        return new Pane(label, view);
    }

    /** Konštruktor pre umiestnenie na plochu - volaný reflexiou zo SchematicSheet. */
    public SevenSegmentDisplay(SchematicSheet sheet) {
        super(sheet);
        buildContextMenu();
        this.addEventHandler(MouseEvent.MOUSE_PRESSED, this::showContextMenu);
    }

    private void buildContextMenu() {
        colorGroup = new ToggleGroup();
        for (Map.Entry<String, Color> entry : DISPLAY_COLORS.entrySet()) {
            RadioMenuItem item = new RadioMenuItem(entry.getKey());
            item.setToggleGroup(colorGroup);
            item.setUserData(entry.getValue());
        }
        colorGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            Object data = now != null ? now.getUserData() : null;
            if (data instanceof Color) setColor((Color) data);
        });

        contextMenu = new ContextMenu();
        for (Toggle toggle : colorGroup.getToggles()) {
            contextMenu.getItems().add((MenuItem) toggle);
        }
    }

    private void showContextMenu(MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY) {
            // povolené aj počas simulácie - mení sa iba farba segmentov, nie zapojenie
            // označiť aktuálne zvolenú farbu (alebo zrušiť označenie pri vlastnej farbe)
            boolean found = false;
            for (Toggle toggle : colorGroup.getToggles()) {
                if (segmentColor.equals(toggle.getUserData())) {
                    toggle.setSelected(true);
                    found = true;
                    break;
                }
            }
            if (!found) colorGroup.selectToggle(null);
            ContextMenuHelper.show(contextMenu, this, event.getScreenX(), event.getScreenY());
            event.consume();
        }
    }

    /** Zmena farby segmentov (ekvivalent voľby v kontextovom menu). */
    public void setColor(Color color) {
        segmentColor = color;
        refreshVisual();
    }

    public Color getSegmentColor() {
        return segmentColor;
    }

    public boolean isContextMenuShowing() {
        return contextMenu != null && contextMenu.isShowing();
    }

    @Override
    protected List<Pin> createPins() {
        segmentPins = new ArrayList<>(SEGMENT_COUNT);
        List<Pin> pins = new ArrayList<>(PIN_NAMES.length);
        for (int i = 0; i < PIN_NAMES.length; i++) {
            // segmenty A-G a DP majú index 0..7, CA je posledný (index 8)
            InputPin pin = new InputPin(this, PIN_NAMES[i], 0, 1 + i, Side.LEFT);
            pins.add(pin);
            if (i < SEGMENT_COUNT) {
                segmentPins.add(pin);
            } else {
                caPin = pin;
            }
        }
        return pins;
    }

    @Override
    protected Pane drawBody() {
        int cell = getSheet().getComponentCell();
        double c = cell;

        segmentShapes = new Shape[SEGMENT_COUNT];

        // rámik okolo displeja - zaberá celý gridový rozmer súčiastky, takže vývody
        // (piny vľavo, v gride x=0) ležia presne na ľavej hrane rámika
        double cw = getGridWidth() * c;
        double ch = getGridHeight() * c;
        Rectangle frame = new Rectangle(0, 0, cw, ch);
        frame.setFill(Color.WHITE);
        frame.setStroke(Color.BLACK);
        frame.setStrokeWidth(3);

        // posun cifry tak, aby bola v rámiku symetricky vycentrovaná (stred cifry = stred rámika)
        double shift = 0.7 * c;
        double thickness = 0.5 * c;
        double hx1 = 1.0 * c + shift, hx2 = 3.6 * c + shift;
        double ay = 0.9 * c + shift, gy = 4.3 * c + shift, dy = 7.7 * c + shift;
        double vx1 = 0.7 * c + shift, vx2 = 3.9 * c + shift;
        double upperTop = 1.35 * c + shift, upperBottom = 3.8 * c + shift;
        double lowerTop = 4.8 * c + shift, lowerBottom = 7.25 * c + shift;

        segmentShapes[0] = horizontalBar(hx1, hx2, ay, thickness);      // A - horný
        segmentShapes[1] = verticalBar(vx2, upperTop, upperBottom, thickness);   // B - pravý horný
        segmentShapes[2] = verticalBar(vx2, lowerTop, lowerBottom, thickness);   // C - pravý dolný
        segmentShapes[3] = horizontalBar(hx1, hx2, dy, thickness);      // D - dolný
        segmentShapes[4] = verticalBar(vx1, lowerTop, lowerBottom, thickness);   // E - ľavý dolný
        segmentShapes[5] = verticalBar(vx1, upperTop, upperBottom, thickness);   // F - ľavý horný
        segmentShapes[6] = horizontalBar(hx1, hx2, gy, thickness);      // G - stredný
        segmentShapes[7] = new Circle(4.35 * c + shift, dy, 0.28 * c);          // DP - desatinná bodka (v úrovni D)

        for (Shape segment : segmentShapes) {
            segment.setFill(SEGMENT_OFF);
            //segment.setStroke(Color.BLACK);
            //segment.setStrokeWidth(0.75);
        }

        Pane pane = new Pane();
        pane.getChildren().add(frame);
        pane.getChildren().addAll(segmentShapes);

        // názvy vývodov vpravo od pripojovacích bodov, vnútri rámika (piny sú v gride x=0,
        // riadky y=1..9, takže každý riadok zodpovedá jednému vstupu); text je vertikálne
        // vycentrovaný presne na stred čierneho bodu daného pinu
        Font pinFont = Font.font(cell * 0.55);
        double labelX = cell * 0.53;
        for (int i = 0; i < PIN_NAMES.length; i++) {
            Text pinLabel = new Text(PIN_NAMES[i]);
            pinLabel.setFont(pinFont);
            pinLabel.setFill(Color.BLACK);
            Bounds b = pinLabel.getBoundsInLocal();
            pinLabel.setLayoutX(labelX - (b.getMinX() + b.getWidth() / 2.0));
            pinLabel.setLayoutY((1 + i) * cell + cell / 2.0 - (b.getMinY() + b.getHeight() / 2.0) - cell * 0.5);
            pane.getChildren().add(pinLabel);
        }
        return pane;
    }

    private static Polygon horizontalBar(double x1, double x2, double y, double thickness) {
        double half = thickness / 2;
        return new Polygon(
                x1 + half, y - half,
                x2 - half, y - half,
                x2, y,
                x2 - half, y + half,
                x1 + half, y + half,
                x1, y);
    }

    private static Polygon verticalBar(double x, double y1, double y2, double thickness) {
        double half = thickness / 2;
        return new Polygon(
                x - half, y1 + half,
                x - half, y2 - half,
                x, y2,
                x + half, y2 - half,
                x + half, y1 + half,
                x, y1);
    }

    @Override
    public void simulate() {
        long now = System.currentTimeMillis();
        boolean caEnabled = isLow(caPin); // CA=LOW => displej aktivovaný

        if (caEnabled) {
            // CA vrátené do aktívneho stavu: timeout sa zruší a displej pracuje normálne.
            caWasEnabled = true;
            caHoldActive = false;
            caHoldDeadlineMs = 0L;
            powered = true;

            boolean[] newLit = new boolean[SEGMENT_COUNT];
            for (int i = 0; i < SEGMENT_COUNT; i++) {
                newLit[i] = isLow(segmentPins.get(i));
            }

            applyLitState(newLit, true);
            return;
        }

        // CA = HIGH = neaktívne. Triggerujeme hold iba na prechode 0->1.
        if (caWasEnabled) {
            caWasEnabled = false;
            caHoldActive = true;
            caHoldDeadlineMs = now + CA_HOLD_MS;
            holdSnapshot = Arrays.copyOf(litSegments, SEGMENT_COUNT);
            powered = true;
            applyLitState(holdSnapshot, true);
            return;
        }

        if (caHoldActive) {
            if (now >= caHoldDeadlineMs) {
                caHoldActive = false;
                powered = false;
                applyLitState(new boolean[SEGMENT_COUNT], true);
            } else {
                powered = true;
                applyLitState(holdSnapshot, false);
            }
            return;
        }

        // Po timeoute ostane displej vypnutý, kým sa CA nevráti na LOW.
        powered = false;
        applyLitState(new boolean[SEGMENT_COUNT], true);
    }

    private void applyLitState(boolean[] newLit, boolean powerChanged) {
        boolean changed = powerChanged;

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            boolean desired = newLit[i];
            if (litSegments[i] != desired) {
                litSegments[i] = desired;
                changed = true;
            }
        }

        if (changed) {
            refreshVisual();
        }
    }

    @Override
    public void reset() {
        for (Pin pin : getPins()) {
            setPinForce(pin, Pin.PinState.NOT_CONNECTED);
        }
        litSegments = new boolean[SEGMENT_COUNT];
        holdSnapshot = new boolean[SEGMENT_COUNT];
        caWasEnabled = false;
        caHoldActive = false;
        caHoldDeadlineMs = 0L;
        powered = false;
        refreshVisual();
    }

    /**
     * Aktualizácia vizuálu vždy na FX vlákne (simulácia beží na separátnom vlákne).
     * Zmeny sú skoalescované do jednej čakajúcej úlohy, aby FX vlákno nebolo zahltené.
     */
    private volatile boolean visualUpdateScheduled;

    private void refreshVisual() {
        if (visualUpdateScheduled) return;
        visualUpdateScheduled = true;
        Platform.runLater(() -> {
            visualUpdateScheduled = false;
            if (!powered) {
                for (Shape segment : segmentShapes) {
                    segment.setFill(SEGMENT_OFF);
                }
                return;
            }
            for (int i = 0; i < SEGMENT_COUNT; i++) {
                segmentShapes[i].setFill(litSegments[i] ? segmentColor : SEGMENT_OFF);
            }
        });
    }

    @Override
    public int getGridWidth() {
        return GRID_WIDTH;
    }

    @Override
    public int getGridHeight() {
        return GRID_HEIGHT;
    }

    @Override
    public Map<String, String> saveProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("color", toHex(segmentColor));
        return properties;
    }

    @Override
    public void loadProperties(Map<String, String> properties) {
        String color = properties.get("color");
        if (color != null) {
            Color found = DISPLAY_COLORS.get(color);
            setColor(found != null ? found : Color.web(color));
        }
    }

    private static String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    @Override
    public String getName() {
        return "7-segment";
    }

    @Override
    public String getShortDescription() {
        return "7-segmentový displej - vstupy A-G, DP (desatinná bodka) a CA (spoločná anóda), "
                + "segment svieti pri logickej 0 na vstupe aj na CA";
    }
}