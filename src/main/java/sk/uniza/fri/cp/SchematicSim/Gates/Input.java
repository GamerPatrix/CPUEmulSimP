package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.VPos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import sk.uniza.fri.cp.SchematicSim.ContextMenuHelper;
import sk.uniza.fri.cp.SchematicSim.Pin.OutputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Side;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Samostatný prepínač - biely štvorček s jedným prípojným bodom {@code O}. Kliknutím
 * na štvorček sa stav prepne a vnútri sa zobrazí logická hodnota vývodu (1 = HIGH, 0 = LOW).
 * Pravým tlačidlom myši sa otvorí kontextové menu na výber umiestnenia vývodu
 * (vpravo/vľavo/hore/dole); štandardne je vývod vpravo. Vývod {@code O} trvalo generuje
 * HIGH pre zapnutý prepínač a LOW pre vypnutý.
 *
 * @author Claude (návrh podľa SchematicSim architektúry)
 */
public class Input extends GateSymbol {

    private static final int GRID_WIDTH = 2;
    private static final int GRID_HEIGHT = 2;

    private Pin outPin;
    private Rectangle button;
    private Text valueText;
    private ContextMenu contextMenu;

    private volatile boolean on = false;

    /** Konštruktor pre paletku (ItemPicker). */
    public Input() {
        super();
    }

