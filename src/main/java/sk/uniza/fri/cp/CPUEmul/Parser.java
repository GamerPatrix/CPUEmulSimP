package sk.uniza.fri.cp.CPUEmul;

import javafx.concurrent.Task;
import sk.uniza.fri.cp.CPUEmul.Exceptions.InvalidCodeLinesException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trieda pre vytvorenie vlákna na parsovanie kódu do inštrukcií procesora.
 *
 * @author Tomáš Hianik
 * @version 1.0
 * @created 07-feb-2017 18:40:27
 */
public class Parser extends Task<Program>{

    private static final String LABEL_PATTERN = "^\\s*" + Regexes.rLabel + ":"; //identifikátor (nesmie začínať číslicou) a dvojbodka na začiatku riadku
    private static final String COMMENT_PATTERN = "(?m)(?:(?<!');|(?<=');(?!')).*$"; //vsetko za bodkociarkou po koniec riadku (okrem ';' ako znaku v uvodzovkach)

    //KOD
    private String code;
    private boolean hasError; //je v kode chyba?
    private int numOfCodeLines;

    //PROGRAM
    private ArrayList<Instruction> instructions;
    private TreeMap<Integer, Integer> lineIndexToInstructionIndex; //prevodnik medzi riadkom a instrukciou
    private ArrayList<Byte> progMemory;
    private TreeMap<Integer, Integer> interruptionLabels; //<cislo prerusenia, adresa instrukcie>

    //POMOCNE
    //TreeMap pre zapisanie navestia a indexu instrukcie na ktoru odkazuje
    private TreeMap<String, Integer> labels = new TreeMap<>();
    //pole pre uchovanie indexov instrukcii, ktore obsahuju navestie pre jeho nahradenie indexom skoku
    private ArrayList<Integer> instructionsWithLabels;

    //CHYBY
    //uchovavanie chyb pre vypis na konzolu
    private ArrayList<String> errors;
    private ArrayList<Integer> errorLines;

    /**
     * Vytvorenie vlákna pre parsovanie kódu.
     *
     * @param code Kód, ktorý sa má parsovať na inštrukcie programu
     * @param numberOfCodeLines Počet riadkov kódu pre približný odhad postupu pri parsovaní
     */
	public Parser(String code, int numberOfCodeLines){
        this.code = code;
        this.numOfCodeLines = numberOfCodeLines;
        this.instructions = new ArrayList<>(numberOfCodeLines);
        this.instructionsWithLabels = new ArrayList<>();
        this.progMemory = new ArrayList<>();
        this.lineIndexToInstructionIndex = new TreeMap<>();
        this.interruptionLabels = new TreeMap<>();

        this.hasError = false;
	}

    /**
     * Funkcia prijíma reťazec a kontroluje, či obsahuje inštrukciu, ktorá ale nemusi byť v správnom tvare
     *
     * @param line Reťazec s inštrukciou
     * @return True - riadok obsahuje inštrukciu
     */
    public static boolean isInstrucionLine(String line){
        //odstranenie komentarov
        Pattern patternComment = Pattern.compile(COMMENT_PATTERN);
        line = patternComment.matcher(line).replaceAll("");
        if(line.isEmpty()) return false;

        //odstranenie navestia
        Pattern patternLabel = Pattern.compile(LABEL_PATTERN);
        line = patternLabel.matcher(line).replaceAll("");
        if(line.isEmpty()) return false;

        line = line.trim();

        //pokus sa rozdelit instrukciu a parametre
        String[] instructionParts = line.split("\\s*,\\s*|\\s+"); //rozdelenie instrukcie (medzery a ciarka)

        //je instrukcia platna?
        try {
            enumInstructionsSet.valueOf(instructionParts[0].toUpperCase());
            return true;
        } catch (IllegalArgumentException e){
            return false;
        }
    }

