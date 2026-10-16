package sk.uniza.fri.cp.SchematicSim.Wire;

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import sk.uniza.fri.cp.SchematicSim.Movable;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;

/**
 * Spájač/zlom na vodiči. Základ pre {@link WireEnd} (koniec vodiča) a
 * {@link WireJunction} (bod, v ktorom sa stretávajú tri a viac vodičov).
 * <p>
 * V novom modeli sú vodiče vždy priame úsečky - žiadne vnútorné zlomy ani segmenty,
 * takže trieda už nenesie žiadnu routing logiku (len grafický krúžok a bounding box).
 *
 * @author Tomáš Hianik (pôvodný autor), adaptácia pre SchematicSim
 */
public class Joint extends Movable {

    private static final Color DEFAULT_COLOR = Color.DARKGRAY;

    /** Vodič, ktorého je tento joint súčasťou (null pre {@link WireJunction}). */
    private final Wire wire;
    private Circle joint;
    private Circle colorizer;
    private double radius;

    public Joint(SchematicSheet sheet, Wire wire) {
        super(sheet);
        this.wire = wire;

        double cell = getSheet().getComponentCell();

        // klikacia plocha jointu = jedna bunka mriežky (nie celá komponentová bunka,
        // tá je 20x20 a prekrývala by susedný vodič vzdialený o mriežku)
        double box = getSheet().getGrid().getSizeMin();
        Rectangle boundingBox = new Rectangle(box, box);
        boundingBox.setOpacity(0);
        boundingBox.setLayoutX(-box / 2.0);
        boundingBox.setLayoutY(-box / 2.0);

        this.radius = cell / 7.0;
        Group graphic = generateJointGraphic(radius);

        this.getChildren().addAll(boundingBox, graphic);
    }

    public Wire getWire() {
        return this.wire;
    }

    public void setColor(Color color) {
        if (this.colorizer == null) return;
        if (Platform.isFxApplicationThread()) {
            this.colorizer.setFill(color);
            this.colorizer.setOpacity(1);
        } else {
            Platform.runLater(() -> {
                this.colorizer.setFill(color);
                this.colorizer.setOpacity(1);
            });
        }
    }

    public void setDefaultColor() {
        if (this.colorizer == null) return;
        if (Platform.isFxApplicationThread()) this.colorizer.setOpacity(0);
        else Platform.runLater(() -> this.colorizer.setOpacity(0));
    }

    void incRadius() {
        if (this.joint == null) return;
        radius *= 1.2;
        this.joint.setRadius(radius);
    }

    /**
     * Viditeľnosť základného (tmavého) bodu tohto jointu. {@link WireEnd} ho skrýva -
     * koniec vodiča sa vykresľuje bez krúžku; čierny krúžok tak ostáva len na
     * {@link WireJunction}, kde sa stretávajú tri a viac vodičov. Krúžok sa iba skryje,
     * nie odstráni, aby naďalej fungoval {@link #incRadius()} aj pick-test.
     */
    protected void setJointDotVisible(boolean visible) {
        if (this.joint != null) this.joint.setVisible(visible);
    }

    protected Group generateJointGraphic(double radius) {
        Group graphics = new Group();

        this.joint = new Circle(radius, radius, radius, DEFAULT_COLOR);
        this.joint.setTranslateX(-radius);
        this.joint.setLayoutY(-radius);

        this.colorizer = new Circle(0, 0, radius, Color.RED);
        this.colorizer.setOpacity(0);

        graphics.getChildren().addAll(this.joint, this.colorizer);
        return graphics;
    }

    /**
     * Bod, v ktorom sa vodič má napojiť na tento joint - stred jointu
     * v súradniciach plochy.
     */
    public Point2D getConnectionPoint() {
        return new Point2D(getLayoutX(), getLayoutY());
    }
}
