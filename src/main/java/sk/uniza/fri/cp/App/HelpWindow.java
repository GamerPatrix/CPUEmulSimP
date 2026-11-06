package sk.uniza.fri.cp.App;

import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Okno so stručným popisom práce s programom. Neblokuje hlavné okno, môže ostať otvorené popri práci.
 */
public final class HelpWindow {

    private static final double WIDTH = 660;
    private static final double HEIGHT = 560;
    private static final double KEY_COLUMN_WIDTH = 190;

    private static final String[][] CODE = {
            {"Základné pravidlá", null},
            {"Riadok", "Jedna inštrukcia na riadok. Parametre sa oddeľujú medzerou alebo čiarkou. Pri inštrukciách a registroch sa veľkosť písmen nerozlišuje."},
            {"; komentár", "Všetko za bodkočiarkou až do konca riadku."},
            {"meno:", "Návestie na začiatku riadku. Začína písmenom alebo _, rozlišuje veľké a malé písmená."},
            {"Čísla a znaky", null},
            {"12  0x1F  017  1010b", "Desiatkové, šestnástkové, osmičkové a dvojkové číslo."},
            {"'A'", "Znak, iba pre 8-bitové hodnoty."},
            {"Registre", null},
            {"A  B  C  D", "8-bitové všeobecné registre."},
            {"M  S", "16-bitový ukazovateľ do pamäte a ukazovateľ zásobníka."},
            {"Konštanty a zbernica", null},
            {"BYTE hodnota", "Konštanta v pamäti programu (najviac 256), číta sa inštrukciou MMR."},
            {"INN  OUT", "Čítanie a zápis na vstupno-výstupnú adresu cez zbernicu."},
            {"LMI  LMR  SMI  SMR", "Čítanie a zápis do pamäte cez zbernicu."},
            {"Prerušenia a klávesnica", null},
            {"int00 až int15", "Návestie obsluhy prerušenia 0 až 15 (10 až 15 aj ako int0A až int0F). Povolenie EIT, zákaz DIT."},
            {"SCALL KEY", "Počká na stlačenie klávesy a jej kód uloží do D."},
            {"SCALL KPR", "Nastaví príznak CY, ak čaká stlačená klávesa."},
            {"SCALL DSP", "Vypíše znak z registra D na konzolu."},
    };

    private static final String[][] PROGRAM = {
            {"Beh programu", null},
            {"F4", "Kontrola syntaxe a zavedenie programu."},
            {"F5", "Spustenie programu alebo pokračovanie po pauze."},
            {"F7", "Krok na ďalšiu inštrukciu."},
            {"F9", "Pauza."},
            {"F10", "Zastavenie programu."},
            {"F12", "Reset registrov a pamätí CPU."},
            {"Okno simulátora", "Fungujú v ňom F5, F7, F9, F10 a F12."},
            {"Editor kódu", null},
            {"Ctrl + /", "Zakomentovanie alebo odkomentovanie vybraných riadkov."},
            {"Ctrl + F9, Ctrl + F10", "Zmenšenie a zväčšenie písma."},
            {"Klik naľavo od čísla riadku", "Zarážka (breakpoint). Všetky zruší tlačidlo UnBreak All."},
            {"Počas behu programu", null},
            {"Klávesy", "Stlačené klávesy vrátane šípok sa odovzdávajú programu pre SCALL KEY a KPR."},
            {"Nastavenia", "Spôsob vyvolania prerušenia (úroveň alebo zmena), mikro krokovanie a aktualizácia GUI."},
    };

