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
 * Prioritný kóder 8 na 3 alebo 4 na 2 podľa obvodu 74148 - typ sa volí cez kontextové menu
 * (pravé kliknutie). Povolovací vstup EI, dátové vstupy aj všetky výstupy sú aktívne v 0 (low).
 * <p>
 * Pri EI=1 sú všetky výstupy (EO, GS, A0..) v log. 1.
 * Pri EI=0 a žiadnej nule na dátových vstupoch je EO v 0 (ostatné v 1).
 * Pri EI=0 a aspoň jednej nule je GS v 0, EO v 1 a adresné výstupy predstavujú v negovanom
 * tvare číslo najvyššieho vstupu, na ktorom je 0 (prioritu má najvyšší vstup) - napr. pri
 * type 8-3 dá 0 na D7 A2A1A0=000 a 0 iba na D0 dá A2A1A0=111.
 * <p>
 * Typ sa ukladá do schémy ako vlastnosť {@code inputs} a obnovuje sa pri načítaní ešte pred
 * pripájaním vodičov. Schémy bez tejto vlastnosti sa načítajú ako typ 8-3.
 *
 * @author Claude (návrh podľa SchematicSim architektúry, inšpirované Decoder38)
 */
public class Encoder83 extends GateSymbol {

    private static final String ICON_PATH = "/icons/koder.png";
    private static final int GRID_WIDTH = 3;
    private static final int[] SUPPORTED_COUNTS = {4, 8};
    private static final int DEFAULT_DATA_INPUTS = 8;

    // Polia bez inicializátorov: createPins() a drawBody() volá už konštruktor predka
    // a inicializátor by nastavené hodnoty následne prepísal.
    private int dataInputCount;
    private Pin[] dataInputs;
    private Pin pinEI;
    private Pin outputEO;
    private Pin outputGS;
    private Pin[] addressOutputs;

    private ContextMenu contextMenu;

    /** Konštruktor pre paletku (ItemPicker). */
    public Encoder83() {
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
    public Encoder83(SchematicSheet sheet) {
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
            RadioMenuItem item = new RadioMenuItem(count + "-" + Integer.numberOfTrailingZeros(count));
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
        // ľavý okraj: povolenie EI a dátové vstupy D0.., začínajú na riadku 1
        pinEI = new InputPin(this, "EI", 0, 1, Side.LEFT);
        dataInputs = resize(new Pin[0], getDataInputCount(), this::newDataInput);

        // pravý okraj (zhora dole): EO, GS, voľné miesto, A0..
        outputEO = new OutputPin(this, "EO", GRID_WIDTH, 1, Side.RIGHT);
        outputGS = new OutputPin(this, "GS", GRID_WIDTH, 2, Side.RIGHT);
        addressOutputs = resize(new Pin[0], addressBits(), this::newAddressOutput);

        return allPins();
    }

    private Pin newDataInput(int index) {
        return new InputPin(this, "D" + index, 0, index + 2, Side.LEFT);
    }

    private Pin newAddressOutput(int index) {
        return new OutputPin(this, "A" + index, GRID_WIDTH, index + 4, Side.RIGHT);
    }

    @Override
    protected Pane drawBody() {
        int cell = getSheet().getComponentCell();
        Rectangle body = new Rectangle(GRID_WIDTH * cell, getGridHeight() * cell, Color.WHITE);
        body.setStroke(Color.BLACK);
        body.setStrokeWidth(3);

        Pane pane = new Pane();
        pane.getChildren().add(body);

        Text title = new Text("ENCODER");
        title.setLayoutY(-cell * 0.2);
        title.setFont(Font.font(cell * 0.6));
        title.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (GRID_WIDTH * cell - title.getBoundsInLocal().getWidth()) / 2.0,
                title.boundsInLocalProperty()));
        registerNameLabel(title);
        pane.getChildren().add(title);

