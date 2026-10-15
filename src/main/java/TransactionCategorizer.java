import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

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
        if (Files.exists(output)) {
            System.err.println("Output already exists: " + output);
            System.exit(2);
        }

        try {
            String csv = Files.readString(input, StandardCharsets.UTF_8);
            boolean hasBom = csv.startsWith("\uFEFF");
            List<List<String>> rows = parseCsv(hasBom ? csv.substring(1) : csv);
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("The CSV is empty.");
            }
            String lineEnding = csv.contains("\r\n") ? "\r\n" : csv.contains("\r") ? "\r" : "\n";
            Files.writeString(output, (hasBom ? "\uFEFF" : "") + toCsv(rows, lineEnding),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            System.out.println("Wrote " + output);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean afterQuote = false;

        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        afterQuote = true;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == ',' || c == '\r' || c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                afterQuote = false;
                if (c != ',') {
                    rows.add(row);
                    row = new ArrayList<>();
                    if (c == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                        i++;
                    }
                }
            } else if (c == '"' && field.isEmpty() && !afterQuote) {
                inQuotes = true;
            } else if (c == '"' || afterQuote) {
                throw new IllegalArgumentException("Malformed CSV near character " + (i + 1) + ".");
            } else {
                field.append(c);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Unclosed quoted field in CSV.");
        }
        if (!row.isEmpty() || !field.isEmpty() || afterQuote) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static String toCsv(List<List<String>> rows, String lineEnding) {
        StringBuilder csv = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    csv.append(',');
                }
                String field = row.get(i);
                if (field.indexOf(',') >= 0 || field.indexOf('"') >= 0
                        || field.indexOf('\r') >= 0 || field.indexOf('\n') >= 0) {
                    csv.append('"').append(field.replace("\"", "\"\"")).append('"');
                } else {
                    csv.append(field);
                }
            }
            csv.append(lineEnding);
        }
        return csv.toString();
    }
}
