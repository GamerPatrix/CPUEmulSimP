package sk.uniza.fri.cp.SchematicSim.Wire;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import sk.uniza.fri.cp.SchematicSim.Connectable;
import sk.uniza.fri.cp.SchematicSim.Electrical.Potential;
import sk.uniza.fri.cp.SchematicSim.Item;
import sk.uniza.fri.cp.SchematicSim.Movable;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Side;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spájač - bod, v ktorom sa stretávajú vodiče.
 * <p>
 * Spájač je v novom modeli vlastnícky nezávislý (nepatrí žiadnemu vodiču) a správa
 * sa podľa počtu napojených koncov:
 * <ul>
 *     <li>0-1 koniec → zničí sa (zostáva len voľný koniec vodiča),</li>
 *     <li>2 konce → <b>neviditeľný</b> zlom dvoch priamych vodičov; ak sú konce
 *         kolmé, spájač ostáva ako neviditeľný zlom, ak sú protiľahlé (v jednej
 *         priamke), vodiče sa zlúčia do jedného,</li>
 *     <li>3 a viac koncov → viditeľný čierny krúžok.</li>
 * </ul>
 * Konce sa nikdy neťahajú ani nepresúvajú ({@link #makeImmovable()}) - spájač je
 * pevný bod; pohyb vzniká len posunom súčiastok. Zmeny sa neriešia priamo vo
 * listeneroch, ale cez špinavú množinu ({@link #markDirty}) a {@link #processNow()},
 * aby sa počas vytvárania/rušenia vodičov neriešili prechodné stavy.
 *
 * @author Tomáš Hianik (pôvodný autor), adaptácia pre SchematicSim
 */
public class WireJunction extends Joint implements Connectable {

    private static final Color FILL_COLOR = Color.BLACK;

    /** Tolerancia pre kolinearitu (súradnice sú presné násobky mriežky). */
    private static final double EPS = 1e-6;

    private final List<WireEnd> connectedEnds = new ArrayList<>();
    private final Circle junctionDot;
    private final double baseRadius;

    /** Príznak, že je spájač už zničený (zabezpečuje idempotentné mazanie). */
    private boolean removed;

    // === špinavá množina spájačov čakajúcich na prepočítanie ===

    private static final Set<WireJunction> dirtyJunctions = new LinkedHashSet<>();
    private static boolean processing;
    private static boolean reconcileScheduled;

    public WireJunction(SchematicSheet sheet) {
        super(sheet, null);

        double r = getSheet().getComponentCell() / 7.0;
        this.baseRadius = r;

        this.junctionDot = new Circle(0, 0, r, FILL_COLOR);

        this.getChildren().add(this.junctionDot);

        // nový spájač je spočiatku neviditeľný (stane sa viditeľným až pri 3+ vodičoch)
        updateVisibility();

        // spájače sa nikdy neťahajú priamo - pohyb vzniká len posunom súčiastok
        makeImmovable();

        // zvýraznenie bodu pri nájazde kurzora (len ak je viditeľný)
        this.addEventFilter(MouseEvent.MOUSE_ENTERED, event -> this.junctionDot.setRadius(this.baseRadius * 1.2));
        this.addEventFilter(MouseEvent.MOUSE_EXITED, event -> this.junctionDot.setRadius(this.baseRadius));

        registerWireStartHandlers();
    }

    /**
     * Vytvorí nový spájač na zadanej pozícii a vloží ho do vrstvy spájačov.
     */
    public static WireJunction at(SchematicSheet sheet, double x, double y) {
        WireJunction junction = new WireJunction(sheet);
        junction.setLayoutX(x);
        junction.setLayoutY(y);
        sheet.getJunctionsLayer().getChildren().add(junction);
        return junction;
    }

    /** Skrátené vytvorenie spájača z bodu. */
    public static WireJunction at(SchematicSheet sheet, Point2D position) {
        return at(sheet, position.getX(), position.getY());
    }

    /**
     * Najbližší existujúci spájač v okolí bodu (vrátane neviditeľných zlomov, ktoré
     * myšou nebuchne). Používa sa pri pustení/začatí ťahu v blízkosti existujúceho
     * spájača.
     */
    public static WireJunction findNear(SchematicSheet sheet, Point2D point, double tolerance) {
        WireJunction best = null;
        double bestDist = Double.MAX_VALUE;
        for (javafx.scene.Node node : sheet.getJunctionsLayer().getChildren()) {
            if (!(node instanceof WireJunction)) continue;
            WireJunction junction = (WireJunction) node;
            if (junction.removed) continue;
            double dist = junction.getConnectionPoint().distance(point);
            if (dist <= tolerance && dist < bestDist) {
                bestDist = dist;
                best = junction;
            }
        }
        return best;
    }

    // === udalosti: výber vodičov + tvorba odbočky ťahaním ===

    private void registerWireStartHandlers() {
        // Klik (bez ťahu) na viditeľný spájač vyberá vodiče, ktoré sa v ňom stretávajú.
        // Ťah (DRAG_DETECTED nižšie) z neho spúšťa novú odbočku.
        this.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (!event.isPrimaryButtonDown()) return;

            SchematicSheet sheet = getSheet();
            List<Wire> wires = getConnectedWires();

            if (event.isShortcutDown() && !event.isShiftDown()) {
                // Ctrl+klik = výber reťazca medzi ukotveniami (cez neviditeľné zlomy)
                if (!wires.isEmpty()) {
                    sheet.clearSelect();
                    for (Wire wire : wires.get(0).collectChain()) {
                        sheet.addSelect(wire);
                    }
                }
            } else if (event.isShiftDown()) {
                for (Wire wire : wires) {
                    if (wire.isSelected()) sheet.removeSelect(wire);
                    else sheet.addSelect(wire);
                }
            } else {
                sheet.clearSelect();
                for (Wire wire : wires) {
                    sheet.addSelect(wire);
                }
            }
            event.consume();
        });

        // ťah zo spájača = nová odbočka
        this.addEventFilter(MouseEvent.DRAG_DETECTED, event -> {
            if (!event.isPrimaryButtonDown()) return;
            if (!getSheet().isEditingEnabled()) return;

            Pin.beginWireCreation(this);
            this.startFullDrag();
            event.consume();
        });

        this.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            Wire inProgress = Pin.getInProgressWire();
            if (inProgress != null) {
                Point2D sheetXY = getSheet().sceneToSheet(event.getSceneX(), event.getSceneY());
                inProgress.updateCreationDrag(sheetXY.getX(), sheetXY.getY());
                event.consume();
            }
        });

        this.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (Pin.getInProgressWire() != null) {
                Pin.finishInProgressWire();
                event.consume();
            }
        });
    }

    /**
     * Prepočíta viditeľnosť spájača podľa počtu napojených koncov. Krúžok sa zobrazí
     * až pri troch a viacerých vodičoch; menej ako tri = neviditeľný zlom dvoch vodičov.
     * Neviditeľný spájač je aj myšou nepriepustný - nájde sa geometricky cez vodič.
     */
    void updateVisibility() {
        boolean visible = connectedEnds.size() >= 3;
        this.junctionDot.setVisible(visible);
        setMouseTransparent(!visible);
    }

    // === špinavá množina a reconcile ===

    /**
     * Označí spájač na prepočítanie. Riešenie sa odloží na koniec frontu udalostí
     * ({@code Platform.runLater}) - počas vytvárania vodiča potrebujeme, aby spájač
     * najprv existoval s prechodným počtom koncov. Viacnásobné volania sa spájajú.
     */
    public static void markDirty(WireJunction junction) {
        if (junction == null || junction.removed) return;
        if (!dirtyJunctions.add(junction)) return;
        if (reconcileScheduled) return;
        reconcileScheduled = true;
        try {
            Platform.runLater(() -> {
                reconcileScheduled = false;
                processNow();
            });
        } catch (IllegalStateException ex) {
            // FX toolkit nie je inicializovaný (headless) - vyriešime synchronne
            reconcileScheduled = false;
            if (!processing) processNow();
        }
    }

    /**
     * Okamžité prepočítanie všetkých označených spájačov. Volá sa na bezpečných
     * miestach (zmazanie vodiča, dokončenie vytvárania, načítanie/lepenie schémy);
     * inak sa rieši odložením cez {@link #markDirty}.
     */
    public static void processNow() {
        if (processing) return;
        processing = true;
        try {
            int guard = 0;
            while (!dirtyJunctions.isEmpty() && guard++ < 1000) {
                Iterator<WireJunction> iterator = dirtyJunctions.iterator();
                WireJunction junction = iterator.next();
                iterator.remove();
                reconcile(junction);
            }
        } finally {
            processing = false;
        }
    }

    private static void reconcile(WireJunction junction) {
        if (junction == null || junction.removed) return;

        // odstráň mŕtve konce (vodič už neexistuje)
        junction.connectedEnds.removeIf(end -> end.getWire() == null);

        int degree = junction.connectedEnds.size();
        if (degree <= 1) {
            destroy(junction);
            return;
        }

        if (degree == 2) {
            WireEnd end1 = junction.connectedEnds.get(0);
            WireEnd end2 = junction.connectedEnds.get(1);
            Wire wire1 = end1.getWire();
            Wire wire2 = end2.getWire();

            // oba konce toho istého vodiča (slučka) - necháme ako je
            if (wire1 == wire2) {
                junction.updateVisibility();
                return;
            }

            Point2D dir1 = legDirection(junction, end1, wire1);
            Point2D dir2 = legDirection(junction, end2, wire2);

            // nulový ramenný vektor = vodič má nulovú dĺžku (obe konce v bode spájača);
            // vodič v ťahu/náhľade (alebo počas presunu súčiastky) sa zatiaľ nesmie
            // zmazať - inak by reconcile odstránil rozpracovanú odbočku hneď po stlačení
            // (alebo by počas ťahu predčasne zmizol vodič, ktorý sa práve skracuje na nulu)
            if (dir1.magnitude() < EPS) {
                removeZeroLength(junction, wire1, end1);
                return;
            }
            if (dir2.magnitude() < EPS) {
                removeZeroLength(junction, wire2, end2);
                return;
            }

            double cross = dir1.getX() * dir2.getY() - dir1.getY() * dir2.getX();
            double dot = dir1.getX() * dir2.getX() + dir1.getY() * dir2.getY();

            if (Math.abs(cross) <= EPS && dot < 0) {
                // protiľahlé smery = jedna priama - zlúčime vodiče do jedného
                merge(junction, wire1, end1, wire2, end2);
                return;
            }
        }

        // kolmé zlomy (alebo 3+ vodiče) - viditeľnosť podľa počtu koncov
        junction.updateVisibility();
    }

    /**
     * Odstránenie vodiča s nulovou dĺžkou na spájači. Ak je jeho druhý koniec na
     * splynutom spájači (typicky stred Z-vodiča vo chvíli, keď obe vodorovné časti
     * zosunú do jednej priamky), spájače sa najprv zlúčia - zvyšné dva vodiče sa tak
     * stretnú v jednom bode a reconcile ich potom spojí do jednej priamky.
     */
    private static void removeZeroLength(WireJunction junction, Wire wire, WireEnd end) {
        if (isTemporary(wire)) {
            junction.updateVisibility();
            return;
        }

        WireEnd other = wire.getEnds()[0] == end ? wire.getEnds()[1] : wire.getEnds()[0];
        WireJunction otherJunction = other.getJunction();

        wire.delete();

        if (otherJunction != null && otherJunction != junction && !otherJunction.isRemoved()
                && otherJunction.getConnectionPoint().distance(junction.getConnectionPoint()) < EPS) {
            collapseJunction(junction, otherJunction);
        }
        markDirty(junction);
    }

    /**
     * Zlúčenie splynutého spájača {@code source} do {@code target}: presunie všetky
     * jeho konce na {@code target} a {@code source} zničí.
     */
    private static void collapseJunction(WireJunction target, WireJunction source) {
        if (target == source || target.isRemoved() || source.isRemoved()) return;

        for (WireEnd end : new ArrayList<>(source.getConnectedEnds())) {
            end.disconnect();
            end.connect(target);
        }
        destroy(source);
        markDirty(target);
    }

    /** Smer ramena vodiča od spájača - smer na DRUHÝ koniec vodiča. */
    private static Point2D legDirection(WireJunction junction, WireEnd end, Wire wire) {
        WireEnd other = wire.getEnds()[0] == end ? wire.getEnds()[1] : wire.getEnds()[0];
        return new Point2D(other.getLayoutX() - junction.getLayoutX(),
                other.getLayoutY() - junction.getLayoutY());
    }

    /**
     * Vodič, ktorý sa práve ťahá, je rozpracovaný, alebo sa práve presúva súčiastka.
     * Počas toho ho reconcile nesmie zmazať ako nulový ani zlúčiť - až po pustení myši
     * sa jeho geometria usadí a spájače sa prepočítajú znova.
     */
    private static boolean isTemporary(Wire wire) {
        return wire == null || Pin.getInProgressWire() == wire
                || wire.isPreview() || WireEnd.isGrabbing(wire)
                || Movable.isComponentDragging();
    }

    /**
     * Zlúčenie dvoch kolínkových vodičov do jedného. ZACHOVÁ sa vodič {@code keep}
     * (aj s jeho koncom na spájači); vodič {@code drop} sa zruší a jeho voľný koniec
     * sa prepojí na cieľ pôvodného konca {@code dropEnd} (pin alebo iný spájač),
     * prípadne sa presunie na jeho pozíciu. Spájač sa zničí.
     */
    private static void merge(WireJunction junction, Wire keep, WireEnd keepEnd, Wire drop, WireEnd dropEnd) {
        // počas náhľadu/ťahu sa nemá zlúčovať - dokončenie ťahu prepočíta znova
        if (isTemporary(keep) || isTemporary(drop)) return;
        if (keep == drop) return;

        WireEnd dropOther = drop.getEnds()[0] == dropEnd ? drop.getEnds()[1] : drop.getEnds()[0];
        Connectable target = dropOther.getPin() != null ? (Connectable) dropOther.getPin() : dropOther.getJunction();
        Point2D targetPos = dropOther.getConnectionPoint();

        // DÔLEŽITÉ: ponechaný koniec sa na cieľ napojí ešte PRED zrušením drop vodiča.
        // Inak by {@code drop.delete()} (vnútorne {@link #processNow()}) zničil cieľový
        // spájač - kým je keep koniec ešte odpojený, cieľ mal stupeň 1 a reconcile by ho
        // odstránil; keepEnd by sa potom pripojil na už zničený (ghost) spájač a vodiče
        // by nepatrili do jednej siete.
        keepEnd.disconnect();
        destroy(junction);
        if (target != null) {
            keepEnd.connect(target);
        } else {
            keepEnd.moveTo(targetPos.getX(), targetPos.getY());
        }

        // cieľová väzba už drží ponechaný vodič, takže zrušenie drop vodiča (odpojenie
        // jeho vzdialenejšieho konca už nedeje stupeň cieľa pod 2) neprekreslí spojenie
        drop.delete();
        keep.updateGeometry();
    }

    /**
     * Zlúčenie prekrývajúcich sa kolineárnych vodičov - odstránenie "slepých výbežkov".
     * <p>
     * Ide o prípad, keď na spájači končia dva vodiče na tej istej priamke a od spájača
     * vedú oba ROVNAKÝM smerom (jeden je dlhší, druhý - kratší - je jeho náhradným
     * presahom, napr. vodič natiahnutý z B do C a potom z C naspäť cez B do A).
     * Longer z oboch (užívateľsky "protichodný" priebeh) sa svojim koncom pri spájači
     * presunie do spoločného bodu kratšieho a kratší sa zruší. Výsledkom je jediný
     * vodič medzi vonkajšími koncami (odpadá nadbytočný presah).
     * <p>
     * Reconcile tento prípad automaticky nespúšťa (nebezpečné počas ťahov) - volá sa
     * len explicitne z príkazu "Zlúčiť kolineárne vodiče" na ploche.
     *
     * @return true ak sa zlúčila aspoň jedna dvojica.
     */
    public static boolean mergeCollinearOverlaps(SchematicSheet sheet) {
        if (sheet == null) return false;
        boolean any = false;
        for (Node node : new ArrayList<>(sheet.getJunctionsLayer().getChildren())) {
            if (!(node instanceof WireJunction)) continue;
            WireJunction junction = (WireJunction) node;
            if (junction.isRemoved()) continue;
            if (junction.connectedEnds.size() != 2) continue;

            WireEnd end1 = junction.connectedEnds.get(0);
            WireEnd end2 = junction.connectedEnds.get(1);
            Wire wire1 = end1.getWire();
            Wire wire2 = end2.getWire();
            if (wire1 == null || wire2 == null || wire1 == wire2) continue;
            if (isTemporary(wire1) || isTemporary(wire2)) continue;

            WireEnd far1 = otherEnd(wire1, end1);
            WireEnd far2 = otherEnd(wire2, end2);
            Point2D dir1 = new Point2D(far1.getLayoutX() - junction.getLayoutX(),
                    far1.getLayoutY() - junction.getLayoutY());
            Point2D dir2 = new Point2D(far2.getLayoutX() - junction.getLayoutX(),
                    far2.getLayoutY() - junction.getLayoutY());
            if (dir1.magnitude() < EPS || dir2.magnitude() < EPS) continue;

            double cross = dir1.getX() * dir2.getY() - dir1.getY() * dir2.getX();
            double dot = dir1.getX() * dir2.getX() + dir1.getY() * dir2.getY();
            if (Math.abs(cross) > EPS || dot <= 0) continue;

            // oba vedú rovnakým smerom od spájača - dlhší ponecháme, kratší výbežok zrušíme
            if (dir1.magnitude() > dir2.magnitude()) {
                merge(junction, wire1, end1, wire2, end2);
            } else {
                merge(junction, wire2, end2, wire1, end1);
            }
            any = true;
        }
        return any;
    }

    /** Druhý koniec vodiča (opačný oproti {@code end}). */
    private static WireEnd otherEnd(Wire wire, WireEnd end) {
        return wire.getEnds()[0] == end ? wire.getEnds()[1] : wire.getEnds()[0];
    }

    /** Zničenie spájača - uvoľní všetky jeho konce a odstráni ho z plochy. */
    static void destroy(WireJunction junction) {
        if (junction == null || junction.removed) return;
        junction.markRemoved();
        dirtyJunctions.remove(junction);
        for (WireEnd end : new ArrayList<>(junction.connectedEnds)) {
            end.disconnect();
        }
        junction.connectedEnds.clear();
        javafx.scene.Parent parent = junction.getParent();
        if (parent instanceof javafx.scene.layout.Pane) {
            ((javafx.scene.layout.Pane) parent).getChildren().remove(junction);
        } else if (parent instanceof Group) {
            ((Group) parent).getChildren().remove(junction);
        }
    }

    // === prístup k napojeným koncom ===

    void addWireEnd(WireEnd end) {
        if (!connectedEnds.contains(end)) connectedEnds.add(end);
        updateVisibility();
    }

    void removeWireEnd(WireEnd end) {
        connectedEnds.remove(end);
        updateVisibility();
    }

    List<WireEnd> getConnectedEnds() {
        return connectedEnds;
    }

    /**
     * Vodiče, ktoré sa tohto spájača dotýkajú (každý najraz). Používa sa pri výbere
     * spájačom a pri prechode cez neviditeľné zlomy.
     */
    public List<Wire> getConnectedWires() {
        List<Wire> wires = new ArrayList<>();
        for (WireEnd end : new ArrayList<>(connectedEnds)) {
            Wire wire = end.getWire();
            if (wire != null && !wires.contains(wire)) wires.add(wire);
        }
        return wires;
    }

    /**
     * {@code true} ak je spájač prechodným bodom dvoch vodičov (neviditeľný zlom) -
     * reťazec výberu (Ctrl+klik) cez neho prechádza.
     */
    boolean isCrossable() {
        return !removed && connectedEnds.size() == 2;
    }

    // === potenciály ===

    /**
     * Nájde pin, na ktorý je tento spájač elektricky napojený cez vodiče. V novom modeli
     * sa spájačom vodiace vodiče navzájom dotýkajú cez napojené konce, preto sa prechádza
     * graf napojených koncov (s ochranou proti cyklom).
     */
    public Pin findConnectedPin() {
        return findConnectedPin(null);
    }

    /**
     * Ako {@link #findConnectedPin()}, ale neprechádza cez konce vodiča {@code avoid}.
     * Používa {@link Wire#updatePotential()} pri hľadaní protiľahlého pinu konca: spájač
     * vzniknutý rozdelením vodiča má ako prvý koniec práve jeho vlastný koniec, takže
     * bez obchádzky by vodič dostal potenciál (pin, pin) - pri pasívnom pine (LED, vstup)
     * by mal typ IN a hodnotu NC, čo sa prejaví trvalo sivou debug-farbou vodiča.
     */
    public Pin findConnectedPin(Wire avoid) {
        return findConnectedPin(new HashSet<>(), avoid);
    }

    private Pin findConnectedPin(Set<WireJunction> visited, Wire avoid) {
        if (!visited.add(this)) return null;

        for (WireEnd end : new ArrayList<>(this.connectedEnds)) {
            if (avoid != null && end.getWire() == avoid) continue;
            Pin direct = end.getPin();
            if (direct != null) return direct;

            Wire wire = end.getWire();
            if (wire == null) continue;
            for (WireEnd other : wire.getEnds()) {
                if (other == end) continue;
                Pin pin = other.getPin();
                if (pin != null) return pin;
                WireJunction otherJunction = other.getJunction();
                if (otherJunction != null && otherJunction != this) {
                    Pin found = otherJunction.findConnectedPin(visited, avoid);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /**
     * Prepočíta potenciály vodičov momentálne napojených na tento spájač (obnoví celú
     * sieť okolo prvého z nich - {@link Wire#updatePotentialNetwork()} prejde všetkých
     * susedov cez spájače). Volá sa po odpojení konca.
     */
    void refreshConnectedWires() {
        for (WireEnd end : new ArrayList<>(this.connectedEnds)) {
            Wire wire = end.getWire();
            if (wire != null) {
                wire.updatePotentialNetwork();
                return;
            }
        }
    }

    @Override
    protected Group generateJointGraphic(double radius) {
        return new Group();
    }

    @Override
    public Point2D getConnectionPoint() {
        return new Point2D(getLayoutX(), getLayoutY());
    }

    @Override
    public void delete() {
        destroy(this);
    }

    @Override
    public Item getOwnerItem() {
        return null;
    }

    @Override
    public void highlight(int highlightType) {
    }

    @Override
    public void unhighlight(int highlightType) {
    }

    @Override
    public Point2D getSceneGridPosition() {
        return getConnectionPoint();
    }

    @Override
    public boolean isOccupied() {
        return false;
    }

    @Override
    public Side getExitSide() {
        return null;
    }

    @Override
    public Potential getPotential() {
        Pin pin = findConnectedPin();
        if (pin != null) return pin.getPotential();
        return null;
    }

    public boolean isRemoved() {
        return this.removed;
    }

    void markRemoved() {
        this.removed = true;
    }
}
