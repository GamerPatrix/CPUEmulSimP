package sk.uniza.fri.cp.Bus;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Zbernica zabezpečujúca komunikáciu medzi CPU a doskou (fyzickou / simulovanou).
 * Uchováva aktuálne hodnoty na adresnej, dátovej a riadiacej zbernici.
 * Singleton
 *
 * @author Tomáš Hianik
 * @version 1.0
 * @created 07-feb-2017 18:40:27
 */
public class Bus{
	private static Bus instance; //inštancia singletonu
    private Semaphore dataSemaphore; //semafor pre cakanie na nastavenie dat simulatorom
    private boolean isSimulationRunning; //je spustena simulacia?

	private IntegerProperty addressBus;
	private IntegerProperty dataBus;
	private IntegerProperty controlBus;

	private Random rand;

	private Bus(){
		this.addressBus = new SimpleIntegerProperty(0);
		this.dataBus = new SimpleIntegerProperty(0);
		this.controlBus = new SimpleIntegerProperty(0);
        this.dataSemaphore = new Semaphore(0);

		rand = new Random();

        initControlBus();
	}

    /**
     * Prístup k inštancií singletonu. (synchronizované)
     * 
     * @return Inśtancia zbernice.
     */
	synchronized public static Bus getBus(){
		if(instance == null){
			instance = new Bus();
		}

		return instance;
	}

    /**
     * Informácia, či je zbernica pripojená k USB.
     * 
     * @return True ak je pripojená cez USB, false inak.
     */

    public IntegerProperty addressBusProperty(){
        return addressBus;
    }

    public IntegerProperty dataBusProperty(){
        return dataBus;
    }

    public IntegerProperty controlBusProperty(){
        return controlBus;
    }

    /**
     * Resetovanie stavu riadiacej zbernice, nastavenie náhodnej adresy a dát.
     */
	synchronized public void reset(){
		setRandomAddress();
		setRandomData();
		initControlBus();
	}

    /**
     * Vrátenie hodnoty na adresnej zbernici.
     * 
     * @return hodnota na adresnej zbernici.
     */
	synchronized public short getAddressBus() {
        return addressBus.getValue().shortValue();
	}

    /**
     * Vrátenie hodnoty na dátovej zbernici.
     * 
     * @return Hodnota na dátovej zbernici.
     */
    synchronized public byte getDataBus() {
        return dataBus.getValue().byteValue();
    }

    /**
     * Vrátenie hodnoty na riadiacej zbernici.
     * 
     * @return Hodnota na riadiacej zbernici.
     */
    public int getControlBus() {
        return controlBus.getValue();
    }

    /**
     * Nastavenie hodnoty na adresnú zbernicu.
     * 
     * @param addressBus Nová hodnota adresnej zbernice.
     */
    synchronized public void setAddressBus(short addressBus) {
        this.addressBus.setValue(Short.toUnsignedInt(addressBus));
    }

    /**
     * Nastavenie hodnoty na dátovú zbernicu.
     * 
     * @param dataBus Nová hodnota dátovej zbernice.
     */
    public void setDataBus(byte dataBus) {
        this.dataBus.setValue(Byte.toUnsignedInt(dataBus));
	}

    /**
     * Nastavenie náhodnej adresnej zbernice.
     */
	synchronized public void setRandomAddress(){
        this.addressBus.setValue(rand.nextInt(65535));
	}

    /**
     * Nastaveni náhodnej dátovej zbernice.
     */
	synchronized public void setRandomData(){
        this.dataBus.setValue(rand.nextInt(256));
	}

