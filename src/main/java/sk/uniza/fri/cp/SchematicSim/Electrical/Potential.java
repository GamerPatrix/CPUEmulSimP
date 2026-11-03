package sk.uniza.fri.cp.SchematicSim.Electrical;

import sk.uniza.fri.cp.SchematicSim.Connectable;
import sk.uniza.fri.cp.SchematicSim.Gates.GateSymbol;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;

import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Potenciál medzi dvoma bodmi siete (Pin alebo Joint). Prepájanie bodov potenciálom
 * vytvára binárny strom spojení - úplne rovnaká logika ako v BreadboardSim.Socket.Potential,
 * iba Socket je nahradené všeobecným rozhraním {@link Connectable}, takže potenciál môže
 * spájať piny súčiastok aj zlomy vodičov rovnako.
 *
 * @author Tomáš Hianik (pôvodný autor), adaptácia pre SchematicSim
 */
public class Potential {

    public enum Value { HIGH, LOW, NC }

    private Connectable node1;
    private Connectable node2;
    private Potential parent1;
    private Potential parent2;
    private Potential child;
    private volatile Value value;
    private Boolean floatingHigh;
    private PinType type;

    // skrat
    private boolean shortCircuit;
    private final LinkedList<Connectable> shortedNodes;
    private final List<Runnable> valueListeners = new ArrayList<>();

    /** Počet zaregistrovaných poslucháčov (farby vodičov v debug režime) vo všetkých potenciáloch. */
    private static final AtomicInteger listenerCount = new AtomicInteger();

    public Potential(Connectable node1, Connectable node2) {
        this.shortedNodes = new LinkedList<>();
        this.node1 = node1;
        this.node2 = node2;
        this.update();
    }

    /**
     * Ak má potenciál potomka, vráti odkaz naň, inak vráti seba.
     */
    public synchronized Potential getPotential() {
        if (child != null) return child.getPotential();
        return this;
    }

    /**
     * Naplní zoznam bodmi siete, ktoré sú v danom strome spojení prepojené.
     */
    public void getConnectedNodes(List<Connectable> listToFill) {
        if (this.parent1 == null && this.parent2 == null) {
            if (this.node1 != null) listToFill.add(this.node1);
            if (this.node2 != null) listToFill.add(this.node2);
        } else {
            if (this.parent1 != null) this.parent1.getConnectedNodes(listToFill);
            if (this.parent2 != null) this.parent2.getConnectedNodes(listToFill);
        }
    }

    /**
     * Naplní množinu súčiastkami, ktoré majú k stromu spojení pripojené vstupné piny.
     */
    public void getGatesWithInputs(Set<GateSymbol> setToFill) {
        if (this.parent1 == null && this.parent2 == null) {
            if (this.node1 instanceof Pin && (this.type == PinType.IN || this.type == PinType.IO || this.type == PinType.WEAK_OUT)) {
                setToFill.add(((Pin) this.node1).getOwner());
            }
            if (this.node2 instanceof Pin && (this.type == PinType.IN || this.type == PinType.IO || this.type == PinType.WEAK_OUT)) {
                setToFill.add(((Pin) this.node2).getOwner());
            }
        } else {
            if (this.parent1 != null) this.parent1.getGatesWithInputs(setToFill);
            if (this.parent2 != null) this.parent2.getGatesWithInputs(setToFill);
        }
    }

    public synchronized void update() {
        if (this.parent1 != null) this.parent1.child = null;
        if (this.parent2 != null) this.parent2.child = null;

        if (this.node1 != null) this.parent1 = this.node1.getPotential();
        if (this.node2 != null) this.parent2 = this.node2.getPotential();

        if (this.parent1 == this) this.parent1 = null;
        if (this.parent2 == this) this.parent2 = null;

        if (this.parent1 != null) this.parent1.child = this;
        if (this.parent2 != null) this.parent2.child = this;

        Potential oldChild = this.child;
        this.child = null;

        this.updateType();
        this.setValue(Value.NC);
        this.floatingHigh = null;

        if (oldChild != null) oldChild.update();
    }

    public synchronized Value getValue() {
        return value;
    }

    /**
     * Stabilná náhodná hodnota plávajúceho potenciálu ({@link Value#NC} = High-Z). Kým
     * potenciál ostáva plávajúci, vracia tú istú vygenerovanú hodnotu - vstupy citlivé
     * na plávajúci stav tak neblikajú pri každom kroku simulácie. Pri prechode na
     * definovanú úroveň (0/1) sa náhoda zahodí, takže ďalší prechod do High-Z vygeneruje
     * novú hodnotu.
     *
     * @return {@code true} ak má plávajúci potenciál náhodne predstavovať HIGH
     */
    public synchronized boolean getFloatingValue() {
        if (this.value != Value.NC) {
            this.floatingHigh = null;
            return this.value == Value.HIGH;
        }
        if (this.floatingHigh == null) {
            this.floatingHigh = Math.random() < 0.5;
        }
        return this.floatingHigh;
    }

