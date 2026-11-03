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
 * Prioritný kóder 8 na 3 podľa obvodu 74148 - povolovací vstup EI, dátové vstupy aj
 * všetky výstupy sú aktívne v 0 (low).
 * <p>
 * Pri EI=1 sú všetky výstupy (EO, GS, A0, A1, A2) v log. 1.
 * Pri EI=0 a žiadnej nule na dátových vstupoch je EO v 0 (ostatné v 1).
 * Pri EI=0 a aspoň jednej nule je GS v 0, EO v 1 a A2,A1,A0 predstavujú v negovanom
 * tvare číslo najvyššieho vstupu (prioritu má D7), na ktorom je 0 - napr. 0 na D7
 * dá A2A1A0=000, 0 iba na D0 dá A2A1A0=111.
 *
 * @author Claude (návrh podľa SchematicSim architektúry, inšpirované Decoder38)
 */
public class Encoder83 extends GateSymbol {

    private static final String ICON_PATH = "/icons/koder.png";
    private static final int GRID_WIDTH = 4;
    private static final int GRID_HEIGHT = 10;

    private Pin[] dataInputs;
    private Pin pinEI;
    private Pin outputEO;
    private Pin outputGS;
    private Pin outputA0;
    private Pin outputA1;
    private Pin outputA2;

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
    }

    @Override
    protected List<Pin> createPins() {
        List<Pin> pins = new ArrayList<>();

        // ľavý okraj: povolenie EI a dátové vstupy D0..D7, začínajú na riadku 1
        pinEI = new InputPin(this, "EI", 0, 1, Side.LEFT);
        pins.add(pinEI);

        dataInputs = new InputPin[8];
        for (int index = 0; index < 8; index++) {
            dataInputs[index] = new InputPin(this, "D" + index, 0, index + 2, Side.LEFT);
            pins.add(dataInputs[index]);
        }

        // pravý okraj (zhora dole): EO, GS, voľné miesto, A0, A1, A2
        outputEO = new OutputPin(this, "EO", GRID_WIDTH, 1, Side.RIGHT);
        outputGS = new OutputPin(this, "GS", GRID_WIDTH, 2, Side.RIGHT);
        outputA0 = new OutputPin(this, "A0", GRID_WIDTH, 4, Side.RIGHT);
        outputA1 = new OutputPin(this, "A1", GRID_WIDTH, 5, Side.RIGHT);
        outputA2 = new OutputPin(this, "A2", GRID_WIDTH, 6, Side.RIGHT);
        pins.add(outputEO);
        pins.add(outputGS);
        pins.add(outputA0);
        pins.add(outputA1);
        pins.add(outputA2);

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

        Text title = new Text("ENCODER");
        title.setLayoutY(-cell * 0.2);
        title.setFont(Font.font(cell * 0.6));
        title.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (GRID_WIDTH * cell - title.getBoundsInLocal().getWidth()) / 2.0,
                title.boundsInLocalProperty()));
        registerNameLabel(title);
        pane.getChildren().add(title);

        // EI nad prvým dátovým riadkom
        Text inputLabel = new Text("EI");
        inputLabel.setLayoutX(cell * 0.4);
        inputLabel.setLayoutY(1 * cell + cell * 0.15);
        pane.getChildren().add(inputLabel);

        for (int index = 0; index < 8; index++) {
            Text pinLabel = new Text("D" + index);
            pinLabel.setLayoutX(cell * 0.4);
            pinLabel.setLayoutY((index + 2) * cell + cell * 0.25);
            pane.getChildren().add(pinLabel);
        }

        int[] outputRows = {1, 2, 4, 5, 6};
        String[] outputLabels = {"EO", "GS", "A0", "A1", "A2"};
        for (int index = 0; index < 5; index++) {
            Text pinLabel = new Text(outputLabels[index]);
            pinLabel.setLayoutX(cell * 2.6);
            pinLabel.setLayoutY(outputRows[index] * cell + cell * 0.25);
            pane.getChildren().add(pinLabel);
        }

        return pane;
    }

    @Override
    public void simulate() {
        // pri EI=1 sú všetky výstupy v 1
        if (isHigh(pinEI)) {
            setPin(outputEO, Pin.PinState.HIGH);
            setPin(outputGS, Pin.PinState.HIGH);
            setPin(outputA0, Pin.PinState.HIGH);
            setPin(outputA1, Pin.PinState.HIGH);
            setPin(outputA2, Pin.PinState.HIGH);
            return;
        }

        // pri EI=0 hľadáme najvyšší vstup s 0 (prioritu má D7)
        int highest = -1;
        for (int index = 7; index >= 0; index--) {
            if (isLow(dataInputs[index])) {
                highest = index;
                break;
            }
        }

        if (highest < 0) {
            // žiadna nula na dátových vstupoch: EO=0, ostatné 1
            setPin(outputEO, Pin.PinState.LOW);
            setPin(outputGS, Pin.PinState.HIGH);
            setPin(outputA0, Pin.PinState.HIGH);
            setPin(outputA1, Pin.PinState.HIGH);
            setPin(outputA2, Pin.PinState.HIGH);
        } else {
            setPin(outputEO, Pin.PinState.HIGH);
            setPin(outputGS, Pin.PinState.LOW);
            // výstupy sú aktívne v 0 - číslo vstupu v negovanom tvare
            setPin(outputA0, (highest & 1) != 0 ? Pin.PinState.LOW : Pin.PinState.HIGH);
            setPin(outputA1, (highest & 2) != 0 ? Pin.PinState.LOW : Pin.PinState.HIGH);
            setPin(outputA2, (highest & 4) != 0 ? Pin.PinState.LOW : Pin.PinState.HIGH);
        }
    }

    @Override
    public void reset() {
        setPin(outputEO, Pin.PinState.NOT_CONNECTED);
        setPin(outputGS, Pin.PinState.NOT_CONNECTED);
        setPin(outputA0, Pin.PinState.NOT_CONNECTED);
        setPin(outputA1, Pin.PinState.NOT_CONNECTED);
        setPin(outputA2, Pin.PinState.NOT_CONNECTED);
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
        return "KOD";
    }

    @Override
    public String getInstanceNamePrefix() {
        return "KOD";
    }

    @Override
    public String getShortDescription() {
        return "Prioritný kóder 8→3 (74148) - EI=0: na A2A1A0 negované číslo najvyššieho vstupu s 0 (GS=0); bez nuly EO=0; EI=1 dá všetky výstupy do 1";
    }
}