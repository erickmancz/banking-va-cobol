package br.com.air.extrato;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchCliIT {
    @TempDir Path dir;

    // Literal field widths and padding are independent of the production formatter.
    private static final String HEADER = " BANCO AIR S.A.                CREDIT OPERATIONS STATEMENT"
            + "                                                  " + "                        ";
    private static final String FIRST = "A-FIRST       " + "  " + "CAFÉ CREDIT         " + "  "
            + "           1.25" + "  " + "     2" + "  " + "     1" + "  "
            + "           0.99" + "  " + "01/02/2026" + "  " + "DELINQUENT " + "                     ";
    private static final String SECOND = "B-SECOND      " + "  " + "PERSONAL LOAN       " + "  "
            + "          12.34" + "  " + "    12" + "  " + "     3" + "  "
            + "           9.01" + "  " + "31/12/2026" + "  " + "ACTIVE     " + "                     ";
    private static final String THIRD = "0-INPUT-FIRST " + "  " + "PAYROLL LOAN        " + "  "
            + "         200.10" + "  " + "    10" + "  " + "     5" + "  "
            + "         100.50" + "  " + "29/02/2028" + "  " + "ACTIVE     " + "                     ";
    private static final String FOOTER = "TOTAL OPERATIONS:     " + "      3" + "                    "
            + "TOTAL BALANCE:          " + "         110.50" + "                                            ";
    private static final String EMPTY_FOOTER = "TOTAL OPERATIONS:     " + "      0" + "                    "
            + "TOTAL BALANCE:          " + "           0.00" + "                                            ";
    private static final String ONE_FOOTER = "TOTAL OPERATIONS:     " + "      1" + "                    "
            + "TOTAL BALANCE:          " + "           0.99" + "                                            ";
    private static final String INPUT =
            "0042123456|0-INPUT-FIRST|CSG|PAYROLL LOAN|200.10|10|5|100.50|20280229|A|0042|20260508\n"
            + "0042123456|B-SECOND|CPE|PERSONAL LOAN|12.34|12|3|9.01|20261231|A|0042|20260507\n"
            + "0042123456|A-FIRST|CPE|CAFÉ CREDIT|1.25|2|1|0.99|20260201|I|0042|20260508\n"
            + "9999123456|ACCOUNT-OUT|CPE|OTHER ACCOUNT|1|1|0|1|20260101|A|0042|20260508\n"
            + "0042123456|BRANCH-OUT|CPE|OTHER BRANCH|1|1|0|1|20260101|A|0043|20260508\n"
            + "0042123456|STATUS-OUT|CPE|CLOSED|1|1|0|1|20260101|Q|0042|20260508\n"
            + "0042123456|FUTURE-OUT|CPE|FUTURE|1|1|0|1|20260101|A|0042|20260509\n";

    private int run(String... args) throws Exception {
        return run(Map.of(), args);
    }

    private int run(Map<String, String> environment, String... args) throws Exception {
        return runWithHeap(environment, null, args);
    }

    private int runWithHeap(Map<String, String> environment, String heap, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (heap != null) command.add("-Xmx" + heap);
        command.add("-jar");
        command.add(Path.of("target/quarkus-app/quarkus-run.jar").toAbsolutePath().toString());
        command.addAll(java.util.List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(dir.resolve("console").toFile());
        builder.environment().putAll(environment);
        Process process = builder.start();
        if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("CLI timed out"); }
        return process.exitValue();
    }

    private String[] args(Path input, Path output, Path log, String charset) {
        return new String[] {"--source", "mock", "--mock", input.toString(), "--account", "0042123456",
                "--branch", "0042", "--date", "20260508", "--output", output.toString(), "--log", log.toString(),
                "--encoding", charset};
    }

    private void assertRecords(Path output, Charset charset, String... records) throws Exception {
        for (String record : records) assertEquals(132, record.length(), "Expected literal must be 132 characters");
        String expected = String.join("", records);
        byte[] expectedBytes = expected.getBytes(charset);
        assertEquals(records.length * 132, expectedBytes.length, "Expected records must be 132 bytes each");
        assertEquals(expected, new String(expectedBytes, charset), "Expected records must round-trip");
        assertArrayEquals(expectedBytes, Files.readAllBytes(output), "Complete fixed-record output differs");
    }

    private void assertLog(Path log, Charset charset, String finalLevelAndMessage) throws Exception {
        byte[] bytes = Files.readAllBytes(log);
        assertEquals(264, bytes.length, "Expected exactly two 132-byte log records");
        String records = new String(bytes, charset);
        assertArrayEquals(bytes, records.getBytes(charset), "Log must round-trip through the selected charset");
        assertLogRecord(records.substring(0, 132), "INFO CREDIT STATEMENT STARTED");
        assertLogRecord(records.substring(132), finalLevelAndMessage);
    }

    private void assertLogRecord(String record, String levelAndMessage) {
        assertTrue(record.substring(0, 6).matches("(?:[01][0-9]|2[0-3])[0-5][0-9][0-5][0-9]"), "Log must begin with a valid HHmmss time");
        String expected = "  PGMEXTRT " + levelAndMessage;
        assertEquals(expected + " ".repeat(126 - expected.length()), record.substring(6), "Fixed-record log differs");
    }

    @Test void correctedGoldenIncludesAllSelectedRowsAndSortsThem() throws Exception {
        Path input = dir.resolve("input"), output = dir.resolve("output"), log = dir.resolve("log");
        Files.writeString(input, INPUT, StandardCharsets.ISO_8859_1);
        assertEquals(0, run(args(input, output, log, "ISO-8859-1")));
        assertRecords(output, StandardCharsets.ISO_8859_1, HEADER, FIRST, SECOND, THIRD, FOOTER);
        assertLog(log, StandardCharsets.ISO_8859_1, "INFO CREDIT STATEMENT COMPLETED");
    }

    @Test void defaultMockSelectionMatchesExplicitSelection() throws Exception {
        Path input = dir.resolve("input"), output = dir.resolve("output"), log = dir.resolve("log");
        Files.writeString(input, INPUT, StandardCharsets.ISO_8859_1);
        assertEquals(0, run("--mock", input.toString(), "--output", output.toString(), "--log", log.toString()));
        assertRecords(output, StandardCharsets.ISO_8859_1, HEADER, FIRST, SECOND, THIRD, FOOTER);
        assertLog(log, StandardCharsets.ISO_8859_1, "INFO CREDIT STATEMENT COMPLETED");
        byte[] defaults = Files.readAllBytes(output);
        assertEquals(0, run(args(input, output, log, "ISO-8859-1")));
        assertArrayEquals(defaults, Files.readAllBytes(output));
    }

    @Test void helpExplainsDb2RequirementsAndMockDefaults() throws Exception {
        assertEquals(0, run("--help"));
        assertTrue(Files.readString(dir.resolve("console")).contains(
                "For db2, --account, --branch and --date are required explicitly; mock uses defaults when omitted."));
        assertTrue(Files.readString(dir.resolve("console")).contains("--summary-log adds a separate success log record"));
    }

    @Test void summaryFlagHasNoValueAndCannotBeRepeated() throws Exception {
        for (String[] args : List.of(new String[] {"--summary-log", "--summary-log"},
                new String[] {"--summary-log", "true"}, new String[] {"--summary-log=true"})) {
            assertEquals(12, run(args));
            assertTrue(Files.readString(dir.resolve("console")).contains("Batch failed:"));
        }
    }

    @Test void summaryAndDefaultBytesForZeroOneAndFilteredMultipleInBothEncodings() throws Exception {
        Path input = dir.resolve("input"), output = dir.resolve("output"), log = dir.resolve("log");
        for (String encoding : List.of("ISO-8859-1", "IBM037")) {
            Charset charset = Charset.forName(encoding);
            for (int count : List.of(0, 1, 3)) {
                String data = count == 0 ? "" : count == 1 ? INPUT.lines().filter(s -> s.contains("A-FIRST")).findFirst().orElseThrow() + "\n" : INPUT;
                Files.writeString(input, data, StandardCharsets.ISO_8859_1);
                String[] expected = count == 0 ? new String[] {HEADER, EMPTY_FOOTER}
                        : count == 1 ? new String[] {HEADER, FIRST, ONE_FOOTER}
                        : new String[] {HEADER, FIRST, SECOND, THIRD, FOOTER};
                String[] defaults = args(input, output, log, encoding);
                assertEquals(0, run(defaults));
                assertRecords(output, charset, expected);
                assertLog(log, charset, "INFO CREDIT STATEMENT COMPLETED");
                List<String> options = new ArrayList<>(List.of(defaults));
                options.add("--summary-log");
                assertEquals(0, run(options.toArray(String[]::new)));
                assertRecords(output, charset, expected);
                byte[] bytes = Files.readAllBytes(log);
                assertEquals(396, bytes.length);
                String records = new String(bytes, charset);
                assertArrayEquals(bytes, records.getBytes(charset));
                assertLogRecord(records.substring(0, 132), "INFO CREDIT STATEMENT STARTED");
                assertLogRecord(records.substring(132, 264), "INFO CREDIT STATEMENT COMPLETED");
                assertLogRecord(records.substring(264), "INFO OPERATIONS: " + count + " LINES: " + (count + 2) + " ERRORS: 0");
            }
        }
    }

    @Test void missingDb2SelectionsFailBeforeFilesOrConnection() throws Exception {
        Path output = dir.resolve("output"), log = dir.resolve("log");
        byte[] outputBefore = "existing report".getBytes(StandardCharsets.UTF_8);
        byte[] logBefore = "existing log".getBytes(StandardCharsets.UTF_8);
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(300);
            Map<String, String> env = Map.of("EXTRATO_DB2_URL", "jdbc:db2://127.0.0.1:" + server.getLocalPort() + "/TESTDB",
                    "EXTRATO_DB2_USER", "secret-user", "EXTRATO_DB2_PASSWORD", "secret-password");
            for (String missing : List.of("account", "branch", "date", "all")) {
                Files.write(output, outputBefore);
                Files.write(log, logBefore);
                List<String> options = new ArrayList<>(List.of("--source", "db2", "--output", output.toString(), "--log", log.toString()));
                if (!missing.equals("account") && !missing.equals("all")) options.addAll(List.of("--account", "secret123"));
                if (!missing.equals("branch") && !missing.equals("all")) options.addAll(List.of("--branch", "0042"));
                if (!missing.equals("date") && !missing.equals("all")) options.addAll(List.of("--date", "20260508"));
                assertEquals(12, run(env, options.toArray(String[]::new)), missing);
                assertArrayEquals(outputBefore, Files.readAllBytes(output), missing);
                assertArrayEquals(logBefore, Files.readAllBytes(log), missing);
                String console = Files.readString(dir.resolve("console"));
                assertTrue(console.contains("DB2 requires explicit --account, --branch and --date"), missing);
                assertFalse(console.contains("secret"), missing);
                assertThrows(SocketTimeoutException.class, server::accept, missing);
            }
        }
    }

    @Test void explicitDb2SelectionsIncludingDemoValuesReachJdbc() throws Exception {
        Path output = dir.resolve("output"), log = dir.resolve("log");
        for (String[] selection : List.of(new String[] {"0042123456", "0042", "20260508"},
                new String[] {"1234567890", "1234", "20240101"})) {
            try (ServerSocket server = new ServerSocket(0)) {
                server.setSoTimeout(20000);
                var accepted = CompletableFuture.runAsync(() -> {
                    try (var socket = server.accept()) { /* Close without speaking DB2. */ }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
                Map<String, String> env = Map.of("EXTRATO_DB2_URL", "jdbc:db2://127.0.0.1:" + server.getLocalPort() + "/TESTDB:loginTimeout=5;",
                        "EXTRATO_DB2_USER", "secret-user", "EXTRATO_DB2_PASSWORD", "secret-password");
                assertEquals(12, run(env, "--source", "db2", "--account", selection[0], "--branch", selection[1],
                        "--date", selection[2], "--output", output.toString(), "--log", log.toString()));
                accepted.get(20, TimeUnit.SECONDS);
                assertEquals(0, Files.size(output));
                assertLog(log, StandardCharsets.ISO_8859_1, "ERROR BATCH PROCESSING FAILED: DB2 operation failed");
                String console = Files.readString(dir.resolve("console"));
                assertFalse(console.contains("secret"));
                assertFalse(console.contains("DB2 requires explicit"));
            }
        }
    }

    @Test void ibm037GoldenUsesTheSameIndependentRecords() throws Exception {
        Path input = dir.resolve("input"), output = dir.resolve("output"), log = dir.resolve("log");
        Files.writeString(input, INPUT, StandardCharsets.ISO_8859_1);
        Charset charset = Charset.forName("IBM037");
        assertEquals(0, run(args(input, output, log, "IBM037")));
        assertRecords(output, charset, HEADER, FIRST, SECOND, THIRD, FOOTER);
        assertLog(log, charset, "INFO CREDIT STATEMENT COMPLETED");
    }

    @Test void noMatchProducesHeaderAndZeroFooter() throws Exception {
        Path input = dir.resolve("input"), output = dir.resolve("output"), log = dir.resolve("log");
        Files.writeString(input, INPUT, StandardCharsets.ISO_8859_1);
        String[] args = args(input, output, log, "ISO-8859-1");
        args[5] = "OTHER"; // Account, distinct from every input row.
        assertEquals(0, run(args));
        assertRecords(output, StandardCharsets.ISO_8859_1, HEADER, EMPTY_FOOTER);
        assertLog(log, StandardCharsets.ISO_8859_1, "INFO CREDIT STATEMENT COMPLETED");
    }

    @Test void malformedInputFailsBeforeWritingReportAndDoesNotExposeInput() throws Exception {
        Path input = dir.resolve("input"), output = dir.resolve("output"), log = dir.resolve("log");
        Files.writeString(input, "sensitive malformed input\n", StandardCharsets.ISO_8859_1);
        assertEquals(12, run(args(input, output, log, "ISO-8859-1")));
        assertEquals(0, Files.size(output));
        assertLog(log, StandardCharsets.ISO_8859_1, "ERROR BATCH PROCESSING FAILED: Invalid mock field count at line 1");
        String console = Files.readString(dir.resolve("console"));
        assertTrue(console.contains("Batch processing failed: Invalid mock field count at line 1"));
        assertFalse(console.contains("sensitive malformed input"));
        assertEquals(12, run("--mock", input.toString(), "--output", input.toString(), "--log", log.toString()));
        assertEquals("sensitive malformed input\n", Files.readString(input));
    }

    @Test void hugeExponentAmountsFailSafelyUnderConstrainedHeap() throws Exception {
        Path input = dir.resolve("input"), output = dir.resolve("output"), log = dir.resolve("log");
        for (String account : List.of("0042123456", "9999123456")) {
            for (boolean original : List.of(true, false)) {
                for (String amount : List.of("1e100000000", "-1e-100000000")) {
                    Files.writeString(input, account + "|SENTINEL|CPE|SENTINEL|" + (original ? amount : "1") + "|1|0|"
                            + (original ? "1" : amount) + "|20260101|A\n", StandardCharsets.ISO_8859_1);
                    assertEquals(12, runWithHeap(Map.of(), "64m", args(input, output, log, "ISO-8859-1")));
                    assertEquals(0, Files.size(output));
                    String field = original ? "original amount" : "balance";
                    assertLog(log, StandardCharsets.ISO_8859_1, "ERROR BATCH PROCESSING FAILED: Invalid mock " + field + " at line 1");
                    String console = Files.readString(dir.resolve("console"));
                    assertTrue(console.contains("Invalid mock " + field + " at line 1"));
                    assertFalse(console.contains("SENTINEL"));
                    assertFalse(console.contains(amount));
                    assertFalse(Files.readString(log).contains("SENTINEL"));
                }
            }
        }
        Files.writeString(input, INPUT.replace("|1.25|2|1|0.99|", "|1.2500e0|2|1|99e-2|")
                .replace("|1|1|0|1|20260101|A|0042|20260508", "|0e100000000|1|0|0e-100000000|20260101|A|0042|20260508"), StandardCharsets.ISO_8859_1);
        assertEquals(0, runWithHeap(Map.of(), "64m", args(input, output, log, "ISO-8859-1")));
        assertRecords(output, StandardCharsets.ISO_8859_1, HEADER, FIRST, SECOND, THIRD, FOOTER);
        assertLog(log, StandardCharsets.ISO_8859_1, "INFO CREDIT STATEMENT COMPLETED");
    }

    @Test void formattingFailureLeavesPartialReportAndFatalLog() throws Exception {
        Path input = dir.resolve("input"), output = dir.resolve("output"), log = dir.resolve("log");
        Files.writeString(input, "0042123456|A-FIRST|CPE|CAFÉ CREDIT|1.25|2|1|0.99|20260201|I|0042|20260508\n"
                + "0042123456|B-FAIL|CPE|DESCRIPTION IS TOO LONG|1|1|0|1|20260101|A|0042|20260508\n", StandardCharsets.ISO_8859_1);
        assertEquals(12, run(args(input, output, log, "ISO-8859-1")));
        assertRecords(output, StandardCharsets.ISO_8859_1, HEADER, FIRST);
        assertLog(log, StandardCharsets.ISO_8859_1, "ERROR BATCH PROCESSING FAILED: Invalid or oversized fixed-width field");
        assertTrue(Files.readString(dir.resolve("console")).contains("Batch processing failed: Invalid or oversized fixed-width field"));
    }
}