        // EI nad prvým dátovým riadkom
        pane.getChildren().add(pinLabel("EI", 0.4, 1, cell));
        for (int index = 0; index < getDataInputCount(); index++) {
            pane.getChildren().add(pinLabel("D" + index, 0.4, index + 2, cell));
        }

        pane.getChildren().add(pinLabel("EO", 1.8, 1, cell));
        pane.getChildren().add(pinLabel("GS", 1.8, 2, cell));
        for (int index = 0; index < addressBits(); index++) {
            pane.getChildren().add(pinLabel("A" + index, 1.8, index + 4, cell));
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
        // pri EI=1 sú všetky výstupy v 1
        if (isHigh(pinEI)) {
            setPin(outputEO, Pin.PinState.HIGH);
            setPin(outputGS, Pin.PinState.HIGH);
            for (Pin output : addressOutputs) setPin(output, Pin.PinState.HIGH);
            return;
        }

        // pri EI=0 hľadáme najvyšší vstup s 0 (prioritu má najvyšší vstup)
        int highest = -1;
        for (int index = dataInputs.length - 1; index >= 0; index--) {
            if (isLow(dataInputs[index])) {
                highest = index;
                break;
            }
        }

        if (highest < 0) {
            // žiadna nula na dátových vstupoch: EO=0, ostatné 1
            setPin(outputEO, Pin.PinState.LOW);
            setPin(outputGS, Pin.PinState.HIGH);
            for (Pin output : addressOutputs) setPin(output, Pin.PinState.HIGH);
        } else {
            setPin(outputEO, Pin.PinState.HIGH);
            setPin(outputGS, Pin.PinState.LOW);
            // výstupy sú aktívne v 0 - číslo vstupu v negovanom tvare
            for (int bit = 0; bit < addressOutputs.length; bit++) {
                setPin(addressOutputs[bit], (highest & (1 << bit)) != 0 ? Pin.PinState.LOW : Pin.PinState.HIGH);
            }
        }
    }

    @Override
    public void reset() {
        setPin(outputEO, Pin.PinState.NOT_CONNECTED);
        setPin(outputGS, Pin.PinState.NOT_CONNECTED);
        for (Pin output : addressOutputs) setPin(output, Pin.PinState.NOT_CONNECTED);
    }

    /** Počet dátových vstupov: 8 alebo 4. */
    public int getDataInputCount() {
        return dataInputCount == 0 ? DEFAULT_DATA_INPUTS : dataInputCount;
    }

    /**
     * Zmena typu kódera. Zachované vývody ostávajú na svojich miestach aj s vodičmi;
     * vodiče na odoberaných vývodoch sa vymažú.
     */
    public void setDataInputCount(int count) {
        if (!isSupported(count) || count == getDataInputCount()) return;

        getSheet().getOccupancy().free(this);
        this.dataInputCount = count;

        dataInputs = resize(dataInputs, count, this::newDataInput);
        addressOutputs = resize(addressOutputs, addressBits(), this::newAddressOutput);

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

    /** Poradie zodpovedá starším schémam typu 8-3: EI, D0..D7, EO, GS, A0..A2. */
    private List<Pin> allPins() {
        List<Pin> pins = new ArrayList<>();
        pins.add(pinEI);
        pins.addAll(Arrays.asList(dataInputs));
        pins.add(outputEO);
        pins.add(outputGS);
        pins.addAll(Arrays.asList(addressOutputs));
        return pins;
    }

    private int addressBits() {
        return Integer.numberOfTrailingZeros(getDataInputCount());
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
        return getDataInputCount() + 2;
    }

    @Override
    public String getName() {
        return "KOD";
    }

    @Override
    public String getInstanceNamePrefix() {
        return "KOD";
    }

    @Override
    public String getShortDescription() {
        return "Prioritný kóder 8-3 alebo 4-2 (typ cez pravé tlačidlo, 74148) - EI=0: na adresných výstupoch negované číslo najvyššieho vstupu s 0 (GS=0); bez nuly EO=0; EI=1 dá všetky výstupy do 1";
    }
}
