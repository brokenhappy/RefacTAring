import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(15)
class TransactionCategorizerCliTest {
    @TempDir
    Path directory;

    @Test
    void addsCategoryColumnAndKeepsTheInputUntouched() throws Exception {
        String csv = "Date,Receiver,Amount\n2026-10-01,Shop,-12.40\n";
        Path input = input(csv);

        CliResult result = runCli(input, directory.resolve("output.csv"), answers("", "Groceries"));

        assertOutput(result, "Date,Receiver,Amount,Category\n2026-10-01,Shop,-12.40,Groceries\n");
        assertEquals(csv, Files.readString(input));
        assertTrue(result.transcript().contains("Transaction 1 of 1"));
    }

    @Test
    void suggestsTheCategoryFromThePreviousMatchingReceiver() throws Exception {
        CliResult result = run("Receiver,Amount\nShop,-10\nShop,-5\n",
                answers("", "Groceries", "", ""));

        assertOutput(result, "Receiver,Amount,Category\nShop,-10,Groceries\nShop,-5,Groceries\n");
        assertTrue(result.transcript().contains("Category [Groceries]:"));
    }

    @Test
    void matchesReceiversIgnoringCaseAndSurroundingSpaces() throws Exception {
        CliResult result = run("Receiver,Amount\n ACME ,-10\nacme,-5\n",
                answers("", "Supplies", "", ""));

        assertOutput(result, "Receiver,Amount,Category\n ACME ,-10,Supplies\nacme,-5,Supplies\n");
        assertTrue(result.transcript().contains("Category [Supplies]:"));
    }

    @Test
    void anOverrideBecomesTheNextSuggestion() throws Exception {
        CliResult result = run("Receiver,Amount\nShop,-10\nShop,-5\nShop,-3\n",
                answers("", "Groceries", "", "Gifts", "", ""));

        assertOutput(result, "Receiver,Amount,Category\nShop,-10,Groceries\nShop,-5,Gifts\nShop,-3,Gifts\n");
        assertTrue(result.transcript().contains("Category [Groceries]:"));
        assertTrue(result.transcript().contains("Category [Gifts]:"));
    }

    @Test
    void retainsAnExistingCategoryColumnInItsOriginalPosition() throws Exception {
        CliResult result = run("Date,Category,Receiver\n1,Travel,Taxi\n",
                answers("", ""));

        assertOutput(result, "Date,Category,Receiver\n1,Travel,Taxi\n");
        assertTrue(result.transcript().contains("Category [Travel]:"));
    }

    @Test
    void anExistingCategoryCanTeachTheNextRow() throws Exception {
        CliResult result = run("Receiver,Category\nShop,Groceries\nShop,\n",
                answers("", "", "", ""));

        assertOutput(result, "Receiver,Category\nShop,Groceries\nShop,Groceries\n");
    }

    @Test
    void existingCategoryCanBeChanged() throws Exception {
        CliResult result = run("Receiver,Category\nTaxi,Travel\n",
                answers("", "Business"));

        assertOutput(result, "Receiver,Category\nTaxi,Business\n");
    }

    @Test
    void uncheckedReceiverGetsNoAutomaticCategoryOnLaterRows() throws Exception {
        CliResult result = run("Receiver,Amount\nTaxi,-10\nTaxi,-5\nTaxi,-3\n",
                answers("x", "Travel", "", "Business", "", "Personal"));

        assertOutput(result, "Receiver,Amount,Category\nTaxi,-10,Travel\nTaxi,-5,Business\nTaxi,-3,Personal\n");
        assertTrue(result.transcript().contains("Auto-select for Taxi? [ ]"));
        assertFalse(result.transcript().contains("Category [Travel]:"));
        assertFalse(result.transcript().contains("Category [Business]:"));
    }

    @Test
    void checkingTheReceiverAgainUsesItsLastChosenCategory() throws Exception {
        CliResult result = run("Receiver,Amount\nTaxi,-10\nTaxi,-5\nTaxi,-3\n",
                answers("x", "Travel", "", "Business", "x", ""));

        assertOutput(result, "Receiver,Amount,Category\nTaxi,-10,Travel\nTaxi,-5,Business\nTaxi,-3,Business\n");
        assertTrue(result.transcript().contains("Auto-select on for Taxi."));
        assertTrue(result.transcript().contains("Category [Business]:"));
    }

