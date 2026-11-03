package sk.uniza.fri.cp.SchematicSim.Gates;

import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import sk.uniza.fri.cp.SchematicSim.Item;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Sheet.SheetChangeEvent;
import sk.uniza.fri.cp.SchematicSim.Electrical.Potential;
import sk.uniza.fri.cp.SchematicSim.Wire.Wire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logická súčiastka na schéme. Nahrádza {@code Device}(+{@code Chip}) z BreadboardSim.
 * Narozdiel od pôvodného modelu tu nejde o reálne 74xx puzdro s napájaním (VCC/GND) -
 * ide o abstraktný logický symbol (AND/OR/NOT...), ktorý sa na plochu umiestňuje voľne,
 * bez kolíznej detekcie s breadboardom (o voľnosť buniek sa stará
 * {@link sk.uniza.fri.cp.SchematicSim.GridOccupancy} v {@link SchematicSheet}).
 *
 * @author Claude (návrh podľa SchematicSim architektúry, simulačné helpery prevzaté z pôvodného Device)
 */
public abstract class GateSymbol extends Item {

    private List<Pin> pins;

    /** Názov inštancie súčiastky na schéme (napr. REG1) - null pre súčiastky bez názvu. */
    private String instanceName;

    /** Textová značka na tele súčiastky, ktorá zobrazuje {@link #instanceName}. */
    private Text nameLabel;

    /**
     * Bezparametrický konštruktor pre ItemPicker (paletku).
     */
    protected GateSymbol() {
        super();
        this.pins = null; // v paletke sa piny nevytvárajú, iba statický obrázok cez getImage()
    }

    protected GateSymbol(SchematicSheet sheet) {
        super(sheet);
        this.pins = createPins();
        this.getChildren().addAll(pins);
        this.getChildren().add(0, drawBody()); // telo pod pinmi
        this.addEventHandler(MouseEvent.MOUSE_RELEASED, this::snapPinsToFreeEnds);
    }

    /**
     * Po pustení myši sa piny, ktoré doľahnú na voľný koniec vodiča, pripoja naň
     * (pin sa "priloží" na voľný koniec). Kontrolujú sa aj súčiastky presunuté
     * spolu s touto pri viacnásobnom výbere.
     */
    private void snapPinsToFreeEnds(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY && event.getButton() != MouseButton.NONE) return;
        SchematicSheet sheet = getSheet();
        if (sheet == null || !sheet.isEditingEnabled()) return;

        List<GateSymbol> moved = new ArrayList<>();
        if (this.isSelected()) moved.addAll(sheet.getSelectedGates());
        if (!moved.contains(this)) moved.add(this);

