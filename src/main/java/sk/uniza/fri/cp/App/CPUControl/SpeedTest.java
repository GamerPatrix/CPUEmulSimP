package sk.uniza.fri.cp.App.CPUControl;

import javafx.application.Platform;
import sk.uniza.fri.cp.Bus.Bus;
import sk.uniza.fri.cp.CPUEmul.CPU;
import sk.uniza.fri.cp.CPUEmul.Parser;
import sk.uniza.fri.cp.CPUEmul.Program;
import sk.uniza.fri.cp.SchematicSim.Gates.GateSymbol;
import sk.uniza.fri.cp.SchematicSim.Gates.Input;
import sk.uniza.fri.cp.SchematicSim.Pin.Pin;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSheet;
import sk.uniza.fri.cp.SchematicSim.Sheet.SchematicSimulator;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

/**
 * Test rýchlosti vykonávania programu a simulácie zapojenia, trvá približne 3 sekundy.
 * <p>
 * Inštrukcie vykonáva samostatná inštancia CPU so skutočnou zbernicou a so simuláciou aktuálneho
 * zapojenia, ako pri bežnom programe. Bez zapojenia beží simulácia prázdnej plochy.
 * Simulácia sa meria ako najhorší prípad - počet prepočtov celej schémy za sekundu, ak by jedna
 * zmena signálu vyvolala prepočet všetkých súčiastok. Stav prepínačov sa nemení, zápisy CPU
 * však môžu zmeniť obsah RAM a registrov v zapojení.
 */
final class SpeedTest {

    /** Výsledok testu; pri chybe je vyplnené iba {@link #error}. */
    static final class Result {
        final long instructionsPerSecond;
        final long busInstructionsPerSecond;
        final long simulationsPerSecond;
        final int gateCount;
        final String error;

        private Result(long instructions, long busInstructions, long simulations, int gateCount, String error) {
            this.instructionsPerSecond = instructions;
            this.busInstructionsPerSecond = busInstructions;
            this.simulationsPerSecond = simulations;
            this.gateCount = gateCount;
            this.error = error;
        }
    }

    private static final long TOTAL_MS = 3000;
    /** Začiatok každej fázy sa nemeria - kód sa ešte iba rozbieha (JIT kompilácia). */
    private static final long WARMUP_MS = 100;
    private static final long SIMULATION_START_TIMEOUT_MS = 2000;
    private static final long STOP_TIMEOUT_MS = 1000;
    private static final long SETTLE_TIMEOUT_MS = 200;

    private static final String NON_BUS_PROGRAM = "loop:\n"
            + " INC A\n ADD B,A\n MOV C,B\n XOR D,C\n ADI A,1\n CMP A,B\n PUS A\n POP C\n JMP loop\n";
    private static final String BUS_PROGRAM = "loop:\n"
            + " OUT 0,A\n INN B,0\n SMI 0,A\n LMI B,0\n JMP loop\n";

    private static final OutputStream NO_CONSOLE = new OutputStream() {
        @Override
        public void write(int b) {
        }
    };

    private SpeedTest() {
    }

    /**
     * Spustenie testu na pozadí. Volá sa na FX vlákne, naň sa odovzdá aj výsledok.
     * Simulácia, ktorú zapol až test, sa po skončení opäť vypne.
     */
    static void start(SchematicSheet sheet, Consumer<Result> onDone) {
        boolean simulationWasRunning = sheet.isSimulationRunning();
        List<GateSymbol> gates = new ArrayList<>(sheet.getGates());
        if (!simulationWasRunning) sheet.powerOn();

        Thread thread = new Thread(() -> {
            Result result = run(sheet, gates);
            if (!simulationWasRunning) sheet.powerOff();
            Platform.runLater(() -> onDone.accept(result));
        }, "SpeedTest");
        thread.setDaemon(true);
        thread.start();
    }