    @Test
    void checkboxAndCategoryHistoryAreIndependentForEachReceiver() throws Exception {
        CliResult result = run("Receiver,Amount\nTaxi,-10\nShop,-5\nTaxi,-3\nShop,-2\n",
                answers("x", "Travel", "", "Groceries", "", "Business", "", ""));

        assertOutput(result, "Receiver,Amount,Category\nTaxi,-10,Travel\nShop,-5,Groceries\nTaxi,-3,Business\nShop,-2,Groceries\n");
        assertTrue(result.transcript().contains("Auto-select for Taxi? [ ]"));
        assertTrue(result.transcript().contains("Category [Groceries]:"));
    }

    @Test
    void blankReceiverRowsAreCategorizedSeparately() throws Exception {
        CliResult result = run("Receiver,Amount\n,-10\n,-5\n", answers("One", "Two"));

        assertOutput(result, "Receiver,Amount,Category\n,-10,One\n,-5,Two\n");
        assertFalse(result.transcript().contains("Auto-select for"));
    }

    @Test
    void autoDetectsPayeeBeforeDescription() throws Exception {
        CliResult result = run("Description,Payee,Amount\nFirst ride,Taxi,-10\nSecond ride,Taxi,-5\n",
                answers("", "Travel", "", ""));

        assertOutput(result, "Description,Payee,Amount,Category\nFirst ride,Taxi,-10,Travel\nSecond ride,Taxi,-5,Travel\n");
    }

    @Test
    void acceptsAnExplicitReceiverHeaderWithSpaces() throws Exception {
        CliResult result = run("Beneficiary Name,Amount\nClinic,-10\nClinic,-5\n",
                answers("", "Health", "", ""), "Beneficiary Name");

        assertOutput(result, "Beneficiary Name,Amount,Category\nClinic,-10,Health\nClinic,-5,Health\n");
    }

    @Test
    void roundTripsQuotedCommasQuotesAndMultilineFields() throws Exception {
        String csv = "Receiver,Note\n\"Shop, Inc\",\"He said \"\"hi\"\"\"\n"
                + "\"Shop, Inc\",\"Line one\nLine two\"\n";
        CliResult result = run(csv, answers("", "Food, drinks", "", ""));

        assertOutput(result, "Receiver,Note,Category\n"
                + "\"Shop, Inc\",\"He said \"\"hi\"\"\",\"Food, drinks\"\n"
                + "\"Shop, Inc\",\"Line one\nLine two\",\"Food, drinks\"\n");
    }

    @Test
    void preservesUtf8BomAndCrLfLineEndings() throws Exception {
        CliResult result = run("\uFEFFDate,Receiver\r\n1,Café\r\n", answers("", "Meals"));

        assertOutput(result, "\uFEFFDate,Receiver,Category\r\n1,Café,Meals\r\n");
    }

    @Test
    void skipsBlankLinesBetweenTransactions() throws Exception {
        CliResult result = run("\nReceiver,Amount\n\nShop,-10\n\n", answers("", "Food"));

        assertOutput(result, "Receiver,Amount,Category\nShop,-10,Food\n");
    }

    @Test
    void headerOnlyCsvProducesHeaderOnlyOutput() throws Exception {
        CliResult result = run("Receiver,Amount\n", "");

        assertOutput(result, "Receiver,Amount,Category\n");
        assertFalse(result.transcript().contains("Transaction 1"));
    }

    @Test
    void emptyCategoryInputAsksAgain() throws Exception {
        CliResult result = run("Receiver,Amount\nShop,-10\n", answers("", "", "Food"));

        assertOutput(result, "Receiver,Amount,Category\nShop,-10,Food\n");
        assertTrue(result.transcript().contains("Please enter a category."));
    }

    @Test
    void invalidCheckboxInputAsksAgain() throws Exception {
        CliResult result = run("Receiver,Amount\nShop,-10\n", answers("no", "", "Food"));

        assertOutput(result, "Receiver,Amount,Category\nShop,-10,Food\n");
        assertTrue(result.transcript().contains("Press Enter to keep the checkbox or x to toggle it."));
    }

    @Test
    void emptyCsvIsRejectedWithoutCreatingOutput() throws Exception {
        assertRejected(run("", ""), 1, "The CSV is empty.");
    }

    @Test
    void missingReceiverColumnIsRejected() throws Exception {
        assertRejected(run("Date,Amount\n1,-10\n", ""), 1, "No receiver column found");
    }

    @Test
    void unknownExplicitReceiverColumnIsRejected() throws Exception {
        assertRejected(run("Receiver,Amount\nShop,-10\n", "", "Vendor"),
                1, "Receiver column not found: Vendor");
    }