        List<Pin> pins = new ArrayList<>();
        for (GateSymbol gate : moved) {
            if (gate.getPins() != null) pins.addAll(gate.getPins());
        }
        Wire.connectFreeEndsToPins(sheet, pins);
    }

    /**
     * Vytvorenie pinov súčiastky. Volá sa raz z konštruktora.
     */
    protected abstract List<Pin> createPins();

    /**
     * Grafická značka tela súčiastky (obdĺžnik/IEEE tvar s popiskom...).
     */
    protected abstract Pane drawBody();

    /**
     * Simulačná metóda - prepočíta výstupy na základe aktuálnych vstupov.
     */
    public abstract void simulate();

    /**
     * Resetovanie výstupov (napr. pri zastavení simulácie).
     */
    public abstract void reset();

    public abstract int getGridWidth();

    public abstract int getGridHeight();

    public abstract String getName();

    public abstract String getShortDescription();

    /**
     * Prefix názvu inštancie (napr. {@code REG} pre register). Súčiastky, ktoré majú
     * automaticky číslovaný názov na schéme, ho prekryjú; predvolene {@code null}
     * (súčiastka sa čísluje iba vtedy, ak je prefix nastavený).
     */
    public String getInstanceNamePrefix() {
        return null;
    }

    /**
     * Názov inštancie súčiastky na schéme (napr. {@code REG1}), alebo {@code null}
     * ak súčiastka názov nemá.
     */
    public String getInstanceName() {
        return this.instanceName;
    }

    /**
     * Nastavenie názvu inštancie a jeho premietnutie do textovej značky na tele súčiastky.
     */
    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
        if (this.nameLabel != null && instanceName != null) {
            this.nameLabel.setText(instanceName);
        }
    }

    /**
     * Zaregistrovanie textovej značky, ktorá bude zobrazovať názov inštancie súčiastky
     * (napr. {@code REG1}). Volá sa z {@link #drawBody()}; ak už je názov pridelený,
     * rovno sa do značky zapíše.
     */
    protected void registerNameLabel(Text label) {
        this.nameLabel = label;
        if (this.instanceName != null) {
            label.setText(this.instanceName);
        }
    }

    public List<Pin> getPins() {
        return pins;
    }

    /**
     * Nahradenie zoznamu pinov súčiastky (dynamická zmena počtu vstupov cez kontextové menu).
     * Musí byť v súlade s obsahom potomkov (children) - SchemeLoader indexuje piny podľa
     * tohto zoznamu, a preto poradie (vstupy, výstup) musí zostať stabilné.
     */
    protected final void replacePins(List<Pin> pins) {
        this.pins = pins;
    }

    /**
     * Vlastnosti súčiastky pre ukladanie schémy (názov → hodnota). Predvolene prázdne -
     * konfigurovateľné súčiastky (LED, prepínač, zbernice...) ju prekrývajú. SchemeLoader
     * ukladá výslednú mapu genericky, takže pridanie novej súčiastky nevyžaduje zmeny v loaderi.
     */
    public Map<String, String> saveProperties() {
        if (this.instanceName == null) return Collections.emptyMap();
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("name", this.instanceName);
        return properties;
    }

    /**
     * Obnovenie vlastností súčiastky po načítaní schémy. Volá sa vždy po vytvorení hradla
     * reflexiou a pred pripájaním vodičov (zbernica si tak najskôr vytvorí svoje odbočky,
     * aby sa na ne dali pripojiť vodiče podľa indexu pinu). Predvolene obnoví iba názov
     * inštancie; potomkovia musia prekrytie riešiť cez {@code super.loadProperties(...)}.
     */
    public void loadProperties(Map<String, String> properties) {
        String name = properties.get("name");
        if (name != null) setInstanceName(name);
    }

    public Point2D getGridPos() {
        return new Point2D(getGridPosX(), getGridPosY());
    }

    // === simulačné helpery, prevzaté z BreadboardSim.Device ===

    /**
     * Skontroluje a aktualizuje aktuálnu hodnotu na vstupnom pine podľa potenciálu, ku ktorému je pripojený.
     * Plávajúci vstup (High-Z) sa správa náhodne - náhoda je však stabilná, kým sa stav
     * signálu nemení (generuje ju {@link Potential#getFloatingValue()}), takže vstup nebliká
     * pri každom kroku simulácie.
     */
    public boolean isHigh(Pin inputPin) {
        if (inputPin == null || !inputPin.isConnected()) return false;

        Potential potential = inputPin.getPotential();
        Potential.Value value = potential != null ? potential.getValue() : Potential.Value.NC;

        if (value == Potential.Value.HIGH) {
            inputPin.setState(Pin.PinState.HIGH);
            return true;
        } else if (value == Potential.Value.LOW) {
            inputPin.setState(Pin.PinState.LOW);
        } else {
            boolean high = potential != null && potential.getFloatingValue();
            inputPin.setState(high ? Pin.PinState.HIGH : Pin.PinState.LOW);
            return high;
        }
        return false;
    }

    public boolean isLow(Pin inputPin) {
        if (inputPin == null || !inputPin.isConnected()) return false;

        Potential potential = inputPin.getPotential();
        Potential.Value value = potential != null ? potential.getValue() : Potential.Value.NC;

        if (value == Potential.Value.LOW) {
            inputPin.setState(Pin.PinState.LOW);
            return true;
        } else if (value == Potential.Value.HIGH) {
            inputPin.setState(Pin.PinState.HIGH);
        } else {
            boolean low = !(potential != null && potential.getFloatingValue());
            inputPin.setState(low ? Pin.PinState.LOW : Pin.PinState.HIGH);
            return low;
        }
        return false;
    }

    /**
     * Nastavenie hodnoty na výstupnom pine a zaznamenanie zmenovej udalosti pre simuláciu,
     * ak sa hodnota oproti predošlému stavu zmenila.
     */
    public void setPin(Pin pin, Pin.PinState state) {
        if (pin.getState() != state) {
            pin.setState(state);
            postChangeEvent(pin, state);
        }
    }

    /**
     * Ako setPin(), ale udalosť sa vytvorí vždy, aj keď sa hodnota nezmenila.
     */
    public void setPinForce(Pin pin, Pin.PinState state) {
        pin.setState(state);
        postChangeEvent(pin, state);
    }

    private void postChangeEvent(Pin pin, Pin.PinState state) {
        Potential.Value value;
        switch (state) {
            case HIGH: value = Potential.Value.HIGH; break;
            case LOW: value = Potential.Value.LOW; break;
            default: value = Potential.Value.NC;
        }
        getSheet().addEvent(new SheetChangeEvent(pin, value));
    }

    @Override
    public void delete() {
        // pri zmazaní súčiastky sa zmažú aj všetky na jej piny pripojené vodiče
        // (narozdiel od Socket.disconnect() v origináli tu niet samostatného komponentu,
        // na ktorom by mohol "osirelý" vodič ostať visieť).
        // Vodiče sa mažú ešte PRED odobratím súčiastky z plochy: odobratím sa zmení jej
        // transformácia a konce vodičov na jej pinoch by sa prepočítali na polohu mimo
        // plochy (posun o pol hrúbky obrysu) - posunuli by sa aj pripojené spájače a
        // susedné vodiče by ostali mimo mriežky.
        if (pins != null) {
            for (Pin pin : pins) {
                if (pin.getWireEnd() != null) pin.getWireEnd().getWire().delete();
            }
        }
        super.delete();
    }
}