    private static final String[][] SCHEMATIC = {
            {"Súčiastky", null},
            {"Ťah z paletky", "Pridanie súčiastky na plochu."},
            {"Ťah súčiastky", "Presun, vybrané súčiastky sa presúvajú spolu."},
            {"Pravé tlačidlo", "Nastavenia súčiastky (typ, počet vstupov, vývod...)."},
            {"Vodiče", null},
            {"Ťah z vývodu", "Nový vodič, pustením na vývod alebo vodič sa pripojí."},
            {"Ťah z vodiča", "Nová odbočka."},
            {"Ťah konca vodiča", "Prepojenie na iné miesto."},
            {"Dvojklik na koniec vodiča", "Zmazanie vodiča."},
            {"Pravé tlačidlo na ploche", "Vymazanie nezapojených a zlúčenie kolineárnych vodičov."},
            {"Výber a úpravy", null},
            {"Klik", "Výber súčiastky alebo vodiča."},
            {"Shift + klik", "Pridanie k výberu alebo odobratie z neho."},
            {"Shift + ťah na ploche", "Výber obdĺžnikom."},
            {"Ctrl + klik na vodič", "Výber celého reťazca vodičov."},
            {"Delete, Backspace", "Zmazanie výberu."},
            {"Ctrl + C, Ctrl + V", "Kopírovanie a vloženie na polohu myši."},
            {"Ctrl + Z, Ctrl + Y", "Späť a znova (aj Ctrl + Shift + Z)."},
            {"Zobrazenie", null},
            {"Koliesko myši", "Priblíženie a oddialenie."},
            {"Ťah prázdnej plochy", "Posun plochy."},
            {"Zbernice", null},
            {"Klik na lištu", "Výber signálu a vytvorenie vývodu."},
            {"Pravé tlačidlo na vývode", "Zmazanie vývodu."},
            {"Ťah konca lišty", "Zmena dĺžky zbernice."},
            {"Simulácia", null},
            {"Spínač v lište simulátora", "Zapnutie a vypnutie. Schéma sa dá upravovať iba pri vypnutej simulácii."},
            {"Program s I/O", "Simuláciu zapne automaticky."},
            {"Klik na prepínač alebo tlačidlo", "Zmena vstupu počas simulácie."},
            {"Label", "Labely s rovnakým menom sú prepojené bez vodiča."},
            {"Nezapojený vstup", "Má náhodnú hodnotu, ktorá sa počas behu nemení."},
    };

    private final Stage stage = new Stage();

    public HelpWindow(Window owner) {
        TabPane tabs = new TabPane(
                tab("Kód", CODE),
                tab("Program", PROGRAM),
                tab("Schéma", SCHEMATIC));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Scene scene = new Scene(tabs, WIDTH, HEIGHT);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) stage.close();
        });

        stage.initOwner(owner);
        stage.setTitle("Pomoc");
        stage.setScene(scene);
    }

    /** Zobrazenie okna, prípadne jeho presunutie navrch, ak je už otvorené. */
    public void show() {
        stage.show();
        stage.toFront();
    }

    private static Tab tab(String title, String[][] rows) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(5);
        grid.setPadding(new Insets(12));

        ColumnConstraints keys = new ColumnConstraints(KEY_COLUMN_WIDTH);
        ColumnConstraints texts = new ColumnConstraints();
        texts.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keys, texts);

        Font base = Font.getDefault();
        for (int row = 0; row < rows.length; row++) {
            String key = rows[row][0];
            String text = rows[row][1];
            if (text == null) {
                Label heading = new Label(key);
                heading.setFont(Font.font(base.getFamily(), FontWeight.BOLD, base.getSize() + 1));
                heading.setPadding(new Insets(row == 0 ? 0 : 8, 0, 0, 0));
                grid.add(heading, 0, row, 2, 1);
            } else {
                Label keyLabel = new Label(key);
                keyLabel.setFont(Font.font(base.getFamily(), FontWeight.BOLD, base.getSize()));
                keyLabel.setWrapText(true);
                Label textLabel = new Label(text);
                textLabel.setWrapText(true);
                GridPane.setValignment(keyLabel, VPos.TOP);
                GridPane.setValignment(textLabel, VPos.TOP);
                grid.add(keyLabel, 0, row);
                grid.add(textLabel, 1, row);
            }
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        return new Tab(title, scroll);
    }
}
