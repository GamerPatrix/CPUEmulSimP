package sk.uniza.fri.cp.SchematicSim;

import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import sk.uniza.fri.cp.SchematicSim.Gates.GateSymbol;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Wire.Wire;
import sk.uniza.fri.cp.SchematicSim.Wire.WireEnd;
import sk.uniza.fri.cp.SchematicSim.Wire.WireJunction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Objekt s ktorým je možné pohybovať pomocou kurzora na ploche schémy, so snapovaním na mriežku.
 * Prevzaté takmer bezo zmeny z BreadboardSim.Movable - tento mechanizmus je úplne nezávislý
 * od breadboardu, mení sa iba typ plochy (Board -> SchematicSheet).
 *
 * @author Tomáš Hianik (pôvodný autor), adaptácia pre SchematicSim
 */
public abstract class Movable extends HighlightGroup {

    private int gridPosX;
    private int gridPosY;
    private SchematicSheet sheet;

    private double nodeOffsetX = -1;
    private double nodeOffsetY = -1;

    /** Pre viacnásobný pohyb: posledná pozícia ťahaného objektu - delta sa aplikuje na všetky vybraté. */
    private int dragLastGridX;
    private int dragLastGridY;

    /**
     * Príznak práve prebiehajúceho presunu súčiastky. Kým je nastavený, spájače
     * neodstraňujú nulové vodiče ani nezlúčia kolineárne vodiče ({@link WireJunction}) -
     * počas ťahu tak vodič nulovej dĺžky nezmizne predčasne. Priestor sa vyrieši
     * až po pustení myši ({@link #finishComponentDrag()}).
     */
    private static boolean componentDragging;

    public static boolean isComponentDragging() {
        return componentDragging;
    }

    private final EventHandler<MouseEvent> onMousePressedEventHandler = event -> {
        if (!event.isPrimaryButtonDown()) return;
        if (sheet != null && !sheet.isEditingEnabled()) return;
        nodeOffsetX = event.getSceneX() - getLayoutX() * sheet.getAppliedScale();
        nodeOffsetY = event.getSceneY() - getLayoutY() * sheet.getAppliedScale();
        dragLastGridX = gridPosX;
        dragLastGridY = gridPosY;
        event.consume();
    };

    private final EventHandler<MouseEvent> onMouseDraggedEventHandler = event -> {
        if (!event.isPrimaryButtonDown()) return;
        if (sheet != null && !sheet.isEditingEnabled()) return;

        componentDragging = true;
        setCursor(Cursor.DEFAULT);

        if (nodeOffsetX == -1) {
            Point2D layout = getParent().sceneToLocal(event.getSceneX(), event.getSceneY());
            Bounds boundsInParent = getBoundsInParent();
            nodeOffsetX = event.getSceneX() - (layout.getX() - boundsInParent.getWidth() / 2.0 - boundsInParent.getMinX()) * sheet.getAppliedScale();
            nodeOffsetY = event.getSceneY() - (layout.getY() - boundsInParent.getHeight() / 2.0 - boundsInParent.getMinY()) * sheet.getAppliedScale();
        }

        GridSystem grid = sheet.getGrid();
        int gridX;
        int gridY;

        gridX = (int) (Math.round((event.getSceneX() - nodeOffsetX) / grid.getSizeX() / sheet.getAppliedScale()) * grid.getSizeX()) / grid.getSizeX();
        gridY = (int) (Math.round((event.getSceneY() - nodeOffsetY) / grid.getSizeY() / sheet.getAppliedScale()) * grid.getSizeY()) / grid.getSizeY();

        if (gridPosX != gridX || gridPosY != gridY) {
            if (gridX < 0) gridX = 0;
            else if (gridX * grid.getSizeX() + getBoundsInParent().getWidth() > sheet.getWidthPx())
                gridX = (int) (Math.round(sheet.getWidthPx() - getBoundsInParent().getWidth()) / grid.getSizeX());

            if (gridY < 0) gridY = 0;
            else if (gridY * grid.getSizeY() + getBoundsInParent().getHeight() > sheet.getHeightPx())
                gridY = (int) (Math.round(sheet.getHeightPx() - getBoundsInParent().getHeight()) / grid.getSizeY());

            if (this instanceof GateSymbol && isSelected()) {
                int changeX = gridX - dragLastGridX;
                int changeY = gridY - dragLastGridY;
                List<GateSymbol> selectedGates = sheet.getSelectedGates();
                Set<WireJunction> carried = (changeX != 0 || changeY != 0)
                        ? collectCarriedJunctions(sheet, selectedGates)
                        : Collections.emptySet();
                sheet.setGroupMoveCarried(carried);
                try {
                    for (GateSymbol other : selectedGates) {
                        if (other != this) other.moveBy(changeX, changeY);
                    }
                    moveTo(gridX, gridY);
                    // spájače vo vnútri presúvanej skupiny sa presunú pevne o celý posun -
                    // kaskáda vodičov by ich inak posunula len kolmo a viackrát
                    for (WireJunction junction : carried) {
                        junction.setLayoutX(junction.getLayoutX() + changeX * grid.getSizeX());
                        junction.setLayoutY(junction.getLayoutY() + changeY * grid.getSizeY());
                    }
                } finally {
                    sheet.setGroupMoveCarried(Collections.emptySet());
                }
            } else {
                moveTo(gridX, gridY);
            }

            gridPosX = gridX;
            gridPosY = gridY;
            dragLastGridX = gridX;
            dragLastGridY = gridY;
        }

        event.consume();
    };

