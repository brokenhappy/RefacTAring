import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Timeout(15)
class TransactionCategorizerCliTest {
    @field:TempDir
    lateinit var directory: Path

    // Categorizing and remembering receivers

    @Test
    fun `adds a category column without changing the input`() {
        val source = csv("Date,Receiver,Amount", "2026-10-01,Shop,-12.40")
        val input = inputFile(source)

        val result = runCli(input, directory.resolve("output.csv"), responses("", "Groceries"))

        result.assertCsv(csv("Date,Receiver,Amount,Category", "2026-10-01,Shop,-12.40,Groceries"))
        assertEquals(source, Files.readString(input))
        result.assertTranscriptContains("Transaction 1 of 1")
    }

    @Test
    fun `suggests the previous category for the same receiver`() {
        val result = runCsv(
            csv("Receiver,Amount", "Shop,-10", "Shop,-5"),
            responses("", "Groceries", "", ""),
        )

        result.assertCsv(csv("Receiver,Amount,Category", "Shop,-10,Groceries", "Shop,-5,Groceries"))
        result.assertTranscriptContains("Category [Groceries]:")
    }

    @Test
    fun `matches receivers regardless of case and surrounding spaces`() {
        val result = runCsv(
            csv("Receiver,Amount", " ACME ,-10", "acme,-5"),
            responses("", "Supplies", "", ""),
        )

        result.assertCsv(csv("Receiver,Amount,Category", " ACME ,-10,Supplies", "acme,-5,Supplies"))
        result.assertTranscriptContains("Category [Supplies]:")
    }

    @Test
    fun `a changed category becomes the next suggestion`() {
        val result = runCsv(
            csv("Receiver,Amount", "Shop,-10", "Shop,-5", "Shop,-3"),
            responses("", "Groceries", "", "Gifts", "", ""),
        )

        result.assertCsv(
            csv("Receiver,Amount,Category", "Shop,-10,Groceries", "Shop,-5,Gifts", "Shop,-3,Gifts"),
        )
        result.assertTranscriptContains("Category [Groceries]:")
        result.assertTranscriptContains("Category [Gifts]:")
    }

    @Test
    fun `keeps an existing category column in its original position`() {
        val result = runCsv(csv("Date,Category,Receiver", "1,Travel,Taxi"), responses("", ""))

        result.assertCsv(csv("Date,Category,Receiver", "1,Travel,Taxi"))
        result.assertTranscriptContains("Category [Travel]:")
    }

    @Test
    fun `an existing category teaches the next row`() {
        val result = runCsv(
            csv("Receiver,Category", "Shop,Groceries", "Shop,"),
            responses("", "", "", ""),
        )

        result.assertCsv(csv("Receiver,Category", "Shop,Groceries", "Shop,Groceries"))
    }

    @Test
    fun `an existing category can be changed`() {
        val result = runCsv(csv("Receiver,Category", "Taxi,Travel"), responses("", "Business"))

        result.assertCsv(csv("Receiver,Category", "Taxi,Business"))
    }

    // Receiver checkbox behavior

    @Test
    fun `unchecked receiver gets no suggestions on later rows`() {
        val result = runCsv(
            csv("Receiver,Amount", "Taxi,-10", "Taxi,-5", "Taxi,-3"),
            responses("x", "Travel", "", "Business", "", "Personal"),
        )

        result.assertCsv(
            csv("Receiver,Amount,Category", "Taxi,-10,Travel", "Taxi,-5,Business", "Taxi,-3,Personal"),
        )
        result.assertTranscriptContains("Auto-select for Taxi? [ ]")
        assertFalse("Category [Travel]:" in result.transcript)
        assertFalse("Category [Business]:" in result.transcript)
    }

    @Test
    fun `checking a receiver again uses its last chosen category`() {
        val result = runCsv(
            csv("Receiver,Amount", "Taxi,-10", "Taxi,-5", "Taxi,-3"),
            responses("x", "Travel", "", "Business", "x", ""),
        )

        result.assertCsv(
            csv("Receiver,Amount,Category", "Taxi,-10,Travel", "Taxi,-5,Business", "Taxi,-3,Business"),
        )
        result.assertTranscriptContains("Auto-select on for Taxi.")
        result.assertTranscriptContains("Category [Business]:")
    }

