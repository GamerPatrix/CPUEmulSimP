package sk.uniza.fri.cp.App;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import sk.uniza.fri.cp.App.CPUControl.CPUController;
import sk.uniza.fri.cp.App.SchematicControl.SchematicController;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;

/**
 * Aplikácia simulátora vývojovej dosky FRI UNIZA a emulátor 8 bitového CPU.
 *
 * @author Tomáš Hianik
 * @created 7.2.2017
 */
public class App extends Application {
    private static final int CPU_WINDOW_WIDTH = 1280;
    private static final int CPU_WINDOW_HEIGHT = 640;

    public static final String PROGRAM_VERSION = "0.9.7";
    public static final String WINDOW_TITLE = "CPU Emulator v. " + PROGRAM_VERSION;

    public static void main(String[] args) {
        launch();
    }
    public static Stage primStage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        //nacitanie ulozeneho motivu aplikacie (svetly / tmavy) a prihlasenie novych okien
        ThemeManager.install();

        //nacitanie predvolenych farieb vodicov schémy
        WireColorSettings.loadAndApply();

        //nacitanie sablony okna CPU
        FXMLLoader CpuLayoutLoader = new FXMLLoader();
        CpuLayoutLoader.setLocation(getClass().getResource("/fxml/Main_CPUEmul.fxml"));
        Parent mainContent = CpuLayoutLoader.load();

        //nacitanie sceny CPU
        Scene mainScene = new Scene(mainContent, CPU_WINDOW_WIDTH, CPU_WINDOW_HEIGHT);

        //priradenie CSS stylov k scene
        mainScene.getStylesheets().add(getClass().getResource("/css/CPUEmul_style.css").toExternalForm());

        //prihlasenie sceny pod spravu motivu (svetly / tmavy)
        ThemeManager.registerScene(mainScene);

        //zachytenie vsetkych klaves (okrem funkcnych klaves) na mainScene
        //(http://stackoverflow.com/questions/25397742/javafx-keyboard-event-shortcut-key)
        //a posielanie ich do CPU
        EventHandler<KeyEvent> onKeyPressed = (ke) -> {
            if (ke.getEventType() == KeyEvent.KEY_PRESSED) {
                CPUController controller = CpuLayoutLoader.getController();
                switch (ke.getCode()) {
                    case UP:
                    case DOWN:
                    case LEFT:
                    case RIGHT:
                        if (controller.isExecuting()) {
                            controller.keyboardInput(ke);
                            ke.consume();
                        }
                }
            }
        };
        EventHandler<KeyEvent> onKeyTyped = (ke) -> {
            CPUController controller = CpuLayoutLoader.getController();
            if (controller.isExecuting()) {
                controller.keyboardInput(ke);
                ke.consume();
            }
        };

        mainScene.addEventFilter(KeyEvent.KEY_TYPED, onKeyTyped);
        mainScene.addEventFilter(KeyEvent.ANY, onKeyPressed);

        //pri kliknuti na tlacidlo zatvorenia okna
        primaryStage.setOnCloseRequest(event -> {
            if (!((CPUController) CpuLayoutLoader.getController()).exit())
                event.consume(); //ak si to uzivatel rozmysli, nezatvaraj aplikaciu
        });

        primaryStage.setScene(mainScene);
        primaryStage.setTitle(WINDOW_TITLE);
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/cpu_icon.png")));
        WindowSettings.restore("cpu", primaryStage, CPU_WINDOW_WIDTH, CPU_WINDOW_HEIGHT);
        //obnovenie zapamätaného rozloženia panelov a výpis pomerov rozloženia okna CPU
        //(až po zobrazení okna, keď majú panely konečný rozmer - inak by sa rozloženie posúvalo)
        primaryStage.setOnShown(event -> Platform.runLater(() -> {
            CPUController cpuController = CpuLayoutLoader.getController();
            cpuController.applySavedDividers();
            cpuController.printLayoutRatios();
        }));
        primaryStage.show();
        primStage = primaryStage;
        WindowSettings.track("cpu", primaryStage);

        // SchematicSim - okno kreslenia schémy, ktoré otvára tlačidlo Simulátor.
        // Nahradilo pôvodné okno Breadboard sim.
        SchematicController schematicController = new SchematicController(CpuLayoutLoader.getController());

        Stage schematicStage = new Stage();
        schematicStage.setTitle("Simulátor - Schéma");
        schematicStage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/simulator_icon_128.png")));
        schematicStage.setScene(schematicController.getScene());
        WindowSettings.restore("schematic", schematicStage, 1280, 850);
        WindowSettings.track("schematic", schematicStage);
        //obnovenie zapamätaného rozloženia panelov a výpis pomerov rozloženia okna simulátora
        //(až po zobrazení okna, keď má panel konečný rozmer - inak by sa rozloženie posúvalo)
        schematicStage.setOnShown(event -> Platform.runLater(() -> {
            schematicController.applySavedDividers();
            schematicController.printLayoutRatios();
        }));

        // klávesové skratky a mazanie objektov
        schematicController.registerKeyboardHandlers();

        CpuLayoutLoader.<CPUController>getController().setSchematicStage(schematicStage);
        CpuLayoutLoader.<CPUController>getController().setSchematicSheet(schematicController.getSheet());
        CpuLayoutLoader.<CPUController>getController().setSchematicController(schematicController);
    }
}
