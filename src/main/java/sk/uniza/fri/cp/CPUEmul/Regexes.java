package sk.uniza.fri.cp.CPUEmul;

/**
 * Zoznam regex výrazov na parsovanie čísiel.
 */
final class Regexes {

    private Regexes(){}

    //public String rByteDec = "^([0-1]?\\d?\\d|2[0-4]\\d|25[0-5])$";
    static final String rByteDec = "^([0-1]?\\d?\\d|2[0-4]\\d|25[0-5])$";;
    static final String rByteHex = "^0[xX]\\p{XDigit}{1,2}$";
    static final String rByteOct = "^0([0-2]?[0-7]{1,2}|3[0-7]{0,2})$";
    static final String rByteBin = "^[0-1]{1,8}b$";
    static final String rByteChar = "^'.'$";
    static final String rByte = String.join("|", rByteDec, rByteHex, rByteBin, rByteOct, rByteChar);


    static final String rShortDec = "^([0-5]?\\d?\\d?\\d?\\d|6[0-4]\\d\\d\\d|65[0-4]\\d\\d|655[0-2]\\d|6553[0-5])$";
    static final String rShortHex = "^0[xX]\\p{XDigit}{1,4}$";
    static final String rShortOct = "^0([0-7]{1,5}|1[0-7]{0,5})$";
    static final String rShortBin = "^[0-1]{1,16}b$";
    static final String rShort = String.join("|", rShortDec, rShortHex, rShortBin, rShortOct);

    //navestie - identifikátor, nesmie začínať číslicou
    static final String rLabel = "[a-zA-Z_][a-zA-Z0-9_]*";

}
