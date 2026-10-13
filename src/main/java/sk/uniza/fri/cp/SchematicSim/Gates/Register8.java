package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.beans.binding.Bindings;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import sk.uniza.fri.cp.SchematicSim.ContextMenuHelper;
import sk.uniza.fri.cp.SchematicSim.Pin.InputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.OutputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Side;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 8-bitový register (D-type latch, transparentný pri LE=1).
 * <p>
 * Dátové vstupy D0..D7 sa počas aktívneho {@code LE} (log. 1) transparentne premietajú
 * do registra; po zániku LE si register hodnotu podrží. Výstupy Q0..Q7 sú tri-state:
 * pri aktívnom {@code OE_} (log. 0) hnané obsahom registra, pri neaktívnom (log. 1)
 * v stave vysokej impedancie (Z), aby mohol po zbernici hnať dáta iný zdroj.
 * <p>
 * Pravým tlačidlom myši sa otvorí kontextové menu s položkou "Vymeň D-Q", ktorá prehodí
 * dátové vstupy Dx a výstupy Qx (výstupy naľavo, vstupy napravo). Poloha riadiacich
 * signálov {@code OE_} a {@code LE} zostáva nezmenená.
 *
 * @author Claude (návrh podľa SchematicSim architektúry)
 */
public class Register8 extends GateSymbol {

    private static final int GRID_WIDTH = 4;
    private static final int GRID_HEIGHT = 11;

    private final ContextMenu contextMenu = new ContextMenu();

    private Pin[] dataPins;
    private Pin[] outputPins;
    private Pin pinOE_;
    private Pin pinLE;

    private boolean swapped = false;
    private int latch = 0;

    /** Konštruktor pre paletku (ItemPicker). */
    public Register8() {
        super();
    }

