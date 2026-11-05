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
import java.util.function.IntFunction;

/**
 * Dekodér 3 na 8 alebo 2 na 4 s povolovacím vstupom G a aktívnymi výstupmi v 0 (low) - typ
 * sa volí cez kontextové menu (pravé kliknutie).
 * <p>
 * Pri G=0 sú všetky výstupy Y0.. v log. 1; pri G=1 kombinácia adresných vstupov
 * (C,)B,A určuje poradové číslo výstupu, ktorý bude v log. 0 (ostatné ostanú v 1).
 * <p>
 * Typ sa ukladá do schémy ako vlastnosť {@code outputs} a obnovuje sa pri načítaní ešte pred
 * pripájaním vodičov. Schémy bez tejto vlastnosti sa načítajú ako typ 3-8.
 *
 * @author Claude (návrh podľa SchematicSim architektúry, inšpirované Register8)
 */
public class Decoder38 extends GateSymbol {

    private static final String ICON_PATH = "/icons/dekoder.png";
    private static final int GRID_WIDTH = 3;
    private static final int[] SUPPORTED_COUNTS = {4, 8};
    private static final int DEFAULT_OUTPUTS = 8;
    private static final String[] ADDRESS_NAMES = {"A", "B", "C"};

    // Polia bez inicializátorov: createPins() a drawBody() volá už konštruktor predka
    // a inicializátor by nastavené hodnoty následne prepísal.
    private int outputCount;
    private Pin[] addressPins;
    private Pin pinG;
    private Pin[] outputs;

    private ContextMenu contextMenu;

    /** Konštruktor pre paletku (ItemPicker). */
    public Decoder38() {
        super();
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

    /** Konštruktor pre umiestnenie na plochu - volaný reflexiou zo SchematicSheet. */
    public Decoder38(SchematicSheet sheet) {
        super(sheet);
        this.addEventHandler(MouseEvent.MOUSE_PRESSED, this::showContextMenu);
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
            RadioMenuItem item = new RadioMenuItem(Integer.numberOfTrailingZeros(count) + "-" + count);
            item.setToggleGroup(group);
            item.setSelected(count == getOutputCount());
            item.setOnAction(event -> {
                setOutputCount(count);
                getSheet().markChanged();
            });
            menu.getItems().add(item);
        }
        return menu;
    }

    @Override
    protected List<Pin> createPins() {
        // ľavý okraj: adresné vstupy zhora (A,B,C) a povolenie G dole oproti poslednému výstupu
        addressPins = resize(new Pin[0], addressBits(), this::newAddressPin);
        pinG = new InputPin(this, "G", 0, enableRow(), Side.LEFT);

        // pravý okraj: výstupy Y0..
        outputs = resize(new Pin[0], getOutputCount(), this::newOutputPin);

        return allPins();
    }

    private Pin newAddressPin(int index) {
        return new InputPin(this, ADDRESS_NAMES[index], 0, index + 1, Side.LEFT);
    }

    private Pin newOutputPin(int index) {
        return new OutputPin(this, "Y" + index, GRID_WIDTH, index + 1, Side.RIGHT);
    }

