package br.com.air.extrato;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Db2IT {
    @TempDir Path dir;

    @Test void consultaEExtratoComDadosExternos() throws Exception {
        String url = obrigatoria("EXTRATO_DB2_URL");
        String usuario = obrigatoria("EXTRATO_DB2_USER");
        String senha = obrigatoria("EXTRATO_DB2_PASSWORD");
        String data = obrigatoria("EXTRATO_DB2_IT_DATA");
        String alternativa = obrigatoria("EXTRATO_DB2_IT_DATA_ALTERNATIVA");
        String corteTexto = obrigatoria("EXTRATO_DB2_IT_CORTE");
        Path operacoes = Path.of(obrigatoria("EXTRATO_DB2_IT_OPERACOES"));
        Path golden = Path.of(obrigatoria("EXTRATO_DB2_IT_SAIDA"));
        assertTrue(data.matches("[0-9]{8}") && alternativa.matches("[0-9]{8}") && !data.equals(alternativa),
                "Datas de parâmetro devem ter 8 dígitos e ser distintas");
        LocalDate corte = LocalDate.parse(corteTexto);
        assertTrue(Files.isRegularFile(operacoes) && Files.isReadable(operacoes), "Fixture de operações deve ser arquivo legível");
        assertTrue(Files.isRegularFile(golden) && Files.isReadable(golden), "Golden de SAIDA deve ser arquivo legível");
        List<Operacao> esperado = lerEsperado(operacoes);
        assertTrue(esperado.size() >= 3, "Fixture deve conter pelo menos 3 operações aceitas");
        assertTrue(esperado.stream().allMatch(o -> o.conta().equals(data) && List.of("A", "I").contains(o.status())),
                "Fixture deve conter apenas conta e status aceitos");
        assertTrue(esperado.stream().map(Operacao::modalidade).distinct().count() >= 2,
                "Fixture deve conter modalidades distintas");
        assertTrue(esperado.stream().anyMatch(a -> esperado.stream().anyMatch(b -> a != b
                && a.modalidade().equals(b.modalidade()) && !a.contrato().equals(b.contrato()))),
                "Fixture deve conter dois contratos distintos na mesma modalidade");

        // Cada testemunha satisfaz os outros filtros, isolando a condição que deve excluí-la.
        try (Connection c = DriverManager.getConnection(url, usuario, senha)) {
            String agencia = data.substring(0, 4);
            testemunha(c, data, agencia, corte, "OC.CD_AGENCIA <> ? AND OC.CD_STATUS IN ('A','I') AND OC.DT_REFERENCIA <= ?", "agência diferente");
            testemunha(c, data, agencia, corte, "OC.CD_AGENCIA = ? AND OC.CD_STATUS NOT IN ('A','I') AND OC.DT_REFERENCIA <= ?", "status excluído");
            testemunha(c, data, agencia, corte, "OC.CD_AGENCIA = ? AND OC.CD_STATUS IN ('A','I') AND OC.DT_REFERENCIA > ?", "referência posterior");
            testemunha(c, alternativa, agencia, corte, "OC.CD_AGENCIA = ? AND OC.CD_STATUS IN ('A','I') AND OC.DT_REFERENCIA <= ?", "conta diferente");
        } catch (java.sql.SQLException e) {
            fail("Falha ao consultar testemunhas DB2; verifique conexão, privilégios e schema (detalhes omitidos)");
        }

        List<Operacao> obtido = new ArrayList<>();
        try (Fontes.Leitura leitura = Fontes.db2(url, usuario, senha, data, corte)) {
            Operacao op;
            while ((op = leitura.proxima()) != null) obtido.add(normalizar(op));
        } catch (Exception e) {
            fail("Falha na leitura DB2; verifique conexão, privilégios, schema e dados (detalhes omitidos)");
        }
        assertEquals(esperado, obtido, "Operações, filtros, conversões e ordem devem corresponder à fixture independente");

        Path saida = dir.resolve("SAIDA"), log = dir.resolve("LOGFILE");
        int rc = Extrato.executar(() -> abrirSemDetalhes(url, usuario, senha, data, corte), saida, log,
                LocalTime.NOON, Formato::detalhe);
        assertEquals(0, rc, "Processamento DB2 falhou");
        assertArrayEquals(Files.readAllBytes(golden), Files.readAllBytes(saida), "SAIDA diverge do golden externo");
    }

    private static String obrigatoria(String nome) {
        String valor = System.getenv(nome);
        assertTrue(valor != null && !valor.isBlank(), "Configure a variável de ambiente " + nome + " para o perfil db2-it");
        return valor;
    }

    private static Fontes.Leitura abrirSemDetalhes(String url, String usuario, String senha, String data, LocalDate corte) throws SQLException {
        try {
            Fontes.Leitura leitura = Fontes.db2(url, usuario, senha, data, corte);
            return new Fontes.Leitura() {
                public Operacao proxima() throws SQLException {
                    try { return leitura.proxima(); }
                    catch (Exception e) { throw new SQLException("Falha na leitura DB2"); }
                }
                public void close() throws SQLException {
                    try { leitura.close(); }
                    catch (Exception e) { throw new SQLException("Falha ao fechar leitura DB2"); }
                }
            };
        } catch (Exception e) {
            throw new SQLException("Falha ao abrir leitura DB2");
        }
    }

    private static void testemunha(Connection c, String data, String agencia, LocalDate corte, String filtro, String descricao) throws Exception {
        String sql = "SELECT COUNT(*) FROM DB2PROD.TB_OPERACAO_CREDITO OC INNER JOIN DB2PROD.TB_CLIENTE CLI "
                + "ON CLI.NR_CONTA = OC.NR_CONTA WHERE OC.NR_CONTA = ? AND " + filtro + " WITH UR";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, Formato.fixo(data, 10));
            p.setString(2, agencia);
            p.setDate(3, Date.valueOf(corte));
            try (var r = p.executeQuery()) {
                assertTrue(r.next() && r.getLong(1) > 0, "Dados externos devem incluir testemunha: " + descricao);
            }
        }
    }

    private static List<Operacao> lerEsperado(Path arquivo) throws Exception {
        List<Operacao> ops = new ArrayList<>();
        for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
            if (linha.isBlank() || linha.startsWith("#")) continue;
            String[] f = linha.split("\t", -1);
            assertEquals(10, f.length, "Fixture deve ter 10 campos TSV por linha");
            ops.add(normalizar(new Operacao(f[0], f[1], f[2], f[3], new BigDecimal(f[4]),
                    Integer.parseInt(f[5]), Integer.parseInt(f[6]), new BigDecimal(f[7]), f[8], f[9])));
        }
        return ops;
    }

    private static Operacao normalizar(Operacao op) {
        return new Operacao(op.conta().stripTrailing(), op.contrato().stripTrailing(), op.modalidade().stripTrailing(),
                op.descricao().stripTrailing(), op.original().stripTrailingZeros(), op.parcelas(), op.pagas(),
                op.saldo().stripTrailingZeros(), op.vencimento(), op.status().stripTrailing());
    }
}