    /**
     * Po pustení myši (koniec presunu) sa vyriešia spájače odložené počas ťahu -
     * vodiče nulovej dĺžky sa odstránia a zvyšné kolineárne vodiče sa spoja.
     */
    private final EventHandler<MouseEvent> onMouseReleasedEventHandler = event -> {
        if (!componentDragging) return;
        componentDragging = false;
        finishComponentDrag();
    };

    private void finishComponentDrag() {
        SchematicSheet currentSheet = getSheet();
        if (currentSheet == null) return;

        for (Node node : new ArrayList<>(currentSheet.getJunctionsLayer().getChildren())) {
            if (node instanceof WireJunction) WireJunction.markDirty((WireJunction) node);
        }
        WireJunction.processNow();
    }

    /**
     * Spájače, ktoré sa pri hromadnom presune presúvajú PEVNE spolu so skupinou súčiastok:
     * všetky dosiahnuteľné z pinov presúvaných súčiastok cez vodiče a spájače, ktoré nie
     * sú zakotvené (ich cesta sa nikde nekončí na pine nepresúvanej súčiastky). Takýto
     * spájač patrí celým do presúvanej siete a musí sa posunúť o plný posun - kaskáda
     * vodičov ho posúva len kolmo a pri viacerých vodičoch by sa posunul viackrát.
     */
    private static Set<WireJunction> collectCarriedJunctions(SchematicSheet sheet,
                                                             List<GateSymbol> movedGates) {
        if (movedGates == null || movedGates.isEmpty()) return Collections.emptySet();
        Set<GateSymbol> moved = new HashSet<>(movedGates);
        Set<WireJunction> anchored = collectAnchoredJunctions(sheet, moved);

        Set<WireJunction> carried = new HashSet<>();
        LinkedList<Wire> queue = new LinkedList<>();
        Set<Wire> seenWires = new HashSet<>();
        for (GateSymbol gate : moved) {
            if (gate.getPins() == null) continue;
            for (Pin pin : gate.getPins()) {
                WireEnd end = pin.getWireEnd();
                if (end == null) continue;
                Wire wire = end.getWire();
                if (wire != null && seenWires.add(wire)) queue.add(wire);
            }
        }
        while (!queue.isEmpty()) {
            Wire wire = queue.poll();
            for (WireEnd end : wire.getEnds()) {
                WireJunction junction = end.getJunction();
                if (junction == null || anchored.contains(junction)
                        || !carried.add(junction)) continue;
                for (Wire neighbour : junction.getConnectedWires()) {
                    if (neighbour != wire && seenWires.add(neighbour)) queue.add(neighbour);
                }
            }
        }
        return carried;
    }

