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
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Sheet.SheetEvent;
import sk.uniza.fri.cp.SchematicSim.Side;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 8 kB pamäť RAM (8192 × 8 bitov).
 * <p>
 * Riadiace signály:
 * <ul>
 *   <li>{@code E1_} – musí byť 0, inak sú dátové vývody v stave Z</li>
 *   <li>{@code E2} – musí byť 1, inak sú dátové vývody v stave Z</li>
 *   <li>{@code W_} – zápis pri log. 0 (dáta sa zapíšu do pamäte, vývody sa uvoľnia)</li>
 *   <li>{@code G_} – čítanie pri log. 0 (pamäť naháňa dáta na D0..D7)</li>
 * </ul>
 */
public class Ram8k extends GateSymbol {

    private static final int GRID_WIDTH = 5;
    private static final int GRID_HEIGHT = 14;

    private final byte[] memory = new byte[8192];
    private final ContextMenu contextMenu = new ContextMenu();
    private RAMContentWindow contentWindow;

    private Pin[] addressPins;
    private Pin[] dataPins;
    private Pin pinW_;
    private Pin pinG_;
    private Pin pinE1_;
    private Pin pinE2;

    public Ram8k() {
        super();
    }

    @Override
    public Pane getImage() {
        Text label = new Text("8kB RAM");
        label.setLayoutY(label.getBoundsInLocal().getHeight());

        Image image = new Image(getClass().getResourceAsStream("/icons/ram.png"));
        ImageView view = new ImageView(image);
        view.setLayoutY(label.getBoundsInLocal().getHeight() + 5);

        label.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (image.getWidth() - label.getBoundsInLocal().getWidth()) / 2.0,
                image.widthProperty(), label.boundsInLocalProperty()));

        return new Pane(label, view);
    }

    public Ram8k(SchematicSheet sheet) {
        super(sheet);
        buildContextMenu();
        this.addEventHandler(MouseEvent.MOUSE_PRESSED, this::showContextMenu);
    }

    /**
     * Vytvorenie a naviazanie kontextového menu s položkami "Obsah" (zobrazenie obsahu pamäte)
     * a "Reset" (vymazanie celej pamäte).
     */
    private void buildContextMenu() {
        MenuItem contentItem = new MenuItem("Obsah");
        contentItem.setOnAction(event -> openContentWindow());
        MenuItem resetItem = new MenuItem("Reset");
        resetItem.setOnAction(event -> clearMemory());
        contextMenu.getItems().addAll(contentItem, resetItem);
    }

    /**
     * Vymazanie celej pamäte (všetky bajty na 0). Otvorené okno s obsahom sa obnoví samo.
     * Pri bežiacej simulácii sa RAM prepočíta na simulačnom vlákne - pri práve prebiehajúcom
     * čítaní tak hneď budí na dátové vývody vymazanú hodnotu.
     */
    private void clearMemory() {
        Arrays.fill(memory, (byte) 0);
        SchematicSheet sheet = getSheet();
        if (sheet != null && sheet.isSimulationRunning()) {
            sheet.addEvent(new SheetEvent(pinG_));
        }
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
     * Otvorenie samostatného okna s obsahom pamäte RAM.
     */
    private void openContentWindow() {
        if (contentWindow == null) contentWindow = new RAMContentWindow();
        contentWindow.showContent(memory);
    }

    @Override
    public void delete() {
        // okno s obsahom zmazanej RAM by ďalej ukazovalo pamäť, ktorá už na ploche nie je
        if (contentWindow != null) contentWindow.close();
        super.delete();
    }

    @Override
    protected List<Pin> createPins() {
        List<Pin> pins = new ArrayList<>();

        // lavy okraj: dátové vývody (双向) a riadiace signály
        dataPins = new Pin[8];
        for (int index = 0; index < 8; index++) {
            dataPins[index] = new IoPin(this, "D" + index, 0, index + 1, Side.LEFT);
            pins.add(dataPins[index]);
        }
        pinW_ = new InputPin(this, "W/", 0, 9, Side.LEFT);
        pinG_ = new InputPin(this, "G/", 0, 10, Side.LEFT);
        pinE1_ = new InputPin(this, "E1/", 0, 11, Side.LEFT);
        pinE2 = new InputPin(this, "E2", 0, 12, Side.LEFT);
        pins.add(pinW_);
        pins.add(pinG_);
        pins.add(pinE1_);
        pins.add(pinE2);

        // pravy okraj: adresové vývody
        addressPins = new Pin[13];
        for (int index = 0; index < 13; index++) {
            addressPins[index] = new InputPin(this, "A" + index, GRID_WIDTH, index + 1, Side.RIGHT);
            pins.add(addressPins[index]);
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

        Text title = new Text("RAM 8k×8");
        title.setLayoutY(-cell * 0.2);
        title.setFont(Font.font(cell * 0.6));
        title.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (GRID_WIDTH * cell - title.getBoundsInLocal().getWidth()) / 2.0,
                title.boundsInLocalProperty()));
        registerNameLabel(title);
        pane.getChildren().add(title);

        // lave popisy: D0..D7
        for (int index = 0; index < 8; index++) {
            Text dLabel = new Text("D" + index);
            dLabel.setLayoutX(cell * 0.4);
            dLabel.setLayoutY((index + 1) * cell + cell * 0.25);
            pane.getChildren().add(dLabel);
        }

        // lave popisy: riadiace signály
        Text wLabel = new Text("W/");
        wLabel.setLayoutX(cell * 0.4);
        wLabel.setLayoutY(9 * cell + cell * 0.25);
        pane.getChildren().add(wLabel);

        Text gLabel = new Text("G/");
        gLabel.setLayoutX(cell * 0.4);
        gLabel.setLayoutY(10 * cell + cell * 0.25);
        pane.getChildren().add(gLabel);

        Text e1Label = new Text("E1/");
        e1Label.setLayoutX(cell * 0.4);
        e1Label.setLayoutY(11 * cell + cell * 0.25);
        pane.getChildren().add(e1Label);

        Text e2Label = new Text("E2");
        e2Label.setLayoutX(cell * 0.4);
        e2Label.setLayoutY(12 * cell + cell * 0.25);
        pane.getChildren().add(e2Label);

        // prave popisy: A0..A12
        for (int index = 0; index < 13; index++) {
            Text aLabel = new Text("A" + index);
            aLabel.setLayoutX(cell * 3.6);
            aLabel.setLayoutY((index + 1) * cell + cell * 0.25);
            pane.getChildren().add(aLabel);
        }

        return pane;
    }

    @Override
    public void simulate() {
        boolean chipEnabled = isLow(pinE1_) && isHigh(pinE2);

        if (!chipEnabled) {
            setDataImpedance();
            return;
        }

        if (isLow(pinW_)) {
            // zápis
            memory[readAddress()] = (byte) readData();
            setDataImpedance();
        } else if (isLow(pinG_)) {
            // čítanie
            driveData(memory[readAddress()] & 0xFF);
        } else {
            setDataImpedance();
        }
    }

    @Override
    public void reset() {
        if (getPins() == null) return;
        for (Pin pin : getPins()) pin.setState(Pin.PinState.NOT_CONNECTED);
    }

    private int readAddress() {
        int address = 0;
        for (int index = 0; index < 13; index++) {
            if (isHigh(addressPins[index])) address |= 1 << index;
        }
        return address;
    }

    private int readData() {
        int value = 0;
        for (int index = 0; index < 8; index++) {
            if (isHigh(dataPins[index])) value |= 1 << index;
        }
        return value & 0xFF;
    }

    private void driveData(int value) {
        for (int index = 0; index < 8; index++) {
            setPin(dataPins[index],
                    ((value & (1 << index)) != 0) ? Pin.PinState.HIGH : Pin.PinState.LOW);
        }
    }

    private void setDataImpedance() {
        for (int index = 0; index < 8; index++) {
            setPin(dataPins[index], Pin.PinState.HIGH_IMPEDANCE);
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
        return "RAM 8k";
    }

    @Override
    public String getInstanceNamePrefix() {
        return "RAM";
    }

    @Override
    public String getShortDescription() {
        return "8kB RAM – E1/=0 & E2=1 zapne čip, W/=0 zápis, G/=0 čítanie";
    }

    private static class IoPin extends Pin {
        IoPin(GateSymbol owner, String name, int gridOffsetX, int gridOffsetY, Side side) {
            super(owner, name, Direction.INOUT, gridOffsetX, gridOffsetY, side);
        }
    }
}