    @Override
    public Pane getImage() {
        Text label = new Text("REG");
        label.setLayoutY(label.getBoundsInLocal().getHeight());

        Image image = new Image(getClass().getResourceAsStream("/icons/register.png"));
        ImageView view = new ImageView(image);
        view.setLayoutY(label.getBoundsInLocal().getHeight() + 5);

        label.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (image.getWidth() - label.getBoundsInLocal().getWidth()) / 2.0,
                image.widthProperty(), label.boundsInLocalProperty()));

        return new Pane(label, view);
    }

    /** Konštruktor pre umiestnenie na plochu - volaný reflexiou zo SchematicSheet. */
    public Register8(SchematicSheet sheet) {
        super(sheet);
        buildContextMenu();
        this.addEventHandler(MouseEvent.MOUSE_PRESSED, this::showContextMenu);
    }

    /**
     * Vytvorenie a naviazanie kontextového menu s položkou "Vymeň D-Q".
     */
    private void buildContextMenu() {
        MenuItem swapItem = new MenuItem("Vymeň D-Q");
        swapItem.setOnAction(event -> toggleSwap());
        contextMenu.getItems().add(swapItem);
    }

    /**
     * Zobrazenie kontextového menu po stlačení pravého tlačidla myši.
     */
    private void showContextMenu(MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY) {
            ContextMenuHelper.show(contextMenu, this, event.getScreenX(), event.getScreenY());
            event.consume();
        }
    }

    /**
     * Prehodenie vstupov Dx a výstupov Qx: výstupy idú naľavo, vstupy napravo,
     * riadiace signály OE_ a LE zostávajú naľavo.
     */
    private void toggleSwap() {
        swapped = !swapped;
        applySwap();
    }

    /** Premietnutie {@link #swapped} do pozícií pinov Dx/Qx a ich popiskov. */
    private void applySwap() {
        int cell = getSheet().getComponentCell();
        Text[] dLabels = (Text[]) bodyPane().getProperties().get("dLabels");
        Text[] qLabels = (Text[]) bodyPane().getProperties().get("qLabels");
        for (int index = 0; index < 8; index++) {
            if (swapped) {
                dataPins[index].setLayoutX(GRID_WIDTH * cell);
                dataPins[index].setSide(Side.RIGHT);
                outputPins[index].setLayoutX(0);
                outputPins[index].setSide(Side.LEFT);
            } else {
                dataPins[index].setLayoutX(0);
                dataPins[index].setSide(Side.LEFT);
                outputPins[index].setLayoutX(GRID_WIDTH * cell);
                outputPins[index].setSide(Side.RIGHT);
            }

            // vodič pripojený na pin presuň na novú polohu a preveď rerouting
            refreshWireEnd(dataPins[index]);
            refreshWireEnd(outputPins[index]);

            dLabels[index].setLayoutX(swapped ? cell * 2.6 : cell * 0.4);
            qLabels[index].setLayoutX(swapped ? cell * 0.4 : cell * 2.6);
        }
    }

    private static void refreshWireEnd(Pin pin) {
        if (pin.getWireEnd() != null) pin.getWireEnd().refreshPosition();
    }

    /** Pane tela súčiastky (prvé dieťa - pridáva ho GateSymbol do potomkov). */
    private Pane bodyPane() {
        return (Pane) getChildren().get(0);
    }

    @Override
    protected List<Pin> createPins() {
        List<Pin> pins = new ArrayList<>();

        // lavy okraj: dátové vstupy a riadiace signály (prvý riadok je prázdny)
        dataPins = new Pin[8];
        for (int index = 0; index < 8; index++) {
            dataPins[index] = new InputPin(this, "D" + index, 0, index + 1, Side.LEFT);
            pins.add(dataPins[index]);
        }
        pinOE_ = new InputPin(this, "OE/", 0, 9, Side.LEFT);
        pinLE = new InputPin(this, "LE", 0, 10, Side.LEFT);
        pins.add(pinOE_);
        pins.add(pinLE);

        // pravy okraj: výstupy (tri-state)
        outputPins = new OutputPin[8];
        for (int index = 0; index < 8; index++) {
            outputPins[index] = new OutputPin(this, "Q" + index, GRID_WIDTH, index + 1, Side.RIGHT);
            pins.add(outputPins[index]);
        }

        return pins;
    }

    @Override
    protected Pane drawBody() {
        int cell = getSheet().getComponentCell();
        Rectangle body = new Rectangle(GRID_WIDTH * cell, GRID_HEIGHT * cell, Color.WHITE);
        body.setStroke(Color.BLACK);
        body.setStrokeWidth(3);

        Pane pane = new Pane();
        pane.getChildren().add(body);

        Text[] dLabels = new Text[8];
        Text[] qLabels = new Text[8];

        Text title = new Text("REGISTER");
        title.setLayoutY(-cell * 0.2);
        title.setFont(Font.font(cell * 0.6));
        title.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (GRID_WIDTH * cell - title.getBoundsInLocal().getWidth()) / 2.0,
                title.boundsInLocalProperty()));
        registerNameLabel(title);
        pane.getChildren().add(title);

        for (int index = 0; index < 8; index++) {
            Text dLabel = new Text("D" + (index + 1));
            dLabel.setLayoutX(cell * 0.4);
            dLabel.setLayoutY((index + 1) * cell + cell * 0.25);
            dLabels[index] = dLabel;

            Text qLabel = new Text("Q" + (index + 1));
            qLabel.setLayoutX(cell * 2.6);
            qLabel.setLayoutY((index + 1) * cell + cell * 0.25);
            qLabels[index] = qLabel;

            pane.getChildren().add(dLabel);
            pane.getChildren().add(qLabel);
        }

        // popisky D/Q si odložíme, aby ich aplikácia mohla prehadzovať pri "Vymeň D-Q"
        pane.getProperties().put("dLabels", dLabels);
        pane.getProperties().put("qLabels", qLabels);

        Text oeLabel = new Text("OE/");
        oeLabel.setLayoutX(cell * 0.4);
        oeLabel.setLayoutY(cell * 9.3);

        Text leLabel = new Text("LE");
        leLabel.setLayoutX(cell * 0.4);
        leLabel.setLayoutY(cell * 10.3);

        pane.getChildren().add(oeLabel);
        pane.getChildren().add(leLabel);

        return pane;
    }

    @Override
    public void simulate() {
        // počas aktívneho LE (log. 1) sa dáta transparentne premietajú do registra
        if (isHigh(pinLE)) {
            latch = readData();
        }

        // pri aktívnom OE_ (log. 0) outputs hná obsah registra, inak sú v Z
        if (isLow(pinOE_)) {
            driveOutputs(latch);
        } else {
            setOutputsImpedance();
        }
    }

    @Override
    public void reset() {
        if (getPins() == null) return;
        for (Pin pin : getPins()) pin.setState(Pin.PinState.NOT_CONNECTED);
    }

    private int readData() {
        int value = 0;
        for (int index = 0; index < 8; index++) {
            if (isHigh(dataPins[index])) value |= 1 << index;
        }
        return value & 0xFF;
    }

    private void driveOutputs(int value) {
        for (int index = 0; index < 8; index++) {
            setPin(outputPins[index],
                    ((value & (1 << index)) != 0) ? Pin.PinState.HIGH : Pin.PinState.LOW);
        }
    }

    private void setOutputsImpedance() {
        for (int index = 0; index < 8; index++) {
            setPin(outputPins[index], Pin.PinState.HIGH_IMPEDANCE);
        }
    }

    /**
     * Aktuálny obsah registra (iba na čítanie v testoch).
     */
    public int getLatch() {
        return latch;
    }

    /**
     * Je zapnuté prehodenie Dx/Qx (výstupy naľavo, vstupy napravo)?
     */
    public boolean isSwapped() {
        return swapped;
    }

    @Override
    public Map<String, String> saveProperties() {
        Map<String, String> properties = new LinkedHashMap<>(super.saveProperties());
        properties.put("swapped", Boolean.toString(swapped));
        return properties;
    }

    @Override
    public void loadProperties(Map<String, String> properties) {
        super.loadProperties(properties);
        String swappedValue = properties.get("swapped");
        if (swappedValue != null) {
            swapped = Boolean.parseBoolean(swappedValue.trim());
            applySwap();
        }
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
        return "Register 8";
    }

    @Override
    public String getInstanceNamePrefix() {
        return "REG";
    }

    @Override
    public String getShortDescription() {
        return "8-bitový register (D-type latch) - LE=1 zapisuje dáta, OE/=0 povoľuje výstupy";
    }
}