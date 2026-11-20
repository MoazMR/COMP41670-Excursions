# Group 1 - Excursions

Moaz Refaei, Benjamin Long, Joe Biju, Matthew Gartland, Abdus Samad Bhadmus

## Setup

Install a Java 21 JDK and use the included Gradle Wrapper (`./gradlew` on macOS/Linux or `gradlew.bat` on Windows):

```bash
./gradlew clean build test
```

## Run the console

Build the local launcher and start the menu in a terminal:

```sh
./gradlew :cli:installDist
./cli/build/install/cli/bin/cli
```

On Windows use `gradlew.bat :cli:installDist` and `cli\build\install\cli\bin\cli.bat`. For a scripted session, pipe newline-separated choices to `./gradlew -q :cli:run`. The installed launcher supports Escape before Enter in a Unix terminal with `stty`; line-oriented terminals submit input with Enter.

Choose **1 Register**, enter a name and email, then choose **0 Back**. Choose **2 Login** and look up the new member by email or ID. Under **11 View balance and history**, choose **1 Balance**. The first successful registration receives member ID `100001` and 120 credits. Data lasts for the current run. The main menu also supports requests and proposals, selection, confirmation, cancellation, member status, community grants, personal history, the global report and the simulated date.

The library-level CLI supports `help` and `exit`. Commands are separated by
whitespace. Arguments can be enclosed in double quotes to preserve spaces,
including empty quoted arguments (`""`). Inside quoted arguments, use `\"` for
a double quote and `\\` for a backslash. An unmatched double quote or any
other/dangling escape is a syntax error.

The focused CLI test command is:

```text
.\gradlew.bat :cli:test
```
