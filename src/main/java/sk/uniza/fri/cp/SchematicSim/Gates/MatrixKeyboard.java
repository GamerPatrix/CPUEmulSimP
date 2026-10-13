package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.VPos;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import sk.uniza.fri.cp.SchematicSim.ContextMenuHelper;
import sk.uniza.fri.cp.SchematicSim.Electrical.PinType;
import sk.uniza.fri.cp.SchematicSim.Pin.InputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.OutputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Side;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Matricová klávesnica 4x4 - štyri vstupné riadky ({@code R0}..{@code R3}, vľavo) a štyri
 * výstupné stĺpce ({@code C0}..{@code C3}, hore). Každá z šestnástich kláves sa správa rovnako
 * ako samotné tlačidlo. Štandardné labely kláves sú (zľava doprava, zdola hore)
 * {@code 0123456789ABCDEF}. Pravým tlačidlom myši sa otvorí kontextové menu s položkou
 * {@code Toggle}: ak je zapnutá, každé ľavé stlačenie klávesu trvalo prepne (latch),
 * ak je vypnutá, klávesa je stlačená len počas držania myši. Položka {@code Label} umožní
 * vpísať jedno písmeno na klávesu, na ktorej bolo pravé tlačidlo myši stlačené (určí sa
 * podľa polohy myši).
 * Ak je stlačená niektorá klávesa v stĺpci a jej riadok je v logickej 0, výstup stĺpca sa
 * pretiahne na 0, inak je na 1 (slabý pull-up). Výstupy sú typu {@link PinType#WEAK_OUT} -
 * silný push-pull vodič na sieti ich vždy pretiahne, klávesnica nemôže spôsobiť skrat.
 *
 * @author Claude (návrh podľa SchematicSim architektúry)
 */
public class MatrixKeyboard extends GateSymbol {

    private static final int ROWS = 4;
    private static final int COLS = 4;
    /** Veľkosť jednej klávesy v bunkách mriežky - ako samostatné tlačidlo (2x2). */
    private static final int KEY_GRID = 2;
    private static final int GRID_WIDTH = COLS * KEY_GRID + 1;
    private static final int GRID_HEIGHT = ROWS * KEY_GRID + 1;
    /** Štandardné labely kláves - zľava doprava, zdola hore. */
    private static final String DEFAULT_LABELS = "0123456789ABCDEF";

    private Pin[] pinIn;
    private Pin[] pinOut;
    private Circle[][] keyPlungers;
    private boolean[][] keyOn;
    private String[][] keyLabels;
    private Text[][] keyLabelTexts;
    private ContextMenu contextMenu;
    private CheckMenuItem toggleItem;

    private volatile int pressedRow = -1;
    private volatile int pressedCol = -1;
    private volatile boolean toggle = false;
    private volatile int menuRow = -1;
    private volatile int menuCol = -1;

    /** Konštruktor pre paletku (ItemPicker). */
    public MatrixKeyboard() {
        super();
    }

    @Override
    public Pane getImage() {
        Text label = new Text(getName());
        label.setLayoutY(label.getBoundsInLocal().getHeight());

        Image image = new Image(getClass().getResourceAsStream("/icons/klavesnica.png"));
        ImageView view = new ImageView(image);
        view.setLayoutY(label.getBoundsInLocal().getHeight() + 5);

        label.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (image.getWidth() - label.getBoundsInLocal().getWidth()) / 2.0,
                image.widthProperty(), label.boundsInLocalProperty()));

        return new Pane(label, view);
    }

    /** Konštruktor pre umiestnenie na plochu - volaný reflexiou zo SchematicSheet. */
    public MatrixKeyboard(SchematicSheet sheet) {
        super(sheet);
    }

    @Override
    protected List<Pin> createPins() {
        // pole sa MUSIA vytvoriť tu, nie ako inštancové inicializátory - drawBody() sa volá
        // z konštruktora GateSymbol ešte PRED spustením inštancových inicializátorov
        keyPlungers = new Circle[ROWS][COLS];
        keyOn = new boolean[ROWS][COLS];
        keyLabels = new String[ROWS][COLS];
        keyLabelTexts = new Text[ROWS][COLS];
        // štandardné labely: zľava doprava, zdola hore = 0123456789ABCDEF
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                keyLabels[row][col] = String.valueOf(DEFAULT_LABELS.charAt((ROWS - 1 - row) * COLS + col));
            }
        }

        List<Pin> pins = new ArrayList<>(ROWS + COLS);
        pinIn = new Pin[ROWS];
        for (int row = 0; row < ROWS; row++) {
            pinIn[row] = new InputPin(this, "R" + row, 1, 1 + row * KEY_GRID + KEY_GRID / 2, Side.LEFT);
            pins.add(pinIn[row]);
        }
        pinOut = new Pin[COLS];
        for (int col = 0; col < COLS; col++) {
            pinOut[col] = new OutputPin(this, "C" + col, 1 + col * KEY_GRID + KEY_GRID / 2, 1, Side.TOP);
            // slabý výstup (pull-up): idle HIGH, silný vodič na sieti ho môže pretiahnuť
            pinOut[col].getOwnedPotential().setType(PinType.WEAK_OUT);
            pins.add(pinOut[col]);
        }
        return pins;
    }

    @Override
    protected Pane drawBody() {
        int cell = getSheet().getComponentCell();

        Pane pane = new Pane();

        // klávesy v matici 4x4 - každá klávesa má veľkosť/štýl samostatného tlačidla (2x2 bunky);
        // ľavým tlačidlom sa stláča - pri zapnutom Toggle sa každé stlačenie prepne (trvalo),
        // pri vypnutom je stlačená len počas držania myši. Pravým tlačidlom sa otvorí
        // kontextové menu (Toggle + Label pre klávesu pod kurzorom).
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                double bx = (1 + col * KEY_GRID) * cell;
                double by = (1 + row * KEY_GRID) * cell;

                Rectangle keyBody = new Rectangle(bx, by, KEY_GRID * cell, KEY_GRID * cell);
                keyBody.setFill(Color.WHITESMOKE);
                keyBody.setStroke(Color.GRAY);
                keyBody.setStrokeWidth(3);

                Circle key = new Circle(bx + cell, by + cell, cell * 0.6, Color.BLACK);
                key.setStroke(Color.WHITE);
                key.setStrokeWidth(3);
                final int r = row;
                final int c = col;
                key.setOnMousePressed(event -> {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        if (toggle) {
                            keyOn[r][c] = !keyOn[r][c];
                            refreshVisual();
                            notifyStateChanged();
                        } else {
                            setPressed(r, c);
                        }
                    } else if (event.getButton() == MouseButton.SECONDARY) {
                        if (getSheet() != null) showMenu(event.getScreenX(), event.getScreenY(), r, c);
                        event.consume();
                    }
                });
                key.setOnMouseReleased(event -> {
                    if (event.getButton() == MouseButton.PRIMARY && !toggle) {
                        releasePressed(r, c);
                    }
                });
                key.setOnContextMenuRequested(event -> {
                    if (getSheet() == null) return;
                    if (contextMenu == null || !contextMenu.isShowing()) {
                        showMenu(event.getScreenX(), event.getScreenY(), r, c);
                    }
                    event.consume();
                });

                // písmeno klávesy - biela farba, vystredené v rámci klávesy
                Text keyLabel = new Text(keyLabels[r][c]);
                keyLabel.setFill(Color.WHITE);
                keyLabel.setFont(Font.font(cell * 0.7));
                keyLabel.setTextOrigin(VPos.CENTER);
                keyLabel.setMouseTransparent(true);
                keyLabel.setLayoutY(by + cell);
                keyLabel.setLayoutX(bx + cell - keyLabel.getBoundsInLocal().getWidth() / 2.0);

                keyPlungers[row][col] = key;
                keyLabelTexts[row][col] = keyLabel;
                pane.getChildren().addAll(keyBody, key, keyLabel);
            }
        }

        return pane;
    }

    private void showMenu(double screenX, double screenY, int row, int col) {
        menuRow = row;
        menuCol = col;
        if (contextMenu == null) {
            toggleItem = new CheckMenuItem("Toggle");
            toggleItem.setSelected(toggle);
            toggleItem.setOnAction(e -> setToggle(toggleItem.isSelected()));

            MenuItem labelItem = new MenuItem("Label…");
            labelItem.setOnAction(e -> showLabelDialog());

            contextMenu = new ContextMenu(toggleItem, new SeparatorMenuItem(), labelItem);
        } else {
            toggleItem.setSelected(toggle);
        }
        ContextMenuHelper.show(contextMenu, keyPlungers[row][col], screenX, screenY);
    }

    private void showLabelDialog() {
        int row = menuRow;
        int col = menuCol;
        if (row < 0 || col < 0) return;

        TextInputDialog dialog = new TextInputDialog(keyLabels[row][col]);
        dialog.setTitle("Label klávesy");
        dialog.setHeaderText(null);
        dialog.setContentText("Zadaj jedno písmeno:");
        dialog.getEditor().setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= 1 ? change : null));
        dialog.showAndWait().ifPresent(s -> setKeyLabel(row, col, s));
    }

    private boolean isActiveKey(int row, int col) {
        return (pressedRow == row && pressedCol == col) || keyOn[row][col];
    }

    @Override
    public void simulate() {
        // kľud: slabý pull-up drží stĺpce na 1; stlačená klávesa prenáša svoj riadok na stĺpec
        for (int col = 0; col < COLS; col++) {
            if (anyLowInColumn(col)) {
                setPin(pinOut[col], Pin.PinState.LOW);
            } else {
                setPin(pinOut[col], Pin.PinState.HIGH);
            }
        }
    }

    private boolean anyLowInColumn(int col) {
        for (int row = 0; row < ROWS; row++) {
            if (isActiveKey(row, col) && isLow(pinIn[row])) return true;
        }
        return false;
    }

    @Override
    public void reset() {
        pressedRow = -1;
        pressedCol = -1;
        for (boolean[] row : keyOn) {
            Arrays.fill(row, false);
        }
        for (int row = 0; row < ROWS; row++) {
            if (pinIn[row] != null) setPinForce(pinIn[row], Pin.PinState.NOT_CONNECTED);
        }
        for (int col = 0; col < COLS; col++) {
            if (pinOut[col] != null) setPinForce(pinOut[col], Pin.PinState.NOT_CONNECTED);
        }
    }

    /**
     * Momentové stlačenie klávesy (ekvivalent podržania ľavým tlačidlom myši).
     */
    public void setPressed(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return;
        pressedRow = row;
        pressedCol = col;
        refreshVisual();

        notifyStateChanged();
    }

    /**
     * Uvoľnenie stlačenej klávesy - v momentovom režime (vypnutý Toggle) zruší aj jej
     * prípadné trvalé zapnutie, presne ako pri samostatnom tlačidle.
     */
    public void releasePressed(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return;
        if (pressedRow != row || pressedCol != col) return;
        pressedRow = -1;
        pressedCol = -1;
        keyOn[row][col] = false;
        refreshVisual();

        notifyStateChanged();
    }

    /**
     * Prepnutie režimu Toggle: pri {@code true} každé ľavé stlačenie klávesu trvalo prepne,
     * pri {@code false} je klávesa stlačená len počas držania myši.
     */
    public void setToggle(boolean value) {
        if (toggle == value) return;
        toggle = value;
        // pri vypnutí Toggle sa všetky trvalo prepnuté klávesy vrátia do kľudu
        if (!toggle) {
            pressedRow = -1;
            pressedCol = -1;
            for (boolean[] row : keyOn) {
                Arrays.fill(row, false);
            }
            refreshVisual();
            notifyStateChanged();
        }
    }

    public boolean isToggle() {
        return toggle;
    }

    /**
     * Ak beží simulácia, okamžite prepočítaj vývody, aby sa zmena rozšírila do vodičov a LED.
     */
    private void notifyStateChanged() {
        if (getSheet() != null && getSheet().isSimulationRunning()) {
            simulate();
        }
    }

    public boolean isKeyOn(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return false;
        return keyOn[row][col];
    }

    /**
     * Nastavenie popisu klávesy - jedno písmeno zobrazené bielou farbou. Prázdny reťazec
     * label odstráni.
     */
    public void setKeyLabel(int row, int col, String value) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return;
        keyLabels[row][col] = value == null ? "" : value;
        Text label = keyLabelTexts[row][col];
        if (label != null) {
            double cell = getSheet().getComponentCell();
            label.setText(keyLabels[row][col]);
            label.setLayoutX((1 + col * KEY_GRID) * cell + cell - label.getBoundsInLocal().getWidth() / 2.0);
        }
    }

    public String getKeyLabel(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return "";
        return keyLabels[row][col];
    }

    public boolean isContextMenuShowing() {
        return contextMenu != null && contextMenu.isShowing();
    }

    @Override
    public Map<String, String> saveProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        // štandardné labely sa neukladajú - iba odlišnosti
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                String def = String.valueOf(DEFAULT_LABELS.charAt((ROWS - 1 - row) * COLS + col));
                if (!keyLabels[row][col].equals(def)) {
                    properties.put("label" + (row * COLS + col), keyLabels[row][col]);
                }
            }
        }
        return properties;
    }

    @Override
    public void loadProperties(Map<String, String> properties) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                String label = properties.get("label" + (row * COLS + col));
                if (label != null) {
                    setKeyLabel(row, col, label);
                }
            }
        }
    }

    /**
     * Aktualizácia vizuálu vždy na FX vlákne (simulácia beží na separátnom vlákne).
     * Zmeny sú skoalescované do jednej čakajúcej úlohy.
     */
    private volatile boolean visualUpdateScheduled;

    private void refreshVisual() {
        if (keyPlungers == null || visualUpdateScheduled) return;
        visualUpdateScheduled = true;
        Platform.runLater(() -> {
            visualUpdateScheduled = false;
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    Circle key = keyPlungers[row][col];
                    if (key != null) {
                        key.setFill(isActiveKey(row, col) ? Color.DARKGRAY : Color.BLACK);
                    }
                }
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
    public String getName() {
        return "KBD";
    }

    @Override
    public String getShortDescription() {
        return "Klávesnica 4x4 - riadky R0-R3 (vľavo), stĺpce C0-C3 (hore); ľavým tlačidlom stlač, pravým tlačidlom Toggle a Label";
    }
}