package sk.uniza.fri.cp.SchematicSim.Sheet;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import sk.uniza.fri.cp.SchematicSim.Gates.GateSymbol;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Wire.Wire;
import sk.uniza.fri.cp.SchematicSim.Wire.WireEnd;
import sk.uniza.fri.cp.SchematicSim.Wire.WireJunction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ukladanie / načítanie schémy (súčiastky + vodiče) do XML súboru (.schx).
 * <p>
 * Formát je zámerne jednoduchý - CVO: názov triedy hradla, pozícia na mriežke a generická
 * mapa vlastností {@code <property name value>}, ktorú si každá súčiastka serializuje sama
 * (pozri {@link sk.uniza.fri.cp.SchematicSim.Gates.GateSymbol#saveProperties()});
 * vodič: farba, identifikácia koncov (id hradla + index pinu) a pozície zlomov.
 * Hradlá sa vytvárajú reflexiou (musia mať konštruktor {@code (SchematicSheet)}),
 * takže pribúdanie nových súčiastok nevyžaduje zmeny v tomto súbore.
 *
 * @author Claude (návrh podľa BreadboardSim.SchemeLoader)
 */
public class SchemeLoader {

    private static final String VERSION = "2.0";
    private static final String FORGIVEN_ID_PREFIX = "s"; // id pouzite pri nacitani ak chyba ulozene id

    /** Tolerancia zhodnosti bodov - vodiče sa spájajú len na presne rovnakom bode. */
    private static final double POINT_EPS = 0.5;

    private SchemeLoader() {
    }

    /**
     * Uloženie schémy do súboru.
     *
     * @param file  Súbor, do ktorého sa schéma uloží.
     * @param sheet Plocha schémy.
     * @return true ak sa uloženie podarilo.
     */
    public static boolean save(File file, SchematicSheet sheet) {
        XMLOutputter xmlOutputter = new XMLOutputter();
        xmlOutputter.setFormat(Format.getPrettyFormat());

        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            xmlOutputter.output(createDocument(sheet), bw);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Serializácia aktuálneho stavu schémy do XML reťazca - snímka pre undo/redo.
     *
     * @param sheet Plocha schémy.
     * @return XML reťazec so stavom plochy.
     */
    public static String saveToString(SchematicSheet sheet) {
        XMLOutputter xmlOutputter = new XMLOutputter();
        xmlOutputter.setFormat(Format.getPrettyFormat());
        return xmlOutputter.outputString(createDocument(sheet));
    }

    /**
     * Zostavenie XML dokumentu so stavom schémy (súčiastky + vodiče). Spoločný základ
     * pre uloženie do súboru {@link #save} aj pre snímku stavu {@link #saveToString}.
     */
    private static Document createDocument(SchematicSheet sheet) {
        Document jdomDoc = new Document();
        Element rootElement = new Element("Scheme");
        rootElement.setAttribute("ver", VERSION);
        jdomDoc.setRootElement(rootElement);

        //súčiastky
        Element gatesElement = new Element("Gates");
        for (GateSymbol gate : sheet.getGates()) {
            Element gateElement = new Element("Gate");
            gateElement.setAttribute("id", gate.getId());

            Element className = new Element("class");
            className.addContent(gate.getClass().getName());
            gateElement.addContent(className);

            Element gridX = new Element("gridX");
            gridX.addContent(Integer.toString(gate.getGridPosX()));
            gateElement.addContent(gridX);

            Element gridY = new Element("gridY");
            gridY.addContent(Integer.toString(gate.getGridPosY()));
            gateElement.addContent(gridY);

            Element propertiesElement = new Element("properties");
            for (Map.Entry<String, String> property : gate.saveProperties().entrySet()) {
                Element propertyElement = new Element("property");
                propertyElement.setAttribute("name", property.getKey());
                propertyElement.setAttribute("value", property.getValue());
                propertiesElement.addContent(propertyElement);
            }
            gateElement.addContent(propertiesElement);

            gatesElement.addContent(gateElement);
        }
        rootElement.addContent(gatesElement);

        //vodiče
        Element wiresElement = new Element("Wires");
        for (Wire wire : sheet.getWires()) {
            Element wireElement = new Element("Wire");
            wireElement.setAttribute("color", colorToHex(wire.getColor()));

            WireEnd[] ends = wire.getEnds();
            appendWireEnd(wireElement, "start", ends[0]);
            appendWireEnd(wireElement, "end", ends[1]);

            wiresElement.addContent(wireElement);
        }
        rootElement.addContent(wiresElement);

        return jdomDoc;
    }

    private static void appendWireEnd(Element wireElement, String name, WireEnd end) {
        Element endElement = new Element(name);
        Pin pin = end.getPin();
        if (pin != null) {
            Element gateId = new Element("gateId");
            gateId.addContent(pin.getOwner().getId());
            endElement.addContent(gateId);

            Element pinIndex = new Element("pinIndex");
            pinIndex.addContent(Integer.toString(pin.getOwner().getPins().indexOf(pin)));
            endElement.addContent(pinIndex);
        } else if (end.getJunction() != null) {
            Element junction = new Element("junction");

            Element x = new Element("x");
            x.addContent(Double.toString(end.getJunction().getLayoutX()));
            junction.addContent(x);

            Element y = new Element("y");
            y.addContent(Double.toString(end.getJunction().getLayoutY()));
            junction.addContent(y);

            endElement.addContent(junction);
        } else {
            Element x = new Element("x");
            x.addContent(Double.toString(end.getLayoutX()));
            endElement.addContent(x);

            Element y = new Element("y");
            y.addContent(Double.toString(end.getLayoutY()));
            endElement.addContent(y);
        }
        wireElement.addContent(endElement);
    }

    /**
     * Načítanie schémy zo súboru. Pred načítaním vymaže obsah plochy.
     *
     * @param file  Súbor, z ktorého sa schéma načíta.
     * @param sheet Plocha schémy.
     * @return true ak sa načítanie podarilo.
     */
    public static boolean load(File file, SchematicSheet sheet) {
        SAXBuilder builder = new SAXBuilder();
        try {
            return loadDocument(builder.build(file), sheet);
        } catch (JDOMException | IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Načítanie stavu schémy z XML reťazca - snímka pre undo/redo. Rovnaká rekonštrukcia
     * plochy ako {@link #load(File, SchematicSheet)} - pred načítaním sa obsah plochy vymaže.
     *
     * @param xml   Reťazec so stavom schémy vo formáte .schx.
     * @param sheet Plocha, na ktorú sa stav načíta.
     * @return true ak sa stav načítal.
     */
    public static boolean loadFromString(String xml, SchematicSheet sheet) {
        SAXBuilder builder = new SAXBuilder();
        try {
            return loadDocument(builder.build(new StringReader(xml)), sheet);
        } catch (JDOMException | IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Rekonštrukcia plochy z XML dokumentu (spoločný základ pre {@link #load} aj
     * {@link #loadFromString}). Pred načítaním vymaže obsah plochy.
     */
    private static boolean loadDocument(Document jdomDoc, SchematicSheet sheet) {
        try {
            Element rootElement = jdomDoc.getRootElement();
            if (!"Scheme".equalsIgnoreCase(rootElement.getName())) return false;

            sheet.clearSheet();

            //súčiastky
            Map<String, GateSymbol> gatesById = new HashMap<>();
            Element gatesElement = rootElement.getChild("Gates");
            if (gatesElement != null) {
                for (Element gateElement : gatesElement.getChildren("Gate")) {
                    String className = gateElement.getChildText("class");
                    GateSymbol gate = (GateSymbol) Class.forName(className)
                            .getConstructor(SchematicSheet.class).newInstance(sheet);

                    String id = gateElement.getAttributeValue("id");
                    gate.setId(id != null && !id.isEmpty() ? id : FORGIVEN_ID_PREFIX + gatesById.size());
                    sheet.addItem(gate);

                    int gridX = Integer.parseInt(gateElement.getChildText("gridX"));
                    int gridY = Integer.parseInt(gateElement.getChildText("gridY"));
                    gate.moveTo(gridX, gridY);
                    sheet.getOccupancy().occupy(gate);

                    gate.loadProperties(readProperties(gateElement));

                    gatesById.put(gate.getId(), gate);
                }
            }

            //vodiče
            Element wiresElement = rootElement.getChild("Wires");
            if (wiresElement != null) {
                List<Wire> loadedWires = new ArrayList<>();
                List<WireEntry> pendingResolve = new ArrayList<>();
                // zdieľané spájače: konce vodičov v jednom bode sa napoja na rovnaký hub
                Map<Long, WireJunction> hubByKey = new HashMap<>();
                // koľko koncov odkazuje na spájač v danom bode - rozhoduje, či bod spájača
                // treba dodatočne rozdeľovať na prechádzajúcom vodiči (len staré súbory)
                Map<Long, Integer> junctionRefs = countJunctionRefs(wiresElement);
                for (Element wireElement : wiresElement.getChildren("Wire")) {
                    WireEntry entry = loadWire(sheet, wireElement, gatesById, loadedWires, hubByKey, junctionRefs);
                    if (entry == null) continue;

                    if (loadable(entry)) {
                        loadedWires.add(entry.wire);
                    } else {
                        pendingResolve.add(entry);
                    }
                }

                // druhý prechod - vodiče so spájačom na konci, ktoré boli uložené PRED kmeňovým
                // vodičom (v praxi sa to nestáva, ale kvôli robustnosti to ošetrujeme)
                List<WireEntry> stillPending = new ArrayList<>();
                for (WireEntry entry : pendingResolve) {
                    resolveJunctionEnd(entry.wire.getEnds()[0], entry.startElement, loadedWires, hubByKey, junctionRefs);
                    resolveJunctionEnd(entry.wire.getEnds()[1], entry.endElement, loadedWires, hubByKey, junctionRefs);

                    if (loadable(entry)) {
                        loadedWires.add(entry.wire);
                    } else {
                        stillPending.add(entry);
                    }
                }
                for (WireEntry entry : stillPending) {
                    entry.wire.delete();
                }

                // vodiče so slobodnými koncami (a staršie súbory) potrebujú prepočítať geometriu
                for (Wire wire : loadedWires) {
                    wire.settleToGrid();
                }
            }
        } catch (InvocationTargetException | InstantiationException
                | IllegalAccessException | ClassNotFoundException | NoSuchMethodException
                | NullPointerException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Pomocná trieda spájajúca načítaný vodič s XML elementmi jeho koncov, aby bolo možné
     * vykonať neskorší (druhý) prechod na vyriešenie koncov napojených na spájače.
     */
    private static final class WireEntry {
        final Wire wire;
        final Element startElement;
        final Element endElement;

        WireEntry(Wire wire, Element startElement, Element endElement) {
            this.wire = wire;
            this.startElement = startElement;
            this.endElement = endElement;
        }
    }

    /**
     * Načítanie jedného vodiča: pripojenie koncov na piny a prvý pokus o vyriešenie
     * koncov na spájačoch. Vracia záznam pre prípadný druhý prechod, alebo
     * {@code null} ak sa vodič nepodarilo načítať.
     */
    private static WireEntry loadWire(SchematicSheet sheet, Element wireElement,
                                      Map<String, GateSymbol> gatesById, List<Wire> loadedWires,
                                      Map<Long, WireJunction> hubByKey, Map<Long, Integer> junctionRefs) {
        Element startElement = wireElement.getChild("start");
        Element endElement = wireElement.getChild("end");

        Wire wire = new Wire(sheet);
        sheet.addItem(wire);
        wire.changeColor(Color.valueOf(colorFromHex(wireElement.getAttributeValue("color"))));

        WireEnd[] ends = wire.getEnds();

        connectPinEnd(ends[0], startElement, gatesById);
        connectPinEnd(ends[1], endElement, gatesById);

        resolveJunctionEnd(ends[0], startElement, loadedWires, hubByKey, junctionRefs);
        resolveJunctionEnd(ends[1], endElement, loadedWires, hubByKey, junctionRefs);

        // voľné konce sa obnovia na uloženú pozíciu - vodič so slobodným koncom nezmizne
        positionFreeEnd(ends[0], startElement);
        positionFreeEnd(ends[1], endElement);

        return new WireEntry(wire, startElement, endElement);
    }

    /**
     * Či sa načítaný vodič zachová. Spojené konce sa vždy zachovajú; nepripojené konce
     * sa zachovajú len pokiaľ majú v súbore uloženú pozíciu voľného konca (priame
     * súradnice), inak by zo vodiča zostala neúplná silueta.
     */
    private static boolean loadable(WireEntry entry) {
        if (entry.wire.areBothEndsConnected()) return true;
        WireEnd[] ends = entry.wire.getEnds();
        Element[] elements = new Element[]{entry.startElement, entry.endElement};
        for (int i = 0; i < 2; i++) {
            if (ends[i].isConnected()) continue;
            if (elements[i] == null || elements[i].getChild("x") == null
                    || elements[i].getChild("y") == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Umiestnenie voľného konca na uloženú pozíciu (koniec bez pinu aj spájača, uložený
     * ako priame súradnice {@code <x>/<y>}). Vodič so slobodným koncom sa tak po načítaní
     * zachová a nezahodí.
     */
    private static void positionFreeEnd(WireEnd end, Element endElement) {
        if (endElement == null || end.isConnected()) return;
        String x = endElement.getChildText("x");
        String y = endElement.getChildText("y");
        if (x == null || y == null) return;
        try {
            end.moveTo(Double.parseDouble(x), Double.parseDouble(y));
        } catch (NumberFormatException ignored) {
        }
    }

    private static Map<String, String> readProperties(Element gateElement) {
        Map<String, String> properties = new HashMap<>();
        Element propertiesElement = gateElement.getChild("properties");
        if (propertiesElement != null) {
            for (Element propertyElement : propertiesElement.getChildren("property")) {
                String name = propertyElement.getAttributeValue("name");
                String value = propertyElement.getAttributeValue("value");
                if (name != null) properties.put(name, value == null ? "" : value);
            }
        }
        return properties;
    }

    private static Pin findPin(SchematicSheet sheet, Element endElement, Map<String, GateSymbol> gatesById) {
        if (endElement == null) return null;

        String gateId = endElement.getChildText("gateId");
        if (gateId == null) return null; // voľný koniec - nenačítavame vodiče so slobodnými koncami

        GateSymbol gate = gatesById.get(gateId);
        if (gate == null) return null;

        try {
            int pinIndex = Integer.parseInt(endElement.getChildText("pinIndex"));
            List<Pin> pins = gate.getPins();
            if (pinIndex >= 0 && pinIndex < pins.size()) return pins.get(pinIndex);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static void connectPinEnd(WireEnd end, Element endElement, Map<String, GateSymbol> gatesById) {
        if (endElement == null || end.isConnected()) return;
        Pin pin = findPin(end.getSheet(), endElement, gatesById);
        if (pin != null && !pin.isOccupied()) {
            end.connect(pin);
        }
    }

    /**
     * Pripojenie konca vodiča, ktorý je v súbore uložený ako spájač (junction) na iný vodič.
     * <p>
     * Všetky konce zdieľajúce rovnaký bod sa napájajú na jeden spoločný spájač (hub) -
     * preto sa spájače najprv zdieľajú cez mapu {@code hubByKey} (všetci pripojení
     * v danom bode dostanú ten istý objekt). Ak už v bode hub existuje, pripojí sa naň.
     * <p>
     * Pre staré súbory (kde kmeň cez bod len prechádza) sa ako poistka hľadá vodič
     * prechádzajúci bodom a na ňom sa spájač vytvorí rozdeľujúcim {@code splitAtPoint}.
     *
     * @return true ak sa koniec podarilo pripojiť na spájač.
     */
    private static boolean resolveJunctionEnd(WireEnd end, Element endElement, List<Wire> loadedWires,
                                              Map<Long, WireJunction> hubByKey, Map<Long, Integer> junctionRefs) {
        if (endElement == null || end.isConnected()) return true;

        Element junctionElement = endElement.getChild("junction");
        if (junctionElement == null) return false;

        double x;
        double y;
        try {
            x = Double.parseDouble(junctionElement.getChildText("x"));
            y = Double.parseDouble(junctionElement.getChildText("y"));
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }

        Point2D pos = new Point2D(x, y);
        long key = keyOf(x, y);

        // spájač už môže byť vytvorený iným koncom v rovnakom bode
        WireJunction shared = hubByKey.get(key);
        if (shared != null && !shared.isRemoved()) {
            end.connect(shared);
            return true;
        }

        // existujúci hub presne v bode (napr. zápísaný prvým koncom)
        for (Wire loadedWire : loadedWires) {
            if (loadedWire == end.getWire()) continue;
            for (WireEnd other : loadedWire.getEnds()) {
                WireJunction existing = other.getJunction();
                if (existing != null && keyOf(existing.getLayoutX(), existing.getLayoutY()) == key) {
                    hubByKey.put(key, existing);
                    end.connect(existing);
                    return true;
                }
            }
        }

        // kmeňový vodič len prechádza bodom - rozdelí sa na dva vodiče. Tento prípad
        // vzniká len v starých súboroch (kmeň cez bod prechádzal ako zlom). Bod sa smie
        // spojiť LEN keď leží PRESNE na vodiči - ak vodič vedie len v blízkosti bodu,
        // spojenie by skratovalo dve rôzne siete.
        // Rozdeľovať sa smie LEN ak na bod odkazuje jediný koniec vodiča. Ak na bod
        // odkazujú dva a viac koncov (nový formát), bod je už úplne popísaný týmito
        // koncami a vodič, ktorý ním len prechádza, sa NESMIE rozdeliť - inak by obnova
        // snímky (undo/redo) zmenila topológiu (vznikol by duch-spájač).
        if (junctionRefs.getOrDefault(key, 0) <= 1) {
            for (Wire loadedWire : loadedWires) {
                if (loadedWire == end.getWire()) continue;
                if (liesOnWire(loadedWire, pos)) {
                    WireJunction junction = loadedWire.splitAtPoint(pos);
                    if (junction != null) {
                        hubByKey.put(key, junction);
                        end.connect(junction);
                        return true;
                    }
                }
            }
        }

        // koniec je na mieste, kde žiadny vodič neprechádza: samostatný hub
        WireJunction standalone = WireJunction.at(end.getSheet(), x, y);
        hubByKey.put(key, standalone);
        end.connect(standalone);
        return true;
    }

    /**
     * Počet koncov vodičov, ktoré v XML odkazujú na spájač v tom istom bode. Slúži na
     * rozlíšenie starých súborov (na bod odkazuje jediný koniec odbočky) od nových, kde
     * je bod popísaný všetkými svojimi koncami - vtedy sa prechádzajúci kmeň nerozdeľuje.
     */
    private static Map<Long, Integer> countJunctionRefs(Element wiresElement) {
        Map<Long, Integer> refs = new HashMap<>();
        for (Element wireElement : wiresElement.getChildren("Wire")) {
            for (String name : new String[]{"start", "end"}) {
                Element endElement = wireElement.getChild(name);
                if (endElement == null) continue;
                Element junctionElement = endElement.getChild("junction");
                if (junctionElement == null) continue;
                try {
                    double x = Double.parseDouble(junctionElement.getChildText("x"));
                    double y = Double.parseDouble(junctionElement.getChildText("y"));
                    refs.merge(keyOf(x, y), 1, Integer::sum);
                } catch (NumberFormatException | NullPointerException ignored) {
                }
            }
        }
        return refs;
    }

    /**
     * Leží bod {@code position} presne na osi vodiča {@code wire} (s toleranciou
     * {@link #POINT_EPS})? Vodiče sú vždy priame vodorovné alebo zvislé úsečky.
     */
    private static boolean liesOnWire(Wire wire, Point2D position) {
        Point2D p0 = wire.getEnds()[0].getConnectionPoint();
        Point2D p1 = wire.getEnds()[1].getConnectionPoint();
        boolean horizontal = Math.abs(p0.getY() - p1.getY()) <= POINT_EPS;
        boolean vertical = Math.abs(p0.getX() - p1.getX()) <= POINT_EPS;
        if (!horizontal && !vertical) return false;

        double axis = horizontal ? p0.getY() : p0.getX();
        double coord = horizontal ? position.getY() : position.getX();
        if (Math.abs(coord - axis) > POINT_EPS) return false;

        double along = horizontal ? position.getX() : position.getY();
        double a = horizontal ? p0.getX() : p0.getY();
        double b = horizontal ? p1.getX() : p1.getY();
        return along >= Math.min(a, b) - POINT_EPS && along <= Math.max(a, b) + POINT_EPS;
    }

    /**
     * Kľúč bodu pre zdieľanie spájačov pri načítaní. Body sú zarovnané na mriežku,
     * preto stačí celočíselné zaokrúhlenie.
     */
    private static long keyOf(double x, double y) {
        return Math.round(x) * 1000003L + Math.round(y);
    }

    private static String colorToHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private static String colorFromHex(String hex) {
        return hex != null && hex.startsWith("#") ? hex : "#000000";
    }
}