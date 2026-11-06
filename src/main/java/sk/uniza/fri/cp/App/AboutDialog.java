package sk.uniza.fri.cp.App;

import javafx.scene.control.Alert;
import javafx.stage.Window;

/**
 * Okno s informáciami o programe.
 */
public final class AboutDialog {

    private AboutDialog() {
    }

    public static void show(Window owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle("O programe");
        alert.setHeaderText("CPU emulátor a logický simulátor");
        alert.setContentText("Pôvodný autor: Tomáš Hianik\n"
                + "(bakalárska práca v r. 2017)\n"
                + "Úpravy: Ondrej Karpiš a AI");
        alert.showAndWait();
    }
}
