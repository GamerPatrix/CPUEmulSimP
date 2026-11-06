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
 * Demultiplexor typu 1-2, 1-4 alebo 1-8 - typ sa volí cez kontextové menu (pravé kliknutie).
 * <p>
 * Vstupy sú vľavo, zhora v poradí: adresné A0.. (1 až 3 podľa typu), povolenie E/
 * (aktívne v 0) a dátový I. Výstupy O0.. (2 až 8 podľa typu) sú vpravo.
 * Pri E/=1 sú všetky výstupy v 1. Pri E/=0 adresovaný výstup kopíruje vstup I
 * a ostatné výstupy sú v 1.
 * <p>
 * Typ sa ukladá do schémy ako vlastnosť {@code outputs} a obnovuje sa pri načítaní ešte pred
 * pripájaním vodičov, takže indexy pinov vo vodičoch zodpovedajú uloženému typu.
 *
 * @author Claude (návrh podľa SchematicSim architektúry, inšpirované Multiplexer)
 */
public class Demultiplexer extends GateSymbol {

    private static final String ICON_PATH = "/icons/demultiplexor.png";
    private static final int GRID_WIDTH = 3;
    private static final int[] SUPPORTED_COUNTS = {2, 4, 8};
    /** Najmenší typ - nový demultiplexor je 1-2, kým sa nezvolí iný typ. */
    private static final int DEFAULT_OUTPUTS = 2;

    // Polia bez inicializátorov: createPins() a drawBody() volá už konštruktor predka
    // a inicializátor by nastavené hodnoty následne prepísal.
    private int outputCount;
    private Pin[] addressPins;
    private Pin pinE;
    private Pin pinI;
    private Pin[] outputPins;

    private ContextMenu contextMenu;

    /** Konštruktor pre paletku (ItemPicker). */
    public Demultiplexer() {
        super();
    }

    /** Konštruktor pre umiestnenie na plochu - volaný reflexiou zo SchematicSheet. */
    public Demultiplexer(SchematicSheet sheet) {
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
            RadioMenuItem item = new RadioMenuItem("1-" + count);
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
        addressPins = resize(new Pin[0], addressBits(), this::newAddressPin);
        pinE = new InputPin(this, "E/", 0, 0, Side.LEFT);
        pinI = new InputPin(this, "I", 0, 0, Side.LEFT);
        outputPins = resize(new Pin[0], getOutputCount(), this::newOutputPin);
        layoutPins();
        return allPins();
    }

    private Pin newAddressPin(int index) {
        return new InputPin(this, "A" + index, 0, 0, Side.LEFT);
    }

    private Pin newOutputPin(int index) {
        return new OutputPin(this, "O" + index, GRID_WIDTH, 0, Side.RIGHT);
    }

    @Override
    protected Pane drawBody() {
        int cell = getSheet().getComponentCell();
        Rectangle body = new Rectangle(GRID_WIDTH * cell, getGridHeight() * cell, Color.WHITE);
        body.setStroke(Color.BLACK);
        body.setStrokeWidth(3);

        Pane pane = new Pane();
        pane.getChildren().add(body);

        Text title = new Text("DMUX");
        title.setLayoutY(-cell * 0.2);
        title.setFont(Font.font(cell * 0.6));
        title.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (GRID_WIDTH * cell - title.getBoundsInLocal().getWidth()) / 2.0,
                title.boundsInLocalProperty()));
        registerNameLabel(title);
        pane.getChildren().add(title);

        for (int index = 0; index < addressBits(); index++) {
            pane.getChildren().add(pinLabel("A" + index, 0.4, addressRow(index), cell));
        }
        pane.getChildren().add(pinLabel("E/", 0.4, enableRow(), cell));
        pane.getChildren().add(pinLabel("I", 0.4, dataRow(), cell));
        for (int index = 0; index < getOutputCount(); index++) {
            pane.getChildren().add(pinLabel("O" + index, 1.8, outputRow(index), cell));
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
        // E/ je aktívne v 0; neaktívny obvod má všetky výstupy v 1
        if (isHigh(pinE)) {
            for (Pin output : outputPins) setPin(output, Pin.PinState.HIGH);
            return;
        }

        int address = 0;
        for (int bit = 0; bit < addressPins.length; bit++) {
            if (isHigh(addressPins[bit])) address |= 1 << bit;
        }
        Pin.PinState selected = isHigh(pinI) ? Pin.PinState.HIGH : Pin.PinState.LOW;
        for (int index = 0; index < outputPins.length; index++) {
            setPin(outputPins[index], index == address ? selected : Pin.PinState.HIGH);
        }
    }

    @Override
    public void reset() {
        if (outputPins == null) return;
        for (Pin output : outputPins) setPinForce(output, Pin.PinState.NOT_CONNECTED);
    }

    /** Počet výstupov: 2, 4 alebo 8. */
    public int getOutputCount() {
        return Math.max(outputCount, DEFAULT_OUTPUTS);
    }

    /**
     * Zmena typu demultiplexora. Zachované vývody ostanú tými istými objektmi a pripojené
     * vodiče sa s nimi iba presunú; vodiče na odoberaných vývodoch sa vymažú.
     */
    public void setOutputCount(int count) {
        if (!isSupported(count) || count == getOutputCount()) return;

        getSheet().getOccupancy().free(this);
        this.outputCount = count;

        addressPins = resize(addressPins, addressBits(), this::newAddressPin);
        outputPins = resize(outputPins, count, this::newOutputPin);
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

    private void layoutPins() {
        int cell = getSheet().getComponentCell();
        for (int index = 0; index < addressPins.length; index++) {
            addressPins[index].setLayoutY(addressRow(index) * cell);
        }
        pinE.setLayoutY(enableRow() * cell);
        pinI.setLayoutY(dataRow() * cell);
        for (int index = 0; index < outputPins.length; index++) {
            outputPins[index].setLayoutY(outputRow(index) * cell);
        }
    }

    private List<Pin> allPins() {
        List<Pin> pins = new ArrayList<>();
        pins.addAll(Arrays.asList(addressPins));
        pins.add(pinE);
        pins.add(pinI);
        pins.addAll(Arrays.asList(outputPins));
        return pins;
    }

    private int addressBits() {
        return Integer.numberOfTrailingZeros(getOutputCount());
    }

    private int addressRow(int index) {
        return 1 + index;
    }

    private int enableRow() {
        return 1 + addressBits();
    }

    private int dataRow() {
        return 2 + addressBits();
    }

    private int outputRow(int index) {
        return 1 + index;
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
        // ľavá strana má adresy + E/ + I, pravá strana výstupy; rozhoduje vyššia z nich
        return Math.max(addressBits() + 2, getOutputCount()) + 1;
    }

    @Override
    public String getName() {
        return "DMUX";
    }

    @Override
    public String getInstanceNamePrefix() {
        return "DMUX";
    }

    @Override
    public String getShortDescription() {
        return "Demultiplexor 1-2, 1-4 alebo 1-8 (typ cez pravé tlačidlo) - pri E/=0 adresovaný výstup kopíruje I, ostatné sú v 1; pri E/=1 sú všetky výstupy v 1";
    }
}
