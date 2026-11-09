package br.com.air.extrato;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtratoTest {
    @TempDir Path dir;

    private Operacao op(String id, String status, String data, String valor) {
        return new Operacao("0001", id, "CPE", "DESCRICAO", new BigDecimal("12.34"), 12, 3,
                new BigDecimal(valor), data, status);
    }

    @Test void registrosFixosPrimeiroDescartadoERodape() throws Exception {
        Path saida = dir.resolve("saida"), log = dir.resolve("log");
        Extrato.executar(List.of(op("PRIMEIRO", "A", "20260601", "9"),
                op("SEGUNDO", "I", "20261231", "100.50")), saida, log, LocalTime.NOON);
        String s = Files.readString(saida, StandardCharsets.ISO_8859_1);
        assertEquals(396, s.length());
        assertTrue(s.substring(0, 132).startsWith(" BANCO AIR S.A."));
        assertTrue(s.substring(132, 264).startsWith("SEGUNDO"));
        assertTrue(s.substring(132, 264).contains("31/12/2026  INADIMPLENT"));
        assertTrue(s.substring(264).contains("1,00"));
        assertEquals(132, Files.size(log));
    }
    @Test void vazioEUmRegistroNaoGeramDetalhes() throws Exception {
        for (var ops : List.of(List.<Operacao>of(), List.of(op("X", "A", "20260601", "1")))) {
            Path saida = dir.resolve("s"), log = dir.resolve("l");
            Extrato.executar(ops, saida, log, LocalTime.MIDNIGHT);
            assertEquals(264, Files.size(saida));
        }
    }
    @Test void mockPreservaOrdemEIgnoraFiltros() throws Exception {
        Path input = dir.resolve("mock");
        Files.writeString(input, "# comentário\nlinha ruim\n1|A|CPE|x|1|1|0|1|20260101|Q\n2|B|CPE|y|2|2|1|2|20260202|R\n");
        var ops = Fontes.mock(input);
        assertEquals(List.of("A", "B"), ops.stream().map(Operacao::contrato).toList());
        assertEquals("R", ops.get(1).status());
        assertTrue(Formato.detalhe(ops.get(1)).contains("DESCONHECID"));
        assertTrue(Formato.detalhe(ops.get(0)).contains("QUITADO"));
    }
    @Test void mockVazioUsaTabelaPadrao() throws Exception {
        Path input = dir.resolve("empty"); Files.writeString(input, "# comentario\n");
        assertEquals(3, Fontes.mock(input).size());
        assertEquals(3, Fontes.mock(dir.resolve("ausente")).size());
    }
    @Test void dataIsoTruncadaSemCorrigir() {
        assertTrue(Formato.detalhe(op("X", "A", "2026-06-", "0")).contains("6-/-0/2026"));
    }
    @Test void formatosNumericosEContagemLimitada() {
        assertEquals("          0,00 ", Formato.editado(BigDecimal.ZERO));
        assertEquals("        123,45 ", Formato.editado(new BigDecimal("12345.67")));
        assertEquals(Formato.rodape(1, BigDecimal.TEN), Formato.rodape(1001, BigDecimal.TEN));
    }
    @Test void validacoesDeCliEArquivos() {
        assertThrows(IllegalArgumentException.class, () -> Main.argumentos(new String[]{"--fonte"}));
        assertThrows(IllegalArgumentException.class, () -> Main.argumentos(new String[]{"--foo", "bar"}));
        assertThrows(IllegalArgumentException.class, () -> Main.argumentos(new String[]{"--data", "1", "--data", "2"}));
        assertThrows(IllegalArgumentException.class, () -> Extrato.executar(List.of(), dir.resolve("x"), dir.resolve("x"), LocalTime.NOON));
    }
    @Test void queryDocumentaFiltrosEOrdemDb2() {
        assertTrue(Fontes.SQL.contains("OC.CD_STATUS IN ('A', 'I')"));
        assertTrue(Fontes.SQL.contains("OC.DT_REFERENCIA <= ?"));
        assertTrue(Fontes.SQL.contains("ORDER BY OC.CD_MODALIDADE, OC.NR_CONTRATO WITH UR"));
    }
    @Test void mockNumerosTruncadosComoPic() throws Exception {
        Path input = dir.resolve("m"); Files.writeString(input, "1|A|CPE|x|1.239|2500|1|2.555|20260101|A\n");
        var op = Fontes.mock(input).getFirst();
        assertEquals(500, op.parcelas());
        assertEquals(new BigDecimal("1.23"), op.original());
        assertEquals(new BigDecimal("2.55"), op.saldo());
    }
    @Test void totalPassaPeloCampoDeOnzeDigitosAntesDaEdicao() throws Exception {
        Path saida = dir.resolve("saida"), log = dir.resolve("log");
        Extrato.executar(List.of(op("descartado", "A", "20260601", "0"),
                op("primeiro", "A", "20260601", "60000000000.00"),
                op("segundo", "A", "20260601", "60000000000.00")), saida, log, LocalTime.NOON);
        String rodape = Files.readString(saida, StandardCharsets.ISO_8859_1).substring(396);
        assertEquals(Formato.rodape(2, new BigDecimal("20000000000.00")), rodape);
        assertEquals("  200000000,00", rodape.substring(73, 87));
    }
    @Test void quantidadesUsamAlgarismosAsciiEmLocaleArabe() throws Exception {
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            Path saida = dir.resolve("saida"), log = dir.resolve("log");
            Extrato.executar(List.of(op("descartado", "A", "20260601", "0"),
                    op("detalhe", "A", "20260601", "1")), saida, log, LocalTime.NOON);
            String detalhe = Files.readString(saida, StandardCharsets.ISO_8859_1).substring(132, 264);
            assertEquals("  12", detalhe.substring(54, 58));
            assertEquals("    3", detalhe.substring(60, 65));
        } finally {
            Locale.setDefault(anterior);
        }
    }
}
