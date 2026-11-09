package br.com.air.extrato;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtratoQaTest {
    @TempDir Path dir;

    @Test void quantidadeGrandeMantemOsTresDigitosFinais() throws Exception {
        Path mock = dir.resolve("mock");
        Files.writeString(mock, "1|A|CPE|x|1|2147483648|4294967295|1|20260101|A\n");
        var op = Fontes.mock(mock).getFirst();
        assertEquals(648, op.parcelas());
        assertEquals(295, op.pagas());
    }

    @Test void saidaNaoPodeSobrescreverMock() throws Exception {
        Path mock = dir.resolve("mock");
        String input = "1|A|CPE|x|1|1|0|1|20260101|A\n";
        Files.writeString(mock, input, StandardCharsets.ISO_8859_1);
        int status = new Main().run("--mock", mock.toString(), "--saida", mock.toString(),
                "--log", dir.resolve("log").toString());
        assertEquals(12, status);
        assertEquals(input, Files.readString(mock, StandardCharsets.ISO_8859_1));
    }

    @Test void saidaELogNaoPodemSerMesmoArquivoPorLink() throws Exception {
        Path saida = dir.resolve("saida"), log = dir.resolve("log"), mock = dir.resolve("mock");
        Files.writeString(saida, "anterior");
        Files.createSymbolicLink(log, saida);
        Files.writeString(mock, "1|A|CPE|x|1|1|0|1|20260101|A\n");
        int status = new Main().run("--mock", mock.toString(), "--saida", saida.toString(),
                "--log", log.toString());
        assertEquals(12, status);
        assertEquals("anterior", Files.readString(saida));
    }
}
