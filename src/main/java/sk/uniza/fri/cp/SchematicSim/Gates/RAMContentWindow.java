package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;
import sk.uniza.fri.cp.App.ThemeManager;

/**
 * Samostatné okno so zobrazením obsahu pamäte RAM 8k.
 * <p>
 * V ľavej časti je adresa a obsah pamäte v hexadecimálnej forme (v jednom riadku
 * 16 hodnôt oddelených medzerou), v pravej časti sú zodpovedajúce ASCII znaky
 * (16 znakov na riadok, nezobraziteľné znaky sú nahradené bodkou).
 * Okno je možné presúvať, minimalizovať aj zatvárať ako ostatné okná aplikácie.
 * <p>
 * Keď je okno otvorené, obsah sa pravidelne obnovuje podľa reálnej pamäte RAM
 * (referenciu na {@code byte[] memory} súčiastky si okno drží a v intervale
 * {@link #REFRESH_INTERVAL} ju znovu prečíta). Vďaka porovnaniu s posledným
 * zobrazeným obsahom sa text aktualizuje len pri skutočnej zmene - pri preklepnutí
 * sa nezresetuje pozícia posúvania ani výber.
 *
 * @author Claude (návrh podľa SchematicSim architektúry)
 */
public class RAMContentWindow {

    private static final int ROW_BYTE_COUNT = 16;
    private static final String ICON = "/icons/ram.png";
    private static final Duration REFRESH_INTERVAL = Duration.millis(200);

    private final Stage stage;
    private final TextArea taHexContent;
    private final TextArea taAsciiContent;
    private final Timeline refreshTimeline;

    /** Živá referencia na pamäťový pole súčiastky (simulácia ju môže prepisovať). */
    private byte[] ram;
    /** Posledný zobrazený hex obsah - porovnáva sa, aby sa text menil len pri zmene. */
    private String lastHexContent = "";

    /**
     * Vytvorí okno so zobrazením obsahu pamäte RAM, ktoré sa zobrazí po kliknutí
     * na kontextové menu "Obsah" súčiastky RAM 8k.
     */
    public RAMContentWindow() {
        this.stage = new Stage();
        this.stage.setTitle("Obsah pamäte RAM 8k");
        try {
            this.stage.getIcons().add(new Image(getClass().getResourceAsStream(ICON)));
        } catch (Exception ignored) {
            //bez ikony
        }

        this.taHexContent = createTextArea();
        this.taHexContent.setPrefColumnCount(56);
        this.taAsciiContent = createTextArea();
        this.taAsciiContent.setPrefColumnCount(36);

        Label lbHex = new Label("Adresa / HEX");
        Label lbAscii = new Label("ASCII");

        VBox hexColumn = new VBox(4, lbHex, taHexContent);
        hexColumn.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(taHexContent, Priority.ALWAYS);

        VBox asciiColumn = new VBox(4, lbAscii, taAsciiContent);
        asciiColumn.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(taAsciiContent, Priority.ALWAYS);

        HBox root = new HBox(10, hexColumn, asciiColumn);
        root.setPadding(new Insets(8));
        HBox.setHgrow(hexColumn, Priority.ALWAYS);
        HBox.setHgrow(asciiColumn, Priority.ALWAYS);

        Scene scene = new Scene(root, 820, 540);
        ThemeManager.registerScene(scene);
        this.stage.setScene(scene);
        this.stage.setMinWidth(520);
        this.stage.setMinHeight(300);

        // periodické obnovovanie sa zastaví, len čo sa okno zavrie alebo schová
        this.refreshTimeline = new Timeline(new KeyFrame(REFRESH_INTERVAL, event -> refresh()));
        this.refreshTimeline.setCycleCount(Timeline.INDEFINITE);

        this.stage.setOnCloseRequest(event -> refreshTimeline.stop());
        this.stage.setOnHidden(event -> refreshTimeline.stop());
    }

    /**
     * Naviaže okno na aktuálnu pamäť súčiastky, okamžite ju zobrazí a spustí
     * pravidelné obnovovanie obsahu.
     *
     * @param ram Živá referencia na pamäťové pole RAM 8k
     */
    public void showContent(byte[] ram) {
        this.ram = ram;
        updateContent(false);
        refreshTimeline.playFromStart();
        if (stage.isShowing()) {
            stage.toFront();
        } else {
            stage.show();
        }
    }

    /**
     * Vytvorí neupraviteľnú TextArea s pevným (mono) písmom bez zalomenia riadkov.
     *
     * @return TextArea pre zobrazenie obsahu pamäte
     */
    private TextArea createTextArea() {
        TextArea ta = new TextArea();
        ta.setEditable(false);
        ta.setWrapText(false);
        ta.setFocusTraversable(false);
        ta.setFont(Font.font("monospace", 11));
        return ta;
    }

    /**
     * Periodicky volaná obnova obsahu: prečíta aktuálny stav pamäte a ak je iný ako
     * naposledy zobrazený, aktualizuje obe časti okna.
     */
    private void refresh() {
        updateContent(true);
    }

    /**
     * Naplní obe časti okna obsahom pamäte. Každý riadok zobrazuje 16 bajtov:
     * vľavo adresa (hex) a hodnoty z pamäte (hex), vpravo zodpovedajúce ASCII znaky.
     * <p>
     * Aby sa pri nečinnej pamäti nepreklepával posúvač aj výber, text sa nastaví
     * len vtedy, keď sa hex obsah naozaj zmenil (ascii sa prekreslí spolu s anim).
     *
     * @param checkChanged Ak je {@code true}, text sa aktualizuje len pri skutočnej zmene obsahu
     */
    private void updateContent(boolean checkChanged) {
        if (ram == null) return;

        StringBuilder hexContent = new StringBuilder();
        StringBuilder asciiContent = new StringBuilder();

        for (int addr = 0; addr < ram.length; addr += ROW_BYTE_COUNT) {
            hexContent.append(String.format("%04X", addr));
            for (int i = 0; i < ROW_BYTE_COUNT; i++) {
                byte value = ram[addr + i];
                hexContent.append(' ').append(String.format("%02X", value & 0xFF));
                asciiContent.append(' ').append(isDisplayable(value) ? (char) (value & 0xFF) : '.');
            }
            hexContent.append('\n');
            asciiContent.append('\n');
        }

        String hex = hexContent.toString();
        if (checkChanged && hex.equals(lastHexContent)) return;

        this.lastHexContent = hex;
        taHexContent.setText(hex);
        taAsciiContent.setText(asciiContent.toString());
    }

    /**
     * Zistí, či je daná hodnota zobraziteľný ASCII znak (0x20 - 0x7E).
     *
     * @param value Hodnota bajtu
     * @return true ak je hodnota tlačiteľný ASCII znak
     */
    private boolean isDisplayable(byte value) {
        int intValue = value & 0xFF;
        return intValue >= 32 && intValue <= 126;
    }
}