    /**
     * Funkcia pre parsovanie reťazca obsahujúceho číslo v decimálnej, binárnej, hexa, octa sústave alebo znak.
     *
     * @param constant Reťazec s číslom alebo znakom
     * @return Ak sa podarilo parsovať číslo z jednej zo sústav alebo znak, vráti jeho reprezentáciu, inak -1.
     */
    static int parseConstant(String constant){

        //hexa
        if(constant.matches("0[xX].+")){
            return Integer.parseInt(constant.substring(2), 16);
        }

        //binary
        if(constant.matches("[0-1]+b$")){
            return Integer.parseInt(constant.substring(0, constant.length()-1), 2);
        }

        //octa
        if(constant.matches("^0[0-7]+")){
            return Integer.parseInt(constant, 8);
        }

        //dec
        if(constant.matches("(^[1-9][0-9]*)|(^0$)")){
            return Integer.parseInt(constant);
        }

        //char
        if(constant.matches(Regexes.rByteChar)){
            return (int) constant.charAt(1);
        }

        try{
            return Integer.parseInt(constant);
        } catch (NumberFormatException e){
            return -1;
        }
    }

    /**
     * Spustenie obsluhy vlákna a teda parsovanie kódu programu.
     *
     * @return Program obsahujúci inštrukcie pre procesor.
     * @throws InvalidCodeLinesException Chyby pri parsovaní riadkov kódu.
     */
    @Override
    protected Program call() throws InvalidCodeLinesException {
        int lineIndex = 0;

        //Priprava na parsovanie instrukcie
        List<String> instructionParts = new ArrayList<>(3);
        Pattern instructionPattern = Pattern.compile("[^\\s\"',]+|'.'");
        Matcher instructionMatcher;

        //odstranenie vsetkych komentarov
        Pattern pat = Pattern.compile(COMMENT_PATTERN);
        code = pat.matcher(code).replaceAll("");

        //pattern pre navestia
        Pattern patternLabel = Pattern.compile(LABEL_PATTERN);

        //citanie po riadkoch kodu
        try(BufferedReader br = new BufferedReader(new StringReader(code))){
            String line = br.readLine();

            //citaj pokial nebolo vlakno ukoncene alebo nie je koniec kodu
            while (!isCancelled() && line != null){

                //nastavenie progresu
                updateProgress(lineIndex+1, numOfCodeLines);

                //kontrola ci riadok obsahuje navestie
                Matcher matcherLabel = patternLabel.matcher(line);
                if(matcherLabel.find()){
                    String label = matcherLabel.group();
                    label = label.substring(0, label.length()-1).trim(); //odstranenie dvojbodky a medzier

                    //existuje uz dane navestie?
                    if(!labels.containsKey(label)) {
                        boolean isInterruptLabel = false;

                        //kontrola, ci sa jedna o navestie pre prerusenie
                        if(label.matches("[iI][nN][tT].*")){
                            // Karpis - navestie musi byt int00 az int15
                            // alebo aj int00 az int0f
                            if(label.matches("[iI][nN][tT]([0]\\d|[1][012345]|[0][abcdef])")){
                                //splna podmienky navestia pre prerusenie
                                isInterruptLabel = true;
                                int intNumber;

                                if(label.matches("int0\\p{XDigit}")){
                                    // hexa
                                    intNumber = Integer.parseInt(label.substring(4,5), 16);
                                } else {
                                    // dec
                                    intNumber = Integer.parseInt(label.substring(3,5), 10);
                                }

                                if(!interruptionLabels.containsKey(intNumber)){
                                    interruptionLabels.put(intNumber, instructions.size()); //index nasledujucej instrukcie, ktora bude pridana
                                } else{
                                    addError("Návestie pre prerušenie " + label + " už existuje", lineIndex);
                                }

                            } else {
                                //asi to malo byt prerusenie ale je v zlom tvare
                                // Karpis: Nasl. riadok zablokovany, aby boli mozne navestia zacinajuce na "int"
                                //addError("Nesprávny tvar návestia pre prerušenie '" + label + "'", lineIndex);
                            }
                        }

                        //obycajne navestie - navestia preruseni (int00..int15) su rezervovane pre obsluhu preruseni
                        if(!isInterruptLabel){
                            labels.put(label, instructions.size()); //index nasledujucej instrukcie, ktora bude pridana
                        }
                    } else {
                        addError("Návestie " + label + " už existuje", lineIndex);
                    }

                    //odstran navestie z riadku
                    line = matcherLabel.replaceFirst("");
                }

                //idealne by v riadku mala ostat uz iba instrukcia
                line = line.trim();
                //ak je riadok prazdny, chod na dalsi
                if(line.isEmpty()){
                    lineIndex++;
                    line = br.readLine();
                    continue;
                }

                //inak sa pokus rozdelit instrukciu a parametre
                //String[] instructionParts = line.split("\\s*,\\s*|\\s+"); //rozdelenie instrukcie (medzery a ciarka)
                instructionParts.clear();
                instructionMatcher = instructionPattern.matcher(line);
                while (instructionMatcher.find()){
                    instructionParts.add(instructionMatcher.group());
                }

                //je instrukcia platna?
                try {
                    enumInstructionsSet codeInstruction = enumInstructionsSet.valueOf(instructionParts.get(0).toUpperCase());

                    //ak je platna
                    //kontrola poctu parametrov
                    if(codeInstruction.getNumOfParameters() == instructionParts.size()-1) { //porovnanie pozadovanych parametrov s poctom nacitanych (-1 lebo instrukcia samotna)
                        boolean parametersError = false;

                        //kontrola spravnosti parametrov
                        if(codeInstruction.getNumOfParameters() > 0 && !instructionParts.get(1).matches(codeInstruction.getFirstRegex())){ //kontrola prveho parametra
                            //ak nezodpoveda predpisu
                            addError("Nesprávny prvý parameter inštrukcie '" + instructionParts.get(0) + "'", lineIndex);
                            parametersError = true;
                        }

                        if(codeInstruction.getNumOfParameters() > 1 && !instructionParts.get(2).matches(codeInstruction.getSecondRegex())){ //kontrola druheho parametra
                            //ak nezodpoveda predpisu
                            addError("Nesprávny druhý parameter inštrukcie '" + instructionParts.get(0) + "'", lineIndex);
                            parametersError = true;
                        }

                        //instrukcia MVX je specialna
                        if(codeInstruction == enumInstructionsSet.MVX){
                            //ak je prvy operand register C a druhy A - chyba
                            if(instructionParts.get(1).matches("(?i)C") && instructionParts.get(2).matches("(?i)A")){
                                addError("Nesprávny druhý parameter inštrukcie '" + instructionParts.get(0) + "', povolené sú iba reg. S,M", lineIndex);
                                parametersError = true;
                            } else if (instructionParts.get(1).matches("(?i)S|M") && instructionParts.get(2).matches("(?i)S|M")){
                                addError("Nesprávny druhý parameter inštrukcie '" + instructionParts.get(0) + "', povolený je iba reg. A", lineIndex);
                                parametersError = true;
                            }
                        }

                        //ak je cela instrukcia aj s parametrami v poriadku, mozeme ju zaviest do programu (ak je aj chybny, kontroluje sa este spravnost navesti v instrukciach)
                        if(!parametersError){
                            switch (codeInstruction.getNumOfParameters()){
                                case 0:
                                    instructions.add(new Instruction(codeInstruction));
                                    break;
                                case 1:
                                    //ak je instrukcia BYTE, zaved konstantu do pamate ale nie instrukciu do programu
                                    if(codeInstruction == enumInstructionsSet.BYTE){
                                        if(progMemory.size() >= 256){
                                            addError("Príliš veľa pseudoinštrukcií BYTE - maximálne 256 konštánt", lineIndex);
                                        } else {
                                            byte b = (byte) parseConstant(instructionParts.get(1));
                                            progMemory.add(b);
                                        }
                                    } else {
                                        instructions.add(new Instruction(codeInstruction, instructionParts.get(1)));
                                    }
                                    break;
                                case 2:
                                    instructions.add(new Instruction(codeInstruction, instructionParts.get(1), instructionParts.get(2)));
                                    break;
                                default:
                                    break;
                            }

                            //ak instrukcia pouziva navestie
                            if(codeInstruction.usesLabel()){
                                //pridanie indexu inst. pre jej zmenu (navestie -> index)
                                instructionsWithLabels.add(instructions.size()-1);
                            }

                            //zaradenie do pervodnika medzi riadkami kodu a indexami instrukcii (pre breaky)
                            lineIndexToInstructionIndex.put(lineIndex, instructions.size()-1);
                        }

                    } else { //pocet parametrov neodpoveda
                        addError("Zlý počet parametrov inštrukcie '" + instructionParts.get(0) + "', požadovaný počet '" + codeInstruction.getNumOfParameters() + "'", lineIndex);
                    }

                } catch (IllegalArgumentException e){
                    //ak instrukcia nie je platna (nie je v zozname instrukcii)
                    addError("Neplatná inštrukcia '" + instructionParts.get(0) + "'", lineIndex);
                }

                lineIndex++;
                line = br.readLine();
            }

            updateProgress(numOfCodeLines, numOfCodeLines);
        } catch (IOException e) {
            //chyba so streamom stringu z kodu
            e.printStackTrace();
        }

        //ak je program v poriadku, preved navestia na indexy
        if(!hasError && !instructionsWithLabels.isEmpty()) transformLabelsToIndexes();

        //zmensenie pola arraylistu na minimum
        instructions.trimToSize();

        //zostavenie programu
        if(!hasError){
            return new Program(instructions, progMemory, lineIndexToInstructionIndex, interruptionLabels);
        } else {
            //vyhodit vynimku s chybnymi riadkami
            int[] lines = errorLines.stream().filter(Objects::nonNull).mapToInt(i -> i).toArray();
            String[] errorsMsgs = errors.toArray(new String[0]);
            throw new InvalidCodeLinesException(lines, errorsMsgs);
        }
    }