    public synchronized void addValueListener(Runnable listener) {
        valueListeners.add(listener);
        listenerCount.incrementAndGet();
    }

    public synchronized void removeValueListener(Runnable listener) {
        if (valueListeners.remove(listener)) listenerCount.decrementAndGet();
    }

    /**
     * Upozornenie poslucháčov v celom strome pod týmto potenciálom. Hodnota celej siete je
     * iba v koreni stromu - vodič, ktorého podstrom nemá budič (napr. za labelom spojeným
     * neviditeľným vodičom), má vlastnú hodnotu NC, no farbí sa podľa koreňa, a preto musí
     * vedieť aj o zmene koreňa. Bez poslucháčov (debug farby vypnuté) sa strom neprechádza.
     */
    public void notifyTreeListeners() {
        if (listenerCount.get() == 0) return;
        notifyValueListeners();
        if (this.parent1 != null) this.parent1.notifyTreeListeners();
        if (this.parent2 != null) this.parent2.notifyTreeListeners();
    }

    /**
     * Odpojenie ako {@link #detach()}, po ktorom sa obnovia poslucháči stromov bývalých
     * predkov - ich koreňom je odteraz každý predok sám (napr. odstránenie neviditeľného vodiča).
     */
    public void detachAndNotify() {
        Potential former1 = this.parent1;
        Potential former2 = this.parent2;
        detach();
        if (former1 != null) former1.notifyTreeListeners();
        if (former2 != null) former2.notifyTreeListeners();
    }

    private void notifyValueListeners() {
        List<Runnable> listeners;
        synchronized (this) {
            listeners = new ArrayList<>(valueListeners);
        }
        listeners.forEach(Runnable::run);
    }

    /**
     * Nastavenie novej hodnoty potenciálu. Berie do úvahy typ potenciálu a hodnoty predkov.
     * Kontroluje aj skrat pri spojení dvoch rozdielnych výstupov.
     *
     * @return True - hodnota sa správne nastavila, False - nastal skrat.
     */
    public boolean setValue(Value newVal) {
        this.shortCircuit =
                (this.parent1 != null && this.parent1.shortCircuit)
                        || (this.parent2 != null && this.parent2.shortCircuit);

        if (this.shortCircuit) {
            this.value = Math.random() < 0.5 ? Value.LOW : Value.HIGH;
        } else if (this.parent1 != null && this.parent2 != null) {

            if (this.type == PinType.NC || this.type == PinType.IN) {
                this.value = Value.NC;
            } else if (this.parent1.getValue() == Value.NC && this.parent2.getValue() == Value.NC) {
                this.value = Value.NC;
            } else if (this.type == PinType.IO || this.type == PinType.TRI_OUT) {
                if ((this.parent1.type == PinType.IO || this.parent1.type == PinType.TRI_OUT) &&
                        (this.parent2.type == PinType.IO || this.parent2.type == PinType.TRI_OUT)) {
                    this.checkShortCircuit();
                } else {
                    if (this.parent1.type == PinType.IO || this.parent1.type == PinType.TRI_OUT)
                        this.value = this.parent1.value;
                    else
                        this.value = this.parent2.value;
                }
            } else if (this.type == PinType.WEAK_OUT) {
                if (this.parent1.type == PinType.WEAK_OUT && this.parent2.type == PinType.WEAK_OUT) {
                    this.value = (this.parent1.value == Value.LOW || this.parent2.value == Value.LOW)
                            ? Value.LOW : Value.HIGH;
                } else if (this.parent1.type == PinType.WEAK_OUT) {
                    if (((this.parent2.type == PinType.IO || this.parent2.type == PinType.TRI_OUT) && this.parent2.getValue() != Value.NC)
                            || this.parent2.type == PinType.OUT)
                        this.value = this.parent2.value;
                    else
                        this.value = this.parent1.value;
                } else {
                    if (((this.parent1.type == PinType.IO || this.parent1.type == PinType.TRI_OUT) && this.parent1.getValue() != Value.NC)
                            || this.parent1.type == PinType.OUT)
                        this.value = this.parent1.value;
                    else
                        this.value = this.parent2.value;
                }
            } else if (this.type == PinType.OUT) {
                PinType theOther = this.parent1.type == PinType.OUT ? this.parent2.type : this.parent1.type;

                if (theOther == PinType.OUT || theOther == PinType.IO || theOther == PinType.TRI_OUT) {
                    this.checkShortCircuit();
                } else if (this.parent1.type == PinType.OUT) {
                    this.value = this.parent1.value;
                } else {
                    this.value = this.parent2.value;
                }
            }
        } else if (this.parent1 != null) {
            this.value = this.parent1.getValue();
        } else if (this.parent2 != null) {
            this.value = this.parent2.getValue();
        } else {
            this.value = newVal;
        }

        if (this.value != Value.NC) this.floatingHigh = null;

        this.unhighlightShortCircuitNodes();

        if (this.shortCircuit && this.child == null) this.highlightShortCircuitNodes();

        // notifikujeme vždy, nielen keď sme list (child == null) - potenciál vložený do
        // stredu siete (napr. kmeňový vodič, z ktorého vedie odbočka) je internal node
        // a zmena hodnoty by ho inak obišla a jeho poslucháč by sa nikdy neozval
        notifyValueListeners();

        if (this.child != null) return this.child.setValue(this.value);

        // koreň stromu - zmena hodnoty siete sa týka aj vodičov mimo cesty k listu
        notifyTreeListeners();

        return !this.shortCircuit;
    }

