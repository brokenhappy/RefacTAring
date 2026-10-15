import java.nio.file.Files;
import java.nio.file.Path;

public final class TransactionCategorizer {
    private TransactionCategorizer() {
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: TransactionCategorizer <input.csv> <output.csv> [receiver-column]");
            System.exit(2);
        }

        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        if (input.equals(output)) {
            System.err.println("Input and output must be different files.");
            System.exit(2);
        }
        if (!Files.isRegularFile(input)) {
            System.err.println("Input file does not exist: " + input);
            System.exit(2);
        }

        System.out.println("Ready to categorize " + input + " into " + output);
    }
}
