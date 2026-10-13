package sk.uniza.fri.cp.SchematicSim.Sheet;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import sk.uniza.fri.cp.Bus.Bus;
import sk.uniza.fri.cp.SchematicSim.Buses.BusSymbol;
import sk.uniza.fri.cp.SchematicSim.Gates.GateSymbol;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Simulačné jadro. Nahrádza {@code BoardSimulator} z BreadboardSim - narozdiel od neho
 * pri spustení nemá čo "napájať" (žiadne PowerSocket/VCC ako pri reálnych 74xx čipoch),
 * namiesto toho pri štarte jednoducho vyhodnotí všetky súčiastky raz od nuly, aby sa
 * ustálili počiatočné hodnoty výstupov, a ďalej beží identická event-driven slučka
 * ako v origináli.
 *
 * @author Tomáš Hianik (pôvodný autor BoardSimulator), adaptácia pre SchematicSim
 */
public class SchematicSimulator {

    private static final int ASYNCH_TIMEOUT_MS = 5000;

    /** Čas (ms), ako dlho čaká simulačná slučka na udalosť, kým vyhlási ustálený stav. */
    private static final int IDLE_POLL_TIMEOUT_MS = 5;

    private final LinkedBlockingQueue<SheetEvent> eventsQueue;
    private List<GateSymbol> allGates;
    private final BooleanProperty running;

