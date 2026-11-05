package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.beans.binding.Bindings;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import sk.uniza.fri.cp.SchematicSim.Pin.InputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.OutputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Side;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Dekodér 3 na 8 s povolovacím vstupom G a aktívnymi výstupmi v 0 (low).
 * <p>
 * Pri G=0 sú všetky výstupy Y0..Y7 v log. 1; pri G=1 kombinácia adresných vstupov
 * C,B,A určuje poradové číslo výstupu, ktorý bude v log. 0 (ostatné ostanú v 1).
 *
 * @author Claude (návrh podľa SchematicSim architektúry, inšpirované Register8)
 */
public class Decoder38 extends GateSymbol {

    private static final String ICON_PATH = "/icons/dekoder.png";
    private static final int GRID_WIDTH = 3;
    private static final int GRID_HEIGHT = 9;

    private Pin[] outputs;
    private Pin pinG;
    private Pin pinC;
    private Pin pinB;
    private Pin pinA;

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
    }

    @Override
    protected List<Pin> createPins() {
        List<Pin> pins = new ArrayList<>();

        // ľavý okraj: adresné vstupy zhora (A,B,C) a povolenie G dole oproti Y7
        pinA = new InputPin(this, "A", 0, 1, Side.LEFT);
        pinB = new InputPin(this, "B", 0, 2, Side.LEFT);
        pinC = new InputPin(this, "C", 0, 3, Side.LEFT);
        pinG = new InputPin(this, "G", 0, 8, Side.LEFT);
        pins.add(pinA);
        pins.add(pinB);
        pins.add(pinC);
        pins.add(pinG);

        // pravý okraj: výstupy Y0..Y7
        outputs = new OutputPin[8];
        for (int index = 0; index < 8; index++) {
            outputs[index] = new OutputPin(this, "Y" + index, GRID_WIDTH, index + 1, Side.RIGHT);
            pins.add(outputs[index]);
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

        Text title = new Text("DECODER");
        title.setLayoutY(-cell * 0.2);
        title.setFont(Font.font(cell * 0.6));
        title.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (GRID_WIDTH * cell - title.getBoundsInLocal().getWidth()) / 2.0,
                title.boundsInLocalProperty()));
        registerNameLabel(title);
        pane.getChildren().add(title);

        int[] inputRows = {1, 2, 3, 8};
        String[] leftLabels = {"A", "B", "C", "G"};
        for (int index = 0; index < 4; index++) {
            Text inputLabel = new Text(leftLabels[index]);
            inputLabel.setLayoutX(cell * 0.4);
            inputLabel.setLayoutY(inputRows[index] * cell + cell * 0.25);
            pane.getChildren().add(inputLabel);
        }

        for (int index = 0; index < 8; index++) {
            Text outputLabel = new Text("Y" + index);
            outputLabel.setLayoutX(cell * 1.8);
            outputLabel.setLayoutY((index + 1) * cell + cell * 0.25);
            pane.getChildren().add(outputLabel);
        }

        return pane;
    }

    @Override
    public void simulate() {
        // pri G=0 sú všetky výstupy v 1
        if (isLow(pinG)) {
            for (int index = 0; index < 8; index++) {
                setPin(outputs[index], Pin.PinState.HIGH);
            }
            return;
        }

        // pri G=1 kombinácia CBA vyberie výstup, ktorý bude v 0
        int address = (isHigh(pinC) ? 4 : 0) | (isHigh(pinB) ? 2 : 0) | (isHigh(pinA) ? 1 : 0);
        for (int index = 0; index < 8; index++) {
            setPin(outputs[index], index == address ? Pin.PinState.LOW : Pin.PinState.HIGH);
        }
    }

    @Override
    public void reset() {
        if (outputs == null) return;
        for (Pin output : outputs) output.setState(Pin.PinState.NOT_CONNECTED);
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
        return "DEK";
    }

    @Override
    public String getInstanceNamePrefix() {
        return "DEK";
    }

    @Override
    public String getShortDescription() {
        return "Dekodér 3→8 - G=1 vyberie výstup podľa CBA (aktívny 0), G=0 dá všetky výstupy do 1";
    }
}