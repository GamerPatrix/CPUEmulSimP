package sk.uniza.fri.cp.SchematicSim.Buses;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import sk.uniza.fri.cp.Bus.Bus;
import sk.uniza.fri.cp.SchematicSim.ContextMenuHelper;
import sk.uniza.fri.cp.SchematicSim.Electrical.PinType;
import sk.uniza.fri.cp.SchematicSim.Electrical.Potential;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Side;
import sk.uniza.fri.cp.SchematicSim.Wire.Wire;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dátová 8-bitová zbernica zobrazená ako hrubá zvislá čiara ("lištová zbernica").
 * <p>
 * Chovanie (rovnako ako {@link AddressBus16}):
 * <ul>
 *     <li>dĺžku čiary možno meniť potiahnutím koncového rukoväťa (spodný koniec myšou);</li>
 *     <li>ľavé kliknutie na čiaru otvorí menu výberu vývodu DB0..DB7 a vytvorí vývod;</li>
 *     <li>natiahnutie spojenia (vodiča) na čiaru a pustenie myši tiež otvorí menu a pripojí vodič;</li>
 *     <li>každý vývod má meno Dx zobrazené nad spojením vedľa zbernice;</li>
 *     <li>z jednej zbernice možno ťahať viac rovnakých signálov - každé odbočenie tvorí
 *         vlastný samostatný vývod (potenciálová sieť má pre každý vývod vlastný list).</li>
 * </ul>
 * <p>
 * Podľa riadiacich signálov CPU zbernica buď premieta dáta zo zbernice na vývody
 * (zápis - vývody hná), alebo číta hodnotu z vývodov obvodu a ukladá ju na zbernicu
 * (čítanie - vývody sú v stave vysokej impedancie, aby mohol obvod odpovedať).
 *
 * @author Tomáš Hianik (pôvodný autor DataBusCommunicator), adaptácia pre SchematicSim,
 *         redizajn na lištovú zbernicu (Claude)
 */
public class DataBus8 extends BusSymbol {

    private static final String[] PIN_NAMES = createPinNames();

    private static String[] createPinNames() {
        String[] names = new String[8];
        for (int index = 0; index < names.length; index++) {
            names[index] = "DB" + index;
        }
        return names;
    }

    private static final int RAIL_WIDTH = 2;
    private static final int DEFAULT_ROWS = 8;
    private static final int MIN_ROWS = 3;
    private static final int MAX_ROWS = 60;

    private static final Color RAIL_COLOR = Color.DARKGREEN;

    /** Hrúbka čiary relatívne k bunke (vodič má ~6 px, lišta teda o málo hrubšia). */
    private static final double RAIL_THICKNESS = 0.45;

    private int rows = DEFAULT_ROWS;
    /** Riadok, na ktorom začína nakreslená lišta (0 = horný okraj súčiastky). */
    private int topRow = 0;

    private Pane railPane;
    private Rectangle line;
    private Circle resizeHandle;
    private Circle resizeTopHandle;
    private Text title;
    private boolean resizing;
    private boolean resizingTop;

    private List<Pin> pinsRef;
    private final List<TapPoint> taps = new ArrayList<>();

    private ContextMenu signalMenu;
    private ContextMenu deleteMenu;
    private MenuItem allMenuItem;
    private MenuItem deleteMenuItem;
    private final RadioMenuItem[] menuItems = new RadioMenuItem[8];

    private volatile boolean visualsScheduled;

    private final ChangeListener<Number> onDataBusChange = (obs, oldValue, newValue) -> handleDataChange();
    private final ChangeListener<Number> onControlBusChange = (obs, oldValue, newValue) -> handleControlChange();

    private volatile boolean read;
    private volatile boolean write;
    private volatile int data;

    public DataBus8() {
        super();
    }

