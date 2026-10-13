package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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
import javafx.geometry.VPos;
import sk.uniza.fri.cp.SchematicSim.ContextMenuHelper;
import sk.uniza.fri.cp.SchematicSim.Electrical.PinType;
import sk.uniza.fri.cp.SchematicSim.Pin.InputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.OutputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Side;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tlačidlo - v kľude je výstup {@code O} (slabý push/pull so slabým pull-upom)
 * na logickej 1, po stlačení sa vstup {@code I} behaviorálne prepája na výstup: ak je vstup
 * v logickej 0, výstup sa pretiahne na 0, inak zostane 1. Ľavým tlačidlom myši sa tlačidlo
 * stláča momentovo (stlač a drž). Pravým tlačidlom sa otvorí kontextové menu s položkou
 * {@code Toggle}: ak je zapnutá, každé ľavé stlačenie opakovane prepína stav (sticky/trvalo),
 * ak je vypnutá, tlačidlo je stlačené len počas držania myši. Položka {@code Label} otvorí
 * okno, v ktorom je možné na tlačidlo vpísať jedno písmeno (bielou farbou).
 * Vývod {@code O} je typu {@link PinType#WEAK_OUT} - na spoločnej sieti ho vždy pretiahne
 * silný (push-pull) výstup, takže tlačidlo nemôže spôsobiť skrat.
 *
 * @author Claude (návrh podľa SchematicSim architektúry)
 */
public class PushButton extends GateSymbol {

    private static final int GRID_WIDTH = 2;
    private static final int GRID_HEIGHT = 2;

    private Pin pinIn;
    private Pin pinOut;
    private Circle plunger;
    private ContextMenu contextMenu;
    private CheckMenuItem toggleItem;
    private Text labelText;

    private volatile boolean pressed = false;
    private volatile boolean toggle = false;
    private volatile String label = "";

    /** Konštruktor pre paletku (ItemPicker). */
    public PushButton() {
        super();
    }

    @Override
    public Pane getImage() {
        Text label = new Text(getName());
        label.setLayoutY(label.getBoundsInLocal().getHeight());

        Image image = new Image(getClass().getResourceAsStream("/icons/tlacidlo.png"));
        ImageView view = new ImageView(image);
        view.setLayoutY(label.getBoundsInLocal().getHeight() + 5);

        label.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (image.getWidth() - label.getBoundsInLocal().getWidth()) / 2.0,
                image.widthProperty(), label.boundsInLocalProperty()));

        return new Pane(label, view);
    }

    /** Konštruktor pre umiestnenie na plochu - volaný reflexiou zo SchematicSheet. */
    public PushButton(SchematicSheet sheet) {
        super(sheet);
    }

    @Override
    protected List<Pin> createPins() {
        pinIn = new InputPin(this, "I", 0, 1, Side.LEFT);
        pinOut = new OutputPin(this, "O", 1, 0, Side.TOP);
        // slabý výstup (pull-up): idle HIGH, silný vodič na sieti ho môže pretiahnuť
        pinOut.getOwnedPotential().setType(PinType.WEAK_OUT);
        return Arrays.asList(pinIn, pinOut);
    }

    @Override
    protected Pane drawBody() {
        int cell = getSheet().getComponentCell();
        double w = getGridWidth() * cell;
        double h = getGridHeight() * cell;

        Rectangle body = new Rectangle(w, h);
        body.setFill(Color.WHITESMOKE);
        body.setStroke(Color.GRAY);
        body.setStrokeWidth(3);

        // otlačené tlačidlo; ľavým tlačidlom sa stláča - pri zapnutom Toggle sa každé
        // stlačenie prepne (trvalo), pri vypnutom je stlačené len počas držania myši.
        // Pravým tlačidlom sa otvorí kontextové menu s položkou Toggle.
        plunger = new Circle(w / 2.0, h / 2.0, cell * 0.6, Color.BLACK);
        plunger.setStroke(Color.WHITE);
        plunger.setStrokeWidth(3);
        plunger.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                if (toggle) {
                    setPressedState(!pressed);
                } else {
                    setPressedState(true);
                }
            } else if (event.getButton() == MouseButton.SECONDARY) {
                if (getSheet() != null) showMenu(event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });
        plunger.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !toggle) {
                setPressedState(false);
            }
        });
        plunger.setOnContextMenuRequested(event -> {
            if (getSheet() == null) return;
            if (contextMenu == null || !contextMenu.isShowing()) {
                showMenu(event.getScreenX(), event.getScreenY());
            }
            event.consume();
        });

        // label písmeno na tlačidle - biela farba, vystredené cez plunžer
        labelText = new Text(label);
        labelText.setFill(Color.WHITE);
        labelText.setFont(Font.font(cell * 0.7));
        labelText.setTextOrigin(VPos.CENTER);
        labelText.setMouseTransparent(true);
        centerLabel();

        return new Pane(body, plunger, labelText);
    }

    private void showMenu(double screenX, double screenY) {
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
        ContextMenuHelper.show(contextMenu, plunger, screenX, screenY);
    }

    private void showLabelDialog() {
        TextInputDialog dialog = new TextInputDialog(label);
        dialog.setTitle("Label tlačidla");
        dialog.setHeaderText(null);
        dialog.setContentText("Zadaj jedno písmeno:");
        dialog.getEditor().setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= 1 ? change : null));
        dialog.showAndWait().ifPresent(this::setLabel);
    }

    private void centerLabel() {
        if (labelText == null) return;
        double w = getGridWidth() * getSheet().getComponentCell();
        double h = getGridHeight() * getSheet().getComponentCell();
        labelText.setLayoutY(h / 2.0);
        labelText.setLayoutX(w / 2.0 - labelText.getBoundsInLocal().getWidth() / 2.0);
    }

    @Override
    public void simulate() {
        // kľud: slabý pull-up drží výstup na 1; stlačenie prenáša vstup na výstup
        if (pressed && isLow(pinIn)) {
            setPin(pinOut, Pin.PinState.LOW);
        } else {
            setPin(pinOut, Pin.PinState.HIGH);
        }
    }

    @Override
    public void reset() {
        pressed = false;
        if (pinIn != null) setPinForce(pinIn, Pin.PinState.NOT_CONNECTED);
        if (pinOut != null) setPinForce(pinOut, Pin.PinState.NOT_CONNECTED);
    }

    /**
     * Priame nastavenie stavu stlačenia (ekvivalent kliknutia tlačidla).
     */
    public void setPressedState(boolean value) {
        pressed = value;
        refreshVisual();

        // ak beží simulácia, okamžite prepočítaj vývody, aby sa zmena rozšírila do vodičov a LED
        if (getSheet() != null && getSheet().isSimulationRunning()) {
            simulate();
        }
    }

    public boolean isPressedState() {
        return pressed;
    }

    /**
     * Prepnutie režimu Toggle: pri {@code true} každé ľavé stlačenie trvalo prepne stav,
     * pri {@code false} je tlačidlo stlačené len počas držania myši.
     */
    public void setToggle(boolean value) {
        if (toggle == value) return;
        toggle = value;
        // pri vypnutí Toggle počas držania sa momentovo stlačené tlačidlo vráti do kľudu
        if (!toggle && pressed) {
            setPressedState(false);
        }
    }

    public boolean isToggle() {
        return toggle;
    }

    /**
     * Nastavenie popisu tlačidla - jedno písmeno zobrazené bielou farbou na plunžeri.
     * Prázdny reťazec label odstráni.
     */
    public void setLabel(String value) {
        this.label = value == null ? "" : value;
        if (labelText != null) {
            labelText.setText(label);
            centerLabel();
        }
    }

    public String getLabel() {
        return label;
    }

    public boolean isContextMenuShowing() {
        return contextMenu != null && contextMenu.isShowing();
    }

    @Override
    public Map<String, String> saveProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        if (!label.isEmpty()) {
            properties.put("label", label);
        }
        return properties;
    }

    @Override
    public void loadProperties(Map<String, String> properties) {
        String label = properties.get("label");
        if (label != null) {
            setLabel(label);
        }
    }

    /**
     * Aktualizácia vizuálu vždy na FX vlákne (simulácia beží na separátnom vlákne).
     * Zmeny sú skoalescované do jednej čakajúcej úlohy.
     */
    private volatile boolean visualUpdateScheduled;

    private void refreshVisual() {
        if (plunger == null || visualUpdateScheduled) return;
        visualUpdateScheduled = true;
        Platform.runLater(() -> {
            visualUpdateScheduled = false;
            plunger.setFill(pressed ? Color.DARKGRAY : Color.BLACK);
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
        return "BTN";
    }

    @Override
    public String getShortDescription() {
        return "Tlačidlo - ľavým tlačidlom stlačíš (0/1), pravým tlačidlom Toggle a Label";
    }
}