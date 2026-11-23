package br.com.air.extrato;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchCliIT {
    @TempDir Path dir;
    private int run(String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-jar");
        command.add(Path.of("target/quarkus-app/quarkus-run.jar").toAbsolutePath().toString());
        command.addAll(java.util.List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(dir.resolve("console").toFile()).start();
        if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("CLI timed out"); }
        return process.exitValue();
    }
    @Test void packagedCliProducesRecordsAndReturnsFatalOnMalformedInput() throws Exception {
        Path input = dir.resolve("input"), output = dir.resolve("output"), log = dir.resolve("log");
        Files.writeString(input, "0042123456|Z|CSG|CUSTOMER DATA|12.34|12|3|9.01|20261231|A|0042|20260508\n"
                + "0042123456|A|CPE|VALUE|1|2|1|100.50|20260201|I|0042|20260501\n", StandardCharsets.ISO_8859_1);
        String[] args = {"--mock", input.toString(), "--output", output.toString(), "--log", log.toString()};
        assertEquals(0, run(args));
        byte[] bytes = Files.readAllBytes(output);
        assertEquals(528, bytes.length);
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(content.substring(132, 264).startsWith("A"));
        assertTrue(content.substring(264, 396).startsWith("Z"));
        assertTrue(content.substring(396).contains("109.51"));
        assertEquals(264, Files.size(log));
        Files.writeString(input, "malformed\n");
        assertEquals(12, run(args));
        String error = Files.readString(dir.resolve("console"));
        assertTrue(error.contains("Invalid mock field count at line 1"));
        assertFalse(error.contains("malformed"));
        assertTrue(Files.readString(log).contains("Invalid mock field count at line 1"));
        assertEquals(12, run("--mock", input.toString(), "--output", input.toString(), "--log", log.toString()));
        assertEquals("malformed\n", Files.readString(input));
    }
}