    @Override
    protected Pane drawBody() {
        int cell = getSheet().getComponentCell();
        Rectangle body = new Rectangle(GRID_WIDTH * cell, getGridHeight() * cell, Color.WHITE);
        body.setStroke(Color.BLACK);
        body.setStrokeWidth(3);

        Pane pane = new Pane();
        pane.getChildren().add(body);

        Text title = new Text("DECODER");
        title.setLayoutY(-cell * 0.2);
        title.setFont(Font.font(cell * 0.6));
        title.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (GRID_WIDTH * cell - title.getBoundsInLocal().getWidth()) / 2.0,
                title.boundsInLocalProperty()));
        registerNameLabel(title);
        pane.getChildren().add(title);

        for (int index = 0; index < addressBits(); index++) {
            pane.getChildren().add(pinLabel(ADDRESS_NAMES[index], 0.4, index + 1, cell));
        }
        pane.getChildren().add(pinLabel("G", 0.4, enableRow(), cell));

        for (int index = 0; index < getOutputCount(); index++) {
            pane.getChildren().add(pinLabel("Y" + index, 1.8, index + 1, cell));
        }

        return pane;
    }

    private static Text pinLabel(String text, double column, int row, int cell) {
        Text label = new Text(text);
        label.setLayoutX(cell * column);
        label.setLayoutY(row * cell + cell * 0.25);
        return label;
    }

    @Override
    public void simulate() {
        // pri G=0 sú všetky výstupy v 1
        if (isLow(pinG)) {
            for (Pin output : outputs) setPin(output, Pin.PinState.HIGH);
            return;
        }

        // pri G=1 kombinácia adresných vstupov vyberie výstup, ktorý bude v 0
        int address = 0;
        for (int bit = 0; bit < addressPins.length; bit++) {
            if (isHigh(addressPins[bit])) address |= 1 << bit;
        }
        for (int index = 0; index < outputs.length; index++) {
            setPin(outputs[index], index == address ? Pin.PinState.LOW : Pin.PinState.HIGH);
        }
    }

    @Override
    public void reset() {
        if (outputs == null) return;
        for (Pin output : outputs) output.setState(Pin.PinState.NOT_CONNECTED);
    }

    /** Počet výstupov: 8 alebo 4. */
    public int getOutputCount() {
        return outputCount == 0 ? DEFAULT_OUTPUTS : outputCount;
    }

    /**
     * Zmena typu dekodéra. Zachované vývody ostanú tými istými objektmi; vstup G sa presunie
     * oproti poslednému výstupu aj s vodičom, vodiče na odoberaných vývodoch sa vymažú.
     */
    public void setOutputCount(int count) {
        if (!isSupported(count) || count == getOutputCount()) return;

        getSheet().getOccupancy().free(this);
        this.outputCount = count;

        addressPins = resize(addressPins, addressBits(), this::newAddressPin);
        outputs = resize(outputs, count, this::newOutputPin);
        pinG.setLayoutY(enableRow() * getSheet().getComponentCell());
        if (pinG.getWireEnd() != null) pinG.getWireEnd().refreshPosition();

        getChildren().set(0, drawBody());
        replacePins(allPins());
        getSheet().getOccupancy().occupy(this);
        refreshSelectionShape();
    }

    /**
     * Zmena počtu vývodov jednej skupiny: prebytočné sa odoberú aj s vodičmi, chýbajúce sa
     * vytvoria. Súčiastka z konštruktora predka ešte nemá vývody medzi potomkami - tie pridá
     * predok zo zoznamu {@link #createPins()}, preto sa pridávajú iba pri zmene typu.
     */
    private Pin[] resize(Pin[] current, int count, IntFunction<Pin> factory) {
        for (int index = count; index < current.length; index++) {
            Pin removed = current[index];
            if (removed.getWireEnd() != null) removed.getWireEnd().getWire().delete();
            getChildren().remove(removed);
        }

        Pin[] resized = Arrays.copyOf(current, count);
        for (int index = current.length; index < count; index++) {
            resized[index] = factory.apply(index);
            if (getPins() != null) getChildren().add(resized[index]);
        }
        return resized;
    }

    /** Poradie zodpovedá starším schémam typu 3-8: A, B, C, G, Y0..Y7. */
    private List<Pin> allPins() {
        List<Pin> pins = new ArrayList<>();
        pins.addAll(Arrays.asList(addressPins));
        pins.add(pinG);
        pins.addAll(Arrays.asList(outputs));
        return pins;
    }

    private int addressBits() {
        return Integer.numberOfTrailingZeros(getOutputCount());
    }

    private int enableRow() {
        return getOutputCount();
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
        properties.put("outputs", Integer.toString(getOutputCount()));
        return properties;
    }

    @Override
    public void loadProperties(Map<String, String> properties) {
        super.loadProperties(properties);
        String value = properties.get("outputs");
        if (value == null) return;

        try {
            setOutputCount(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public int getGridWidth() {
        return GRID_WIDTH;
    }

    @Override
    public int getGridHeight() {
        return getOutputCount() + 1;
    }

    @Override
    public String getName() {
        return "DEK";
    }

    @Override
    public String getInstanceNamePrefix() {
        return "DEK";
    }

    @Override
    public String getShortDescription() {
        return "Dekodér 3-8 alebo 2-4 (typ cez pravé tlačidlo) - G=1 vyberie výstup podľa adresy (aktívny 0), G=0 dá všetky výstupy do 1";
    }
}