    /**
     * Pasívne čakanie na ustálenie simulácie. Čaká, kým simulačné vlákno neoznámi,
     * že nie je činné (prázdny front udalostí spracovaný, nič mu nezostalo),
     * prípadne 30 s bez života simulácie - vtedy berie simuláciu ako zlyhanú.
     *
     * @return True ak je simulácia ustálená, false inak (simulácia nebeží alebo je zaseknutá).
     */
    public boolean waitForSteadyState() {
        if (Thread.currentThread().getName().equalsIgnoreCase("SimulationThread")) return true;

        long waitStart = System.currentTimeMillis();
        synchronized (steadyMonitor) {
            while (isSimulationRunning) {
                // ustálené = front udalostí je prázdny A simulácia nie je práve v činnosti
                // (čaká na take() - nemá čo spracúvať). Toto platí aj pre zápis CPU, ktorý
                // vyrobil nula udalostí (vývody už v želanom stave) - predtým na ňom
                // starý semafor (odčerpané povolenia) naveky visel.
                if (queue.size() == 0 && simulationIdle) return true;

                // simulácia stále spracúva zvyšok udalostí - počkáme si na jej signál
                if (!simulationAlive()) {
                    reportSteadyFailure("simulacia mŕtva/ticho príliš dlho",
                            System.currentTimeMillis() - waitStart, queue.size());
                    return false;
                }

                try {
                    steadyMonitor.wait(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            reportSteadyFailure("simulacia nebezi", 0, -1);
            return false;
        }
    }

    /** Bezpečnostná poistka: ak simulačné vlákno neprešlo slučkou viac ako 30 s, berieme ho ako mŕtve. */
    private boolean simulationAlive() {
        long heartbeat = this.simLoopHeartbeatMs;
        return heartbeat == 0 || (System.currentTimeMillis() - heartbeat) < 30000;
    }

    private long lastSteadyDiagPrint = 0;

    /** Heartbeat simulácie - čas (ms) posledného prechodu simulačnou slučkou (na diagnostiku). */
    private volatile long simLoopHeartbeatMs = 0;

    public void reportSimLoopActivity() {
        this.simLoopHeartbeatMs = System.currentTimeMillis();
    }

    private long simIdleMs() {
        long pulse = this.simLoopHeartbeatMs;
        return pulse == 0 ? -1 : System.currentTimeMillis() - pulse;
    }

    private void reportSteadyFailure(String reason, long waitedMs, int queueSize) {
        // diagnostika - najviac 1x za 2 s, aby nezahlcoval konzolu
        long now = System.currentTimeMillis();
        if (now - lastSteadyDiagPrint > 2000) {
            lastSteadyDiagPrint = now;
            System.err.println("[Bus] waitForSteadyState=false (" + reason
                    + " | cakal ms: " + waitedMs
                    + " | fronte: " + queueSize
                    + " | voľné permity: " + dataSemaphore.availablePermits()
                    + " | simulationIsRunning=" + isSimulationRunning
                    + " | sim slucka nebezi uz ms: " + simIdleMs() + ")");
            // výpis stacktrace vlákna simulácie - ukáže, kde je zaseknuté
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if ("SchematicSimulationThread".equals(t.getName())) {
                    System.err.println("[Bus] StackTrace vlákna simulácie:");
                    for (StackTraceElement el : t.getStackTrace()) {
                        System.err.println("    at " + el);
                    }
                }
            }
        }
    }

    private LinkedBlockingQueue<?> queue;

    public void setEventsQueue(LinkedBlockingQueue<?> queue) {
        this.queue = queue;
    }

    private final Object steadyMonitor = new Object();

    /** Pravda = simulačné vlákno nemá žiadnu nedokončenú prácu (čaká na take(), front je prázdny). */
    private volatile boolean simulationIdle;

    /**
     * Oznámevnie zbernici, že dáta boli ustálené a je možné ich čítať.
     * Volá simulačné vlákno, keď vyprázdnilo front a ide si sadnúť na čakanie.
     */
    public void dataInSteadyState() {
        synchronized (steadyMonitor) {
            // medzičasom pribudla udalosť (napr. od CPU) - ešte nie sme ustálení
            if (queue != null && !queue.isEmpty()) return;
            simulationIdle = true;
            steadyMonitor.notifyAll();
        }
    }

    /**
     * Oznam simulácie, že začína spracúvať udalosť - simulácia nie je ustálená,
     * CPU musí počkať. Volá simulačné vlákno pred každým spracovaním udalosti.
     */
    public void simulationProcessingStarts() {
        synchronized (steadyMonitor) {
            simulationIdle = false;
        }
    }

    /**
     * Oznámevnie zbernici, že prebiehajú zmeny, ktoré môžu ovplyvniť dáta na dátovej zbernici a nie je teda bezpečné
     * z nej čítať.
     * <p>
     * Zámerne nevykonáva nič kritické: volá sa z CPU (DataBus8.handleControlChange) počas zápisov,
     * kedy by zneplatnenie nečinnosti simulácie spôsobilo nekončiace čakanie pri zápisoch s nulovými
     * udalosťami (vývody už v želanom stave). Či je simulácia ustálená, oznamuje samotné simulačné
     * vlákno cez {@link #dataInSteadyState()}/{@link #simulationProcessingStarts()}.
     */
    public void dataIsChanging() {
        // zámerne prázdna metóda - pozri poznámku vyššie
    }

    /**
     * Oznámenie simulácie o zmene stavu. Či je spustená a má CPU čakať na nastavenie dát alebo nie je spustená.
     *
     * @param isRunning Stav simulácie, true ak je spustená, false inak.
     */
    public void simulationIsRunning(boolean isRunning) {
        this.isSimulationRunning = isRunning;
    }

    /**
     * Nastavenie negovaného signálu MW - memory write.
     * 
     * @param MW_ Nová hodnota signálu MW.
     */
    synchronized public void setMW_(boolean MW_) {
        mapToControlBus("MW/", MW_);
    }

    /**
     * Nastavenie negovaného signálu MR - memory read.
     * 
     * @param MR_ Nová hodnota signálu MR.
     */
    synchronized public void setMR_(boolean MR_) {
        //this.dataBus.setValue(0); //kvazi odpojenie od zbernice
        mapToControlBus("MR/", MR_);
    }
    
    /**
     * Nastavenie negovaného signálu IW - input write.
     *
     * @param IW_ Nová hodnota signálu IW.
     */
    synchronized public void setIW_(boolean IW_) {
        mapToControlBus("IW/", IW_);
    }

    /**
     * Nastavenie negovaného signálu IR - input read.
     *
     * @param IR_ Nová hodnota signálu IR.
     */
    synchronized public void setIR_(boolean IR_) {
        //this.dataBus.setValue(0); //kvazi odpojenie od zbernice
        mapToControlBus("IR/", IR_);
    }

    /**
     * Nastavenie negovaného signálu IA - interruption acknowledge.
     *
     * @param IA_ Nová hodnota signálu IA.
     */
    synchronized public void setIA_(boolean IA_) {
         mapToControlBus("IA/", IA_);
    }

    /**
     * Nastavenie signálu IT - interruption.
     *
     * @param IT Nová hodnota signálu IT.
     */
    public void setIT(boolean IT) {
        mapToControlBus("IT", IT);
    }

    /**
     * Nastavenie signálu RY - ready.
     *
     * @param RY Nová hodnota signálu RY.
     */
    public void setRY(boolean RY) {
        mapToControlBus("RY", RY);
    }

    /**
     * Zistenie hodoty signálu IT - interruption.
     * 
     * @return Hodnota signálu IT.
     */
	synchronized public boolean isIT() {
        return mapFromControlBus("IT");
	}

    /**
     * Inicializácia riadiacej zbernice do východzích hodnôt.
     */
	private void initControlBus(){
        mapToControlBus("MW/", true);
        mapToControlBus("MR/", true);
        mapToControlBus("IW/", true);
        mapToControlBus("IR/", true);
        mapToControlBus("IA/", true);

        mapToControlBus("IT", false);
        mapToControlBus("RY", false);
        mapToControlBus("BQ", false);
        mapToControlBus("BA", false);
    }

    /**
     * Mapovanie hodnoty signálu podľa návzu signálu do riadiacej zbernice.
     * 
     * @param signal Názov signálu.
     * @param value Nová hodnota signálu.
     */
    private void mapToControlBus(String signal, boolean value) {
        int pos = mapSignal(signal);

		if(value)
			controlBus.setValue( controlBus.getValue() | ( 1<<pos ) );
		else
			controlBus.setValue( controlBus.getValue() & ~( 1<<pos ) );
	}

    /**
     * Mapovanie hodnoty signálu podľa návzu signálu z riadiacej zbernice.
     *
     * @param signal Názov signálu.
     * @return Aktuálna hodnota signálu na zbernici.
     */
    synchronized private boolean mapFromControlBus(String signal){
        int pos = mapSignal(signal);

        return (controlBus.getValue() & 1<<pos ) != 0;
    }

    /**
     * Prevodník medzi názvom signálu a jemu odpovedajúcim bitom na riadiacej zbernici.
     *
     * @param signal Názov signálu.
     * @return Odpovedajúci bit na riadiacej zbernici.
     */
    private int mapSignal(String signal){
        switch (signal){
            case "MW/": return 8;
            case "MR/": return 7;
            case "IW/": return 6;
            case "IR/": return 5;
            case "IA/": return 4;
            case "IT": return 3;
            case "RY": return 2;
            case "BQ": return 1;
            case "BA": return 0;
        }

        return -1;
    }

    //
//    synchronized public boolean isMW_() {
//		return mapFromControlBus("MW/");
//	}
//
//	synchronized public boolean isMR_() {
//		return mapFromControlBus("MR/");
//	}
//
//	synchronized public boolean isIW_() {
//		return mapFromControlBus("IW/");
//	}
//
//	synchronized public boolean isIR_() {
//		return mapFromControlBus("IR");
//	}
//
    public boolean isIA_() {
        return mapFromControlBus("IA/");
    }
//    
//    /**
//     * Zistenie hodoty signálu RY - ready
//     *
//     * @return Hodnota signálu RY.
//     */
//    synchronized public boolean isRY() {
//            return mapFromControlBus("RY");
//    }


}//end Bus