    @Test
    fun `checkbox and category history belong to each receiver separately`() {
        val result = runCsv(
            csv("Receiver,Amount", "Taxi,-10", "Shop,-5", "Taxi,-3", "Shop,-2"),
            responses("x", "Travel", "", "Groceries", "", "Business", "", ""),
        )

        result.assertCsv(
            csv(
                "Receiver,Amount,Category",
                "Taxi,-10,Travel",
                "Shop,-5,Groceries",
                "Taxi,-3,Business",
                "Shop,-2,Groceries",
            ),
        )
        result.assertTranscriptContains("Auto-select for Taxi? [ ]")
        result.assertTranscriptContains("Category [Groceries]:")
    }

    @Test
    fun `blank receiver rows are categorized separately`() {
        val result = runCsv(csv("Receiver,Amount", ",-10", ",-5"), responses("One", "Two"))

        result.assertCsv(csv("Receiver,Amount,Category", ",-10,One", ",-5,Two"))
        assertFalse("Auto-select for" in result.transcript)
    }

    @Test
    fun `detects Payee before Description`() {
        val result = runCsv(
            csv("Description,Payee,Amount", "First ride,Taxi,-10", "Second ride,Taxi,-5"),
            responses("", "Travel", "", ""),
        )

        result.assertCsv(
            csv("Description,Payee,Amount,Category", "First ride,Taxi,-10,Travel", "Second ride,Taxi,-5,Travel"),
        )
    }

    @Test
    fun `accepts an explicit receiver header with spaces`() {
        val result = runCsv(
            csv("Beneficiary Name,Amount", "Clinic,-10", "Clinic,-5"),
            responses("", "Health", "", ""),
            receiverColumn = "Beneficiary Name",
        )

        result.assertCsv(csv("Beneficiary Name,Amount,Category", "Clinic,-10,Health", "Clinic,-5,Health"))
    }

    // CSV format and terminal prompts

    @Test
    fun `round trips quoted commas quotes and multiline fields`() {
        val source = csv(
            "Receiver,Note",
            "\"Shop, Inc\",\"He said \"\"hi\"\"\"",
            "\"Shop, Inc\",\"Line one\nLine two\"",
        )
        val result = runCsv(source, responses("", "Food, drinks", "", ""))

        result.assertCsv(
            csv(
                "Receiver,Note,Category",
                "\"Shop, Inc\",\"He said \"\"hi\"\"\",\"Food, drinks\"",
                "\"Shop, Inc\",\"Line one\nLine two\",\"Food, drinks\"",
            ),
        )
    }

    @Test
    fun `preserves UTF-8 BOM and CRLF line endings`() {
        val result = runCsv("\uFEFFDate,Receiver\r\n1,Café\r\n", responses("", "Meals"))

        result.assertCsv("\uFEFFDate,Receiver,Category\r\n1,Café,Meals\r\n")
    }

    @Test
    fun `skips blank lines between transactions`() {
        val result = runCsv("\nReceiver,Amount\n\nShop,-10\n\n", responses("", "Food"))

        result.assertCsv(csv("Receiver,Amount,Category", "Shop,-10,Food"))
    }

    @Test
    fun `header only CSV produces header only output`() {
        val result = runCsv(csv("Receiver,Amount"), "")

        result.assertCsv(csv("Receiver,Amount,Category"))
        assertFalse("Transaction 1" in result.transcript)
    }

    @Test
    fun `empty category input asks again`() {
        val result = runCsv(csv("Receiver,Amount", "Shop,-10"), responses("", "", "Food"))

        result.assertCsv(csv("Receiver,Amount,Category", "Shop,-10,Food"))
        result.assertTranscriptContains("Please enter a category.")
    }

    @Test
    fun `invalid checkbox input asks again`() {
        val result = runCsv(csv("Receiver,Amount", "Shop,-10"), responses("no", "", "Food"))

        result.assertCsv(csv("Receiver,Amount,Category", "Shop,-10,Food"))
        result.assertTranscriptContains("Press Enter to keep the checkbox or x to toggle it.")
    }

    // Rejected CSV and interrupted sessions never create an output file

    @Test
    fun `rejects an empty CSV`() {
        runCsv("", "").assertRejected(1, "The CSV is empty.")
    }

    @Test
    fun `rejects a missing receiver column`() {
        runCsv(csv("Date,Amount", "1,-10"), "").assertRejected(1, "No receiver column found")
    }

    @Test
    fun `rejects an unknown explicit receiver column`() {
        runCsv(csv("Receiver,Amount", "Shop,-10"), "", receiverColumn = "Vendor")
            .assertRejected(1, "Receiver column not found: Vendor")
    }

    @Test
    fun `rejects a receiver column that is also Category`() {
        runCsv(csv("Category,Amount", "Shop,-10"), "", receiverColumn = "Category")
            .assertRejected(1, "receiver and Category columns must be different")
    }

