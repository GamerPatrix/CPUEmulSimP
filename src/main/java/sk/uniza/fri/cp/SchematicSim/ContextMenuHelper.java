package sk.uniza.fri.cp.SchematicSim;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;

/**
 * Zobrazenie kontextového menu s garantovaným zavretím pri kliknutí mimo neho.
 * <p>
 * JavaFX zatvára ContextMenu po kliknutí mimo menu automaticky, ale len vtedy, keď
 * cieľ kliknutia udalosť nekonzumuje. Súčiastky na ploche schémy (ťahanie súčiastok,
 * posúvanie plochy, ťahanie vodičov) MOUSE_PRESSED konzumujú, a preto by menu po
 * kliknutí mimo neho zostalo otvorené. Toto riešenie pridá na scénu event filter
 * v zachytávacej fáze (beží predtým, ako cieľ kliknutia môže udalosť skonzumovať),
 * ktorý menu zavrie hneď pri prvom kliknutí mimo neho.
 */
public final class ContextMenuHelper {

    private static final String SCENE_KEY = ContextMenuHelper.class.getName() + ".scene";
    private static final String FILTER_KEY = ContextMenuHelper.class.getName() + ".filter";

    private ContextMenuHelper() {
    }

    /**
     * Zobrazí kontextové menu ukotvené k uzlu {@code anchor} a zabezpečí, že ho zavrie
     * hneď prvý klik kdekoľvek mimo okna menu (aj klik, ktorým sa napr. presúva súčiastka).
     */
    public static void show(ContextMenu menu, Node anchor, double screenX, double screenY) {
        menu.show(anchor, screenX, screenY);

        Scene scene = anchor.getScene();
        if (scene == null) return;

        Object storedScene = menu.getProperties().get(SCENE_KEY);
        if (storedScene instanceof Scene && storedScene.equals(scene)) {
            return; // filter pre túto scénu je už nainštalovaný
        }

        Object storedFilter = menu.getProperties().get(FILTER_KEY);
        if (storedFilter instanceof EventHandler && storedScene instanceof Scene) {
            ((Scene) storedScene).removeEventFilter(MouseEvent.MOUSE_PRESSED,
                    (EventHandler<MouseEvent>) storedFilter);
        }

        EventHandler<MouseEvent> filter = event -> menu.hide();
        menu.getProperties().put(SCENE_KEY, scene);
        menu.getProperties().put(FILTER_KEY, filter);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, filter);
    }
}