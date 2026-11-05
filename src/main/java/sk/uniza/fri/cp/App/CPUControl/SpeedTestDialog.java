package sk.uniza.fri.cp.App.CPUControl;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Okno testu rýchlosti - počas testu ukazuje jeho priebeh, po skončení výsledky a tlačidlo OK.
 * Je modálne, aby sa počas merania nedal spustiť program ani meniť zapojenie.
 */
final class SpeedTestDialog {

    private final Stage stage = new Stage();
    private final VBox root = new VBox(12);
    private boolean finished;

    SpeedTestDialog(Window owner) {
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Test rýchlosti");
        stage.setResizable(false);
        stage.setOnCloseRequest(event -> {
            if (!finished) event.consume();
        });

        root.setPadding(new Insets(16));
        root.setAlignment(Pos.CENTER);
        ProgressBar progress = new ProgressBar(-1);
        progress.setPrefWidth(260);
        root.getChildren().addAll(new Label("Prebieha test rýchlosti..."), progress);
        stage.setScene(new Scene(root));
    }

    void show() {
        stage.show();
    }

    void showResult(SpeedTest.Result result) {
        finished = true;

        Button ok = new Button("OK");
        ok.setDefaultButton(true);
        ok.setCancelButton(true);
        ok.setPrefWidth(80);
        ok.setOnAction(event -> stage.close());

        root.getChildren().clear();
        if (result.error != null) {
            root.getChildren().add(new Label("Test sa nepodaril: " + result.error));
        } else {
            root.getChildren().add(resultsGrid(result));
            Label note = new Label(result.gateCount > 0
                    ? "Simulácia = prepočet jednej súčiastky po zmene signálu. Počas testu sa zapisovalo\n"
                    + "na zbernicu, obsah RAM a registrov v zapojení sa mohol zmeniť."
                    : "I/O inštrukcie bežali bez zapojenia.");
            root.getChildren().add(note);
        }
        root.getChildren().add(ok);

        stage.sizeToScene();
        stage.centerOnScreen();
        ok.requestFocus();
    }

    private static GridPane resultsGrid(SpeedTest.Result result) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);
        addRow(grid, 0, "Inštrukcie bez zbernice:", perSecond(result.instructionsPerSecond, "inštrukcií/s"));
        addRow(grid, 1, "I/O inštrukcie (zbernica):", perSecond(result.busInstructionsPerSecond, "inštrukcií/s"));
        if (result.gateCount > 0) {
            addRow(grid, 2, "Simulácia zapojenia (súčiastok: " + result.gateCount + "):",
                    perSecond(result.simulationsPerSecond, "simulácií/s"));
        } else {
            addRow(grid, 2, "Simulácia zapojenia:", "žiadne zapojenie");
        }
        return grid;
    }

    private static void addRow(GridPane grid, int row, String name, String value) {
        Label valueLabel = new Label(value);
        grid.add(new Label(name), 0, row);
        grid.add(valueLabel, 1, row);
        GridPane.setHalignment(valueLabel, HPos.RIGHT);
    }

    private static String perSecond(long value, String unit) {
        return NumberFormat.getIntegerInstance(new Locale("sk")).format(value) + " " + unit;
    }
}