    private static Result run(SchematicSheet sheet, List<GateSymbol> gates) {
        try {
            if (!Bus.getBus().waitForSimulationStart(SIMULATION_START_TIMEOUT_MS)) {
                return failed("Simuláciu sa nepodarilo spustiť.");
            }

            boolean hasCircuit = !gates.isEmpty();
            long phaseMs = TOTAL_MS / (hasCircuit ? 3 : 2);
            long instructions = measureProgram(NON_BUS_PROGRAM, phaseMs, CPU::getExecutedInstructions);
            long busInstructions = measureProgram(BUS_PROGRAM, phaseMs, CPU::getExecutedBusInstructions);
            long simulations = hasCircuit ? measureSimulation(sheet, gates, phaseMs) : 0;
            return new Result(instructions, busInstructions, simulations, gates.size(), null);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return failed(e.toString());
        }
    }

    private static Result failed(String error) {
        return new Result(0, 0, 0, 0, error);
    }

    /**
     * Beh programu na samostatnej inštancii CPU - obrazovka ani stav hlavného CPU sa ho netýkajú.
     */
    private static long measureProgram(String code, long phaseMs, ToLongFunction<CPU> counter) throws Exception {
        Parser parser = new Parser(code, (int) code.chars().filter(c -> c == '\n').count());
        parser.run();
        Program program = parser.get();

        CPU cpu = new CPU(NO_CONSOLE, Bus.getBus());
        cpu.setDaemon(true);
        cpu.start();
        cpu.loadProgram(program);
        cpu.startExecute(false);
        try {
            return measureRate(phaseMs, () -> counter.applyAsLong(cpu));
        } finally {
            // prerušenie uvoľní CPU aj zo zapojenia, ktoré sa nikdy neustáli
            cpu.stopExecute();
            cpu.cancel();
            cpu.join(STOP_TIMEOUT_MS);
        }
    }

    /**
     * Najhorší prípad: koľkokrát za sekundu je možné po zmene jedného signálu prepočítať všetky
     * súčiastky schémy. Signálom je výstup ďalšieho prepínača, bez prepínačov bit dátovej zbernice.
     * Riadiace signály zbernice ostávajú neaktívne a prepínače si držia svoj stav - po meraní
     * sa ich výstupy obnovia.
     */
    private static long measureSimulation(SchematicSheet sheet, List<GateSymbol> gates, long phaseMs) throws Exception {
        List<Input> inputs = new ArrayList<>();
        for (GateSymbol gate : gates) {
            if (gate instanceof Input) inputs.add((Input) gate);
        }

        Bus bus = Bus.getBus();
        int[] next = new int[1];
        Runnable signalChange = () -> {
            if (inputs.isEmpty()) {
                bus.setDataBus((byte) (bus.getDataBus() ^ 1));
            } else {
                Input input = inputs.get(next[0]++ % inputs.size());
                Pin output = input.getPins().get(0);
                input.setPin(output, output.getState() == Pin.PinState.HIGH ? Pin.PinState.LOW : Pin.PinState.HIGH);
            }
        };

        SchematicSimulator simulator = sheet.getSimulator();
        simulator.startFullPassMeasurement(signalChange);
        try {
            return measureRate(phaseMs, simulator::getFullPasses);
        } finally {
            simulator.stopFullPassMeasurement();
            inputs.forEach(Input::simulate);
            waitForSteadyState(bus);
        }
    }

    /**
     * Doznenie udalostí po meraní, aby sa simulácia nevypínala s plným frontom. Zapojenie,
     * ktoré sa nikdy neustáli, sa nečaká dlhšie ako {@link #SETTLE_TIMEOUT_MS}.
     */
    private static void waitForSteadyState(Bus bus) throws InterruptedException {
        Thread waiter = new Thread(bus::waitForSteadyState, "SpeedTestSettle");
        waiter.setDaemon(true);
        waiter.start();
        waiter.join(SETTLE_TIMEOUT_MS);
        waiter.interrupt();
    }

    private static long measureRate(long phaseMs, LongSupplier counter) throws InterruptedException {
        Thread.sleep(WARMUP_MS);
        long startCount = counter.getAsLong();
        long startTime = System.nanoTime();
        Thread.sleep(phaseMs - WARMUP_MS);
        long count = counter.getAsLong() - startCount;
        long elapsed = System.nanoTime() - startTime;
        return Math.round(count * 1e9 / elapsed);
    }
}