    public synchronized PinType getType() {
        return this.type;
    }

    public synchronized void setType(PinType newType) {
        if (this.parent1 == null && this.parent2 == null) {
            this.type = newType;
            if (this.child != null) this.child.updateType();
        } else {
            this.updateType();
        }
    }

    public Potential getChild() {
        return this.child;
    }

    /**
     * Zrušenie potenciálu - odpojí sa od predkov a aktualizuje potomkov.
     */
    public void delete() {
        if (this.parent1 != null) this.parent1.child = null;
        if (this.parent2 != null) this.parent2.child = null;

        if (!this.shortedNodes.isEmpty())
            this.shortedNodes.forEach(node -> node.unhighlight(Connectable.WARNING));

        if (this.child != null) {
            this.child.update();
        } else if (this.shortCircuit) {
            if (this.parent1 != null) this.parent1.highlightShortCircuitNodes();
            if (this.parent2 != null) this.parent2.highlightShortCircuitNodes();
        }
    }

    /**
     * Tiché odpojenie potenciálu od predkov a potomkov BEZ kaskádneho prepočtu
     * potomkov. Používa sa pri hromadnej obnove celej siete (Wire.updatePotentialNetwork):
     * kaskáda by počas búrania reťaze prepájala zvyšné potenciály v náhodnom poradí
     * podľa toho, kto sa práve odpojoval. Odpojený potenciál sa okamžite zahodí.
     */
    public synchronized void detach() {
        if (this.parent1 != null) this.parent1.child = null;
        if (this.parent2 != null) this.parent2.child = null;
        this.parent1 = null;
        this.parent2 = null;
        this.child = null;
        this.unhighlightShortCircuitNodes();
    }

    private synchronized void updateType() {
        PinType type1 = PinType.NC;
        PinType type2 = PinType.NC;

        if (this.parent1 != null || this.parent2 != null) {
            if (this.parent1 != null) type1 = this.parent1.getType();
            if (this.parent2 != null) type2 = this.parent2.getType();

            if (type1 == PinType.OUT || type2 == PinType.OUT) {
                this.type = PinType.OUT;
            } else if (type1 == PinType.WEAK_OUT || type2 == PinType.WEAK_OUT) {
                this.type = PinType.WEAK_OUT;
            } else if (type1 == PinType.TRI_OUT || type2 == PinType.TRI_OUT) {
                this.type = PinType.TRI_OUT;
            } else if (type1 == PinType.IO || type2 == PinType.IO) {
                this.type = PinType.IO;
            } else if (type1 == PinType.IN || type2 == PinType.IN) {
                this.type = PinType.IN;
            } else {
                this.type = PinType.NC;
            }
        } else {
            this.type = PinType.NC;
        }

        if (this.child != null) this.child.updateType();
    }

    private void highlightShortCircuitNodes() {
        if (this.shortCircuit) {
            synchronized (this.shortedNodes) {
                getOutputNodes(this.shortedNodes);
                this.shortedNodes.forEach(node -> node.highlight(Connectable.WARNING));
            }
        }
    }

    private void unhighlightShortCircuitNodes() {
        synchronized (this.shortedNodes) {
            if (!this.shortedNodes.isEmpty()) {
                try {
                    this.shortedNodes.forEach(node -> node.unhighlight(Connectable.WARNING));
                    this.shortedNodes.clear();
                } catch (NoSuchElementException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Kontrola skratu na výstupoch predkov. Predpokladá, že obaja predkovia sú výstupy.
     */
    private void checkShortCircuit() {
        if (this.parent1.value != Value.NC && this.parent2.value != Value.NC) {
            if (this.parent1.value != this.parent2.value) {
                this.shortCircuit = true;
                this.value = Math.random() < 0.5 ? Value.LOW : Value.HIGH;
                return;
            } else {
                this.value = this.parent1.value;
            }
        } else if (this.parent1.value != Value.NC) {
            this.value = this.parent1.value;
        } else if (this.parent2.value != Value.NC) {
            this.value = this.parent2.value;
        }

        this.shortCircuit = false;
    }

    private void getOutputNodes(List<Connectable> listForNodes) {
        if (this.parent1 == null && this.parent2 == null) {
            if (this.node1 != null && (this.type == PinType.OUT || this.type == PinType.IO)) {
                listForNodes.add(this.node1);
            }
            if (this.node2 != null && (this.type == PinType.OUT || this.type == PinType.IO)) {
                listForNodes.add(this.node2);
            }
        } else {
            if (this.parent1 != null) this.parent1.getOutputNodes(listForNodes);
            if (this.parent2 != null) this.parent2.getOutputNodes(listForNodes);
        }
    }
}
