import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
            List<String> header = rows.get(0);
            int columnCount = header.size();
            int receiverColumn = -1;
            if (args.length == 3) {
                for (int column = 0; column < columnCount; column++) {
                    if (header.get(column).strip().equalsIgnoreCase(args[2].strip())) {
                        receiverColumn = column;
                        break;
                    }
                }
                if (receiverColumn < 0) {
                    throw new IllegalArgumentException("Receiver column not found: " + args[2]);
                }
            } else {
                for (String candidate : List.of("receiver", "recipient", "payee", "merchant",
                        "counterparty", "beneficiary", "description")) {
                    for (int column = 0; column < columnCount; column++) {
                        if (header.get(column).strip().equalsIgnoreCase(candidate)) {
                            receiverColumn = column;
                            break;
                        }
                    }
                    if (receiverColumn >= 0) {
                        break;
                    }
                }
                if (receiverColumn < 0) {
                    throw new IllegalArgumentException("No receiver column found. Pass its header as the third argument.");
                }
            }
            int categoryColumn = -1;
            for (int column = 0; column < columnCount; column++) {
                if (header.get(column).strip().equalsIgnoreCase("category")) {
                    if (categoryColumn >= 0) {
                        throw new IllegalArgumentException("The CSV has more than one Category column.");
                    }
                    categoryColumn = column;
                }
            }
            for (int record = 1; record < rows.size(); record++) {
                if (rows.get(record).size() != columnCount) {
                    throw new IllegalArgumentException("CSV row " + (record + 1) + " has "
                            + rows.get(record).size() + " fields; expected " + columnCount + ".");
                }
            }
            if (categoryColumn < 0) {
                categoryColumn = header.size();
                header.add("Category");
                for (int record = 1; record < rows.size(); record++) {
                    rows.get(record).add("");
                }
            }

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            Map<String, String> lastCategoryByReceiver = new HashMap<>();
            Set<String> receiversWithoutAutoSelect = new HashSet<>();
            for (int record = 1; record < rows.size(); record++) {
                List<String> row = rows.get(record);
                System.out.printf("%nTransaction %d of %d%n", record, rows.size() - 1);
                for (int column = 0; column < header.size(); column++) {
                    if (column != categoryColumn) {
                        System.out.println(header.get(column) + ": " + row.get(column));
                    }
                }
                String receiver = row.get(receiverColumn).strip();
                String receiverKey = receiver.toLowerCase(Locale.ROOT);
                if (!receiverKey.isEmpty()) {
                    boolean autoSelect = !receiversWithoutAutoSelect.contains(receiverKey);
                    while (true) {
                        System.out.print("Auto-select for " + receiver + "? "
                                + (autoSelect ? "[x]" : "[ ]") + " (Enter=keep, x=toggle): ");
                        System.out.flush();
                        String answer = console.readLine();
                        if (answer == null) {
                            throw new IllegalArgumentException("Input ended before all transactions were categorized.");
                        }
                        if (answer.isBlank()) {
                            break;
                        }
                        if (answer.strip().equalsIgnoreCase("x")) {
                            autoSelect = !autoSelect;
                            break;
                        }
                        System.out.println("Press Enter to keep the checkbox or x to toggle it.");
                    }
                    if (autoSelect) {
                        receiversWithoutAutoSelect.remove(receiverKey);
                    } else {
                        receiversWithoutAutoSelect.add(receiverKey);
                    }
                }
                String category = row.get(categoryColumn).strip();
                if (category.isEmpty() && !receiverKey.isEmpty()
                        && !receiversWithoutAutoSelect.contains(receiverKey)) {
                    category = lastCategoryByReceiver.getOrDefault(receiverKey, "");
                }
                while (true) {
                    System.out.print(category.isEmpty() ? "Category: " : "Category [" + category + "]: ");
                    System.out.flush();
                    String answer = console.readLine();
                    if (answer == null) {
                        throw new IllegalArgumentException("Input ended before all transactions were categorized.");
                    }
                    if (!answer.isBlank()) {
                        category = answer.strip();
                    }
                    if (!category.isEmpty()) {
                        break;
                    }
                    System.out.println("Please enter a category.");
                }
                row.set(categoryColumn, category);
                if (!receiverKey.isEmpty()) {
                    lastCategoryByReceiver.put(receiverKey, category);
                }
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