    @Override
    public Pane getImage() {
        Text label = new Text(getName());
        label.setLayoutY(label.getBoundsInLocal().getHeight());

        Image image = new Image(getClass().getResourceAsStream("/icons/bus.png"));
        ImageView view = new ImageView(image);
        view.setLayoutY(label.getBoundsInLocal().getHeight() + 5);

        label.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> (image.getWidth() - label.getBoundsInLocal().getWidth()) / 2.0,
                image.widthProperty(), label.boundsInLocalProperty()));

        return new Pane(label, view);
    }

    public DataBus8(SchematicSheet sheet) {
        super(sheet);
        // layout by inak po každom pulze prispôsoboval rozmery resizable railPane pref size
        // (rozsah jeho children), čo zväčšovalo maxY, keď lišta siaha nad počiatok súčiastky
        setAutoSizeChildren(false);
        getBus().dataBusProperty().addListener(onDataBusChange);
        getBus().controlBusProperty().addListener(onControlBusChange);
        this.data = Byte.toUnsignedInt(getBus().getDataBus());
        initRail();
        syncFromBus();
    }

    @Override
    protected List<Pin> createPins() {
        this.pinsRef = new ArrayList<>();
        return this.pinsRef;
    }

    @Override
    protected String getBusLabel() {
        return "DB";
    }

    @Override
    protected String[] getBusPinNames() {
        return PIN_NAMES;
    }

    @Override
    protected Pane drawBody() {
        this.railPane = new Pane();
        return this.railPane;
    }

    private void initRail() {
        int cell = getSheet().getComponentCell();
        double railCenterX = RAIL_WIDTH * cell / 2.0;
        double thickness = Math.max(4, cell * RAIL_THICKNESS);

        line = new Rectangle(railCenterX - thickness / 2.0, topRow * cell + cell / 2.0, thickness, railHeight(cell));
        line.setFill(RAIL_COLOR);
        line.setStroke(Color.BLACK);
        line.setStrokeWidth(1);
        line.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleRailClick);
        line.addEventHandler(MouseEvent.MOUSE_RELEASED, this::handleRailRelease);

        // neviditeľné rukoväte na zmenu dĺžky - cítiť ich je len kurzorom, samotné nie sú vidieť.
        // Obidve rukoväte sú vystredené na nakreslené konce čiary, aby sa kurzor menil
        // presne pri koncoch zbernice a nie pod ňou / nad ňou.
        resizeHandle = new Circle(railCenterX, rows * cell - cell / 2.0, cell * 0.35, Color.TRANSPARENT);
        resizeHandle.setStroke(Color.TRANSPARENT);
        resizeHandle.setCursor(Cursor.N_RESIZE);
        resizeHandle.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleResizePressed);
        resizeHandle.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleResizeDragged);
        resizeHandle.addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleResizeReleased);

        resizeTopHandle = new Circle(railCenterX, topRow * cell + cell / 2.0, cell * 0.35, Color.TRANSPARENT);
        resizeTopHandle.setStroke(Color.TRANSPARENT);
        resizeTopHandle.setCursor(Cursor.N_RESIZE);
        resizeTopHandle.addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleResizePressed);
        resizeTopHandle.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleResizeDragged);
        resizeTopHandle.addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleResizeReleased);

        Text title = new Text(getBusLabel());
        title.setLayoutX(railCenterX - cell * 0.55);
        title.setLayoutY(-cell * 0.15);
        title.setFont(Font.font(cell * 0.6));
        title.setFill(RAIL_COLOR);
        title.setMouseTransparent(true);
        this.title = title;

        railPane.getChildren().addAll(line, title, resizeHandle, resizeTopHandle);
    }

    /** Výška nakreslenej čiary v px pre danú bunku (od horného po dolný okraj lišty). */
    private double railHeight(int cell) {
        return (rows - topRow) * cell - cell;
    }

    /**
     * Vytvorenie vývodu "D{bit}" na riadku s lokálnou súradnicou localY. Vracia pin
     * vývodu (aby sa naň dal pripojiť test/vodič). Verejné kvôli testom.
     */
    public Pin createTap(int bit, int localY) {
        int cell = getSheet().getComponentCell();
        return createTap(bit, (double) localY);
    }

    public Pin createTap(int bit, double localY) {
        int cell = getSheet().getComponentCell();
        double x = RAIL_WIDTH * cell / 2.0;
        double y = snap(clamp(localY, (topRow + 1) * cell, rows * cell), cell);

        TapPin pin = new TapPin(this, "DB" + bit, bit);
        pin.setLayoutX(x);
        pin.setLayoutY(y);
        this.getChildren().add(pin);

        Circle dot = new Circle(x, y, cell * 0.28, Color.GRAY);
        dot.setStroke(Color.BLACK);
        dot.setStrokeWidth(1);
        // dot je len vizuálny - myš ide skrz na pin, aby ťahaním z vývodu vzniklo spojenie
        dot.setMouseTransparent(true);

        Text label = new Text("DB" + bit);
        label.setLayoutX(x + cell * 0.6);
        label.setLayoutY(y - cell * 0.4);
        label.setFont(Font.font(cell * 0.5));
        label.setFill(RAIL_COLOR);
        label.setMouseTransparent(true);

        TapPoint tap = new TapPoint(pin, dot, label, bit);
        getChildren().addAll(dot, label);
        taps.add(tap);
        pinsRef.add(pin);

        // pravým tlačidlom na vývode sa zobrazí menu so zmazaním vývodu zo zbernice
        pin.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                openDeleteMenu(e.getScreenX(), e.getScreenY(), () -> deleteTap(tap));
            }
        });

        driveTap(tap);
        return pin;
    }

    private void changeTapBit(TapPoint tap, int bit) {
        tap.bit = bit;
            tap.label.setText("DB" + bit);
        driveTap(tap);
    }

    private void driveTap(TapPoint tap) {
        if (write) {
            boolean high = (data & (1 << tap.bit)) != 0;
            setPin(tap.pin, high ? Pin.PinState.HIGH : Pin.PinState.LOW);
        } else {
            setPin(tap.pin, Pin.PinState.HIGH_IMPEDANCE);
        }
        scheduleTapVisuals();
    }

    private void scheduleTapVisuals() {
        if (visualsScheduled) return;
        visualsScheduled = true;
        Platform.runLater(() -> {
            visualsScheduled = false;
            for (TapPoint tap : taps) {
                // kružok ukazuje rozlíšenú hodnotu celej siete (silný vodič na sieti
                // pretiahne slabú hodnotu zbernice), nie stav, ktorý si pin nastavila samotná zbernica
                Potential.Value value = tap.pin.getPotential() != null
                        ? tap.pin.getPotential().getValue()
                        : Potential.Value.NC;
                tap.dot.setFill(tapDotColor(RAIL_COLOR, value));
            }
        });
    }

    /**
     * Vytvorenie vývodov pre všetky bity zbernice naraz. Vynecháva bity, ktoré už vývod majú,
     * a nové vývody umiestni na prvé voľné riadky (aby neprekrývali už existujúce). Ak je lišta
     * príliš krátka, predĺži ju. Vracia prvý vytvorený vývod (alebo prvý už
     * existujúci), aby sa naň dal pripojiť ťahaný vodič.
     */
    private Pin createAllTaps() {
        int cell = getSheet().getComponentCell();
        int count = menuItems.length;
        Pin first = null;
        Set<Integer> usedRows = new HashSet<>();
        for (TapPoint tap : taps) {
            usedRows.add((int) Math.round(tap.pin.getLayoutY() / cell));
        }
        int nextFreeRow = 1;
        for (int i = 0; i < count; i++) {
            if (hasTap(i)) continue;
            while (usedRows.contains(nextFreeRow)) {
                nextFreeRow++;
            }
            if (nextFreeRow >= rows) {
                setRows(nextFreeRow + 1);
            }
            usedRows.add(nextFreeRow);
            Pin pin = createTap(i, nextFreeRow * cell);
            if (first == null) first = pin;
            nextFreeRow++;
        }
        if (first == null && !pinsRef.isEmpty()) {
            first = pinsRef.get(0);
        }
        return first;
    }

    private boolean hasTap(int bit) {
        for (TapPoint tap : taps) {
            if (tap.bit == bit) return true;
        }
        return false;
    }

    // === kliknutie na čiaru / odovzdanie vodiča ===

    private void handleRailClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) return;
        if (Pin.getInProgressWire() != null) return;
        final double localY = toLocalY(event);
        openSignalMenu(bit -> createTap(bit, localY), this::createAllTaps, -1, event.getScreenX(), event.getScreenY());
    }

    private void handleRailRelease(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) return;
        Wire creating = Pin.getInProgressWire();
        if (creating == null) return;
        if (creating.areBothEndsConnected()) return;

        final double localY = toLocalY(event);
        double thickness = Math.max(4, getSheet().getComponentCell() * RAIL_THICKNESS);
        final boolean leftWired = event.getX() < thickness / 2.0;

        openSignalMenu(bit -> {
            Pin tapPin = createTap(bit, localY);
            tapPin.setSide(leftWired ? Side.LEFT : Side.RIGHT);
            creating.catchFreeEnd().connect(tapPin);
            creating.setMouseTransparent(false);
            creating.setOpacity(1);
            Pin.finishInProgressWire();
        }, () -> {
            Pin tapPin = createAllTaps();
            if (tapPin != null) {
                tapPin.setSide(leftWired ? Side.LEFT : Side.RIGHT);
                creating.catchFreeEnd().connect(tapPin);
                creating.setMouseTransparent(false);
                creating.setOpacity(1);
                Pin.finishInProgressWire();
            }
        }, -1, event.getScreenX(), event.getScreenY());
    }

    // === zmena dĺžky čiary ===

    private void handleResizePressed(MouseEvent event) {
        if (!event.isPrimaryButtonDown()) return;
        resizing = true;
        resizingTop = event.getSource() == resizeTopHandle;
        event.consume();
    }

    private void handleResizeDragged(MouseEvent event) {
        if (!resizing) return;
        int cell = getSheet().getComponentCell();
        double localY = toLocalY(event);
        if (resizingTop) {
            int maxTopRow = Math.min(rows - MIN_ROWS, minTopRowForTaps());
            int newTopRow = clamp((int) Math.round((localY - cell / 2.0) / cell), -MAX_ROWS, maxTopRow);
            if (newTopRow != topRow) {
                topRow = newTopRow;
                updateRailGeometry();
                refreshSelectionShape();
                getSheet().markChanged();
            }
        } else {
            int newRows = clamp((int) Math.round(localY / cell), Math.max(topRow + MIN_ROWS, minRowsForTaps()), MAX_ROWS);
            if (newRows != rows) {
                rows = newRows;
                updateRailGeometry();
                refreshSelectionShape();
                getSheet().markChanged();
            }
        }
        event.consume();
    }

    private void handleResizeReleased(MouseEvent event) {
        if (!resizing) return;
        resizing = false;
        resizingTop = false;
        event.consume();
    }

    /** Premietnutie topRow/rows do geometrie čiary a rukovätí. */
    private void updateRailGeometry() {
        int cell = getSheet().getComponentCell();
        line.setY(topRow * cell + cell / 2.0);
        line.setHeight(railHeight(cell));
        resizeHandle.setCenterY(rows * cell - cell / 2.0);
        resizeTopHandle.setCenterY(topRow * cell + cell / 2.0);
        title.setLayoutY(topRow * cell - cell * 0.15);
    }

    // === menu výberu vývodu ===

    private void openSignalMenu(Consumer<Integer> onSelect, Runnable onSelectAll, int preselected, double screenX, double screenY) {
        ContextMenu menu = ensureMenu();
        allMenuItem.setOnAction(event -> {
            if (onSelectAll != null) onSelectAll.run();
        });
        for (int i = 0; i < menuItems.length; i++) {
            final int bit = i;
            RadioMenuItem item = menuItems[i];
            item.setSelected(i == preselected);
            item.setOnAction(event -> {
                if (onSelect != null) onSelect.accept(bit);
            });
        }
        menu.setOnHidden(event -> cancelInProgressWire());
        ContextMenuHelper.show(menu, railPane, screenX, screenY);
    }

    /**
     * Kontextové menu otvorené pravým tlačidlom na vývode obsahuje len položku "Zmazať".
     */
    private void openDeleteMenu(double screenX, double screenY, Runnable onDelete) {
        ContextMenu menu = ensureDeleteMenu();
        deleteMenuItem.setOnAction(event -> onDelete.run());
        menu.setOnHidden(event -> cancelInProgressWire());
        ContextMenuHelper.show(menu, railPane, screenX, screenY);
    }

    private ContextMenu ensureDeleteMenu() {
        if (deleteMenu != null) return deleteMenu;
        deleteMenu = new ContextMenu();
        deleteMenuItem = new MenuItem("Zmazať");
        deleteMenu.getItems().add(deleteMenuItem);
        return deleteMenu;
    }

    /**
     * Odstránenie vývodu "D{bit}" zo zbernice vrátane vodiča pripojeného na jeho koniec.
     */
    private void deleteTap(TapPoint tap) {
        if (tap.pin.getWireEnd() != null) {
            tap.pin.getWireEnd().getWire().delete();
        }
        getChildren().removeAll(tap.pin, tap.dot, tap.label);
        taps.remove(tap);
        pinsRef.remove(tap.pin);
    }

    private ContextMenu ensureMenu() {
        if (signalMenu != null) return signalMenu;
        signalMenu = new ContextMenu();
        allMenuItem = new MenuItem("Všetky");
        signalMenu.getItems().addAll(allMenuItem, new SeparatorMenuItem());
        ToggleGroup group = new ToggleGroup();
        for (int i = 0; i < menuItems.length; i++) {
            RadioMenuItem item = new RadioMenuItem("DB" + i);
            item.setToggleGroup(group);
            menuItems[i] = item;
            signalMenu.getItems().add(item);
        }
        return signalMenu;
    }

    // === testovacie prístupy (package-private, nepatria do verejného API) ===

    ContextMenu getSignalMenu() {
        return signalMenu;
    }

    Rectangle getRailLine() {
        return line;
    }

    Circle getResizeHandle() {
        return resizeHandle;
    }

    private void cancelInProgressWire() {
        Wire creating = Pin.getInProgressWire();
        if (creating != null) {
            creating.setMouseTransparent(false);
            creating.setOpacity(1);
            if (!creating.areBothEndsConnected()) creating.delete();
            Pin.finishInProgressWire();
        }
    }

    // === pomocné ===

    private double toLocalY(MouseEvent event) {
        // lišta aj rukoväť majú lokálny počiatok v ľavom hornom rohu súčiastky,
        // takže getY() udalosti je priamo súradnica v lokálnom priestore súčiastky
        return event.getY();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double snap(double value, double cell) {
        return Math.round(value / cell) * cell;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // === komunikácia so zbernicou ===

    @Override
    public void syncFromBus() {
        int ctrl = getBus().getControlBus();
        boolean readActive = (ctrl & 0xB0) != 0xB0;
        boolean writeActive = (ctrl & 0x140) != 0x140;
        this.data = Byte.toUnsignedInt(getBus().getDataBus());
        this.read = readActive;
        this.write = writeActive;
        for (TapPoint tap : taps) {
            driveTap(tap);
        }
    }

    /**
     * Nastavenie počtu riadkov (dĺžky) lištovej zbernice. Ekvivalent potiahnutia spodného rukoväťa.
     */
    public void setRows(int newRows) {
        int cell = getSheet().getComponentCell();
        int clamped = clamp(newRows, Math.max(topRow + MIN_ROWS, minRowsForTaps()), MAX_ROWS);
        rows = clamped;
        updateRailGeometry();
        refreshSelectionShape();
    }

    /** Nastavenie horného riadku (posun horného konca lišty). */
    public void setTopRow(int newTopRow) {
        int cell = getSheet().getComponentCell();
        int maxTopRow = Math.min(rows - MIN_ROWS, minTopRowForTaps());
        int clamped = clamp(newTopRow, -MAX_ROWS, maxTopRow);
        topRow = clamped;
        updateRailGeometry();
        refreshSelectionShape();
    }

    /**
     * Priame nastavenie horného riadku bez geometrických prepočtov - použité pri načítaní,
     * keď ešte nie je nastavený počet riadkov (rows) a clamp by ohýbal hodnotu.
     */
    private void setTopRowInternal(int newTopRow) {
        topRow = newTopRow;
    }

    /**
     * Najmenší možný počet riadkov tak, aby bola lišta stále pod všetkými vývodmi
     * (plus rezervný riadok kvôli rukoväti na zmenu dĺžky).
     */
    private int minRowsForTaps() {
        int cell = getSheet().getComponentCell();
        int maxRow = 0;
        for (TapPoint tap : taps) {
            maxRow = Math.max(maxRow, (int) Math.round(tap.pin.getLayoutY() / cell));
        }
        return maxRow + 1;
    }

    /**
     * Najväčší možný horný riadok tak, aby bola lišta stále nad všetkými vývodmi
     * (o rezervný riadok nižšie, aby horný koniec nekryl najvyšší vývod).
     * Bez vývodov neobmedzuje (vracia Integer.MAX_VALUE, clamp použije rows - MIN_ROWS).
     */
    private int minTopRowForTaps() {
        if (taps.isEmpty()) return Integer.MAX_VALUE;
        int cell = getSheet().getComponentCell();
        int minRow = Integer.MAX_VALUE;
        for (TapPoint tap : taps) {
            minRow = Math.min(minRow, (int) Math.round(tap.pin.getLayoutY() / cell));
        }
        return minRow - 1;
    }

    @Override
    public Map<String, String> saveProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("rows", Integer.toString(rows));
        properties.put("topRow", Integer.toString(topRow));

        StringBuilder tapsValue = new StringBuilder();
        for (TapPoint tap : taps) {
            if (tapsValue.length() > 0) tapsValue.append(';');
            tapsValue.append(tap.bit).append(':').append(Math.round(tap.pin.getLayoutY()));
        }
        properties.put("taps", tapsValue.toString());
        return properties;
    }

    @Override
    public void loadProperties(Map<String, String> properties) {
        String topRowValue = properties.get("topRow");
        if (topRowValue != null) {
            try {
                setTopRowInternal(Integer.parseInt(topRowValue.trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        String rowsValue = properties.get("rows");
        if (rowsValue != null) {
            try {
                setRows(Integer.parseInt(rowsValue.trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        String tapsValue = properties.get("taps");
        if (tapsValue != null && !tapsValue.isEmpty()) {
            for (String entry : tapsValue.split(";")) {
                String[] part = entry.split(":");
                if (part.length != 2) continue;
                try {
                    int bit = Integer.parseInt(part[0].trim());
                    double y = Double.parseDouble(part[1].trim());
                    createTap(bit, y);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private void handleDataChange() {
        // pri čítaní sa riadime iba podľa simulácie (obvod odpovedá na zbernicu)
        if ((getBus().getControlBus() & 0xB0) != 0xB0) return;
        this.data = Byte.toUnsignedInt(getBus().getDataBus());
        for (TapPoint tap : taps) {
            boolean high = (data & (1 << tap.bit)) != 0;
            setPin(tap.pin, high ? Pin.PinState.HIGH : Pin.PinState.LOW);
        }
        scheduleTapVisuals();
    }

    private void handleControlChange() {
        int ctrl = getBus().getControlBus();
        boolean writeActive = (ctrl & 0x140) != 0x140;
        boolean readActive = (ctrl & 0xB0) != 0xB0;

        // Mimo čítania si dáta vezmeme priamo zo zbernice. setDataBus() s rovnakou hodnotou
        // (napr. 0x00 po prečítaní vektora prerušenia) nevyvolá zmenu vlastnosti, takže by
        // v this.data ostala zastaraná hodnota a na vývody by sme hnali nesprávne bity
        // (napr. REG1 by si namiesto nuly zapamätal 0x0F a zablokoval by detekciu kláves).
        if (!readActive) {
            this.data = Byte.toUnsignedInt(getBus().getDataBus());
        }

        if (writeActive && !write) {
            // začiatok zápisu - premietneme dáta zo zbernice na vývody
            write = true;
            Bus.getBus().dataIsChanging();
            for (TapPoint tap : taps) {
                driveTap(tap);
            }
        } else if (!writeActive && write) {
            // koniec zápisu - dáta na vývodoch necháme podržané; zmenia sa až pri setRandomData()
            write = false;
            Bus.getBus().dataIsChanging();
        }

        if (readActive && !read) {
            // začiatok čítania - vývody prepneme do stavu vysokej impedancie,
            // aby mohol obvod odpovedať na zbernicu
            read = true;
            Bus.getBus().dataIsChanging();
            for (TapPoint tap : taps) {
                setPin(tap.pin, Pin.PinState.HIGH_IMPEDANCE);
            }
        } else if (!readActive && read) {
            read = false;
            Bus.getBus().dataIsChanging();
            for (TapPoint tap : taps) {
                setPin(tap.pin, Pin.PinState.HIGH_IMPEDANCE);
            }
        }
    }

    private void readPinsIntoBus() {
        // Vychádzame z aktuálnej hodnoty zbernice a meníme IBA bity, ktoré obvod
        // jednoznačne budí (silný vývod HIGH/LOW). Plávajúci (NC) bit sa NESMIE
        // nahradiť náhodnou hodnotou z Potential.getFloatingValue() - na zbernici by
        // vzniklo smetie (napr. nesprávny vektor prerušenia). Viacero vývodov DataBus8
        // na tej istej logickej zbernici sa takto zlúči bez vzájomného prepísania.
        int bus = Byte.toUnsignedInt(getBus().getDataBus());
        boolean changed = false;
        for (TapPoint tap : taps) {
            Potential.Value value = resolvedPotentialValue(tap.pin);
            if (value == Potential.Value.HIGH) {
                if ((bus & (1 << tap.bit)) == 0) {
                    bus |= (1 << tap.bit);
                    changed = true;
                }
            } else if (value == Potential.Value.LOW) {
                if ((bus & (1 << tap.bit)) != 0) {
                    bus &= ~(1 << tap.bit);
                    changed = true;
                }
            }
        }
        if (changed) {
            this.data = bus;
            getBus().setDataBus((byte) bus);
        }
    }

    /** Rozlíšená úroveň potenciálu na vývode; NC znamená plávajúci (nikto nebudi) vývod. */
    private static Potential.Value resolvedPotentialValue(Pin pin) {
        if (pin == null || !pin.isConnected()) return Potential.Value.NC;
        Potential potential = pin.getPotential();
        return potential != null ? potential.getValue() : Potential.Value.NC;
    }

    @Override
    public void simulate() {
        if (read) {
            readPinsIntoBus();
        } else if (write) {
            for (TapPoint tap : taps) {
                driveTap(tap);
            }
        }
        // pri zmene siete (iná súčiastka pretiahla slabú hodnotu zbernice) sa simulate()
        // pre compare vyvolá cez getGatesWithInputs - obnov preto aj vizuál kružkov
        scheduleTapVisuals();
    }

    @Override
    public void reset() {
        for (TapPoint tap : taps) {
            tap.pin.setState(Pin.PinState.NOT_CONNECTED);
        }
    }

    @Override
    public int getGridWidth() {
        return RAIL_WIDTH;
    }

    @Override
    public int getGridHeight() {
        return rows;
    }

    private static class TapPoint {
        final Pin pin;
        final Circle dot;
        final Text label;
        int bit;

        TapPoint(Pin pin, Circle dot, Text label, int bit) {
            this.pin = pin;
            this.dot = dot;
            this.label = label;
            this.bit = bit;
        }
    }

    private static class TapPin extends Pin {
        TapPin(DataBus8 owner, String name, int index) {
            super(owner, name, Direction.INOUT, 0, 0, Side.RIGHT);
            // slabý výstup: náhodné/udržiavané dáta zbernice nepretiahnu silný vodič
            // (OUT/IO/TRI_OUT) pripojený k tej istej sieti - ten vyhráva
            getOwnedPotential().setType(PinType.WEAK_OUT);
        }
    }
}