package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import sk.uniza.fri.cp.SchematicSim.Connectable;
import sk.uniza.fri.cp.SchematicSim.ContextMenuHelper;
import sk.uniza.fri.cp.SchematicSim.Electrical.PinType;
import sk.uniza.fri.cp.SchematicSim.Electrical.Potential;
import sk.uniza.fri.cp.SchematicSim.Pin.OutputPin;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pomenovaný bod siete - signál. Signály s rovnakým menom sú elektricky prepojené
 * tak, ako by boli prepojené skrytým vodičom: hodnota privedená na jeden z nich je
 * dostupná na všetkých ostatných s tým istým menom.
 * <p>
 * Telo signálu je mnohouholník (obdĺžnik s trojuholníkovým výbežkom) s menom v strede.
 * Vývod je na vrchole trojuholníka; jeho stranu (vpravo/vľavo/hore/dole) možno meniť
 * v kontextovom menu. Šírka tela sa prispôsobí dĺžke mena (minimálne 3 bunky).
 * <p>
 * Elektricky je vývod typu {@link PinType#WEAK_OUT} - slabý výstup, ktorý silný budič
 * (výstup hradla) na tej istej sieti vždy pretiahne. Hodnotu signálu preto určuje
 * vždy skutočný zdroj v skupine signálov s rovnakým menom; signály bez zdroja zostávajú
 * plávajúce (NC).
 *
 * @author Claude (návrh podľa SchematicSim architektúry)
 */
public class Signal extends GateSymbol {

    /** Minimálna šírka tela v mriežkových bunkách. */
    private static final int MIN_BODY_WIDTH = 3;
    /** Výška tela v mriežkových bunkách. */
    private static final int BODY_HEIGHT = 2;
    /** Hĺbka trojuholníkového výbežku v mriežkových bunkách. */
    private static final int TRIANGLE_DEPTH = 1;

    /** Meno signálu - null, kým signál nie je pomenovaný. */
    private String signalName;
    /** Pomenovaný signál už pomenovanie znova nevyvolá (dialóg sa otvára len pri vložení). */
    private boolean named;

    /** Šírka tela v mriežkových bunkách - prispôsobí sa dĺžke mena. */
    private int bodyWidth = MIN_BODY_WIDTH;
    /** Strana, na ktorej je vývod. */
    private Side side = Side.RIGHT;

    private Pin lead;
    private Pane body;
    private Text label;
    private ContextMenu contextMenu;

    /** Konštruktor pre paletku (ItemPicker). */
    public Signal() {
        super();
    }

    /** Konštruktor pre umiestnenie na plochu - volaný reflexiou zo SchematicSheet. */
    public Signal(SchematicSheet sheet) {
        super(sheet);
        if (!getChildren().isEmpty()) {
            getChildren().remove(0);
        }
        body = buildBodyPane();
        getChildren().add(0, body);
        if (lead != null) {
            // uistiť sa, že pin je pridaný (GateSymbol ho už pridal)
            if (!getChildren().contains(lead)) {
                getChildren().add(lead);
            }
            placeLead();
        }
    }

    @Override
    public Pane getImage() {
        Text text = new Text(getName());
        text.setLayoutY(text.getBoundsInLocal().getHeight());

        Image image = new Image(getClass().getResourceAsStream("/icons/signal.png"));
        ImageView view = new ImageView(image);
        view.setLayoutY(text.getBoundsInLocal().getHeight() + 5);

        text.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (image.getWidth() - text.getBoundsInLocal().getWidth()) / 2.0,
                image.widthProperty(), text.boundsInLocalProperty()));

        return new Pane(text, view);
    }

    @Override
    protected List<Pin> createPins() {
        lead = new OutputPin(this, "S", 0, 0, side);
        lead.getOwnedPotential().setType(PinType.WEAK_OUT);
        return Collections.singletonList(lead);
    }

    @Override
    protected Pane drawBody() {
        // Placeholder - skutočné telo sa vytvorí v konštruktore Signal(SchematicSheet)
        return new Pane();
    }

    // === vzhľad ===

    private Pane buildBodyPane() {
        int cell = gridCell();

        Polygon polygon = new Polygon();
        polygon.getPoints().addAll(bodyPoints());
        polygon.setFill(Color.WHITESMOKE);
        polygon.setStroke(Color.BLACK);
        polygon.setStrokeWidth(3);
        polygon.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                if (getSheet() != null && getSheet().isEditingEnabled()) {
                    showMenu(event.getScreenX(), event.getScreenY());
                }
                event.consume();
            }
        });
        polygon.setOnContextMenuRequested(event -> {
            if (getSheet() == null || !getSheet().isEditingEnabled()) return;
            if (contextMenu == null || !contextMenu.isShowing()) {
                showMenu(event.getScreenX(), event.getScreenY());
            }
            event.consume();
        });

        label = new Text(signalName == null ? "" : signalName);
        label.setFont(Font.font(cell * 1.2));
        label.setTextOrigin(VPos.CENTER);
        label.setMouseTransparent(true);
        if (side == Side.TOP || side == Side.BOTTOM) {
            label.setRotate(90);
        } else {
            label.setRotate(0);
        }

        Pane pane = new Pane(polygon, label);
        centerLabel();
        return pane;
    }

    /**
     * Vrcholy tela v bodoch (poloha x, poloha y) podľa aktuálnej strany vývodu.
     * Súradnice sú v gridových jednotkách, násobené veľkosťou bunky.
     */
    private List<Double> bodyPoints() {
        int cell = gridCell();
        List<Double> points = new ArrayList<>();
        switch (side) {
            case RIGHT:
                // [0,0],[3,0],[4,1],[3,2],[0,2]
                points.add(0.0 * cell); points.add(0.0 * cell);
                points.add(3.0 * cell); points.add(0.0 * cell);
                points.add(4.0 * cell); points.add(1.0 * cell);
                points.add(3.0 * cell); points.add(2.0 * cell);
                points.add(0.0 * cell); points.add(2.0 * cell);
                break;
            case LEFT:
                // [0,1],[1,0],[4,0],[4,2],[1,2]
                points.add(0.0 * cell); points.add(1.0 * cell);
                points.add(1.0 * cell); points.add(0.0 * cell);
                points.add(4.0 * cell); points.add(0.0 * cell);
                points.add(4.0 * cell); points.add(2.0 * cell);
                points.add(1.0 * cell); points.add(2.0 * cell);
                break;
            case TOP:
                // [0,1],[1,0],[2,1],[2,4],[0,4]
                points.add(0.0 * cell); points.add(1.0 * cell);
                points.add(1.0 * cell); points.add(0.0 * cell);
                points.add(2.0 * cell); points.add(1.0 * cell);
                points.add(2.0 * cell); points.add(4.0 * cell);
                points.add(0.0 * cell); points.add(4.0 * cell);
                break;
            default: // BOTTOM
                // [0,0],[2,0],[2,3],[1,4],[0,3]
                points.add(0.0 * cell); points.add(0.0 * cell);
                points.add(2.0 * cell); points.add(0.0 * cell);
                points.add(2.0 * cell); points.add(3.0 * cell);
                points.add(1.0 * cell); points.add(4.0 * cell);
                points.add(0.0 * cell); points.add(3.0 * cell);
                break;
        }
        return points;
    }

    /**
     * Vycentrovanie mena v telese.
     */
    private void centerLabel() {
        if (label == null) return;
        int cell = gridCell();
        double cx;
        double cy;
        switch (side) {
            case RIGHT:
                cx = 2.0 * cell;
                cy = 1.0 * cell;
                break;
            case LEFT:
                cx = 2.5 * cell;
                cy = 1.0 * cell;
                break;
            case TOP:
                cx = 1.0 * cell;
                cy = 2.5 * cell;
                label.setRotate(90);
                break;
            default: // BOTTOM
                cx = 1.0 * cell;
                cy = 2.0 * cell;
                label.setRotate(90);
                break;
        }
        label.setLayoutX(cx - label.getBoundsInLocal().getWidth() / 2.0);
        label.setLayoutY(cy);
    }

    private int gridCell() {
        if (getSheet() == null) return 10;
        try {
            if (getSheet().getGrid() != null) return getSheet().getGrid().getSizeX();
        } catch (Throwable ignored) {
        }
        return getSheet().getComponentCell();
    }

    /**
     * Šírka tela v bunkách - podľa dĺžky mena, minimálne {@link #MIN_BODY_WIDTH}.
     */
    private int computeBodyWidth() {
        int cell = gridCell();
        double textWidth = 0;
        if (signalName != null && !signalName.isEmpty()) {
            Text probe = new Text(signalName);
            probe.setFont(Font.font(cell * 1.2));
            textWidth = probe.getBoundsInLocal().getWidth();
        }
        int needed = (int) Math.ceil(textWidth / cell) + 1;
        return Math.max(MIN_BODY_WIDTH, needed);
    }

    /**
     * Umiestnenie vývodu na vrchol trojuholníka podľa aktuálnej strany.
     */
    private void placeLead() {
        if (lead == null) return;
        int cell = gridCell();

        double x;
        double y;
        switch (side) {
            case RIGHT:
                x = 4.0 * cell;
                y = 1.0 * cell;
                break;
            case LEFT:
                x = 0.0;
                y = 1.0 * cell;
                break;
            case TOP:
                x = 1.0 * cell;
                y = 0.0;
                break;
            default: // BOTTOM
                x = 1.0 * cell;
                y = 4.0 * cell;
                break;
        }
        lead.setLayoutX(x);
        lead.setLayoutY(y);
        lead.setSide(side);

        if (lead.getWireEnd() != null) {
            lead.getWireEnd().refreshPosition();
        }
    }

    /**
     * Prekreslenie tela a vývodu po zmene mena alebo strany vývodu. Pred zmenou
     * rozmerov sa uvoľnia staré bunky mriežky, po zmene sa obsadia nové.
     */
    private void applyLayout(String newName, Side newSide) {
        SchematicSheet sheet = getSheet();
        if (sheet != null) sheet.getOccupancy().free(this);

        this.signalName = newName == null ? "" : newName.trim();
        this.named = !this.signalName.isEmpty();
        Side oldSide = this.side;
        this.side = newSide == null ? Side.RIGHT : newSide;
        if (oldSide != this.side && lead != null) {
            lead.setSide(this.side);
        }
        this.bodyWidth = computeBodyWidth();

        rebuildVisuals();

        if (sheet != null) sheet.getOccupancy().occupy(this);
        refreshSelectionShape();
    }

    /**
     * Nahradenie tela (index 0 - pod pinmi) novým telom podľa aktuálneho mena a strany
     * a presunutie vývodu na vrchol trojuholníka. Nemení obsadenosť mriežky.
     */
    private void rebuildVisuals() {
        if (body != null && getChildren().contains(body)) {
            getChildren().remove(body);
        } else if (!getChildren().isEmpty()) {
            // placeholder vytvorený v drawBody() pri inicializácii GateSymbol
            for (int i = getChildren().size() - 1; i >= 0; i--) {
                Node n = getChildren().get(i);
                if (n == lead) continue;
                getChildren().remove(i);
            }
        }
        body = buildBodyPane();
        getChildren().add(0, body);
        placeLead();
    }

    // === kontextové menu ===

    private void showMenu(double screenX, double screenY) {
        if (contextMenu == null) {
            Menu placement = new Menu("Vývod");
            ToggleGroup placementGroup = new ToggleGroup();
            placement.getItems().add(placementItem("Vpravo", Side.RIGHT, placementGroup));
            placement.getItems().add(placementItem("Vľavo", Side.LEFT, placementGroup));
            placement.getItems().add(placementItem("Hore", Side.TOP, placementGroup));
            placement.getItems().add(placementItem("Dole", Side.BOTTOM, placementGroup));

            MenuItem nameItem = new MenuItem("Meno");
            nameItem.setOnAction(event -> renameSignal());

            contextMenu = new ContextMenu(placement, new SeparatorMenuItem(), nameItem);
        }
        ContextMenuHelper.show(contextMenu, body, screenX, screenY);
    }

    private RadioMenuItem placementItem(String text, Side newSide, ToggleGroup group) {
        RadioMenuItem item = new RadioMenuItem(text);
        item.setToggleGroup(group);
        item.setUserData(newSide);
        item.setOnAction(event -> setSide((Side) item.getUserData()));
        if (newSide == side) item.setSelected(true);
        return item;
    }

    /**
     * Dialóg na zadanie (alebo výber) mena signálu. Vracia zvolené meno, alebo null,
     * ak používateľ dialóg zrušil.
     */
    private String showNameDialog() {
        SchematicSheet sheet = getSheet();
        if (sheet == null) return null;

        ObservableList<String> existing = FXCollections.observableArrayList();
        for (GateSymbol gate : sheet.getGates()) {
            if (!(gate instanceof Signal)) continue;
            Signal other = (Signal) gate;
            if (other == this) continue;
            String name = other.getSignalName();
            if (name != null && !name.isEmpty() && !existing.contains(name)) {
                existing.add(name);
            }
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Názov signálu");
        dialog.setHeaderText(null);
        dialog.setResizable(false);

        Label prompt = new Label("Meno signálu:");
        ComboBox<String> comboBox = new ComboBox<>(existing);
        comboBox.setEditable(true);
        if (signalName != null) comboBox.setValue(signalName);

        VBox dialogContent = new VBox(8, prompt, comboBox);
        dialogContent.setPadding(new Insets(10));

        ButtonType okButtonType = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Zrušiť", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, cancelButtonType);
        dialog.getDialogPane().setContent(dialogContent);
        dialog.setResultConverter(button -> button == okButtonType ? comboBox.getEditor().getText() : null);

        Node okButton = dialog.getDialogPane().lookupButton(okButtonType);
        comboBox.getEditor().textProperty().addListener((obs, old, value) ->
                okButton.setDisable(value == null || value.trim().isEmpty()));
        okButton.setDisable(comboBox.getEditor().getText() == null
                || comboBox.getEditor().getText().trim().isEmpty());

        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent()) return null;
        String value = result.get().trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Po položení signálu z paletky sa automaticky priradí jednoznačné meno Sxx.
     */
    private void assignAutoName() {
        if (named || signalName != null && !signalName.isEmpty()) return;
        SchematicSheet sheet = getSheet();
        if (sheet == null) return;
        boolean[] used = new boolean[100];
        for (GateSymbol gate : sheet.getGates()) {
            if (!(gate instanceof Signal)) continue;
            String name = ((Signal) gate).getSignalName();
            if (name == null || name.length() != 3 || !name.startsWith("S")) continue;
            try {
                int n = Integer.parseInt(name.substring(1));
                if (n >= 0 && n < 100) used[n] = true;
            } catch (NumberFormatException ignored) {
            }
        }
        for (int i = 0; i < 100; i++) {
            if (!used[i]) {
                applyLayout(String.format("S%02d", i), side);
                named = true;
                return;
            }
        }
        applyLayout("S00", side);
        named = true;
    }

    // === vlastnosti ===

    /**
     * Meno signálu - signály s rovnakým menom sú navzájom prepojené.
     */
    public String getSignalName() {
        return signalName;
    }

    /**
     * Nastavenie mena signálu - zmení sa aj šírka tela.
     */
    public void setSignalName(String name) {
        applyLayout(name, side);
        named = signalName != null && !signalName.isEmpty();
    }

    /**
     * Strana, na ktorej je vývod signálu.
     */
    public Side getSide() {
        return side;
    }

    /**
     * Presun vývodu na inú stranu tela (zmení sa aj tvar trojuholníka).
     */
    private void setSide(Side newSide) {
        if (newSide == null || newSide == side) return;
        applyLayout(signalName, newSide);
    }

    private void renameSignal() {
        String chosen = showNameDialog();
        if (chosen == null) return;
        setSignalName(chosen);
    }

    // === simulácia ===

    @Override
    public void simulate() {
        if (signalName == null || signalName.isEmpty()) return;

        List<Signal> group = groupMembers();
        Potential.Value value = groupValue(group);

        Pin.PinState state;
        switch (value) {
            case HIGH: state = Pin.PinState.HIGH; break;
            case LOW: state = Pin.PinState.LOW; break;
            default: state = Pin.PinState.NOT_CONNECTED; break;
        }
        setPin(lead, state);
    }

    @Override
    public void reset() {
        if (lead != null) {
            lead.setState(Pin.PinState.NOT_CONNECTED);
            // bez postChangeEvent - reset je len vizuálny/čistý stav, nepotrebujeme spúšťať simuláciu
        }
    }

    /**
     * Všetky signály na ploche s rovnakým menom - tvoria jednu elektrickú skupinu.
     */
    private List<Signal> groupMembers() {
        List<Signal> group = new ArrayList<>();
        SchematicSheet sheet = getSheet();
        if (sheet == null || signalName == null) return group;
        for (GateSymbol gate : sheet.getGates()) {
            if (!(gate instanceof Signal)) continue;
            Signal other = (Signal) gate;
            if (signalName.equals(other.getSignalName())) group.add(other);
        }
        return group;
    }

    /**
     * Hodnota skupiny signálov s rovnakým menom. Určuje ju sieť, na ktorej je skutočný
     * budič (výstup hradla alebo odbočka zbernice); ak budič nie je, signály zostávajú
     * plávajúce. Pri dvoch rozporných budičoch je hodnota neurčitá.
     */
    private static Potential.Value groupValue(List<Signal> group) {
        Potential.Value result = Potential.Value.NC;
        for (Signal member : group) {
            if (!member.hasForeignDriver(group)) continue;
            Potential.Value value = member.netValue();
            if (value == Potential.Value.NC) continue;
            if (result == Potential.Value.NC) {
                result = value;
            } else if (result != value) {
                return Potential.Value.NC;
            }
        }
        return result;
    }

    /**
     * Je na sieti vývodu cudzí budič (výstup hradla, odbočka zbernice)? Vlastné
     * signály skupiny sa za budiče nepovažujú.
     */
    private boolean hasForeignDriver(List<Signal> group) {
        Potential aggregate = lead.getPotential();
        if (aggregate == null) return false;

        List<Connectable> nodes = new ArrayList<>();
        aggregate.getConnectedNodes(nodes);
        for (Connectable node : nodes) {
            if (!(node instanceof Pin)) continue;
            Pin pin = (Pin) node;
            Object owner = pin.getOwner();
            if (owner instanceof Signal && group.contains(owner)) continue;
            PinType type = pin.getOwnedPotential().getType();
            if (type == PinType.OUT || type == PinType.IO
                    || type == PinType.TRI_OUT || type == PinType.WEAK_OUT) {
                return true;
            }
        }
        return false;
    }

    /**
     * Aktuálna hodnota siete, na ktorej je vývod signálu.
     */
    private Potential.Value netValue() {
        Potential aggregate = lead.getPotential();
        return aggregate != null ? aggregate.getValue() : Potential.Value.NC;
    }

    // === rozmery a uloženie ===

    @Override
    public int getGridWidth() {
        if (side == Side.TOP || side == Side.BOTTOM) {
            return 3;
        }
        return side == Side.RIGHT ? 5 : 5;
    }

    @Override
    public int getGridHeight() {
        if (side == Side.TOP || side == Side.BOTTOM) {
            return 5;
        }
        return 3;
    }

    @Override
    public Map<String, String> saveProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        if (signalName != null && !signalName.isEmpty()) {
            properties.put("signalName", signalName);
        }
        properties.put("pinSide", side.name());
        return properties;
    }

    @Override
    public void loadProperties(Map<String, String> properties) {
        String name = properties.get("signalName");
        String sideValue = properties.get("pinSide");

        Side loadedSide = side;
        if (sideValue != null) {
            try {
                loadedSide = Side.valueOf(sideValue);
            } catch (IllegalArgumentException ignored) {
            }
        }
        applyLayout(name != null ? name : "", loadedSide);
    }

    @Override
    public String getName() {
        return "SIG";
    }

    @Override
    public String getShortDescription() {
        return "Signál - pomenovaný bod siete; signály s rovnakým menom sú elektricky prepojené";
    }
}
