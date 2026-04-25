# Gradle Wrapper JAR

The `gradle-wrapper.jar` binary file is not included in this repository.

## How to generate it

Run one of the following from the project root:

```bash
# Option 1: If Gradle is installed locally
gradle wrapper --gradle-version 8.9

# Option 2: Copy from an existing Android project
# Copy gradle/wrapper/gradle-wrapper.jar from any Android Studio project

# Option 3: Download via Android Studio
# Open the project in Android Studio — it will automatically download
# and set up the Gradle wrapper including the JAR file.
```

The `gradle-wrapper.jar` is a small bootstrap JAR (~60KB) that downloads the
specified Gradle distribution on first use. It is normally committed to version
control but is excluded here to keep the repository lightweight.
