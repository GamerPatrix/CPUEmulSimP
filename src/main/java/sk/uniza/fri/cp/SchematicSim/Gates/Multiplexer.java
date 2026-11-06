package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.beans.binding.Bindings;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multiplexor typu 8-1, 4-1 alebo 2-1 - typ sa volí cez kontextové menu (pravé kliknutie).
 * <p>
 * Vstupy sú vľavo, zhora v poradí: adresné A0.. (1 až 3 podľa typu), povolenie E/
 * (aktívne v 0) a dátové I0.. (2 až 8 podľa typu). Výstup O je vpravo.
 * Pri E/=0 je na výstupe hodnota adresovaného dátového vstupu, pri E/=1 je výstup v 0.
 * <p>
 * Typ sa ukladá do schémy ako vlastnosť {@code inputs} a obnovuje sa pri načítaní ešte pred
 * pripájaním vodičov. Poradie pinov v zozname je rovnaké ako poradie na súčiastke + výstup
 * na konci, takže indexy pinov vo vodičoch zodpovedajú uloženému typu.
 *
 * @author Claude (návrh podľa SchematicSim architektúry, inšpirované MultiInputGate)
 */
public class Multiplexer extends GateSymbol {

    private static final String ICON_PATH = "/icons/multiplexor.png";
    private static final int GRID_WIDTH = 3;
    private static final int[] SUPPORTED_COUNTS = {2, 4, 8};
    /** Najmenší typ - nový multiplexor je 2-1, kým sa nezvolí iný typ. */
    private static final int DEFAULT_DATA_INPUTS = 2;

    // Polia bez inicializátorov: createPins() a drawBody() volá už konštruktor predka
    // a inicializátor by nastavené hodnoty následne prepísal.
    private int dataInputs;
    private Pin[] addressPins;
    private Pin pinE;
    private Pin[] dataPins;
    private Pin output;

    private ContextMenu contextMenu;

    /** Konštruktor pre paletku (ItemPicker). */
    public Multiplexer() {
        super();
    }

    /** Konštruktor pre umiestnenie na plochu - volaný reflexiou zo SchematicSheet. */
    public Multiplexer(SchematicSheet sheet) {
        super(sheet);
        this.addEventHandler(MouseEvent.MOUSE_PRESSED, this::showContextMenu);
    }

    @Override
    public Pane getImage() {
        Text label = new Text(getName());
        label.setLayoutY(label.getBoundsInLocal().getHeight());

        InputStream stream = getClass().getResourceAsStream(ICON_PATH);
        if (stream != null) {
            Image image = new Image(stream);
            ImageView view = new ImageView(image);
            view.setLayoutY(label.getBoundsInLocal().getHeight() + 5);

            label.layoutXProperty().bind(Bindings.createDoubleBinding(
                    () -> (image.getWidth() - label.getBoundsInLocal().getWidth()) / 2.0,
                    image.widthProperty(), label.boundsInLocalProperty()));

            return new Pane(label, view);
        }
        return new Pane(label);
    }

