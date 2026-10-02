package com.bloom.app.service.impl;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
class ProcessCommandExecutor {
    private static final int MAX_CAPTURED_OUTPUT_BYTES = 64 * 1024;

    CommandResult execute(
            List<String> command,
            Map<String, String> environment,
            Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
        processBuilder.environment().putAll(environment);
        Process process = processBuilder.start();

        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        AtomicReference<IOException> readerFailure = new AtomicReference<>();
        Thread outputReader = Thread.ofVirtual().name("database-backup-command-output").start(() -> {
            try (InputStream input = process.getInputStream()) {
                drainOutput(input, capturedOutput);
            } catch (IOException exception) {
                readerFailure.set(exception);
            }
        });

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            throw exception;
        }

        if (!finished) {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
        outputReader.join();

        if (readerFailure.get() != null) {
            throw readerFailure.get();
        }
        String output = capturedOutput.toString(StandardCharsets.UTF_8).trim();
        if (!finished) {
            throw new IOException("Command timed out after " + timeout + formatOutput(output));
        }
        return new CommandResult(process.exitValue(), output);
    }

    private void drainOutput(InputStream input, ByteArrayOutputStream capturedOutput)
            throws IOException {
        byte[] buffer = new byte[8192];
        int captured = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            int bytesToCapture = Math.min(read, MAX_CAPTURED_OUTPUT_BYTES - captured);
            if (bytesToCapture > 0) {
                capturedOutput.write(buffer, 0, bytesToCapture);
                captured += bytesToCapture;
            }
        }
    }

    private String formatOutput(String output) {
        return output.isBlank() ? "" : ": " + output;
    }

    record CommandResult(int exitCode, String output) {
    }
}
