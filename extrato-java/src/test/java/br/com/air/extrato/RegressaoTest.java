package br.com.air.extrato;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegressaoTest {
    @TempDir Path dir;
    @Test void goldenCobolMockOriginal() throws Exception {
        Path saida = dir.resolve("saida"), log = dir.resolve("log");
        Path mock = Path.of("src/test/resources/mock-operacoes.dat");
        assertEquals(0, new Main().run("--mock", mock.toString(), "--saida", saida.toString(), "--log", log.toString()));
        assertArrayEquals(Files.readAllBytes(Path.of("src/test/resources/SAIDA.golden")), Files.readAllBytes(saida));
    }
    @Test void descricaoEValidacaoHost() throws Exception {
        assertEquals("OUTROS", Fontes.descricao("RF "));
        assertEquals("FINANCIAM. IMOVEIS", Fontes.descricao("RFI"));
        assertEquals(999, Fontes.quantidadeDb2(-999));
        assertThrows(SQLException.class, () -> Fontes.quantidadeDb2(1000));
        assertThrows(SQLException.class, () -> Fontes.quantidadeDb2(-1000));
    }
    @Test void erroAoAbrirLimpaSaidaERegistraInicioEErro() throws Exception {
        Path s = dir.resolve("s"), l = dir.resolve("l");
        Files.writeString(s, "anterior"); Files.writeString(l, "anterior");
        int rc = Extrato.executar(() -> { throw new IOException("OPEN FALHOU"); }, s, l, LocalTime.NOON, Formato::detalhe);
        assertEquals(12, rc); assertEquals(0, Files.size(s));
        String log = Files.readString(l, StandardCharsets.ISO_8859_1);
        assertEquals(264, log.length()); assertTrue(log.contains("OPEN FALHOU"));
    }
    @Test void erroFetchPreservaDetalheJaGravado() throws Exception {
        Path s = dir.resolve("s"), l = dir.resolve("l");
        Operacao op = new Operacao("a", "TESTE", "CPE", "X", BigDecimal.ONE, 1, 0, BigDecimal.ONE, "20260101", "A");
        int rc = Extrato.executar(() -> new Fontes.Leitura() {
            int n;
            public Operacao proxima() throws IOException { if (++n <= 2) return op; throw new IOException("FETCH FALHOU"); }
            public void close() { }
        }, s, l, LocalTime.NOON, Formato::detalhe);
        assertEquals(12, rc); assertEquals(264, Files.size(s));
        assertTrue(Files.readString(s).contains("TESTE")); assertTrue(Files.readString(l).contains("FETCH FALHOU"));
    }
    @Test void erroFormatacaoContinuaEProduzRc4() throws Exception {
        var op = new Operacao("a", "TESTE", "CPE", "X", BigDecimal.ONE, 1, 0, BigDecimal.ONE, "20260101", "A");
        Path s = dir.resolve("s"), l = dir.resolve("l");
        int rc = Extrato.executar(() -> Fontes.lista(List.of(op, op)), s, l, LocalTime.NOON,
                o -> { throw new IllegalArgumentException("FORMATO"); });
        assertEquals(4, rc); assertEquals(396, Files.size(s)); assertTrue(Files.readString(l).contains("FORMATO"));
    }
    @Test void protegeLinksDoMockEHardlinks() throws Exception {
        Path input = dir.resolve("input"), link = dir.resolve("link"), log = dir.resolve("log");
        Files.writeString(input, "conteudo"); Files.createLink(link, input);
        assertEquals(12, new Main().run("--mock", input.toString(), "--saida", link.toString(), "--log", log.toString()));
        assertEquals("conteudo", Files.readString(input));
    }
    @Test void rejeitaLinkPendenteEmCadaCaminhoAntesDeAlterarArquivos() throws Exception {
        Path mock = dir.resolve("mock"), saida = dir.resolve("saida"), log = dir.resolve("log");
        Path destino = dir.resolve("inexistente"), link = dir.resolve("link");
        Files.writeString(mock, "dados"); Files.writeString(saida, "saida anterior"); Files.writeString(log, "log anterior");
        Files.createSymbolicLink(link, destino);
        for (String[] args : List.of(
                new String[]{"--mock", mock.toString(), "--saida", link.toString(), "--log", destino.toString()},
                new String[]{"--mock", mock.toString(), "--saida", destino.toString(), "--log", link.toString()},
                new String[]{"--mock", link.toString(), "--saida", saida.toString(), "--log", log.toString()})) {
            assertEquals(12, new Main().run(args));
            assertFalse(Files.exists(destino));
            assertEquals("dados", Files.readString(mock));
            assertEquals("saida anterior", Files.readString(saida));
            assertEquals("log anterior", Files.readString(log));
        }
    }
    @Test void rejeitaDoisLinksPendentesParaMesmoDestino() throws Exception {
        Path destino = dir.resolve("destino"), a = dir.resolve("a"), b = dir.resolve("b"), mock = dir.resolve("mock");
        Files.createSymbolicLink(a, destino); Files.createSymbolicLink(b, destino);
        Files.writeString(mock, "dados");
        assertEquals(12, new Main().run("--mock", mock.toString(), "--saida", a.toString(), "--log", b.toString()));
        assertFalse(Files.exists(destino)); assertEquals("dados", Files.readString(mock));
    }
    @Test void rejeitaLinkPendenteEmDiretorioIntermediario() throws Exception {
        Path link = dir.resolve("link"), destino = dir.resolve("inexistente"), log = dir.resolve("log");
        Files.createSymbolicLink(link, destino); Files.writeString(log, "log anterior");
        assertEquals(12, new Main().run("--mock", dir.resolve("mock").toString(), "--saida", link.resolve("saida").toString(), "--log", log.toString()));
        assertFalse(Files.exists(destino)); assertEquals("log anterior", Files.readString(log));
    }
}
