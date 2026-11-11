package ie.ucd.meitheal.cli;

import ie.ucd.meitheal.app.InMemoryRegistrationService;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Starts the menu with a fresh in-memory service. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        var service = InMemoryRegistrationService.create();
        var input = new InputStreamReader(System.in, StandardCharsets.UTF_8);
        var output = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
        String original = null;
        if (System.console() != null) {
            try {
                original = stty("-g").trim();
            } catch (IOException unavailable) {
                System.err.println("Terminal key mode unavailable. Submit input with Enter.");
            }
        }
        if (original == null || original.isEmpty()) {
            new RegistrationConsole(input, output, service).run();
            return;
        }

        String settings = original;
        Thread restore = new Thread(() -> {
            try {
                stty(settings);
            } catch (IOException failure) {
                System.err.println("Could not restore terminal settings: " + failure.getMessage());
            }
        }, "restore-terminal");
        Runtime.getRuntime().addShutdownHook(restore);
        try {
            stty("-icanon", "-echo", "min", "1", "time", "0");
            new RegistrationConsole(input, output, service, true).run();
        } finally {
            stty(settings);
            Runtime.getRuntime().removeShutdownHook(restore);
        }
    }

    private static String stty(String... options) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("stty");
        command.addAll(List.of(options));
        Process process = new ProcessBuilder(command)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        try (var stdout = process.getInputStream()) {
            byte[] bytes = stdout.readAllBytes();
            if (process.waitFor() != 0) {
                throw new IOException("stty failed to read or update the terminal");
            }
            return new String(bytes, StandardCharsets.US_ASCII);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while configuring terminal input", interrupted);
        }
    }
}