    @Override
    public Pane getImage() {
        Text label = new Text(getName());
        label.setLayoutY(label.getBoundsInLocal().getHeight());

        Image image = new Image(getClass().getResourceAsStream("/icons/input.png"));
        ImageView view = new ImageView(image);
        view.setLayoutY(label.getBoundsInLocal().getHeight() + 5);

        label.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (image.getWidth() - label.getBoundsInLocal().getWidth()) / 2.0,
                image.widthProperty(), label.boundsInLocalProperty()));

        return new Pane(label, view);
    }

    /** Konštruktor pre umiestnenie na plochu - volaný reflexiou zo SchematicSheet. */
    public Input(SchematicSheet sheet) {
        super(sheet);
    }

    @Override
    protected List<Pin> createPins() {
        outPin = new OutputPin(this, "O", GRID_WIDTH, 1, Side.RIGHT);
        return Arrays.asList(outPin);
    }

    @Override
    protected Pane drawBody() {
        int cell = getSheet().getComponentCell();
        double w = getGridWidth() * cell;
        double h = getGridHeight() * cell;

        double size = cell * 0.9;
        button = new Rectangle(w / 2.0 - size / 2.0, h / 2.0 - size / 2.0, size, size);
        button.setFill(Color.WHITE);
        button.setStroke(Color.BLACK);
        button.setStrokeWidth(2);
        button.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                handleToggle();
            }
        });
        // menu sa otvára na pravom tlačidle myši; MOUSE_PRESSED je spoľahlivejší ako
        // CONTEXT_MENU_REQUESTED (ten sa negeneruje, ak pravý stlač počas cesty niekto skonzumuje)
        button.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                if (getSheet() != null && getSheet().isEditingEnabled()) showPlacementMenu(event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });
        button.setOnContextMenuRequested(event -> {
            if (getSheet() == null || !getSheet().isEditingEnabled()) return;
            if (contextMenu == null || !contextMenu.isShowing()) {
                showPlacementMenu(event.getScreenX(), event.getScreenY());
            }
            event.consume();
        });

        valueText = new Text("0");
        valueText.setTextOrigin(VPos.CENTER);
        valueText.setLayoutY(h / 2.0);
        valueText.setFont(Font.font(cell * 0.6));
        valueText.setMouseTransparent(true);
        valueText.setLayoutX(w / 2.0 - valueText.getBoundsInLocal().getWidth() / 2.0);

        return new Pane(button, valueText);
    }

    private void showPlacementMenu(double screenX, double screenY) {
        if (contextMenu == null) {
            contextMenu = new ContextMenu(
                    new MenuItem("Vývod vpravo"),
                    new MenuItem("Vývod vľavo"),
                    new MenuItem("Vývod hore"),
                    new MenuItem("Vývod dole"));
            contextMenu.getItems().get(0).setOnAction(e -> placeOutput(Side.RIGHT));
            contextMenu.getItems().get(1).setOnAction(e -> placeOutput(Side.LEFT));
            contextMenu.getItems().get(2).setOnAction(e -> placeOutput(Side.TOP));
            contextMenu.getItems().get(3).setOnAction(e -> placeOutput(Side.BOTTOM));
        }
        ContextMenuHelper.show(contextMenu, button, screenX, screenY);
    }

    public void placeOutput(Side side) {
        int cell = getSheet().getComponentCell();
        outPin.setLayoutX(placementOffsetX(side) * cell);
        outPin.setLayoutY(placementOffsetY(side) * cell);
        outPin.setSide(side);

        // ak na vývode už visí vodič, presuň jeho koniec na novú polohu a preveď rerouting
        if (outPin.getWireEnd() != null) {
            outPin.getWireEnd().refreshPosition();
        }
    }

    private static int placementOffsetX(Side side) {
        switch (side) {
            case LEFT: return 0;
            case RIGHT: return GRID_WIDTH;
            case TOP:
            case BOTTOM: return 1;
        }
        return GRID_WIDTH;
    }

    private static int placementOffsetY(Side side) {
        switch (side) {
            case TOP: return 0;
            case BOTTOM: return GRID_HEIGHT;
            case LEFT:
            case RIGHT: return 1;
        }
        return 1;
    }

    @Override
    public void simulate() {
        // prepínač generuje výstup stále
        setPin(outPin, on ? Pin.PinState.HIGH : Pin.PinState.LOW);
    }

    @Override
    public void reset() {
        if (outPin != null) setPinForce(outPin, Pin.PinState.NOT_CONNECTED);
    }

    private void handleToggle() {
        setOn(!on);
    }

    /**
     * Priame nastavenie stavu prepínača (ekvivalent kliknutia).
     */
    public void setOn(boolean value) {
        on = value;
        refreshVisual();

        // ak beží simulácia, okamžite prepočítaj vývody, aby sa zmena rozšírila do vodičov a LED
        if (getSheet() != null && getSheet().isSimulationRunning()) {
            simulate();
        }
    }

    /**
     * Aktualizácia vizuálu vždy na FX vlákne (simulácia beží na separátnom vlákne).
     * Zmeny sú skoalescované do jednej čakajúcej úlohy, aby sa pri rýchlej simulácii
     * FX vlákno nezahltilo radom a aplikácia (klávesy F4/F5/F10...) ostala odozvá.
     */
    private volatile boolean visualUpdateScheduled;

    private void refreshVisual() {
        if (button == null || valueText == null || visualUpdateScheduled) return;
        visualUpdateScheduled = true;
        Platform.runLater(() -> {
            visualUpdateScheduled = false;
            String text = on ? "1" : "0";
            valueText.setText(text);
            valueText.setLayoutX(wCenter() - valueText.getBoundsInLocal().getWidth() / 2.0);
        });
    }

    private double wCenter() {
        return getSheet().getComponentCell() * getGridWidth() / 2.0;
    }

    public boolean isOn() {
        return on;
    }

    @Override
    public Map<String, String> saveProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("pinSide", outPin.getExitSide().name());
        properties.put("state", on ? "1" : "0");
        return properties;
    }

    @Override
    public void loadProperties(Map<String, String> properties) {
        String side = properties.get("pinSide");
        if (side != null) {
            try {
                placeOutput(Side.valueOf(side));
            } catch (IllegalArgumentException ignored) {
            }
        }

        String state = properties.get("state");
        if (state != null) {
            setOn("1".equals(state));
        }
    }

    public boolean isContextMenuShowing() {
        return contextMenu != null && contextMenu.isShowing();
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
        return "INP";
    }

    @Override
    public String getShortDescription() {
        return "Prepínač - klikom prepni, pravým tlačidlom vyber umiestnenie vývodu O (vpravo/vľavo/hore/dole)";
    }
}