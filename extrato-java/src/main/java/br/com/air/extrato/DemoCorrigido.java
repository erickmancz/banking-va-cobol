package br.com.air.extrato;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

final class DemoCorrigido {
    private static final int LIMITE = 8 * 1024 * 1024;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final LinkOption NOFOLLOW = LinkOption.NOFOLLOW_LINKS;
    private static final java.nio.file.attribute.FileAttribute<?> PRIVADO =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
    private static final java.nio.file.attribute.FileAttribute<?> DIRETORIO =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));

    private DemoCorrigido() { }
    private record Linha(Operacao op, String agencia, LocalDate referencia) { }
    interface Formatador { String detalhe(Operacao op) throws Exception; }
    interface AbrirSaida { java.io.OutputStream abrir(Path p) throws Exception; }

    static int executar(String[] args) {
        return executar(args, Formato::detalhe, DemoCorrigido::abrirSaida);
    }

    static int executar(String[] args, Formatador formatador, AbrirSaida abrirSaida) {
        Path execucao = null;
        int rc = 12;
        Integer quantidade = null;
        BigDecimal total = null;
        try {
            Map<String, String> opts = argumentos(args);
            String conta = conta(opts.get("conta"));
            String agencia = agencia(opts.get("agencia"));
            LocalDate corte = data(opts.get("data"));
            Path raiz = caminho(opts.get("raiz"));
            Path fixture = caminho(opts.get("fixture"));
            verificarComponentes(raiz);
            if (!Files.isDirectory(raiz, NOFOLLOW)
                    || !modoPrivado(raiz)
                    || !Files.getAttribute(raiz, "unix:uid", NOFOLLOW).equals(Files.getAttribute(Path.of("/proc/self"), "unix:uid")))
                throw new IOException("raiz inválida");
            byte[] random = new byte[16];
            new SecureRandom().nextBytes(random);
            Path criada = Files.createDirectory(raiz.resolve("demo-" + HexFormat.of().formatHex(random)), DIRETORIO);
            if (!Files.isDirectory(criada, NOFOLLOW) || !modoPrivado(criada))
                throw new IOException("diretório inválido");
            execucao = criada;
            System.out.println("Execucao: " + execucao.getFileName());
            byte[] dados = lerFixture(fixture);
            escrever(execucao.resolve("fixture.dat"), dados);
            List<Linha> linhas = ler(dados);
            List<Operacao> selecionadas = linhas.stream()
                    .filter(l -> l.op().conta().equals(conta) && l.agencia().equals(agencia)
                            && (l.op().status().equals("A") || l.op().status().equals("I"))
                            && !l.referencia().isAfter(corte))
                    .map(Linha::op).sorted(Comparator.comparing(Operacao::modalidade).thenComparing(Operacao::contrato)).toList();
            BigDecimal soma = selecionadas.stream().map(Operacao::saldo).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (selecionadas.size() > 999 || soma.compareTo(new BigDecimal("100000000000")) >= 0)
                throw new IllegalArgumentException("total excede limite");
            quantidade = selecionadas.size();
            total = soma.setScale(2);
            try (var saida = abrirSaida.abrir(execucao.resolve("SAIDA"))) {
                registro(saida, Formato.cabecalho());
                for (Operacao op : selecionadas) {
                    registro(saida, formatador.detalhe(op));
                }
                registro(saida, Formato.rodape(selecionadas.size(), total));
            }
            rc = selecionadas.isEmpty() ? 4 : 0;
        } catch (Exception e) {
            rc = 12;
        }
        if (execucao != null) {
            try {
                escrever(execucao.resolve("LOGFILE"), (rc == 4 ? "RC4: SEM DETALHES\n" : rc == 0 ? "RC0: CONCLUIDO\n" : "RC12: FALHA\n").getBytes(StandardCharsets.US_ASCII));
                StringBuilder resultado = new StringBuilder("RC=").append(rc).append('\n');
                if (rc != 12) resultado.append("QUANTIDADE=").append(quantidade)
                        .append("\nTOTAL_LOGICO=").append(total.toPlainString()).append('\n');
                for (String nome : List.of("fixture.dat", "SAIDA", "LOGFILE")) {
                    Path p = execucao.resolve(nome);
                    if (Files.exists(p, NOFOLLOW)) {
                        byte[] bytes = Files.readAllBytes(p);
                        resultado.append(nome).append(' ').append(bytes.length).append(' ')
                                .append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))).append('\n');
                    }
                }
                escrever(execucao.resolve("resultado.txt"), resultado.toString().getBytes(StandardCharsets.US_ASCII));
            } catch (Exception e) {
                rc = 12;
            }
        }
        if (rc == 4) System.err.println("RC4: SEM DETALHES");
        if (rc == 12) System.err.println("RC12: FALHA NO MODO DEMO");
        return rc;
    }

    private static java.io.OutputStream abrirSaida(Path p) throws IOException {
        return java.nio.channels.Channels.newOutputStream(Files.newByteChannel(p,
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, NOFOLLOW), PRIVADO));
    }

    static boolean modoPrivado(Path p) throws IOException {
        return (((Integer) Files.getAttribute(p, "unix:mode", NOFOLLOW)) & 07777) == 0700;
    }

    private static void registro(java.io.OutputStream saida, String s) throws IOException {
        if (s.length() != 132 || !s.chars().allMatch(c -> c >= 32 && c <= 126)) throw new IOException("formatação inválida");
        saida.write(s.getBytes(StandardCharsets.US_ASCII));
    }

    private static void escrever(Path p, byte[] bytes) throws IOException {
        try (SeekableByteChannel canal = Files.newByteChannel(p, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, NOFOLLOW), PRIVADO)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) canal.write(buffer);
        }
    }

    private static Map<String, String> argumentos(String[] args) {
        Map<String, String> opts = new HashMap<>();
        Set<String> permitidas = Set.of("raiz", "fixture", "conta", "agencia", "data");
        for (int i = 1; i < args.length; i += 2) {
            if (i + 1 >= args.length || !args[i].startsWith("--")) throw new IllegalArgumentException();
            String nome = args[i].substring(2);
            if (!permitidas.contains(nome) || opts.putIfAbsent(nome, args[i + 1]) != null) throw new IllegalArgumentException();
        }
        if (!opts.keySet().equals(permitidas)) throw new IllegalArgumentException();
        return opts;
    }
    private static String conta(String s) {
        if (s == null || !s.matches("[ -~]{10}")) throw new IllegalArgumentException();
        return s;
    }
    private static String agencia(String s) {
        if (s == null || !s.matches("[0-9]{4}")) throw new IllegalArgumentException();
        return s;
    }
    private static LocalDate data(String s) {
        if (s == null || !s.matches("[0-9]{8}")) throw new IllegalArgumentException();
        LocalDate d = LocalDate.parse(s, DATA);
        if (d.getYear() == 0) throw new IllegalArgumentException();
        return d;
    }
    private static Path caminho(String s) {
        Path p = Path.of(s);
        if (!p.isAbsolute() || !p.normalize().equals(p)) throw new IllegalArgumentException();
        return p;
    }
    private static void verificarComponentes(Path p) throws IOException {
        Path atual = p.getRoot();
        for (Path parte : p) {
            atual = atual.resolve(parte);
            var a = Files.readAttributes(atual, java.nio.file.attribute.BasicFileAttributes.class, NOFOLLOW);
            if (a.isSymbolicLink() || (!atual.equals(p) && !a.isDirectory())) throw new IOException("link ou caminho inválido");
        }
    }
    private static byte[] lerFixture(Path p) throws IOException {
        verificarComponentes(p);
        var a = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class, NOFOLLOW);
        if (!a.isRegularFile() || a.size() > LIMITE || !Files.getAttribute(p, "unix:nlink", NOFOLLOW).equals(1)) throw new IOException("fixture inválida");
        try (var canal = Files.newByteChannel(p, Set.of(StandardOpenOption.READ, NOFOLLOW))) {
            var out = new java.io.ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (canal.read(buffer) != -1) {
                buffer.flip();
                out.write(buffer.array(), 0, buffer.remaining());
                if (out.size() > LIMITE) throw new IOException("fixture excede limite");
                buffer.clear();
            }
            return out.toByteArray();
        }
    }
    static List<Linha> ler(byte[] bytes) {
        for (byte b : bytes) if (b != '\n' && (b < 32 || b > 126)) throw new IllegalArgumentException();
        String texto = new String(bytes, StandardCharsets.US_ASCII);
        List<Linha> linhas = new ArrayList<>();
        if (texto.isEmpty()) return linhas;
        String[] registros = texto.split("\n", -1);
        int tamanho = registros.length - (texto.endsWith("\n") ? 1 : 0);
        if (tamanho > 10000) throw new IllegalArgumentException();
        for (int i = 0; i < tamanho; i++) {
            String[] f = registros[i].split("\\|", -1);
            if (f.length != 12) throw new IllegalArgumentException();
            conta(f[0]); agencia(f[1]); LocalDate referencia = data(f[2]);
            if (!f[3].matches("[ -~]{1,14}") || !f[4].matches("[A-Z0-9]{1,3}")
                    || !f[5].matches("[ -~]{1,20}") || !f[11].matches("[A-Z]")) throw new IllegalArgumentException();
            BigDecimal original = decimal(f[6]), saldo = decimal(f[9]);
            int parcelas = quantidade(f[7]), pagas = quantidade(f[8]);
            data(f[10]);
            linhas.add(new Linha(new Operacao(f[0], f[3], f[4], f[5], original, parcelas, pagas, saldo, f[10], f[11]), f[1], referencia));
        }
        return linhas;
    }
    private static BigDecimal decimal(String s) {
        if (!s.matches("[0-9]{1,11}\\.[0-9]{2}")) throw new IllegalArgumentException();
        return new BigDecimal(s);
    }
    private static int quantidade(String s) {
        if (!s.matches("[0-9]{1,3}")) throw new IllegalArgumentException();
        return Integer.parseInt(s);
    }
}
