# RefacTAring

An interactive CLI for categorizing bank transactions. The implementation is
deliberately contained in one Java source file so a group can practice small
refactorings and follow them through the Git history.

## Run it

Use the checked-in Gradle wrapper; no system Java installation or `JAVA_HOME`
configuration is required:

```sh
./gradlew run --args="examples/transactions.csv categorized.csv"
```

On Windows, run:

```bat
gradlew.bat run --args="examples/transactions.csv categorized.csv"
```

The first run downloads a project-local Eclipse Temurin 21 JDK into `.jdks/`.
Later runs reuse it. The build itself is pinned to Java 21, so the same Java
version is used by the command line and IDE imports regardless of any Java
version installed globally.

Pass an input CSV and a new output filename. The output keeps the input columns
and adds `Category`, or fills the existing `Category` column. The input must be
UTF-8, comma-separated CSV with a header. Quoted commas, quotes, and line breaks
are supported. The app never overwrites the input or an existing output file.

The CLI looks for a `Receiver`, `Recipient`, `Payee`, `Merchant`, `Counterparty`,
`Beneficiary`, or `Description` header, in that order. If your bank uses another
name, pass it as the third argument. For example:

```sh
./gradlew run --args='bank.csv categorized.csv "Beneficiary Name"'
```

For each transaction, the CLI shows its fields and asks for a category. Press
Enter to accept the category shown in brackets. A category from the previous
transaction with the same receiver is preselected; receiver matching ignores
case and surrounding spaces. Each receiver also has a text checkbox: `[x]`
means auto-selection is on, and `[ ]` means it is off. Press `x` at that prompt
to toggle it, then enter a category for the current transaction. The choice
applies to later rows for that receiver in this run. If input ends before all
transactions are categorized, no output is written.

In the sample, `Corner, Shop` appears twice. Categorize the first one as
`Groceries` and the second will show `Category [Groceries]`. Toggle off `Taxi`
on its first row and the next `Taxi` row will ask for a fresh category. The
sample output will have the same rows, with a `Category` value on each one.

The checkbox state exists only while processing one CSV. To explore the code's
evolution, run `git log --oneline` and inspect the small commits.

## Verify changes

```sh
./gradlew test
```

The 33 end-to-end tests run the CLI with temporary CSV files and scripted
terminal input. They check the resulting CSV, category suggestions, receiver
checkboxes, CSV quoting and line endings, and failures that must leave input
and output files untouched. The application implementation remains in one Java
file; the tests are in a separate Kotlin test file.
