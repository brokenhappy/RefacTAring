# RefacTAring

## Run the project

Use the checked-in Gradle wrapper; no system Java installation or `JAVA_HOME`
configuration is required:

```sh
./gradlew test
```

On Windows, run:

```bat
gradlew.bat test
```

The first run downloads a project-local Eclipse Temurin 21 JDK into `.jdks/`.
Later runs reuse it. The build itself is pinned to Java 21, so the same Java
version is used by the command line and IDE imports regardless of any Java
version installed globally.