    private final Service<Void> simulatorService = new Service<Void>() {
        @Override
        protected Task<Void> createTask() {
            return new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    Thread.currentThread().setName("SchematicSimulationThread");

                    // celý beh simulácie je v try/finally - pri akejkoľvek chybe alebo rušení
                    // sa musí vždy odpojiť od zbernice, inak by CPU navždy čakalo na ustálenie
                    // dát (isSimulationRunning by ostalo true a waitForSteadyState by sa zacyklilo)
                    try {
                        setRunningQuietly(true);

                        // pripojenie zbernice k simulátoru - CPU dostane znamenie, že nás môže počúvať
                        Bus.getBus().setEventsQueue(eventsQueue);
                        Bus.getBus().simulationIsRunning(true);
                        Bus.getBus().simulationProcessingStarts(); // ešte nie je idle - počiatočné spracovanie

                        // počiatočné vyhodnotenie - nahrádza powerSockets.forEach(PowerSocket::powerUp)
                        allGates.forEach(GateSymbol::reset);
                        allGates.forEach(GateSymbol::simulate);

                        // obnova hodnôt na zberniciach na vývody (pre prípad opätovného zapnutia simulácie)
                        for (GateSymbol gate : allGates) {
                            if (gate instanceof BusSymbol) ((BusSymbol) gate).syncFromBus();
                        }

                        // LinkedHashSet (nie HashSet) - poradie simulovania hradiel pri viacvstupovej
                        // fan-out udalosti musi byt deterministicke (podla poradia zaevidovania),
                        // inak sa sukciastky vedia prepočítat v nahodnom poradi a na zbernici
                        // sa objavia prechodne (glitchove) stavy
                        LinkedHashSet<GateSymbol> gatesToUpdate = new LinkedHashSet<>();
                        SheetEvent event;
                        long lastActivityTime = System.currentTimeMillis();
                        boolean steadyState = false;

                        while (!isCancelled()) {
                            try {
                                // heart-beat pre diagnostiku - Bus vie povedať, či simulačná slučka
                                // počas čakania CPU na ustálenie naozaj bežala, alebo bola zablokovaná
                                Bus.getBus().reportSimLoopActivity();

                                event = eventsQueue.poll(IDLE_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                                if (event == null) {
                                    if (eventsQueue.size() > 0) {
                                        event = eventsQueue.take();
                                    } else {
                                        // simulácia je ustálená - CPU môže bezpečne čítať dáta zo zbernice
                                        // ale niektoré súčiastky (napr. 7-segmentové hold-timeouty) potrebujú
                                        // periodické vyhodnotenie aj bez novej elektrickej udalosti.
                                        allGates.forEach(GateSymbol::simulate);
                                        Bus.getBus().dataInSteadyState();
                                        steadyState = true;
                                        lastActivityTime = System.currentTimeMillis();
                                        continue;
                                    }
                                }

                                if (System.currentTimeMillis() - lastActivityTime > ASYNCH_TIMEOUT_MS) {
                                    // kľud v simulácii (CPU načúva, nič sa nemení) je NORMÁLNY stav -
                                    // watchdog ruší simuláciu iba pri skutočnom zablokovaní (plný front),
                                    // inak by pri dlhšej pauze (krokovanie, zastavený program) zabil živú
                                    // simuláciu a CPU by začalo hlásiť chybu zbernice
                                    if (eventsQueue.size() > 0) {
                                        System.err.println("[Sim] RUŠÍM simuláciu - príliš dlhá odozva "
                                                + "(posledná aktivita pred " + (System.currentTimeMillis() - lastActivityTime)
                                                + " ms, front má " + eventsQueue.size() + " udalostí)");
                                        Platform.runLater(() -> {
                                            Alert alert = new Alert(Alert.AlertType.ERROR,
                                                    "Chyba simulácie. Príliš dlhá odozva.", ButtonType.CLOSE);
                                            alert.show();
                                        });
                                        this.cancel();
                                        break;
                                    } else {
                                        lastActivityTime = System.currentTimeMillis();
                                    }
                                }

if (steadyState) {
                                // podujatie sa našlo - dáta sa menia, CPU nesmie čítať
                                // a hlavne: zneplatnime príznak idle, aby CPU neakceptovalo
                                // príliš skoré ustálenie (napr. pri burstovom fronte)
                                Bus.getBus().simulationProcessingStarts();
                                steadyState = false;
                            }

                                event.process(gatesToUpdate);
                                gatesToUpdate.forEach(GateSymbol::simulate);
                                gatesToUpdate.clear();
                                lastActivityTime = System.currentTimeMillis();

                                // okamžité ohlásenie ustálenia - CPU nemusí čakať na ďalší idle poll.
                                // Bezpečné aj pri burstovom fronte: waitForSteadyState požaduje zároveň
                                // prázdny front (queue.size()==0) aj idle, a nasledujúca udalosť
                                // (ak príde) zneplatní idle cez simulationProcessingStarts().
                                if (eventsQueue.isEmpty()) {
                                    Bus.getBus().dataInSteadyState();
                                    steadyState = true;
                                }

                            } catch (InterruptedException e) {
                                if (isCancelled()) break;
                            } catch (RuntimeException e) {
                                // jeden chybny event nesmie zhltit celu simulaciu - inak by CPU
                                // navzdy cakalo na ustalenie zbernice (isSimulationRunning ostane true)
                                e.printStackTrace(System.err);
                                gatesToUpdate.clear();
                            }
                        }

                        // zostatok udalosti po rušení - označiť, že simulácia ešte spracúva,
                        // aby CPU vedelo, že dátová zbernica nie je ustálená
                        Bus.getBus().simulationProcessingStarts();

                        // zostatok udalosti po rušení - vyhodnot, aby piny nezostali v nekonzistentnom stave
                        while (!eventsQueue.isEmpty()) {
                            eventsQueue.take().process(gatesToUpdate);
                            gatesToUpdate.forEach(GateSymbol::simulate);
                            gatesToUpdate.clear();
                        }

                        allGates.forEach(GateSymbol::reset);
                    } catch (Throwable t) {
                        // zlyhanie simulácie sa nikdy nesmie prehltnúť - vždy je vidieť príčinu
                        System.err.println("[Sim] Chyba simulácie: " + t);
                        t.printStackTrace(System.err);
                    } finally {
                        System.err.println("[Sim] Koniec simulácie (isCancelled=" + isCancelled() + ")");
                        try {
                            setRunningQuietly(false);
                        } finally {
                            Bus.getBus().simulationIsRunning(false);
                        }
                    }
                    return null;
                }
            };
        }
    };

    SchematicSimulator() {
        this.eventsQueue = new LinkedBlockingQueue<>();
        this.running = new SimpleBooleanProperty(false);

        simulatorService.setOnFailed(event -> {
            if (simulatorService.getException() != null) simulatorService.getException().printStackTrace(System.err);
        });
    }

    /**
     * Spustenie simulácie.
     *
     * @param allGates Všetky súčiastky na ploche - vyhodnotia sa raz na začiatok simulácie.
     */
    public void start(List<GateSymbol> allGates) {
        this.allGates = allGates;
        simulatorService.restart();
    }

    public void stop() {
        if (Platform.isFxApplicationThread()) simulatorService.cancel();
        else Platform.runLater(simulatorService::cancel);
    }

    public void addEvent(SheetEvent event) {
        try {
            // asynchrónne zapojenie - odstránenie eventu na pine, ktorý už nie je aktuálny
            eventsQueue.removeIf(e -> e.getPin() != null && e.getPin().equals(event.getPin()));
            eventsQueue.put(event);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Nastavenie vlastnosti behu. Zmena vlastnosti vyvolá listenery - nesmú však zhodiť
     * cez vlastný RuntimeException celý simulačný task (vlastnosť sa mení na simulačnom vlákne).
     */
    private void setRunningQuietly(boolean value) {
        try {
            this.running.setValue(value);
        } catch (RuntimeException ignored) {
            ignored.printStackTrace(System.err);
        }
    }

    public BooleanProperty runningProperty() {
        return this.running;
    }
}