    /**
     * Spájače, z ktorých vedie aspoň jedna cesta cez vodiče k pinu súčiastky mimo
     * presúvanej skupiny (transitívne). Takýto spájač sa nesmie posúvať pevne so
     * skupinou - jeho poloha patrí statickej časti schémy.
     */
    private static Set<WireJunction> collectAnchoredJunctions(SchematicSheet sheet,
                                                              Set<GateSymbol> moved) {
        Set<WireJunction> anchored = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Node node : new ArrayList<>(sheet.getJunctionsLayer().getChildren())) {
                if (!(node instanceof WireJunction)) continue;
                WireJunction junction = (WireJunction) node;
                if (anchored.contains(junction)) continue;
                if (isAnchored(junction, moved, anchored)) {
                    anchored.add(junction);
                    changed = true;
                }
            }
        }
        return anchored;
    }

    /** Je spájač zakotvený cez niektorý z napojených vodičov na nepresúvanú súčiastku? */
    private static boolean isAnchored(WireJunction junction, Set<GateSymbol> moved,
                                      Set<WireJunction> anchored) {
        for (Wire wire : junction.getConnectedWires()) {
            WireEnd opposite = oppositeEnd(junction, wire);
            if (opposite.getPin() != null) {
                if (!moved.contains(opposite.getPin().getOwner())) return true;
            } else if (opposite.getJunction() != null) {
                if (anchored.contains(opposite.getJunction())) return true;
            }
        }
        return false;
    }

    /** Druhý koniec vodiča voči spájaču {@code junction}. */
    private static WireEnd oppositeEnd(WireJunction junction, Wire wire) {
        return wire.getEnds()[0].getJunction() == junction ? wire.getEnds()[1] : wire.getEnds()[0];
    }

    public Movable(SchematicSheet sheet) {
        this.sheet = sheet;
        this.addEventHandler(MouseEvent.MOUSE_PRESSED, onMousePressedEventHandler);
        this.addEventHandler(MouseEvent.MOUSE_DRAGGED, onMouseDraggedEventHandler);
        this.addEventHandler(MouseEvent.MOUSE_RELEASED, onMouseReleasedEventHandler);
    }

    public void makeImmovable() {
        this.removeEventHandler(MouseEvent.MOUSE_PRESSED, onMousePressedEventHandler);
        this.removeEventHandler(MouseEvent.MOUSE_DRAGGED, onMouseDraggedEventHandler);
        this.removeEventHandler(MouseEvent.MOUSE_RELEASED, onMouseReleasedEventHandler);
    }

    public void moveBy(double deltaX, double deltaY) {
        this.gridPosX += Math.round(deltaX) / sheet.getGrid().getSizeX();
        this.gridPosY += Math.round(deltaY) / sheet.getGrid().getSizeY();
        this.setLayoutX(getLayoutX() + deltaX);
        this.setLayoutY(getLayoutY() + deltaY);
    }

    public void moveBy(int deltaX, int deltaY) {
        this.gridPosX += deltaX;
        this.gridPosY += deltaY;
        Point2D point = sheet.getGrid().gridToPixel(this.gridPosX, this.gridPosY);
        this.setLayoutX(point.getX());
        this.setLayoutY(point.getY());
    }

    public void moveTo(Point2D point) {
        this.setLayoutX(point.getX());
        this.setLayoutY(point.getY());
    }

    public void moveTo(double posX, double posY) {
        this.setLayoutX(posX);
        this.setLayoutY(posY);
    }

    public void moveTo(int gridPosX, int gridPosY) {
        this.gridPosX = gridPosX;
        this.gridPosY = gridPosY;

        Point2D point = getSheet().getGrid().gridToPixel(gridPosX, gridPosY);
        this.setLayoutX(point.getX());
        this.setLayoutY(point.getY());
    }

    public SchematicSheet getSheet() {
        return this.sheet;
    }

    public int getGridPosX() {
        return gridPosX;
    }

    public int getGridPosY() {
        return gridPosY;
    }

    public void setGridPos(int gridPosX, int gridPosY) {
        this.gridPosX = gridPosX;
        this.gridPosY = gridPosY;
    }

    @Override
    public void delete() {
        super.delete();
        this.getSheet().removeItem(this);
    }
}
