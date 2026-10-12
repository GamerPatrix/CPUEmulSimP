package sk.uniza.fri.cp.App.SchematicControl;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.ToggleSwitch;
import sk.uniza.fri.cp.App.CPUControl.CPUController;
import sk.uniza.fri.cp.CPUEmul.CPUStates;
import sk.uniza.fri.cp.SchematicSim.Buses.AddressBus16;
import sk.uniza.fri.cp.SchematicSim.Buses.ControlBus;
import sk.uniza.fri.cp.SchematicSim.Buses.DataBus8;
import sk.uniza.fri.cp.SchematicSim.Gates.AndGate;
import sk.uniza.fri.cp.SchematicSim.Gates.BusBuffer;
import sk.uniza.fri.cp.SchematicSim.Gates.Decoder38;
import sk.uniza.fri.cp.SchematicSim.Gates.Encoder83;
import sk.uniza.fri.cp.SchematicSim.Gates.Led;
import sk.uniza.fri.cp.SchematicSim.Gates.NandGate;
import sk.uniza.fri.cp.SchematicSim.Gates.NorGate;
import sk.uniza.fri.cp.SchematicSim.Gates.NotGate;
import sk.uniza.fri.cp.SchematicSim.Gates.OrGate;
import sk.uniza.fri.cp.SchematicSim.Gates.XorGate;
import sk.uniza.fri.cp.SchematicSim.Gates.Ram8k;
import sk.uniza.fri.cp.SchematicSim.Gates.SevenSegmentDisplay;
import sk.uniza.fri.cp.SchematicSim.Gates.PushButton;
import sk.uniza.fri.cp.SchematicSim.Gates.MatrixKeyboard;
import sk.uniza.fri.cp.SchematicSim.Gates.Register8;
import sk.uniza.fri.cp.SchematicSim.Gates.Input;
import sk.uniza.fri.cp.SchematicSim.ItemPicker;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchemeLoader;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;

import java.io.File;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Kontrolér okna simulátora so schémou - nahrádza {@code BreadboardController}.
 * Okno interaktívneho kreslenia logickej schémy, ktoré komunikuje s CPU cez zbernicu.
 * Otvára ho tlačidlo Simulátor v okne CPU.
 *
 * @author Claude (návrh podľa BreadboardController)
 */
public class SchematicController {

    private static final String WINDOW_TITLE = "Simulátor - Schéma";
    private static final double SCHEME_WIDTH = 5000;
    private static final double SCHEME_HEIGHT = 5000;
    private static final int GRID_SIZE = 10;

    private final CPUController cpuController;

    private SchematicSheet sheet;
    private ItemPicker itemPicker;
    private BorderPane root;

    private File currentFile;

    private ToggleSwitch tsPower;
    private ToggleSwitch tsDebug;
    private Button btnF5spusti;
    private Button btnF7krok;
    private Button btnF9pauza;
    private Button btnF10stop;
    private Button btnF12reset;
    private Label lbCoordinates;
    private Label lbZoom;
    private Label lbStatus;

