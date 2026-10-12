package sk.uniza.fri.cp.CPUEmul;

import javafx.embed.swing.JFXPanel;
import javafx.application.Platform;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import sk.uniza.fri.cp.App.CPUControl.CodeEditor.CodeEditorFactory;
import sk.uniza.fri.cp.CPUEmul.Exceptions.InvalidCodeLinesException;

import java.lang.reflect.Field;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ParserJumpLabelTest {

    @BeforeClass
    public static void initFx(){
        //Parser je Task, ktory cez updateProgress potrebuje inicializovany JavaFX toolkit
        new JFXPanel();
    }

    @AfterClass
    public static void exitFx(){
        Platform.exit();
    }

    @Test
    public void labelResolvedToInstructionIndex() throws Exception {
        Program p = new Parser("MVI A, 5\nloop:\nADD A, B\nJMP loop\nRET\n", 5).call();
        assertEquals("1", p.getInstruction(2).getFirstParameter());
    }

    @Test
    public void labelInProgramWithoutOtherLabelsIsResolved() throws Exception {
        Program p = new Parser("loop:\nADD A, B\nJMP loop\n", 3).call();
        assertEquals("0", p.getInstruction(1).getFirstParameter());
    }

    @Test
    public void numericJumpTargetIsRejected() {
        try {
            new Parser("MVI A, 5\nJMP 5\nRET\n", 3).call();
            fail("ciselny ciel skoku mal byt odmietnuty");
        } catch (InvalidCodeLinesException expected) {
            assertTrue(expected.toString(), expected.toString().contains("Nesprávny prvý parameter"));
        }
    }

    @Test
    public void undefinedLabelIsRejected() {
        try {
            new Parser("MVI A, 5\nJMP nowhere\nRET\n", 3).call();
            fail("nedefinovane navestie malo byt odmietnute");
        } catch (InvalidCodeLinesException expected) {
            assertTrue(expected.toString(), expected.toString().contains("Neplatné návestie"));
        }
    }

    @Test
    public void callTargetIsResolvedAndJumpTargetsAllCallVariants() throws Exception {
        Program p = new Parser("start:\nCAL start\nCZR start\nCCY start\nRET\n", 4).call();
        assertEquals("0", p.getInstruction(0).getFirstParameter());
        assertEquals("0", p.getInstruction(1).getFirstParameter());
        assertEquals("0", p.getInstruction(2).getFirstParameter());
    }

    @Test
    public void interruptLabelIsReservedAndNotUsableAsJumpTarget() {
        try {
            new Parser("int03:\nRET\nJMP int03\n", 3).call();
            fail("navestie prerusenia nemohlo byt pouzite ako ciel skoku");
        } catch (InvalidCodeLinesException expected) {
            assertTrue(expected.toString(), expected.toString().contains("Neplatné návestie"));
        }
    }

    @Test
    public void interruptLabelRegistersForInterruptHandling() throws Exception {
        Program p = new Parser("int03:\nRET\n", 2).call();
        assertEquals(0, p.getAddressOfInterrupt(3));
    }

    @Test
    public void labelWithIntPrefixOutsideReservedRangeIsRegular() throws Exception {
        Program p = new Parser("int16:\nRET\nJMP int16\n", 3).call();
        assertEquals("0", p.getInstruction(1).getFirstParameter());
    }

    @Test
    public void tooManyByteConstantsAreRejected() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 257; i++) code.append("BYTE 1\n");
        try {
            new Parser(code.toString(), 257).call();
            fail("viac ako 256 pseudoinštrukcii BYTE malo byt odmietnute");
        } catch (InvalidCodeLinesException expected) {
            assertTrue(expected.toString(), expected.toString().contains("Príliš veľa"));
        }
    }

    @Test
    public void exactly256ByteConstantsAreAllowed() throws Exception {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 256; i++) code.append("BYTE 1\n");
        Program p = new Parser(code.toString(), 256).call();
        assertEquals(256, p.getMemory().length);
    }

    @Test
    public void semicolonAsCharacterParameterIsKeptAsLiteral() throws Exception {
        Program p = new Parser("MVI A, ';'\nRET\n", 2).call();
        assertEquals("';'", p.getInstruction(0).getSecondParameter());
    }

    @Test
    public void semicolonCharLiteralWithFollowingCommentParses() throws Exception {
        Program p = new Parser("MVI A, ';' ; komentar\nRET\n", 2).call();
        assertEquals("';'", p.getInstruction(0).getSecondParameter());
    }

    @Test
    public void commentDirectlyAfterCharLiteralIsStillStripped() throws Exception {
        Program p = new Parser("MVI A, 'a'; komentar\nRET\n", 2).call();
        assertEquals("'a'", p.getInstruction(0).getSecondParameter());
    }

    @Test
    public void plainCommentLineStillStripped() throws Exception {
        Program p = new Parser("; komentar\nMVI A, 5\nRET\n", 3).call();
        assertEquals("A", p.getInstruction(0).getFirstParameter());
        Program p2 = new Parser("loop: ; komentar pri navesti\nMVI A, 5\nJMP loop\n", 3).call();
        assertEquals("0", p2.getInstruction(1).getFirstParameter());
    }

    @Test
    public void labelStartingWithUnderscoreIsResolved() throws Exception {
        Program p = new Parser("_loop:\nMVI A, 5\nJMP _loop\nRET\n", 4).call();
        assertEquals("0", p.getInstruction(1).getFirstParameter());
    }

    @Test
    public void underscoreLabelWithDigitsIsResolved() throws Exception {
        Program p = new Parser("_1:\nRET\nJMP _1\n", 3).call();
        assertEquals("0", p.getInstruction(1).getFirstParameter());
    }

    @Test
    public void labelContainingUnderscoreIsResolved() throws Exception {
        Program p = new Parser("my_label:\nRET\nJMP my_label\n", 3).call();
        assertEquals("0", p.getInstruction(1).getFirstParameter());
    }

    @Test
    public void codeEditorHighlighterAcceptsUnderscoreLabels() throws Exception {
        Field patternField = CodeEditorFactory.class.getDeclaredField("PATTERN");
        patternField.setAccessible(true);
        Pattern pattern = (Pattern) patternField.get(null);
        Matcher matcher = pattern.matcher("_start:\nMOV A, B\nJMP _start\n");
        assertTrue(matcher.find());
        assertEquals("_start:", matcher.group());
        assertNotNull(matcher.group("LABEL"));
    }
}