    /**
     * Metóda prevádza návestia na indexy, kam návestie ukazuje.
     */
    private void transformLabelsToIndexes(){
	    //prechadzaj vsetky instrukcie, ktore pouzivaju navestie
        for (Integer indexOfInstruction: instructionsWithLabels) {
            //zober instrukciu s navestim
            Instruction inst = instructions.get(indexOfInstruction);
            //zober jej navestie
            String label = inst.getFirstParameter();

            if(label != null){
                //pokus sa najst odpovedajuci index instrukcie, na ktore navestie ukazuje
                Integer jumpIndex = labels.get(label);
                //ak sa navestie naslo
                if(jumpIndex != null){
                    //zmen navestie instrukcie na index skoku
                    inst.setFirstParameter(Integer.toString(jumpIndex));
                } else {
                    //ak sa navestie nenaslo
                    addError("Neplatné návestie '" + label + "'", getLineOfInstruction(indexOfInstruction));
                }
            } else {
                addError("Chyba v inštrukcií", getLineOfInstruction(indexOfInstruction));
            }
        }
    }

    /**
     * Hľadá a vracia riadok inštrukcie.
     *
     * @param instructionIndex Index inštrukcie ku ktorej sa hľadá riadok
     * @return Index riadku na ktorom je inštrukcia, ak sa nenašla, vracia -1
     */
    private int getLineOfInstruction(int instructionIndex){
        for (Map.Entry<Integer, Integer> entry : lineIndexToInstructionIndex.entrySet()) {
            if (Objects.equals(instructionIndex, entry.getValue())) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private void addError(String err, int lineIndex){
        if(!hasError){
            errors = new ArrayList<>();
            errorLines = new ArrayList<>();
        }

        errors.add("["+(lineIndex+1)+"] " + err);
        errorLines.add(lineIndex);
        hasError = true;
    }

}//end Parser
