package br.com.air.extrato;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtratoTest {
    @TempDir Path dir;
    private Operacao op(String id, String status, String amount) {
        return new Operacao("0042123456", id, "CPE", "Credit", new BigDecimal("12.34"), 12, 3,
                new BigDecimal(amount), "20261231", status);
    }
    private int run(List<Operacao> operations, Charset charset) throws Exception {
        return Extrato.executar(() -> Fontes.lista(operations), dir.resolve("output"), dir.resolve("log"),
                LocalTime.NOON, charset, Formato::detalhe);
    }
    @Test void allRowsAndExactCentsAppearInFixedRecords() throws Exception {
        assertEquals(0, run(List.of(op("FIRST", "A", "9.01"), op("SECOND", "I", "100.50")), StandardCharsets.ISO_8859_1));
        String output = Files.readString(dir.resolve("output"), StandardCharsets.ISO_8859_1);
        assertEquals(4 * 132, output.length());
        assertTrue(output.substring(132, 264).startsWith("FIRST"));
        assertEquals("FIRST         " + "  " + "Credit              " + "  " + "          12.34" + "  "
                + "    12" + "  " + "     3" + "  " + "           9.01" + "  "
                + "31/12/2026" + "  " + "ACTIVE     " + " ".repeat(21), output.substring(132, 264));
        assertTrue(output.substring(264, 396).contains("31/12/2026  DELINQUENT"));
        assertTrue(output.substring(396).contains("109.51"));
        assertEquals(264, Files.size(dir.resolve("log")));
    }
    @Test void emptyAndSingleRowProduceExpectedCounts() throws Exception {
        assertEquals(0, run(List.of(), StandardCharsets.ISO_8859_1));
        assertEquals(264, Files.size(dir.resolve("output")));
        assertEquals(0, run(List.of(op("ONLY", "A", "0")), StandardCharsets.ISO_8859_1));
        assertEquals(396, Files.size(dir.resolve("output")));
    }
    @Test void selectionAndOrderingIncludeAllFilters() throws Exception {
        Path input = dir.resolve("mock");
        Files.writeString(input, String.join("\n", List.of(
                "0042123456|Z|CSG|customer text|12.34|12|3|9.01|20261231|A|0042|20260508",
                "0042123456|B|CPE|x|1|2|1|1|20260201|I|0042|20260501",
                "0042123456|A|CPE|x|1|2|1|1|20260201|A|0042|20260501",
                "0042123456|FUTURE|CPE|x|1|2|1|1|20260201|A|0042|20260509",
                "0042123456|STATUS|CPE|x|1|2|1|1|20260201|Q|0042|20260501",
                "0042123456|BRANCH|CPE|x|1|2|1|1|20260201|A|0043|20260501",
                "0042123457|ACCOUNT|CPE|x|1|2|1|1|20260201|A|0042|20260501")));
        try (var source = Fontes.abrirMock(input, "0042123456", "0042", LocalDate.of(2026, 5, 8))) {
            assertEquals("A", source.proxima().contrato());
            assertEquals("B", source.proxima().contrato());
            assertEquals("Z", source.proxima().contrato());
            assertNull(source.proxima());
        }
    }
    @Test void legacyTenFieldsDeriveBranchAndUseCutoff() throws Exception {
        Path input = dir.resolve("mock");
        Files.writeString(input, "0042123456|A|CPE|raw description|1|1|0|1|20260201|A\n");
        try (var source = Fontes.abrirMock(input, "0042123456", "0042", LocalDate.of(2026, 5, 8))) {
            assertEquals("raw description", source.proxima().descricao());
            assertNull(source.proxima());
        }
    }
    @Test void shortPaddedAndAlphanumericBranchesAreSelected() throws Exception {
        Path input = dir.resolve("mock"), output = dir.resolve("output"), log = dir.resolve("log");
        for (String branch : List.of("AB", "AB12")) {
            Files.writeString(input, "0042123456|A|CPE|x|1|1|0|1|20260101|A|" + branch + " ".repeat(4 - branch.length()) + "|20260508\n");
            assertEquals(0, new Main().run("--mock", input.toString(), "--branch", branch, "--output", output.toString(), "--log", log.toString()));
            assertEquals(396, Files.size(output));
        }
    }
    @Test void shortAccountWithoutExplicitBranchFailsBeforeSelection() throws Exception {
        Path input = dir.resolve("mock"), output = dir.resolve("output"), log = dir.resolve("log");
        Files.writeString(input, "ABC|A|CPE|x|1|1|0|1|20260101|A\n");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> Fontes.abrirMock(input, "OTHER", "AB", LocalDate.of(2026, 5, 8)));
        assertEquals("Invalid mock unresolvable branch (account shorter than four characters) at line 1", failure.getMessage());
        assertEquals(12, new Main().run("--mock", input.toString(), "--account", "ABC", "--branch", "AB", "--output", output.toString(), "--log", log.toString()));
        assertTrue(Files.readString(log).contains("unresolvable branch (account shorter than four characters) at line 1"));
        Files.writeString(input, "ABC|A|CPE|x|1|1|0|1|20260101|A|AB|20260508\n");
        assertEquals(0, new Main().run("--mock", input.toString(), "--account", "ABC", "--branch", "AB", "--output", output.toString(), "--log", log.toString()));
        assertEquals(396, Files.size(output));
    }
    @Test void malformedMockAndMissingFileFailInsteadOfFallback() throws Exception {
        Path input = dir.resolve("mock");
        Files.writeString(input, "bad\n");
        assertThrows(IllegalArgumentException.class, () -> Fontes.abrirMock(input, "a", "0001", LocalDate.now()));
        assertThrows(java.io.IOException.class, () -> Fontes.abrirMock(dir.resolve("missing"), "a", "0001", LocalDate.now()));
        Files.writeString(input, "# empty\n");
        try (var source = Fontes.abrirMock(input, "a", "0001", LocalDate.now())) { assertNull(source.proxima()); }
    }
    @Test void invalidSelectionKeysAndSelectedPaidCountAreRejected() throws Exception {
        Path input = dir.resolve("mock");
        for (String row : List.of(
                "0042123456X|A|CPE|x|1|1|0|1|20260101|A|0042|20260508",
                "0042123456|A|CPE|x|1|1|0|1|20260101|A|0042X|20260508",
                "0042123456|A|CPE|x|1|1|0|1|20260101|AA|0042|20260508",
                "0042123456|A|CPE|x|1|1|2|1|20260101|A|0042|20260508",
                "0042123456|A|CPE|x|1|1|0|1|00000101|A|0042|20260508")) {
            Files.writeString(input, row);
            assertThrows(IllegalArgumentException.class, () -> Fontes.abrirMock(input, "0042123456", "0042", LocalDate.of(2026, 5, 8)));
        }
        assertThrows(IllegalArgumentException.class, () -> Formato.date("00000101"));
    }
    @Test void monetaryAndCountOverflowFailWithoutTruncation() {
        assertTrue(Formato.rodape(1001, new BigDecimal("12345.67")).contains("1001"));
        assertTrue(Formato.rodape(1001, new BigDecimal("12345.67")).contains("12345.67"));
        assertThrows(IllegalArgumentException.class, () -> Formato.rodape(10_000_000, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Formato.rodape(1, new BigDecimal("1000000000000.00")));
        String boundary = Formato.detalhe(new Operacao("a", "id", "CPE", "x", new BigDecimal("-99999999999.99"), -32768, 32767, new BigDecimal("99999999999.99"), "20260101", "A"));
        assertTrue(boundary.contains("-99999999999.99"));
        assertTrue(boundary.contains("-32768"));
        assertTrue(boundary.contains("32767"));
        assertThrows(ArithmeticException.class, () -> Formato.rodape(1, new BigDecimal("1.001")));
    }
    @Test void mockAmountsRequireExactDecimal13Scale2() throws Exception {
        Path input = dir.resolve("mock");
        for (String[] example : List.of(
                new String[] {"99999999999.99", "99999999999.99"},
                new String[] {"-99999999999.99000", "-99999999999.99"},
                new String[] {"+000123.45000", "123.45"},
                new String[] {"1.2300e+2", "123.00"},
                new String[] {"1000e-5", "0.01"},
                new String[] {"-0e+2147483647", "0.00"},
                new String[] {"0e2147483648", "0.00"},
                new String[] {"0e-2147483647", "0.00"},
                new String[] {"1e10", "10000000000.00"})) {
            Files.writeString(input, "0042123456|A|CPE|x|" + example[0] + "|1|0|" + example[0] + "|20260101|A\n");
            try (var source = Fontes.abrirMock(input, "0042123456", "0042", LocalDate.of(2026, 5, 8))) {
                Operacao op = source.proxima();
                assertEquals(new BigDecimal(example[1]), op.original(), example[0]);
                assertEquals(new BigDecimal(example[1]), op.saldo(), example[0]);
            }
        }
        for (String amount : List.of("100000000000", "-100000000000", "99999999999.991", "0.001",
                "-1e-2147483647", "1e2147483647", "1e100000000", "1e-100000000",
                "0e2147483649", "not-a-number")) {
            for (String account : List.of("0042123456", "9999123456")) {
                for (boolean original : List.of(true, false)) {
                    Files.writeString(input, account + "|A|CPE|x|" + (original ? amount : "1") + "|1|0|"
                            + (original ? "1" : amount) + "|20260101|A\n");
                    var failure = assertThrows(IllegalArgumentException.class,
                            () -> Fontes.abrirMock(input, "0042123456", "0042", LocalDate.of(2026, 5, 8)), amount);
                    assertEquals("Invalid mock " + (original ? "original amount" : "balance") + " at line 1", failure.getMessage());
                }
            }
        }
    }
    @Test void encodingRejectsUnmappableAndMultibyteData() throws Exception {
        var item = new Operacao("a", "é", "CPE", "x", BigDecimal.ONE, 1, 0, BigDecimal.ONE, "20260101", "A");
        assertEquals(12, run(List.of(item), StandardCharsets.UTF_8));
        assertEquals(12, run(List.of(item), StandardCharsets.US_ASCII));
        assertEquals(0, run(List.of(item), Charset.forName("IBM037")));
        assertEquals(396, Files.size(dir.resolve("output")));
    }
    @Test void queryUsesExplicitSelectionParameters() {
        assertTrue(Fontes.SQL.contains("OC.NR_CONTA = ? AND OC.CD_AGENCIA = ?"));
        assertTrue(Fontes.SQL.contains("OC.DT_REFERENCIA <= ?"));
        assertTrue(Fontes.SQL.contains("ORDER BY OC.CD_MODALIDADE, OC.NR_CONTRATO WITH UR"));
        assertEquals("REAL ESTATE FINANCE", Fontes.descricao("RF "));
    }
    @Test void db2RequiresEverySelectionOptionBeforeOutputAccess() throws Exception {
        Path output = dir.resolve("output"), log = dir.resolve("log");
        Files.writeString(output, "report");
        Files.writeString(log, "log");
        for (String[] selection : List.of(new String[] {}, new String[] {"--branch", "0042", "--date", "20260508"},
                new String[] {"--account", "0042123456", "--date", "20260508"},
                new String[] {"--account", "0042123456", "--branch", "0042"})) {
            var arguments = new java.util.ArrayList<>(List.of("--source", "db2", "--output", output.toString(), "--log", log.toString()));
            arguments.addAll(List.of(selection));
            assertEquals(12, new Main().run(arguments.toArray(String[]::new)));
            assertEquals("report", Files.readString(output));
            assertEquals("log", Files.readString(log));
        }
    }
}
