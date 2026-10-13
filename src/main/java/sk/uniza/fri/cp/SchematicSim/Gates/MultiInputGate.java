package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
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
 * Hradlo s premenlivým počtom vstupov (2, 3, 4 alebo 8). Počet vstupov sa mení cez
 * kontextové menu (pravé kliknutie na hradlo), vstupy sa rozkladajú na ľavú stranu tela
 * a výstup zostáva na pravej strane, v strede novej výšky.
 * <p>
 * Pridané vstupy nemajú pripojené vodiče (správajú sa ako plávajúce), odoberané vstupy
 * sa vymažú aj s pripojenými vodičmi. Zoznam pinov (vstupy, výstup) sa mení cez
 * {@link #replacePins(List)}, aby SchemeLoader indexoval piny správne.
 * <p>
 * Počet vstupov sa ukladá do schémy ako vlastnosť {@code inputs} a obnovuje sa pri načítaní
 * ešte pred pripájaním vodičov (indexy pinov vo vodičoch sú preto stále platné).
 *
 * @author Claude (návrh podľa SchematicSim architektúry)
 */
public abstract class MultiInputGate extends GateSymbol {

    private static final int DEFAULT_INPUTS = 2;
    private static final int[] SUPPORTED_COUNTS = {2, 3, 4, 8};
    private static final String[] INPUT_NAMES = {"A", "B", "C", "D", "E", "F", "G", "H"};

    private int inputCount = DEFAULT_INPUTS;
    private final ContextMenu contextMenu = new ContextMenu();

    protected MultiInputGate() {
        super();
    }

    protected MultiInputGate(SchematicSheet sheet) {
        super(sheet);
        buildContextMenu();
        this.addEventHandler(MouseEvent.MOUSE_PRESSED, this::showContextMenu);
    }

    /**
     * Grafická značka na tele hradla ("&", "≥1"...).
     */
    protected abstract String getSymbolText();

    /**
     * Má hradlo na výstupe negačný krúžok (NAND, NOR)?
     */
    protected boolean negated() {
        return false;
    }

    private void buildContextMenu() {
        for (int count : SUPPORTED_COUNTS) {
            MenuItem item = new MenuItem(count + (count == 8 ? " vstupov" : " vstupy"));
            item.setOnAction(event -> setInputCount(count));
            contextMenu.getItems().add(item);
        }
    }

    private void showContextMenu(MouseEvent event) {
        if (event.getButton() == MouseButton.SECONDARY) {
            ContextMenuHelper.show(contextMenu, this, event.getScreenX(), event.getScreenY());
            event.consume();
        }
    }

    @Override
    protected final List<Pin> createPins() {
        int count = effectiveInputCount();
        List<Pin> list = new ArrayList<>(count + 1);
        for (int i = 0; i < count; i++) {
            list.add(new InputPin(this, INPUT_NAMES[i], 0, 1 + i, Side.LEFT));
        }
        list.add(new OutputPin(this, "Y", getGridWidth(), outputRow(), Side.RIGHT));
        return list;
    }

    @Override
    protected Pane drawBody() {
        int cell = getSheet().getComponentCell();
        double w = getGridWidth() * cell;
        double h = getGridHeight() * cell;
        double bubble = cell / 4.0;

        Rectangle body = new Rectangle((negated() ? w - 2 * bubble : w), h, Color.WHITESMOKE);
        body.setStroke(Color.BLACK);
        body.setStrokeWidth(3);

        double bubbleY = outputRow() * cell;

        Text label = new Text(getSymbolText());
        label.setLayoutX((body.getWidth() - label.getBoundsInLocal().getWidth()) / 2);
        label.setLayoutY(h / 2 + 5);

        Pane pane = new Pane(body, label);
        if (negated()) {
            Circle negationBubble = new Circle(w - bubble, bubbleY, bubble);
            negationBubble.setFill(Color.WHITESMOKE);
            negationBubble.setStroke(Color.BLACK);
            negationBubble.setStrokeWidth(3);
            pane.getChildren().add(negationBubble);
        }
        return pane;
    }

    @Override
    public int getGridWidth() {
        return 2;
    }

    @Override
    public int getGridHeight() {
        return effectiveInputCount() + 1;
    }

    /**
     * Vstupy hradla v poradí zhora nadol.
     */
    protected List<Pin> inputPins() {
        List<Pin> pins = getPins();
        return new ArrayList<>(pins.subList(0, pins.size() - 1));
    }

    /**
     * Výstup hradla (vždy posledný pin v zozname).
     */
    protected Pin getOutput() {
        List<Pin> pins = getPins();
        return pins.get(pins.size() - 1);
    }

    public int getInputCount() {
        return effectiveInputCount();
    }

    public void setInputCount(int count) {
        if (!isSupported(count) || count == effectiveInputCount()) return;

        List<Pin> currentPins = new ArrayList<>(getPins());
        List<Pin> currentInputs = currentPins.subList(0, currentPins.size() - 1);
        Pin currentOutput = currentPins.get(currentPins.size() - 1);
        int keep = Math.min(count, currentInputs.size());

        // vodiče na vstupoch, ktoré sa odstraňujú, sa zmažú - inak by na nich nedržal žiadny pin
        for (int i = count; i < currentInputs.size(); i++) {
            Pin pin = currentInputs.get(i);
            if (pin.getWireEnd() != null) pin.getWireEnd().getWire().delete();
        }

        this.inputCount = count;

        // zachované vstupy ostanú tými istými objektmi (pripojené vodiče sa neprerušia),
        // iba nové vstupy sa vytvoria; výstup sa zachová a presunie do stredu novej výšky
        List<Pin> newPins = new ArrayList<>(currentInputs.subList(0, keep));
        for (int i = keep; i < count; i++) {
            newPins.add(new InputPin(this, INPUT_NAMES[i], 0, 1 + i, Side.LEFT));
        }

        int cell = getSheet().getComponentCell();
        currentOutput.setLayoutX(getGridWidth() * cell);
        currentOutput.setLayoutY(outputRow() * cell);
        if (currentOutput.getWireEnd() != null) currentOutput.getWireEnd().refreshPosition();
        newPins.add(currentOutput);

        // prestav potomkov: telo (index 0) a piny v správnom poradí (vstupy, výstup)
        getChildren().remove(0);
        for (Pin pin : currentInputs) getChildren().remove(pin);
        getChildren().remove(currentOutput);
        getChildren().add(0, drawBody());
        getChildren().addAll(newPins);
        replacePins(newPins);

        // selekčný rámik sa musí rozmerovo prispôsobiť novej veľkosti hradla
        if (isSelected() && isSelectable()) {
            deselect();
            select();
        }
    }

    @Override
    public Map<String, String> saveProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("inputs", Integer.toString(effectiveInputCount()));
        return properties;
    }

    @Override
    public void loadProperties(Map<String, String> properties) {
        String value = properties.get("inputs");
        if (value == null) return;

        try {
            int count = Integer.parseInt(value);
            if (isSupported(count)) {
                setInputCount(count);
                // obsadenosť mriežky rozšíriť na nový rozmer hradla (načítava sa pred repopuláciou plochy)
                getSheet().getOccupancy().free(this);
                getSheet().getOccupancy().occupy(this);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private int effectiveInputCount() {
        return Math.max(inputCount, DEFAULT_INPUTS);
    }

    private int outputRow() {
        return (1 + effectiveInputCount()) / 2;
    }

    private static boolean isSupported(int count) {
        for (int supported : SUPPORTED_COUNTS) {
            if (count == supported) return true;
        }
        return false;
    }
}