    /**
     * Vytvorí celé okno simulátora (toolbar + plocha + stavový riadok).
     *
     * @param cpuController Controller okna CPU - poskytuje ovládanie behu programu
     *                      a stav CPU pre tlačidlá v tomto okne.
     */
    public SchematicController(CPUController cpuController) {
        this.cpuController = cpuController;

        //plocha schémy
        this.sheet = new SchematicSheet(SCHEME_WIDTH, SCHEME_HEIGHT, GRID_SIZE);

        //paletka súčiastok
        this.itemPicker = new ItemPicker();
        registerItems(this.itemPicker);

        //rozloženie okna
        SplitPane editableArea = new SplitPane(this.itemPicker, this.sheet);
        editableArea.setDividerPositions(0.13);

        this.root = new BorderPane();
        this.root.setTop(buildToolbar());
        this.root.setCenter(editableArea);

        this.root.setBottom(buildStatusBar());

        //manuálny spínač napájania - reflektuje aj automatické zapnutie/vypnutie simulácie
        //vlastnosť sa mení na simulačnom vlákne -> UI sa musí aktualizovať na FX vlákne,
        //inak by IllegalStateException zabilo celý simulačný task (a CPU by viselo na zbernici)
        this.sheet.simRunningProperty().addListener((obs, oldValue, newValue) -> {
            if (Platform.isFxApplicationThread()) updateSimulationControls(newValue);
            else Platform.runLater(() -> updateSimulationControls(newValue));
        });
        this.tsPower.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue) this.sheet.powerOn();
            else this.sheet.powerOff();
        });

        //tlačidlá behu CPU sledujú stav CPU (enabled/disabled)
        //vlastnosť sa mení na vlákne CPU -> tlačidlá sa musia meniť na FX vlákne,
        //inak by IllegalStateException zabilo celé vlákno CPU
        this.cpuController.cpuStateProperty().addListener((obs, oldValue, newValue) -> {
            if (Platform.isFxApplicationThread()) setButtons(newValue);
            else Platform.runLater(() -> setButtons(newValue));
        });
        setButtons(CPUStates.Idle);

        bindCpuButtons();
    }

    private void updateSimulationControls(boolean running) {
        if (running != this.tsPower.isSelected()) this.tsPower.setSelected(running);
        this.sheet.setEditingEnabled(!running); // editácia len pri vypnutej simulácii
        this.lbStatus.setText(running ? "Simulácia beží" : "Simulácia zastavená");
    }

    /**
     * Vytvorenie scény okna simulátora.
     */
    public Scene getScene() {
        return this.root.getScene() != null ? this.root.getScene() : new Scene(this.root, 1280, 850);
    }

    public SchematicSheet getSheet() {
        return this.sheet;
    }

    /**
     * Registrácia súčiastok do paletky.
     */
    private void registerItems(ItemPicker picker) {
        //zbernice
        picker.registerItem(new AddressBus16());
        picker.registerItem(new DataBus8());
        picker.registerItem(new ControlBus());

        //zariadenia komunikujúce so zbernicou CPU
        picker.registerItem(new Input());
        picker.registerItem(new Led());
        picker.registerItem(new PushButton());
        picker.registerItem(new MatrixKeyboard());
        picker.registerItem(new SevenSegmentDisplay());

        //logické hradlá
        picker.registerItem(new NotGate());
        picker.registerItem(new AndGate());
        picker.registerItem(new OrGate());
        picker.registerItem(new NandGate());
        picker.registerItem(new NorGate());
        picker.registerItem(new XorGate());
        picker.registerItem(new BusBuffer());

        //ostatné zariadenia
        picker.registerItem(new Register8());
        picker.registerItem(new Ram8k());

        //dekodér a kóder
        picker.registerItem(new Decoder38());
        picker.registerItem(new Encoder83());
    }

    private ToolBar buildToolbar() {
        ToolBar toolbar = new ToolBar();

        Button btnNew = iconButton("Nový", "/icons/empty-trash.png", () -> handleClearBoardAction(), "Vymazať plochu");
        Button btnLoad = iconButton("Otvoriť", "/icons/open.png", () -> handleLoadAction(), "Načítať schému zo súboru");
        Button btnSave = iconButton("Uložiť", "/icons/save.png", () -> handleSaveAction(), "Uložiť schému");
        Button btnSaveAs = iconButton("Uložiť ako", "/icons/save-as.png", () -> handleSaveAsAction(), "Uložiť schému do iného súboru");

        this.tsPower = new ToggleSwitch();
        this.tsPower.setSelected(false);
        this.tsPower.setTooltip(new Tooltip("Zapnúť / vypnúť simuláciu"));

        this.tsDebug = new ToggleSwitch();
        this.tsDebug.setSelected(false);
        this.tsDebug.setTooltip(new Tooltip("Prefarbiť vodiče podľa logického stavu (Z sivá, 0 modrá, 1 červená)"));
        this.tsDebug.selectedProperty().addListener((obs, oldValue, newValue) -> this.sheet.setDebugWires(newValue));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        this.btnF5spusti = cpuButton("Spustiť [F5]", "/icons/play.png");
        this.btnF7krok = cpuButton("Krok [F7]", "/icons/step-over.png");
        this.btnF9pauza = cpuButton("Pauza [F9]", "/icons/pause.png");
        this.btnF10stop = cpuButton("Stop [F10]", "/icons/stop.png");
        this.btnF12reset = cpuButton("Reset [F12]", "/icons/reset.png");

        toolbar.getItems().addAll(btnNew, btnLoad, btnSave, btnSaveAs,
                new Label(" | "),
                new Label("  |  "), new Label("On/Off:"), this.tsPower,
                new Label("  |  "), new Label("Debug:"), this.tsDebug,
                spacer, this.btnF5spusti, this.btnF7krok, this.btnF9pauza,
                this.btnF10stop, this.btnF12reset);

        return toolbar;
    }

    private Button iconButton(String text, String icon, Runnable action, String tooltip) {
        Button button = new Button(text);
        if (icon != null) {
            button.setGraphic(new ImageView(new Image(getClass().getResourceAsStream(icon))));
        }
        button.setOnAction(e -> action.run());
        if (tooltip != null) button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private Button cpuButton(String text, String icon) {
        Button button = new Button(text);
        if (icon != null) {
            button.setGraphic(new ImageView(new Image(getClass().getResourceAsStream(icon))));
        }
        button.setOnAction(e -> {});
        return button;
    }

    private BorderPane buildStatusBar() {
        BorderPane statusBar = new BorderPane();

        this.lbCoordinates = new Label("0x0");
        statusBar.setLeft(this.lbCoordinates);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        this.lbStatus = new Label("Simulácia zastavená");
        this.lbZoom = new Label("100%");
        this.lbStatus.setAlignment(Pos.CENTER_LEFT);
        this.lbZoom.setAlignment(Pos.CENTER_RIGHT);

        HBox right = new HBox(this.lbStatus, spacer, this.lbZoom);
        statusBar.setRight(right);

        //súradnice kurzora
        this.sheet.setOnMouseMoved(event -> {
            Point2D gridPoint = this.sheet.getMousePositionOnGrid(event);
            this.lbCoordinates.setText(((int) gridPoint.getX()) + "x" + ((int) gridPoint.getY()));
        });

        //aktuálne priblíženie
        this.sheet.zoomScaleProperty().addListener((obs, oldValue, newValue) ->
                this.lbZoom.setText(((int) (newValue.doubleValue() * 100)) + "%"));

        return statusBar;
    }

    /**
     * Prepojenie CPU tlačidiel na controller CPU.
     */
    private void bindCpuButtons() {
        this.btnF5spusti.setOnAction(e -> this.cpuController.handleButtonStartAction());
        this.btnF7krok.setOnAction(e -> this.cpuController.handleButtonStepAction());
        this.btnF9pauza.setOnAction(e -> this.cpuController.handleButtonPauseAction());
        this.btnF10stop.setOnAction(e -> this.cpuController.handleButtonStopAction());
        this.btnF12reset.setOnAction(e -> this.cpuController.handleButtonResetAction());
    }

    /**
     * Povolenie/zakázanie tlačidiel CPU podľa aktuálneho stavu CPU.
     */
    private void setButtons(CPUStates state) {
        switch (state) {
            case Running:
                this.btnF5spusti.setText("Spustiť [F5]");
                this.btnF5spusti.setDisable(true);
                this.btnF7krok.setDisable(true);
                this.btnF9pauza.setDisable(false);
                this.btnF10stop.setDisable(false);
                this.btnF12reset.setDisable(true);
                break;

            case Paused:
                this.btnF5spusti.setText("Pokračovať [F5]");
                this.btnF5spusti.setDisable(false);
                this.btnF7krok.setDisable(false);
                this.btnF9pauza.setDisable(true);
                this.btnF10stop.setDisable(false);
                this.btnF12reset.setDisable(true);
                break;

            case Waiting:
                this.btnF5spusti.setText("Spustiť [F5]");
                this.btnF5spusti.setDisable(true);
                this.btnF7krok.setDisable(true);
                this.btnF9pauza.setDisable(true);
                this.btnF10stop.setDisable(false);
                this.btnF12reset.setDisable(true);
                break;

            case Idle:
            default:
                this.btnF5spusti.setText("Spustiť [F5]");
                this.btnF5spusti.setDisable(false);
                this.btnF7krok.setDisable(false);
                this.btnF9pauza.setDisable(true);
                this.btnF10stop.setDisable(true);
                this.btnF12reset.setDisable(false);
                break;
        }
    }

    /**
     * Prepojenie scény na ovládanie - klávesy F5/F7/F9/F10/F12, Delete/Backspace a
     * preposielanie vstupu z klávesnice do CPU pri spustenom programe.
     */
    public void registerKeyboardHandlers() {
        Scene scene = this.root.getScene();
        if (scene == null) return;

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getEventType() != KeyEvent.KEY_PRESSED) return;
            switch (event.getCode()) {
                case F5:
                    this.btnF5spusti.fire();
                    event.consume();
                    break;
                case F7:
                    this.btnF7krok.fire();
                    event.consume();
                    break;
                case F9:
                    this.btnF9pauza.fire();
                    event.consume();
                    break;
                case F10:
                    this.btnF10stop.fire();
                    event.consume();
                    break;
                case F12:
                    this.btnF12reset.fire();
                    event.consume();
                    break;
                default:
                    break;
            }
        });

        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode().equals(KeyCode.DELETE) || event.getCode().equals(KeyCode.BACK_SPACE)) {
                this.sheet.deleteSelect();
                event.consume();
            }
        });

        scene.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (this.cpuController.isExecuting()) {
                this.cpuController.keyboardInput(event);
                event.consume();
            }
        });
    }

    //TLAČIDLÁ V NÁSTOJOVEJ LIŠTE

    /**
     * Uloženie schémy do súboru.
     */
    private void handleSaveAction() {
        saveCircuit(false);
    }

    /**
     * Uloženie schémy do iného súboru.
     */
    private void handleSaveAsAction() {
        saveCircuit(true);
    }

    /**
     * Načítanie schémy zo súboru.
     */
    private void handleLoadAction() {
        if (!continueIfUnsavedFile()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Načítať schému...");
        chooser.setInitialDirectory(currentFile != null
                ? currentFile.getParentFile()
                : new File(Paths.get("").toAbsolutePath().toString()));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SCHX", "*.schx"));

        File file = chooser.showOpenDialog(this.root.getScene().getWindow());
        if (file == null) return;

        if (this.sheet.isSimulationRunning()) {
            this.sheet.powerOff();
        }

        if (SchemeLoader.load(file, this.sheet)) {
            ((Stage) this.root.getScene().getWindow()).setTitle(WINDOW_TITLE + " - " + file.getName());
            currentFile = file;
            sheet.clearChange();
            sheet.resetUndoHistory();
        }
    }

    /**
     * Vyčistenie plochy (nová schéma).
     */
    private void handleClearBoardAction() {
        if (!continueIfUnsavedFile()) return;

        if (this.sheet.isSimulationRunning()) {
            this.sheet.powerOff();
        }

        this.sheet.clearSheet();
        ((Stage) this.root.getScene().getWindow()).setTitle(WINDOW_TITLE);
        currentFile = null;
        sheet.clearChange();
        sheet.resetUndoHistory();
    }

    /**
     * Výstraha s otázkou na ďalší postup, ak aktuálna schéma nie je uložená.
     *
     * @return true - môžeme pokračovať, false - užívateľ zrušil akciu
     */
    private boolean continueIfUnsavedFile() {
        if (!this.sheet.hasChanged()) return true;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Potvrdenie");
        alert.setHeaderText("Zmeny vo vašej schéme neboli uložené");
        alert.setContentText("Prajete si uložiť zmeny?");

        ButtonType btnTypeSave = new ButtonType("Uložiť");
        ButtonType btnTypeSaveAs = new ButtonType("Uložiť ako");
        ButtonType btnTypeNo = new ButtonType("Nie");
        ButtonType btnTypeCancel = new ButtonType("Zrušiť");

        alert.getButtonTypes().clear();
        if (currentFile != null) alert.getButtonTypes().add(btnTypeSave);
        alert.getButtonTypes().addAll(btnTypeSaveAs, btnTypeNo, btnTypeCancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == btnTypeCancel) {
                return false;
            } else if (result.get() == btnTypeSaveAs) {
                return saveCircuit(true);
            } else if (result.get() == btnTypeSave) {
                return saveCircuit(false);
            }
        }

        return true;
    }

    /**
     * Je s aktuálnou schémou spojený súbor (dá sa uložiť priamo)?
     */
    public boolean hasCurrentFile() {
        return currentFile != null;
    }

    /**
     * Uloženie schémy do súboru.
     *
     * @param saveAs Uložiť ako?
     * @return true ak sa podarilo uložiť, false inak.
     */
    public boolean saveCircuit(boolean saveAs) {
        File file = saveAs ? null : currentFile;

        if (currentFile == null || saveAs) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Uložiť schému" + (saveAs ? " ako" : "") + "...");
            chooser.setInitialDirectory(new File(Paths.get("").toAbsolutePath().toString()));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("SCHX", "*.schx"));
            if (currentFile != null) chooser.setInitialFileName(currentFile.getName());

            file = chooser.showSaveDialog(this.root.getScene().getWindow());
        }

        if (file == null) return false;

        if (SchemeLoader.save(file, this.sheet)) {
            this.sheet.clearChange();
            ((Stage) this.root.getScene().getWindow()).setTitle(WINDOW_TITLE + " - " + file.getName());
            currentFile = file;
            return true;
        }

        return false;
    }
}