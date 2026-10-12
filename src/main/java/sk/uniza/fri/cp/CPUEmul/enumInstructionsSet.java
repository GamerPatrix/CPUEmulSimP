package sk.uniza.fri.cp.CPUEmul;

import static sk.uniza.fri.cp.CPUEmul.Regexes.rByte;
import static sk.uniza.fri.cp.CPUEmul.Regexes.rLabel;
import static sk.uniza.fri.cp.CPUEmul.Regexes.rShort;

/**
 * Inštrukčná sada CPU Emulátora.
 *
 * @author Tomáš Hianik
 * @version 1.0
 * @created 07-feb-2017 18:40:27
 */
enum enumInstructionsSet{

    //artimeticke a logicke inst.
    ADD("(?i)A|B|C|D", "(?i)A|B|C|D"),
    ADC("(?i)A|B|C|D", "(?i)A|B|C|D"),
    ADI("(?i)A|B|C|D", rByte),
    SUB("(?i)A|B|C|D", "(?i)A|B|C|D"),
    SUC("(?i)A|B|C|D", "(?i)A|B|C|D"),
    SBI("(?i)A|B|C|D", rByte),
    AND("(?i)A|B|C|D", "(?i)A|B|C|D"),
    ANI("(?i)A|B|C|D", rByte),
    ORR("(?i)A|B|C|D", "(?i)A|B|C|D"),
    ORI("(?i)A|B|C|D", rByte),
    XOR("(?i)A|B|C|D", "(?i)A|B|C|D"),
    XRI("(?i)A|B|C|D", rByte),
    INC("(?i)A|B|C|D"),
    INX("(?i)S|M"),
    DEC("(?i)A|B|C|D"),
    DCX("(?i)S|M"),
    CMP("(?i)A|B|C|D", "(?i)A|B|C|D"),
    CMI("(?i)A|B|C|D", rByte),

    //inst. presunu a rotacie
    SHL("(?i)A|B|C|D", "[1-8]"),
    SHR("(?i)A|B|C|D", "[1-8]"),
    SCR("(?i)A|B|C|D", "[1-8]"),
    RTL("(?i)A|B|C|D", "[1-8]"),
    RCL("(?i)A|B|C|D", "[1-8]"),
    RTR("(?i)A|B|C|D", "[1-8]"),
    RCR("(?i)A|B|C|D", "[1-8]"),

    //inst. prenosu dat
    MOV("(?i)A|B|C|D", "(?i)A|B|C|D"),
    MVI("(?i)A|B|C|D", rByte),
    MXI("(?i)S|M", rShort),
    MVX("(?i)S|M|C", "(?i)A|S|M"),
    MMR("(?i)A|B|C|D", "(?i)A|B|C|D"),
    LMI("(?i)A|B|C|D", rShort),
    LMR("(?i)A|B|C|D"),
    SMI(rShort, "(?i)A|B|C|D"),
    SMR("(?i)A|B|C|D"),
    INN("(?i)A|B|C|D", rShort),
    OUT(rShort, "(?i)A|B|C|D"),
    PUS("(?i)A|B|C|D|F|M"),
    POP("(?i)A|B|C|D|F|M"),
    STR("(?i)A|B|C|D", "(?i)A|B|C|D"),
    LDR("(?i)A|B|C|D", "(?i)A|B|C|D"),

    //inst. vetvenia
    JMP(rLabel, true),
    JZR(rLabel, true),
    JNZ(rLabel, true),
    JCY(rLabel, true),
    JNC(rLabel, true),
    JL(rLabel, true),
    JG(rLabel, true),
    JE(rLabel, true),
    JLE(rLabel, true),
    JGE(rLabel, true),
    JNE(rLabel, true),
    JNL(rLabel, true),
    JNG(rLabel, true),
    CAL(rLabel, true),
    CZR(rLabel, true),
    CNZ(rLabel, true),
    CCY(rLabel, true),
    CNC(rLabel, true),
    CL(rLabel, true),
    CG(rLabel, true),
    CE(rLabel, true),
    CLE(rLabel, true),
    CGE(rLabel, true),
    CNE(rLabel, true),
    CNL(rLabel, true),
    CNG(rLabel, true),
    RET(),

    //specialne instrukcie
    EIT(),DIT(),
    SCALL("(?i)KPR|KEY|DSP"),
    BYTE(rByte);

    /**
     * Počet parametrov inštrukcie
     */
    private int numOfParameters;
    private String firstRegex; //regex pre prvý paremeter
    private String secondRegex; //regex pre druhý parameter
    private boolean usesLabel; //indikátor použitia návestia v inštrukcií

    enumInstructionsSet(){
        numOfParameters = 0;
    }

    enumInstructionsSet(String firstArg){
        numOfParameters = 1;
        firstRegex = firstArg;
    }

    enumInstructionsSet(String firstArg, boolean usesLabel){
        numOfParameters = 1;
        firstRegex = firstArg;
        this.usesLabel = usesLabel;
    }

    enumInstructionsSet(String firstArg, String secondArg){
        numOfParameters = 2;
        firstRegex = firstArg;
        secondRegex = secondArg;
    }

    /**
     * Vracia počet parametrov inštrukcie.
     *
     * @return Počet parametrov inštrukcie.
     */
    public int getNumOfParameters() {
        return numOfParameters;
    }

    /**
     * Regex výraz pre prvý parameter inštrukcie.
     *
     * @return Regex výraz pre prvý parameter.
     */
    public String getFirstRegex() {
        return firstRegex;
    }

    /**
     * Regex výraz pre druhý parameter inštrukcie.
     *
     * @return Regex výraz pre prvý parameter.
     */
    public String getSecondRegex() {
        return secondRegex;
    }

    /**
     * Používa inštrukcia návestie?
     *
     * @return True ak inštrukcia používa navestie, false inak.
     */
    public boolean usesLabel() {
        return usesLabel;
    }
}