    private void showContextMenu(MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY) {
            // počas simulácie sa typ meniť nesmie - mení sa tým zapojenie
            if (getSheet() == null || !getSheet().isEditingEnabled()) return;
            if (contextMenu == null) contextMenu = buildContextMenu();
            ContextMenuHelper.show(contextMenu, this, event.getScreenX(), event.getScreenY());
            event.consume();
        }
    }

    /** Menu sa vytvára až pri prvom zobrazení, aby označený typ zodpovedal aj načítanej schéme. */
    private ContextMenu buildContextMenu() {
        ContextMenu menu = new ContextMenu();
        ToggleGroup group = new ToggleGroup();
        for (int count : SUPPORTED_COUNTS) {
            RadioMenuItem item = new RadioMenuItem(count + "-1");
            item.setToggleGroup(group);
            item.setSelected(count == getDataInputCount());
            item.setOnAction(event -> {
                setDataInputCount(count);
                getSheet().markChanged();
            });
            menu.getItems().add(item);
        }
        return menu;
    }

    @Override
    protected List<Pin> createPins() {
        addressPins = resizeInputs(new Pin[0], addressBits(), "A");
        pinE = new InputPin(this, "E/", 0, 0, Side.LEFT);
        dataPins = resizeInputs(new Pin[0], getDataInputCount(), "I");
        output = new OutputPin(this, "O", GRID_WIDTH, 0, Side.RIGHT);
        layoutPins();
        return allPins();
    }

    @Override
    protected Pane drawBody() {
        int cell = getSheet().getComponentCell();
        Rectangle body = new Rectangle(GRID_WIDTH * cell, getGridHeight() * cell, Color.WHITE);
        body.setStroke(Color.BLACK);
        body.setStrokeWidth(3);

        Pane pane = new Pane();
        pane.getChildren().add(body);

        Text title = new Text("MUX");
        title.setLayoutY(-cell * 0.2);
        title.setFont(Font.font(cell * 0.6));
        title.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (GRID_WIDTH * cell - title.getBoundsInLocal().getWidth()) / 2.0,
                title.boundsInLocalProperty()));
        registerNameLabel(title);
        pane.getChildren().add(title);

        for (int index = 0; index < addressBits(); index++) {
            pane.getChildren().add(inputLabel("A" + index, addressRow(index), cell));
        }
        pane.getChildren().add(inputLabel("E/", enableRow(), cell));
        for (int index = 0; index < getDataInputCount(); index++) {
            pane.getChildren().add(inputLabel("I" + index, dataRow(index), cell));
        }

        Text outputLabel = new Text("O");
        outputLabel.setLayoutX(cell * 2.1);
        outputLabel.setLayoutY(outputRow() * cell + cell * 0.25);
        pane.getChildren().add(outputLabel);

        return pane;
    }

    private static Text inputLabel(String text, int row, int cell) {
        Text label = new Text(text);
        label.setLayoutX(cell * 0.4);
        label.setLayoutY(row * cell + cell * 0.25);
        return label;
    }

    @Override
    public void simulate() {
        // E/ je aktívne v 0; neaktívny obvod má výstup v 0
        if (isHigh(pinE)) {
            setPin(output, Pin.PinState.LOW);
            return;
        }

        int address = 0;
        for (int bit = 0; bit < addressPins.length; bit++) {
            if (isHigh(addressPins[bit])) address |= 1 << bit;
        }
        setPin(output, isHigh(dataPins[address]) ? Pin.PinState.HIGH : Pin.PinState.LOW);
    }

    @Override
    public void reset() {
        if (output != null) setPinForce(output, Pin.PinState.NOT_CONNECTED);
    }

    /** Počet dátových vstupov: 8, 4 alebo 2. */
    public int getDataInputCount() {
        return Math.max(dataInputs, DEFAULT_DATA_INPUTS);
    }

    /**
     * Zmena typu multiplexora. Zachované vývody ostanú tými istými objektmi a pripojené
     * vodiče sa s nimi iba presunú; vodiče na odoberaných vývodoch sa vymažú.
     */
    public void setDataInputCount(int count) {
        if (!isSupported(count) || count == getDataInputCount()) return;

        getSheet().getOccupancy().free(this);
        this.dataInputs = count;

        addressPins = resizeInputs(addressPins, addressBits(), "A");
        dataPins = resizeInputs(dataPins, count, "I");
        layoutPins();

        getChildren().set(0, drawBody());
        replacePins(allPins());
        getSheet().getOccupancy().occupy(this);
        refreshSelectionShape();

        // koniec vodiča treba presunúť až potom, keď sú na svojom mieste všetky vývody
        for (Pin pin : getPins()) {
            if (pin.getWireEnd() != null) pin.getWireEnd().refreshPosition();
        }
    }

    /**
     * Zmena počtu vstupov jednej skupiny: prebytočné vývody sa odoberú aj s vodičmi, chýbajúce
     * sa vytvoria. Súčiastka z konštruktora predka ešte nemá vývody medzi potomkami - tie
     * pridá predok zo zoznamu {@link #createPins()}, preto sa pridávajú iba pri zmene typu.
     */
    private Pin[] resizeInputs(Pin[] current, int count, String namePrefix) {
        for (int index = count; index < current.length; index++) {
            Pin removed = current[index];
            if (removed.getWireEnd() != null) removed.getWireEnd().getWire().delete();
            getChildren().remove(removed);
        }

        Pin[] resized = Arrays.copyOf(current, count);
        for (int index = current.length; index < count; index++) {
            resized[index] = new InputPin(this, namePrefix + index, 0, 0, Side.LEFT);
            if (getPins() != null) getChildren().add(resized[index]);
        }
        return resized;
    }

    private void layoutPins() {
        int cell = getSheet().getComponentCell();
        for (int index = 0; index < addressPins.length; index++) {
            addressPins[index].setLayoutY(addressRow(index) * cell);
        }
        pinE.setLayoutY(enableRow() * cell);
        for (int index = 0; index < dataPins.length; index++) {
            dataPins[index].setLayoutY(dataRow(index) * cell);
        }
        output.setLayoutY(outputRow() * cell);
    }

    private List<Pin> allPins() {
        List<Pin> pins = new ArrayList<>();
        pins.addAll(Arrays.asList(addressPins));
        pins.add(pinE);
        pins.addAll(Arrays.asList(dataPins));
        pins.add(output);
        return pins;
    }

    private int addressBits() {
        return Integer.numberOfTrailingZeros(getDataInputCount());
    }

    private int addressRow(int index) {
        return 1 + index;
    }

    private int enableRow() {
        return 1 + addressBits();
    }

    private int dataRow(int index) {
        return 2 + addressBits() + index;
    }

    private int outputRow() {
        return getGridHeight() / 2;
    }

    private static boolean isSupported(int count) {
        for (int supported : SUPPORTED_COUNTS) {
            if (count == supported) return true;
        }
        return false;
    }

    @Override
    public Map<String, String> saveProperties() {
        Map<String, String> properties = new LinkedHashMap<>(super.saveProperties());
        properties.put("inputs", Integer.toString(getDataInputCount()));
        return properties;
    }

    @Override
    public void loadProperties(Map<String, String> properties) {
        super.loadProperties(properties);
        String value = properties.get("inputs");
        if (value == null) return;

        try {
            setDataInputCount(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public int getGridWidth() {
        return GRID_WIDTH;
    }

    @Override
    public int getGridHeight() {
        return addressBits() + getDataInputCount() + 2;
    }

    @Override
    public String getName() {
        return "MUX";
    }

    @Override
    public String getInstanceNamePrefix() {
        return "MUX";
    }

    @Override
    public String getShortDescription() {
        return "Multiplexor 8-1, 4-1 alebo 2-1 (typ cez pravé tlačidlo) - pri E/=0 je na O adresovaný vstup, pri E/=1 je O v 0";
    }
}
