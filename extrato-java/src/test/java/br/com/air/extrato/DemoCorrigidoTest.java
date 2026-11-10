package br.com.air.extrato;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoCorrigidoTest {
    @TempDir Path dir;
    private static final String A = "CTA AB 123|0042|20260508|B|CPE|PESSOAL|100.10|12|2|11.25|20260601|A\n";
    private static final String B = "CTA AB 123|0042|20260507|A|CDC|CREDIARIO|200.99|24|3|22.90|20260602|I\n";
    private Path raiz() throws Exception {
        Path p = dir.resolve("raiz");
        Files.createDirectory(p, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Files.setAttribute(p, "unix:mode", 0700);
        return p;
    }
    private int executar(Path raiz, Path f, String conta, String agencia, String data) {
        return new Main().run("--demo-corrigido", "--raiz", raiz.toString(), "--fixture", f.toString(),
                "--conta", conta, "--agencia", agencia, "--data", data);
    }
    private Path unica(Path raiz) throws Exception {
        try (var s = Files.list(raiz)) { return s.findFirst().orElseThrow(); }
    }
    private String saida(Path p) throws Exception { return Files.readString(p.resolve("SAIDA"), StandardCharsets.US_ASCII); }
    @Test void zeroUmVariosEPrimeiroTotalizado() throws Exception {
        Path raiz = raiz(), f = dir.resolve("fixture");
        Files.writeString(f, "");
        assertEquals(4, executar(raiz, f, "CTA AB 123", "0042", "20260508"));
        Path zero = unica(raiz);
        assertEquals(264, saida(zero).length());
        assertEquals("RC4: SEM DETALHES\n", Files.readString(zero.resolve("LOGFILE")));
        Files.writeString(f, A);
        assertEquals(0, executar(raiz, f, "CTA AB 123", "0042", "20260508"));
        Files.writeString(f, A + B);
        assertEquals(0, executar(raiz, f, "CTA AB 123", "0042", "20260508"));
        try (var paths = Files.list(raiz)) {
            List<Path> pastas = paths.toList();
            assertEquals(3, pastas.size());
            Path um = pastas.stream().filter(p -> { try { return Files.size(p.resolve("SAIDA")) == 396; } catch (Exception e) { return false; } }).findFirst().orElseThrow();
            String u = saida(um);
            assertTrue(u.substring(132, 264).startsWith("B "));
            assertTrue(Files.readString(um.resolve("resultado.txt")).contains("QUANTIDADE=1\nTOTAL_LOGICO=11.25\n"));
            assertEquals("  0,01 ", u.substring(264 + 22, 264 + 29));
            assertEquals("          0,11", u.substring(264 + 73, 264 + 87));
            Path varios = pastas.stream().filter(p -> { try { return Files.size(p.resolve("SAIDA")) == 528; } catch (Exception e) { return false; } }).findFirst().orElseThrow();
            String v = saida(varios);
            assertTrue(Files.readString(varios.resolve("resultado.txt")).contains("QUANTIDADE=2\nTOTAL_LOGICO=34.15\n"));
            assertTrue(v.substring(132, 264).startsWith("A "));
            assertTrue(v.substring(264, 396).startsWith("B "));
            assertEquals("  0,02 ", v.substring(396 + 22, 396 + 29));
            assertEquals("          0,34", v.substring(396 + 73, 396 + 87));
        }
    }
    @Test void limitesDeQuantidadeETotalFalhamSemRodape() throws Exception {
        Path raiz = raiz(), f = dir.resolve("f");
        Files.writeString(f, A.repeat(1000));
        assertEquals(12, executar(raiz, f, "CTA AB 123", "0042", "20260508"));
        Files.writeString(f, A.replace("11.25", "99999999999.99") + B);
        assertEquals(12, executar(raiz, f, "CTA AB 123", "0042", "20260508"));
        try (var pastas = Files.list(raiz)) {
            var lista = pastas.toList();
            assertEquals(2, lista.size());
            for (Path p : lista) {
                assertFalse(Files.exists(p.resolve("SAIDA")));
                assertEquals("RC12: FALHA\n", Files.readString(p.resolve("LOGFILE")));
            }
        }
    }
    @Test void falhaDeFormatoEEscritaPreservaParcialELog() throws Exception {
        Path raiz = raiz(), f = dir.resolve("f");
        Files.writeString(f, A + B);
        String[] args = {"--demo-corrigido", "--raiz", raiz.toString(), "--fixture", f.toString(),
                "--conta", "CTA AB 123", "--agencia", "0042", "--data", "20260508"};
        assertEquals(12, DemoCorrigido.executar(args, op -> { throw new java.io.IOException("formato"); },
                p -> Files.newOutputStream(p, java.nio.file.StandardOpenOption.CREATE_NEW)));
        assertEquals(12, DemoCorrigido.executar(args, Formato::detalhe, p -> {
            var out = Files.newOutputStream(p, java.nio.file.StandardOpenOption.CREATE_NEW);
            return new java.io.FilterOutputStream(out) {
                int n;
                @Override public void write(byte[] bytes) throws java.io.IOException {
                    if (++n == 2) throw new java.io.IOException("falha");
                    out.write(bytes);
                }
            };
        }));
        try (var pastas = Files.list(raiz)) {
            var lista = pastas.toList();
            assertEquals(2, lista.size());
            for (Path p : lista) {
                assertEquals(132, Files.size(p.resolve("SAIDA")));
                assertEquals("RC12: FALHA\n", Files.readString(p.resolve("LOGFILE")));
                assertTrue(Files.readString(p.resolve("resultado.txt")).startsWith("RC=12\n"));
            }
        }
    }
    @Test void filtrosIndependentesEInclusive() throws Exception {
        Path raiz = raiz(), f = dir.resolve("f");
        Files.writeString(f, A + A.replace("CTA AB 123", "OUTRA00001").replace("|B|", "|C|")
                + A.replace("|0042|", "|0043|").replace("|B|", "|D|")
                + A.replace("20260508", "20260509").replace("|B|", "|E|")
                + A.replace("|A\n", "|Q\n").replace("|B|", "|F|"));
        assertEquals(0, executar(raiz, f, "CTA AB 123", "0042", "20260508"));
        assertEquals(396, saida(unica(raiz)).length());
        assertEquals(0, executar(raiz, f, "OUTRA00001", "0042", "20260508"));
        assertEquals(0, executar(raiz, f, "CTA AB 123", "0043", "20260508"));
        assertEquals(4, executar(raiz, f, "CTA AB 123", "0042", "20260507"));
        assertEquals(0, executar(raiz, f, "CTA AB 123", "0042", "20260509"));
        try (var pastas = Files.list(raiz)) {
            List<String> resultados = pastas.map(p -> { try { return saida(p); } catch (Exception e) { throw new RuntimeException(e); } }).toList();
            assertTrue(resultados.stream().anyMatch(s -> s.length() == 396 && s.substring(132).startsWith("C ")));
            assertTrue(resultados.stream().anyMatch(s -> s.length() == 396 && s.substring(132).startsWith("D ")));
            assertTrue(resultados.stream().anyMatch(s -> s.length() == 264));
            assertTrue(resultados.stream().anyMatch(s -> s.length() == 528 && s.substring(264).startsWith("E ")));
        }
    }
    @Test void invalidosFalhamSemFallbackEParciaisProtegidos() throws Exception {
        Path raiz = raiz(), f = dir.resolve("f");
        Files.writeString(f, A + "linha defeituosa\n");
        assertEquals(12, executar(raiz, f, "CTA AB 123", "0042", "20260508"));
        Path exec = unica(raiz);
        assertFalse(Files.exists(exec.resolve("SAIDA")));
        assertEquals("RC12: FALHA\n", Files.readString(exec.resolve("LOGFILE")));
        assertEquals(12, executar(raiz, f, "CTA AB 123", "0042", "20260230"));
        assertEquals(12, executar(raiz, dir.resolve("ausente"), "CTA AB 123", "0042", "20260508"));
        assertEquals(12, executar(raiz, f, "123", "0042", "20260508"));
        assertEquals(12, executar(raiz, f, "CTA AB 123", "004x", "20260508"));
        assertEquals(A + "linha defeituosa\n", Files.readString(f));
    }
    @Test void linksAliasesEPermissoes() throws Exception {
        Path raiz = raiz(), f = dir.resolve("f"), link = dir.resolve("link"), alias = dir.resolve("alias");
        Files.writeString(f, A); Files.createSymbolicLink(link, f);
        assertEquals(12, executar(raiz, link, "CTA AB 123", "0042", "20260508"));
        Files.createLink(alias, f);
        assertEquals(12, executar(raiz, f, "CTA AB 123", "0042", "20260508"));
        Files.delete(alias);
        assertEquals(0, executar(raiz, f, "CTA AB 123", "0042", "20260508"));
        try (var pastas = Files.list(raiz)) {
            for (Path p : pastas.toList()) {
                assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(p)));
                assertEquals(0700, ((Integer) Files.getAttribute(p, "unix:mode")) & 07777);
                try (var arquivos = Files.list(p)) {
                    for (Path a : arquivos.toList()) assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(a)));
                }
            }
        }
    }
    @Test void rejeitaRaizComBitsEspeciaisEVerificaModoCriado() throws Exception {
        Path raiz = raiz(), f = dir.resolve("f");
        Files.writeString(f, A);
        for (int modo : new int[]{01700, 02700, 04700}) {
            Files.setAttribute(raiz, "unix:mode", modo);
            assertEquals(modo, ((Integer) Files.getAttribute(raiz, "unix:mode")) & 07777);
            assertFalse(DemoCorrigido.modoPrivado(raiz));
            assertEquals(12, executar(raiz, f, "CTA AB 123", "0042", "20260508"));
            try (var pastas = Files.list(raiz)) { assertEquals(0, pastas.count()); }
        }
        Files.setAttribute(raiz, "unix:mode", 0700);
        assertEquals(0, executar(raiz, f, "CTA AB 123", "0042", "20260508"));
        Path criada = unica(raiz);
        assertTrue(DemoCorrigido.modoPrivado(criada));
        assertEquals(0700, ((Integer) Files.getAttribute(criada, "unix:mode")) & 07777);
        Files.setAttribute(criada, "unix:mode", 02700);
        assertFalse(DemoCorrigido.modoPrivado(criada));
    }
    @Test void umaskRestritivaRejeitaDiretorioCriadoSemEvidencias() throws Exception {
        Path raiz = raiz(), f = dir.resolve("f");
        Files.writeString(f, A);
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = Path.of("target", "classes").toAbsolutePath() + java.io.File.pathSeparator
                + Path.of("target", "test-classes").toAbsolutePath();
        Process processo = new ProcessBuilder("sh", "-c", "umask 0777; exec \"$@\"", "sh",
                javaBin, "-cp", classpath, DemoSubprocesso.class.getName(),
                "--demo-corrigido", "--raiz", raiz.toString(), "--fixture", f.toString(),
                "--conta", "CTA AB 123", "--agencia", "0042", "--data", "20260508")
                .redirectErrorStream(true).start();
        assertTrue(processo.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
        String mensagens = new String(processo.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        assertEquals(12, processo.exitValue(), mensagens);
        assertFalse(mensagens.contains("Execucao:"), mensagens);
        assertTrue(mensagens.contains("RC12"), mensagens);
        try (var pastas = Files.list(raiz)) {
            List<Path> criadas = pastas.toList();
            assertEquals(1, criadas.size());
            Path criada = criadas.getFirst();
            assertEquals(0, ((Integer) Files.getAttribute(criada, "unix:mode")) & 07777);
            Files.setAttribute(criada, "unix:mode", 0700);
            try (var arquivos = Files.list(criada)) { assertEquals(0, arquivos.count()); }
        }
        assertEquals(A, Files.readString(f));
    }
    @Test void execucoesConcorrentesCriamDiretoriosExclusivosESnapshots() throws Exception {
        Path raiz = raiz(), f = dir.resolve("f"), referencia = dir.resolve("referencia");
        Files.writeString(f, A + B); Files.writeString(referencia, "REFERENCIA INTACTA");
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tarefas = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(i -> (java.util.concurrent.Callable<Integer>)
                            () -> executar(raiz, f, "CTA AB 123", "0042", "20260508")).toList();
            for (var resultado : executor.invokeAll(tarefas)) assertEquals(0, resultado.get().intValue());
        }
        try (var pastas = Files.list(raiz)) {
            var lista = pastas.toList();
            assertEquals(4, lista.size());
            for (var p : lista) {
                assertEquals(A + B, Files.readString(p.resolve("fixture.dat")));
                assertEquals(528, Files.size(p.resolve("SAIDA")));
            }
        }
        assertEquals("REFERENCIA INTACTA", Files.readString(referencia));
        assertEquals(A + B, Files.readString(f));
    }
}

class DemoSubprocesso {
    public static void main(String[] args) { System.exit(DemoCorrigido.executar(args)); }
}
