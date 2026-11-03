package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.beans.binding.Bindings;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Text;
import sk.uniza.fri.cp.SchematicSim.Pin.InputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.TriStateOutputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Side;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Bus buffer s jedným dátovým vstupom, jedným výstupom a jedým negovaným riadiacim
 * signálom (G_). Pri aktívnom G_ (log. 0) sa výstup rovná vstupu; pri neaktívnom
 * (log. 1) je výstup v stave vysokej impedancie (Z), aby mohol zbernicu hnať iný zdroj.
 *
 * @author Claude (návrh podľa SchematicSim architektúry)
 */
public class BusBuffer extends GateSymbol {

    private static final String ICON_PATH = "/icons/bus-buffer.png";

    private Pin a, g, y;

    /** Konštruktor pre paletku (ItemPicker). */
    public BusBuffer() {
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
    public BusBuffer(SchematicSheet sheet) {
        super(sheet);
    }

    @Override
    protected List<Pin> createPins() {
        a = new InputPin(this, "A", 0, 1, Side.LEFT);
        g = new InputPin(this, "G/", 1, 0, Side.TOP);
        y = new TriStateOutputPin(this, "Y", getGridWidth(), 1, Side.RIGHT);
        return Arrays.asList(a, g, y);
    }

    @Override
    protected Pane drawBody() {
        int cell = getSheet().getComponentCell();
        int w = getGridWidth() * cell;
        int h = getGridHeight() * cell;
        double bubble = cell / 4.0;

        // trojuholník: zvislá základňa vľavo (2 štvorce), hrot vpravo v strede výšky
        Polygon triangle = new Polygon(
                0, 0,
                0, h,
                w, h / 2.0);
        triangle.setFill(Color.WHITESMOKE);
        triangle.setStroke(Color.BLACK);
        triangle.setStrokeWidth(3);

        // negačný krúžok na riadiacom vstupe (G_ - aktívny v log. 0). Stred krúžku je
        // v bode (1, 1/4) - polomer cell/4 siaha hore presne k mriežke y=0, kde leží pripojovací
        // bod pinu G_ (1,0), a dole sa dotýka hornej (preponovej) strany trojuholníka.
        Circle gBubble = new Circle(cell, cell / 4.0, bubble);
        gBubble.setFill(Color.WHITESMOKE);
        gBubble.setStroke(Color.BLACK);
        gBubble.setStrokeWidth(3);

        return new Pane(triangle, gBubble);
    }

    @Override
    public void simulate() {
        if (isLow(g)) {
            setPin(y, isHigh(a) ? Pin.PinState.HIGH : Pin.PinState.LOW);
        } else {
            setPin(y, Pin.PinState.HIGH_IMPEDANCE);
        }
    }

    @Override
    public void reset() {
        setPinForce(y, Pin.PinState.NOT_CONNECTED);
    }

    @Override
    public int getGridWidth() {
        return 2;
    }

    @Override
    public int getGridHeight() {
        return 2;
    }

    @Override
    public String getName() {
        return "BUF";
    }

    @Override
    public String getShortDescription() {
        return "Bus buffer - Y = A pri G/=0, inak výstup v Z";
    }
}