    @Test
    fun `rejects duplicate Category columns`() {
        runCsv(csv("Receiver,Category,CATEGORY", "Shop,,"), "")
            .assertRejected(1, "more than one Category column")
    }

    @Test
    fun `rejects a short CSV row before prompting`() {
        val result = runCsv(csv("Receiver,Amount", "Shop"), "")

        result.assertRejected(1, "CSV row 2 has 1 fields; expected 2.")
        assertFalse("Transaction 1" in result.transcript)
    }

    @Test
    fun `rejects an unclosed quoted field`() {
        runCsv("Receiver,Amount\n\"Shop,-10\n", "").assertRejected(1, "Unclosed quoted field")
    }

    @Test
    fun `rejects characters after a closing quote`() {
        runCsv("Receiver,Amount\n\"Shop\"oops,-10\n", "").assertRejected(1, "Malformed CSV")
    }

    @Test
    fun `end of input at the checkbox leaves no output`() {
        runCsv(csv("Receiver,Amount", "Shop,-10"), "")
            .assertRejected(1, "Input ended before all transactions were categorized.")
    }

    @Test
    fun `end of input at the category prompt leaves no output`() {
        runCsv(csv("Receiver,Amount", "Shop,-10"), responses(""))
            .assertRejected(1, "Input ended before all transactions were categorized.")
    }

    @Test
    fun `end of input after completed rows leaves no output`() {
        runCsv(csv("Receiver,Amount", "Shop,-10", "Taxi,-5"), responses("", "Food"))
            .assertRejected(1, "Input ended before all transactions were categorized.")
    }

    // Existing files are never overwritten

    @Test
    fun `existing output is not overwritten`() {
        val input = inputFile(csv("Receiver,Amount", "Shop,-10"))
        val output = directory.resolve("output.csv")
        Files.writeString(output, "keep me", UTF_8)

        val result = runCli(input, output, responses("", "Food"))

        assertEquals(2, result.exitCode, result.transcript)
        result.assertTranscriptContains("Output already exists:")
        assertEquals("keep me", Files.readString(output))
    }

    @Test
    fun `input cannot also be the output`() {
        val source = csv("Receiver,Amount", "Shop,-10")
        val input = inputFile(source)

        val result = runCli(input, input, responses("", "Food"))

        assertEquals(2, result.exitCode, result.transcript)
        result.assertTranscriptContains("Input and output must be different files.")
        assertEquals(source, Files.readString(input))
    }

    @Test
    fun `missing input file is reported without creating output`() {
        runCli(directory.resolve("missing.csv"), directory.resolve("output.csv"), "")
            .assertRejected(2, "Input file does not exist:")
    }

    private fun inputFile(csv: String): Path = directory.resolve("input.csv").also {
        Files.writeString(it, csv, UTF_8)
    }

    private fun runCsv(csv: String, responses: String, receiverColumn: String? = null): CliResult =
        runCli(inputFile(csv), directory.resolve("output.csv"), responses, receiverColumn)

    private fun runCli(input: Path, output: Path, responses: String, receiverColumn: String? = null): CliResult {
        val javaName = if (System.getProperty("os.name").contains("win", ignoreCase = true)) "java.exe" else "java"
        val command = buildList {
            add(Path.of(System.getProperty("java.home"), "bin", javaName).toString())
            addAll(listOf("-cp", System.getProperty("java.class.path"), "TransactionCategorizer"))
            addAll(listOf(input.toString(), output.toString()))
            receiverColumn?.let(::add)
        }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.outputStream.use { it.write(responses.toByteArray(UTF_8)) }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("CLI did not finish")
        }
        return CliResult(
            exitCode = process.exitValue(),
            transcript = process.inputStream.readAllBytes().toString(UTF_8),
            output = output,
        )
    }

    private fun csv(vararg lines: String): String = lines.joinToString("\n", postfix = "\n")

    private fun responses(vararg lines: String): String = lines.joinToString("\n", postfix = "\n")

    private fun CliResult.assertCsv(expected: String) {
        assertEquals(0, exitCode, transcript)
        assertEquals(expected, Files.readString(output, UTF_8))
    }

    private fun CliResult.assertRejected(expectedExitCode: Int, message: String) {
        assertEquals(expectedExitCode, exitCode, transcript)
        assertTranscriptContains(message)
        assertFalse(Files.exists(output), "Rejected input must not create output")
    }

    private fun CliResult.assertTranscriptContains(text: String) {
        assertTrue(text in transcript, transcript)
    }

    private data class CliResult(val exitCode: Int, val transcript: String, val output: Path)
}
