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
import sk.uniza.fri.cp.SchematicSim.Electrical.Potential;
import sk.uniza.fri.cp.SchematicSim.Gates.GateSymbol;
import sk.uniza.fri.cp.SchematicSim.Gates.Label;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
    /** Posledná zaradená udalosť pre každý pin - staršie udalosti pinu vo fronte sa preskočia. */
    private final ConcurrentHashMap<Pin, SheetEvent> latestEventForPin = new ConcurrentHashMap<>();
    private List<GateSymbol> allGates;
    private final BooleanProperty running;

    private final Service<Void> simulatorService = new Service<Void>() {
        @Override
        protected Task<Void> createTask() {
            return new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    Thread.currentThread().setName("SchematicSimulationThread");
                    // neviditeľné vodiče medzi labelmi s rovnakým menom - iba počas tohto behu
                    List<Potential> labelLinks = null;

                    // celý beh simulácie je v try/finally - pri akejkoľvek chybe alebo rušení
                    // sa musí vždy odpojiť od zbernice, inak by CPU navždy čakalo na ustálenie
                    // dát (isSimulationRunning by ostalo true a waitForSteadyState by sa zacyklilo)
                    try {
                        setRunningQuietly(true);

                        // labely s rovnakým menom sa spoja neviditeľnými vodičmi ešte pred
                        // počiatočným vyhodnotením (počas simulácie je editovanie zakázané,
                        // takže sa siete do vypnutia nezmenia)
                        labelLinks = Label.connectLabels(allGates);

                        // pripojenie zbernice k simulátoru - CPU dostane znamenie, že nás môže počúvať
                        // idle sa zneplatní PRED ohlásením behu - inak by CPU (čakajúce na štart
                        // simulácie) videlo bežiacu simuláciu so starým idle=true a prečítalo by
                        // zbernicu ešte pred počiatočným vyhodnotením
                        Bus.getBus().setEventsQueue(eventsQueue);
                        Bus.getBus().simulationProcessingStarts(); // ešte nie je idle - počiatočné spracovanie
                        Bus.getBus().simulationIsRunning(true);

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
                        // jedna vlna (delta cyklus): vsetky udalosti, ktore su prave vo fronte.
                        // Hradla z vlny sa vyhodnotia RAZ; udalosti vytvorene simulaciou padnu
                        // do frontu az po odobrati vlny (drainTo) a tvoria vlnu nasledujucu.
                        List<SheetEvent> wave = new ArrayList<>();
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

                                // zvyšok aktuálnej vlny (delta cyklus) odober hneď - udalosti
                                // vytvorené simuláciou až po tomto bode tvoria vlnu nasledujúcu
                                wave.clear();
                                wave.add(event);
                                eventsQueue.drainTo(wave);

if (steadyState) {
                                // podujatie sa našlo - dáta sa menia, CPU nesmie čítať
                                // a hlavne: zneplatnime príznak idle, aby CPU neakceptovalo
                                // príliš skoré ustálenie (napr. pri burstovom fronte)
                                Bus.getBus().simulationProcessingStarts();
                                steadyState = false;
                            }

                                // najprv prepíš siete z celej vlny (process), potom vyhodnoť
                                // každé hradlo vlny RAZ - bez viacnásobného prepočtu v rámci vlny
                                gatesToUpdate.clear();
                                for (SheetEvent e : wave) {
                                    if (!isSuperseded(e)) e.process(gatesToUpdate);
                                }
                                gatesToUpdate.forEach(GateSymbol::simulate);
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
                            SheetEvent rest = eventsQueue.take();
                            if (isSuperseded(rest)) continue;
                            rest.process(gatesToUpdate);
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
                        // odstránenie neviditeľných vodičov ešte PRED ohlásením konca behu -
                        // nové zapnutie (čaká na running=false) ich tak nemôže prekryť
                        if (labelLinks != null) {
                            try {
                                Label.disconnectLabels(labelLinks);
                            } catch (RuntimeException e) {
                                e.printStackTrace(System.err);
                            }
                        }
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
        // asynchrónne zapojenie - staršia udalosť na tom istom pine už nie je aktuálna. Namiesto
        // removeIf nad celým frontom (O(n) a zamknutý front pri každej udalosti) sa iba zapamätá
        // posledná udalosť pinu; staršie sa preskočia až pri spracovaní (isSuperseded)
        if (event.getPin() != null) latestEventForPin.put(event.getPin(), event);
        // offer() namiesto put(): front je neohraničený, takže nikdy nečaká, a hlavne nie je
        // prerušiteľný - put() vo vlákne s nastaveným príznakom prerušenia (CPU zastavené počas
        // čakania na zbernicu) vyhodí InterruptedException a udalosť by sa potichu stratila
        // (napr. IR/=1 po zastavení by sa do schémy nedostal a signál by ostal v 0)
        eventsQueue.offer(event);
        // zneplatnenie idle ešte na vlákne, ktoré udalosť vložilo (napr. CPU pri IR/=0) - inak by
        // simulácia mohla udalosť vybrať z frontu (front prázdny, idle ešte true) a CPU by
        // v tom okne považovalo zbernicu za ustálenú a prečítalo staré/náhodné dáta
        Bus.getBus().simulationProcessingStarts();
    }

    /**
     * Zistí, či udalosť prekonala novšia udalosť na tom istom pine (vtedy sa nespracuje).
     * Atomické remove(pin, event) - ak novšia udalosť príde až po spracovaní tejto, ostane
     * v mape a spracuje sa tiež.
     */
    private boolean isSuperseded(SheetEvent event) {
        Pin pin = event.getPin();
        return pin != null && !latestEventForPin.remove(pin, event);
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