    @Test
    void receiverColumnCannotAlsoBeTheCategoryColumn() throws Exception {
        assertRejected(run("Category,Amount\nShop,-10\n", "", "Category"),
                1, "receiver and Category columns must be different");
    }

    @Test
    void duplicateCategoryColumnsAreRejected() throws Exception {
        assertRejected(run("Receiver,Category,CATEGORY\nShop,,\n", ""),
                1, "more than one Category column");
    }

    @Test
    void shortCsvRowsAreRejectedBeforePrompting() throws Exception {
        CliResult result = run("Receiver,Amount\nShop\n", "");

        assertRejected(result, 1, "CSV row 2 has 1 fields; expected 2.");
        assertFalse(result.transcript().contains("Transaction 1"));
    }

    @Test
    void unclosedQuotesAreRejectedWithoutCreatingOutput() throws Exception {
        assertRejected(run("Receiver,Amount\n\"Shop,-10\n", ""), 1, "Unclosed quoted field");
    }

    @Test
    void charactersAfterAClosingQuoteAreRejected() throws Exception {
        assertRejected(run("Receiver,Amount\n\"Shop\"oops,-10\n", ""), 1, "Malformed CSV");
    }

    @Test
    void endOfInputAtCheckboxPromptCreatesNoOutput() throws Exception {
        assertRejected(run("Receiver,Amount\nShop,-10\n", ""),
                1, "Input ended before all transactions were categorized.");
    }

    @Test
    void endOfInputAtCategoryPromptCreatesNoOutput() throws Exception {
        assertRejected(run("Receiver,Amount\nShop,-10\n", answers("")),
                1, "Input ended before all transactions were categorized.");
    }

    @Test
    void endOfInputAfterSomeCompletedRowsCreatesNoOutput() throws Exception {
        assertRejected(run("Receiver,Amount\nShop,-10\nTaxi,-5\n", answers("", "Food")),
                1, "Input ended before all transactions were categorized.");
    }

    @Test
    void existingOutputIsNotOverwritten() throws Exception {
        Path input = input("Receiver,Amount\nShop,-10\n");
        Path output = directory.resolve("output.csv");
        Files.writeString(output, "keep me", StandardCharsets.UTF_8);

        CliResult result = runCli(input, output, answers("", "Food"));

        assertEquals(2, result.exitCode(), result.transcript());
        assertTrue(result.transcript().contains("Output already exists:"));
        assertEquals("keep me", Files.readString(output));
    }

    @Test
    void inputCannotBeUsedAsOutput() throws Exception {
        String csv = "Receiver,Amount\nShop,-10\n";
        Path input = input(csv);

        CliResult result = runCli(input, input, answers("", "Food"));

        assertEquals(2, result.exitCode(), result.transcript());
        assertTrue(result.transcript().contains("Input and output must be different files."));
        assertEquals(csv, Files.readString(input));
    }

    @Test
    void missingInputFileIsReportedWithoutCreatingOutput() throws Exception {
        CliResult result = runCli(directory.resolve("missing.csv"), directory.resolve("output.csv"), "");

        assertRejected(result, 2, "Input file does not exist:");
    }

    private Path input(String csv) throws IOException {
        Path path = directory.resolve("input.csv");
        Files.writeString(path, csv, StandardCharsets.UTF_8);
        return path;
    }

    private CliResult run(String csv, String answers, String... extraArguments) throws Exception {
        return runCli(input(csv), directory.resolve("output.csv"), answers, extraArguments);
    }

    private CliResult runCli(Path input, Path output, String answers, String... extraArguments) throws Exception {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-cp", System.getProperty("java.class.path"), "TransactionCategorizer",
                input.toString(), output.toString()));
        command.addAll(List.of(extraArguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (var stdin = process.getOutputStream()) {
            stdin.write(answers.getBytes(StandardCharsets.UTF_8));
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("CLI did not finish");
        }
        String transcript = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new CliResult(process.exitValue(), transcript, output);
    }

    private static String answers(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    private static void assertOutput(CliResult result, String expected) throws IOException {
        assertEquals(0, result.exitCode(), result.transcript());
        assertEquals(expected, Files.readString(result.output(), StandardCharsets.UTF_8));
    }

    private static void assertRejected(CliResult result, int exitCode, String message) {
        assertEquals(exitCode, result.exitCode(), result.transcript());
        assertTrue(result.transcript().contains(message), result.transcript());
        assertFalse(Files.exists(result.output()), "Rejected input must not create output");
    }

    private record CliResult(int exitCode, String transcript, Path output) {
    }
}
