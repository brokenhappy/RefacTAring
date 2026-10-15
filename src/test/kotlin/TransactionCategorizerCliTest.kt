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
        val source = """
            Date,Receiver,Amount
            2026-10-01,Shop,-12.40
        """.trimIndent() + "\n"
        val input = inputFile(source)

        val result = runCategorizerApp(input, directory.resolve("output.csv"), responses("", "Groceries"))

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Date,Receiver,Amount,Category
                2026-10-01,Shop,-12.40,Groceries
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
        assertEquals(source, Files.readString(input))
        result.assertTranscriptContains("Transaction 1 of 1")
    }

    @Test
    fun `suggests the previous category for the same receiver`() {
        val result = runCategorizerAppWithTestIO(
            """
                Receiver,Amount
                Shop,-10
                Shop,-5
            """.trimIndent() + "\n",
            responses("", "Groceries", "", ""),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Amount,Category
                Shop,-10,Groceries
                Shop,-5,Groceries
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
        result.assertTranscriptContains("Category [Groceries]:")
    }

    @Test
    fun `matches receivers regardless of case and surrounding spaces`() {
        val result = runCategorizerAppWithTestIO(
            """
                Receiver,Amount
                 ACME ,-10
                acme,-5
            """.trimIndent() + "\n",
            responses("", "Supplies", "", ""),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Amount,Category
                 ACME ,-10,Supplies
                acme,-5,Supplies
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
        result.assertTranscriptContains("Category [Supplies]:")
    }

    @Test
    fun `a changed category becomes the next suggestion`() {
        val result = runCategorizerAppWithTestIO(
            """
                Receiver,Amount
                Shop,-10
                Shop,-5
                Shop,-3
            """.trimIndent() + "\n",
            responses("", "Groceries", "", "Gifts", "", ""),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Amount,Category
                Shop,-10,Groceries
                Shop,-5,Gifts
                Shop,-3,Gifts
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
        result.assertTranscriptContains("Category [Groceries]:")
        result.assertTranscriptContains("Category [Gifts]:")
    }

    @Test
    fun `keeps an existing category column in its original position`() {
        val result = runCategorizerAppWithTestIO(
            """
                Date,Category,Receiver
                1,Travel,Taxi
            """.trimIndent() + "\n",
            responses("", ""),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Date,Category,Receiver
                1,Travel,Taxi
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
        result.assertTranscriptContains("Category [Travel]:")
    }

    @Test
    fun `an existing category teaches the next row`() {
        val result = runCategorizerAppWithTestIO(
            """
                Receiver,Category
                Shop,Groceries
                Shop,
            """.trimIndent() + "\n",
            responses("", "", "", ""),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Category
                Shop,Groceries
                Shop,Groceries
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
    }

    @Test
    fun `an existing category can be changed`() {
        val result = runCategorizerAppWithTestIO(
            """
                Receiver,Category
                Taxi,Travel
            """.trimIndent() + "\n",
            responses("", "Business"),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Category
                Taxi,Business
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
    }

    // Receiver checkbox behavior

    @Test
    fun `unchecked receiver gets no suggestions on later rows`() {
        val result = runCategorizerAppWithTestIO(
            """
                Receiver,Amount
                Taxi,-10
                Taxi,-5
                Taxi,-3
            """.trimIndent() + "\n",
            responses("x", "Travel", "", "Business", "", "Personal"),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Amount,Category
                Taxi,-10,Travel
                Taxi,-5,Business
                Taxi,-3,Personal
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
        result.assertTranscriptContains("Auto-select for Taxi? [ ]")
        assertFalse("Category [Travel]:" in result.transcript)
        assertFalse("Category [Business]:" in result.transcript)
    }

    @Test
    fun `checking a receiver again uses its last chosen category`() {
        val result = runCategorizerAppWithTestIO(
            """
                Receiver,Amount
                Taxi,-10
                Taxi,-5
                Taxi,-3
            """.trimIndent() + "\n",
            responses("x", "Travel", "", "Business", "x", ""),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Amount,Category
                Taxi,-10,Travel
                Taxi,-5,Business
                Taxi,-3,Business
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
        result.assertTranscriptContains("Auto-select on for Taxi.")
        result.assertTranscriptContains("Category [Business]:")
    }

    @Test
    fun `checkbox and category history belong to each receiver separately`() {
        val result = runCategorizerAppWithTestIO(
            """
                Receiver,Amount
                Taxi,-10
                Shop,-5
                Taxi,-3
                Shop,-2
            """.trimIndent() + "\n",
            responses("x", "Travel", "", "Groceries", "", "Business", "", ""),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Amount,Category
                Taxi,-10,Travel
                Shop,-5,Groceries
                Taxi,-3,Business
                Shop,-2,Groceries
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
        result.assertTranscriptContains("Auto-select for Taxi? [ ]")
        result.assertTranscriptContains("Category [Groceries]:")
    }

    @Test
    fun `blank receiver rows are categorized separately`() {
        val result = runCategorizerAppWithTestIO(
            """
                Receiver,Amount
                ,-10
                ,-5
            """.trimIndent() + "\n",
            responses("One", "Two"),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Amount,Category
                ,-10,One
                ,-5,Two
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
        assertFalse("Auto-select for" in result.transcript)
    }

    @Test
    fun `detects Payee before Description`() {
        val result = runCategorizerAppWithTestIO(
            """
                Description,Payee,Amount
                First ride,Taxi,-10
                Second ride,Taxi,-5
            """.trimIndent() + "\n",
            responses("", "Travel", "", ""),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Description,Payee,Amount,Category
                First ride,Taxi,-10,Travel
                Second ride,Taxi,-5,Travel
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
    }

    @Test
    fun `accepts an explicit receiver header with spaces`() {
        val result = runCategorizerAppWithTestIO(
            """
                Beneficiary Name,Amount
                Clinic,-10
                Clinic,-5
            """.trimIndent() + "\n",
            responses("", "Health", "", ""),
            receiverColumn = "Beneficiary Name",
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Beneficiary Name,Amount,Category
                Clinic,-10,Health
                Clinic,-5,Health
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
    }

    // CSV format and terminal prompts

    @Test
    fun `round trips quoted commas quotes and multiline fields`() {
        val source = """
            Receiver,Note
            "Shop, Inc","He said ""hi"" today"
            "Shop, Inc","Line one
            Line two"
        """.trimIndent() + "\n"
        val result = runCategorizerAppWithTestIO(source, responses("", "Food, drinks", "", ""))

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Note,Category
                "Shop, Inc","He said ""hi"" today","Food, drinks"
                "Shop, Inc","Line one
                Line two","Food, drinks"
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
    }

    @Test
    fun `preserves UTF-8 BOM and CRLF line endings`() {
        val result = runCategorizerAppWithTestIO("\uFEFFDate,Receiver\r\n1,Café\r\n", responses("", "Meals"))

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals("\uFEFFDate,Receiver,Category\r\n1,Café,Meals\r\n", Files.readString(result.output))
    }

    @Test
    fun `skips blank lines between transactions`() {
        val result = runCategorizerAppWithTestIO("\nReceiver,Amount\n\nShop,-10\n\n", responses("", "Food"))

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Amount,Category
                Shop,-10,Food
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
    }

    @Test
    fun `header only CSV produces header only output`() {
        val result = runCategorizerAppWithTestIO("Receiver,Amount\n", "")

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals("Receiver,Amount,Category\n", Files.readString(result.output))
        assertFalse("Transaction 1" in result.transcript)
    }

    @Test
    fun `empty category input asks again`() {
        val result = runCategorizerAppWithTestIO(
            """
                Receiver,Amount
                Shop,-10
            """.trimIndent() + "\n",
            responses("", "", "Food"),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Amount,Category
                Shop,-10,Food
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
        result.assertTranscriptContains("Please enter a category.")
    }

    @Test
    fun `invalid checkbox input asks again`() {
        val result = runCategorizerAppWithTestIO(
            """
                Receiver,Amount
                Shop,-10
            """.trimIndent() + "\n",
            responses("no", "", "Food"),
        )

        assertEquals(0, result.exitCode, result.transcript)
        assertEquals(
            """
                Receiver,Amount,Category
                Shop,-10,Food
            """.trimIndent() + "\n",
            Files.readString(result.output),
        )
        result.assertTranscriptContains("Press Enter to keep the checkbox or x to toggle it.")
    }

    // Rejected CSV and interrupted sessions never create an output file

    @Test
    fun `rejects an empty CSV`() {
        runCategorizerAppWithTestIO("", "").assertRejected(1, "The CSV is empty.")
    }

    @Test
    fun `rejects a missing receiver column`() {
        runCategorizerAppWithTestIO("Date,Amount\n1,-10\n", "")
            .assertRejected(1, "No receiver column found")
    }

    @Test
    fun `rejects an unknown explicit receiver column`() {
        runCategorizerAppWithTestIO("Receiver,Amount\nShop,-10\n", "", receiverColumn = "Vendor")
            .assertRejected(1, "Receiver column not found: Vendor")
    }

    @Test
    fun `rejects a receiver column that is also Category`() {
        runCategorizerAppWithTestIO("Category,Amount\nShop,-10\n", "", receiverColumn = "Category")
            .assertRejected(1, "receiver and Category columns must be different")
    }

    @Test
    fun `rejects duplicate Category columns`() {
        runCategorizerAppWithTestIO("Receiver,Category,CATEGORY\nShop,,\n", "")
            .assertRejected(1, "more than one Category column")
    }

    @Test
    fun `rejects a short CSV row before prompting`() {
        val result = runCategorizerAppWithTestIO("Receiver,Amount\nShop\n", "")

        result.assertRejected(1, "CSV row 2 has 1 fields; expected 2.")
        assertFalse("Transaction 1" in result.transcript)
    }

    @Test
    fun `rejects an unclosed quoted field`() {
        runCategorizerAppWithTestIO("Receiver,Amount\n\"Shop,-10\n", "")
            .assertRejected(1, "Unclosed quoted field")
    }

    @Test
    fun `rejects characters after a closing quote`() {
        runCategorizerAppWithTestIO("Receiver,Amount\n\"Shop\"oops,-10\n", "")
            .assertRejected(1, "Malformed CSV")
    }

    @Test
    fun `end of input at the checkbox leaves no output`() {
        runCategorizerAppWithTestIO("Receiver,Amount\nShop,-10\n", "")
            .assertRejected(1, "Input ended before all transactions were categorized.")
    }

    @Test
    fun `end of input at the category prompt leaves no output`() {
        runCategorizerAppWithTestIO("Receiver,Amount\nShop,-10\n", responses(""))
            .assertRejected(1, "Input ended before all transactions were categorized.")
    }

    @Test
    fun `end of input after completed rows leaves no output`() {
        runCategorizerAppWithTestIO("Receiver,Amount\nShop,-10\nTaxi,-5\n", responses("", "Food"))
            .assertRejected(1, "Input ended before all transactions were categorized.")
    }

    // Existing files are never overwritten

    @Test
    fun `existing output is not overwritten`() {
        val input = inputFile("Receiver,Amount\nShop,-10\n")
        val output = directory.resolve("output.csv")
        Files.writeString(output, "keep me", UTF_8)

        val result = runCategorizerApp(input, output, responses("", "Food"))

        assertEquals(2, result.exitCode, result.transcript)
        result.assertTranscriptContains("Output already exists:")
        assertEquals("keep me", Files.readString(output))
    }

    @Test
    fun `input cannot also be the output`() {
        val source = "Receiver,Amount\nShop,-10\n"
        val input = inputFile(source)

        val result = runCategorizerApp(input, input, responses("", "Food"))

        assertEquals(2, result.exitCode, result.transcript)
        result.assertTranscriptContains("Input and output must be different files.")
        assertEquals(source, Files.readString(input))
    }

    @Test
    fun `missing input file is reported without creating output`() {
        runCategorizerApp(directory.resolve("missing.csv"), directory.resolve("output.csv"), "")
            .assertRejected(2, "Input file does not exist:")
    }

    private fun inputFile(csv: String): Path = directory.resolve("input.csv").also {
        Files.writeString(it, csv, UTF_8)
    }

    private fun runCategorizerAppWithTestIO(
        csvInput: String,
        terminalResponses: String,
        receiverColumn: String? = null,
    ): CliResult = runCategorizerApp(inputFile(csvInput), directory.resolve("output.csv"), terminalResponses, receiverColumn)

    private fun runCategorizerApp(
        input: Path,
        output: Path,
        terminalResponses: String,
        receiverColumn: String? = null,
    ): CliResult {
        val javaName = if (System.getProperty("os.name").contains("win", ignoreCase = true)) "java.exe" else "java"
        val command = buildList {
            add(Path.of(System.getProperty("java.home"), "bin", javaName).toString())
            addAll(listOf("-cp", System.getProperty("java.class.path"), "TransactionCategorizer"))
            addAll(listOf(input.toString(), output.toString()))
            receiverColumn?.let(::add)
        }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        process.outputStream.use { it.write(terminalResponses.toByteArray(UTF_8)) }
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

    private fun responses(vararg lines: String): String = lines.joinToString("\n", postfix = "\n")

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
