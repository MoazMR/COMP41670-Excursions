# Group 1 - Excursions

Moaz Refaei, Benjamin Long, Joe Biju, Matthew Gartland, Abdus Samad Bhadmus

## Setup

Install a Java 21 JDK and use the included Gradle Wrapper (`./gradlew` on macOS/Linux or `gradlew.bat` on Windows):

```bash
./gradlew clean build test
```

The library-level CLI supports `help` and `exit`. Commands are separated by
whitespace. Arguments can be enclosed in double quotes to preserve spaces,
including empty quoted arguments (`""`). Inside quoted arguments, use `\"` for
a double quote and `\\` for a backslash. An unmatched double quote or any
other/dangling escape is a syntax error.

The focused CLI test command is:

```text
.\gradlew.bat :cli:test
```

Application startup wiring is owned by the shared foundation.
