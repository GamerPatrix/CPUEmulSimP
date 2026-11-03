package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import sk.uniza.fri.cp.App.ThemeManager;

/**
 * Samostatné okno so zobrazením obsahu pamäte RAM 8k.
 * <p>
 * Každý riadok obsahuje adresu a obsah pamäte v hexadecimálnej forme (16 hodnôt
 * oddelených medzerou) a za nimi zodpovedajúce ASCII znaky (nezobraziteľné znaky sú
 * nahradené bodkou). Hex aj ASCII časť sú v jednej textovej oblasti - majú spoločný
 * posúvač a posúvajú sa súčasne.
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
    private static final Font CONTENT_FONT = Font.font("monospace", 11);
    /** Medzera medzi hex a ASCII časťou riadku. */
    private static final String COLUMN_GAP = "   ";
    /** Šírka hex časti riadku v znakoch: adresa (4) + 16x (medzera + 2 číslice). */
    private static final int HEX_PART_WIDTH = 4 + ROW_BYTE_COUNT * 3;
    /** Vnútorné odsadenie a okraj textovej oblasti + zvislý posúvač (px). */
    private static final double WIDTH_EXTRA = 40;

    private final Stage stage;
    private final TextArea taContent;
    private final Timeline refreshTimeline;

    /** Živá referencia na pamäťový pole súčiastky (simulácia ju môže prepisovať). */
    private byte[] ram;
    /** Posledný zobrazený obsah - porovnáva sa, aby sa text menil len pri zmene. */
    private String lastContent = "";

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

        this.taContent = createTextArea();

        // hlavička rovnakým písmom ako obsah - "ASCII" začína nad ASCII časťou riadkov
        StringBuilder header = new StringBuilder("Adresa / HEX");
        while (header.length() < HEX_PART_WIDTH + COLUMN_GAP.length()) header.append(' ');
        header.append("ASCII");
        Label lbHeader = new Label(header.toString());
        lbHeader.setFont(CONTENT_FONT);
        // zarovnanie s textom vo vnútri TextArea (jej okraj + vnútorné odsadenie)
        lbHeader.setPadding(new Insets(0, 0, 0, 8));

        VBox root = new VBox(4, lbHeader, taContent);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(8));
        VBox.setVgrow(taContent, Priority.ALWAYS);

        // úvodná šírka podľa dĺžky riadku obsahu (bez prázdneho miesta vpravo): riadok
        // + okraje okna + vnútorné odsadenie textovej oblasti + zvislý posúvač
        Text sampleRow = new Text(String.format("%0" + (HEX_PART_WIDTH + COLUMN_GAP.length()
                + ROW_BYTE_COUNT * 2 - 1) + "d", 0));
        sampleRow.setFont(CONTENT_FONT);
        double width = Math.ceil(sampleRow.getLayoutBounds().getWidth()) + 2 * 8 + WIDTH_EXTRA;

        Scene scene = new Scene(root, width, 540);
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
     * Zatvorenie okna (napr. pri zmazaní súčiastky RAM) a zastavenie obnovovania obsahu.
     */
    public void close() {
        refreshTimeline.stop();
        stage.close();
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
        ta.setFont(CONTENT_FONT);
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
     * Naplní okno obsahom pamäte. Každý riadok zobrazuje 16 bajtov: adresu (hex),
     * hodnoty z pamäte (hex) a za nimi zodpovedajúce ASCII znaky.
     * <p>
     * Aby sa pri nečinnej pamäti nepreklepával posúvač aj výber, text sa nastaví
     * len vtedy, keď sa obsah naozaj zmenil; poloha posúvačov sa pritom zachová.
     *
     * @param checkChanged Ak je {@code true}, text sa aktualizuje len pri skutočnej zmene obsahu
     */
    private void updateContent(boolean checkChanged) {
        if (ram == null) return;

        StringBuilder content = new StringBuilder();
        StringBuilder ascii = new StringBuilder();

        for (int addr = 0; addr < ram.length; addr += ROW_BYTE_COUNT) {
            content.append(String.format("%04X", addr));
            ascii.setLength(0);
            for (int i = 0; i < ROW_BYTE_COUNT; i++) {
                byte value = ram[addr + i];
                content.append(' ').append(String.format("%02X", value & 0xFF));
                ascii.append(' ').append(isDisplayable(value) ? (char) (value & 0xFF) : '.');
            }
            content.append(COLUMN_GAP).append(ascii, 1, ascii.length()).append('\n');
        }

        String text = content.toString();
        if (checkChanged && text.equals(lastContent)) return;

        this.lastContent = text;
        double scrollTop = taContent.getScrollTop();
        double scrollLeft = taContent.getScrollLeft();
        taContent.setText(text);
        taContent.setScrollTop(scrollTop);
        taContent.setScrollLeft(scrollLeft);
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