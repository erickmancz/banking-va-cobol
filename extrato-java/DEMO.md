# Credit statement batch: presentation walkthrough

## Business problem and scope

A credit-operations batch prepares a statement of outstanding balances, with one detail per selected operation, a total and a separate operational log. This walkthrough uses entirely synthetic accounts, contracts and amounts: no customer data, DB2 credentials or cloud service are needed. The current Java implementation is a Quarkus **CLI batch**, not an HTTP API or a deployed cloud application. It intentionally corrects legacy behavior and changes the report, rather than reproducing the historical golden bytes.

The separate remote branch `demo-corrigida-extrato` exists at `2b6a476` (observed 2026-11-23) and has an opt-in `--demo-corrigido` option. Neither `c030776` nor `2b6a476` appears as an exact commit in the complete visible ancestry of the observed `main` (`16cce90`); that does not exclude equivalent or cherry-picked work. This walkthrough uses the existing corrected Java CLI on main, not that branch, and has no dependency on it.

**Existing implementation:** COBOL, JCL, DB2 reader and the corrected Java application/tests already existed. **Added for this walkthrough:** this document, the English root entry point and `demo-operacoes.dat`, a separate synthetic fixture. Legacy sources, documentation and fixtures are retained; no production code is changed. Portuguese program names, identifiers and input descriptions are preserved as data. Java-generated labels are English; COBOL output remains Portuguese.

## From COBOL to Java

| Responsibility | Original flow | Existing Java counterpart |
| --- | --- | --- |
| Batch entry and orchestration | `EXTCRDJ.jcl` runs `PGMEXTRT`, which initializes, reads, accumulates and writes. | `Main` parses explicit CLI inputs; `Extrato` orchestrates records, totals and the log. Scheduling and dataset allocation remain external. |
| Data source | `PGMLECRD` opens a DB2 cursor over operations and clients; the local `PGMLECRD_mock` substitutes a compiled table. | `Fontes` reads a runtime mock file or queries DB2 with parameters; `Operacao` carries a row. |
| Layout and writing | `PGMFMTEXT` formats and `PGMGRVEXT` writes fixed records. | `Formato` formats and `Extrato` writes 132-byte records. Java layout, labels and amount alignment intentionally differ. |

See [TRACEABILITY_MATRIX.md](TRACEABILITY_MATRIX.md) and the [Java README](README.md) for source references, field positions and additional corrections. Source analysis is not proof of production equivalence.

### Two important differences

* **First FETCH:** `PGMEXTRT` calls `PGMLECRD` at initialization. Both the DB2 reader and local mock open and fetch the first row during that call. The main processing loop calls the reader again before accumulating or printing, so the first fetched row is omitted from details and total. Java processes every selected row, including the first.
* **Numeric editing:** `PGMFMTEXT` moves implied-decimal `PIC 9(11)V99` values into `PIC Z(10)9,99-`. There is no `DECIMAL-POINT IS COMMA` declaration, so the comma is an insertion character, not the decimal separator: the edited picture has no fractional positions and the move discards cents. Detail values are then leading-trimmed and left-aligned; footer count and total also pass through narrower fields. Java uses exact `BigDecimal` arithmetic and right-aligned decimal-point amounts, and fails on field overflow. Do not treat the legacy display as an accurate monetary oracle.

The legacy DB2 SQL expresses account, branch, status `A`/`I`, cutoff and modality/contract ordering, but the caller moves the date to account and the reader derives branch and current-date cutoff. Java instead takes all three inputs explicitly. The local COBOL mock does **not** implement DB2 filtering or sorting; its table keeps file order and is limited to 50 rows.

## Fixture and independently calculated expectation

`demo-operacoes.dat` contains these two 10-field rows in file order (no header):

| Account | Contract | Modality | Description | Original | Installments / paid | Balance | Due | Status |
| --- | --- | --- | --- | ---: | --- | ---: | --- | --- |
| `0042123456` | `2026/CSG001` | `CSG` | `CONSIGNADO` | 12.34 | 12 / 3 | 9.01 | 20261231 | A |
| `0042123456` | `2026/CPE001` | `CPE` | `CREDITO PESSOAL` | 150.00 | 2 / 1 | 100.50 | 20260201 | I |

The 10 fields are `account|contract|modality|description|original amount|installments|paid installments|balance|next due date|status`. Java reads ISO-8859-1 and accepts exactly 10 or 12 fields; the optional fields 11 and 12 are branch and reference date `YYYYMMDD`. For 10 fields Java derives branch from the first four account characters and assumes the requested cutoff as the reference date; it cannot reconstruct missing DB2 values. The COBOL generator consumes the first 10 fields at **compile time**, ignoring any extra fields. It does not apply Java's filters. Use the 12-field form to test explicit branch and reference dates; this two-row fixture does not demonstrate exclusions or prove those filters.

With account `0042123456`, branch `0042` and cutoff `20260508`, both rows qualify: their derived branch is `0042`, assumed reference date equals the cutoff, and their statuses are `A` and `I`. The due date is not the selection reference date. Java sorts by modality then contract, so **expected details are `2026/CPE001` first, then `2026/CSG001`**. Independently adding the input balances gives **100.50 + 9.01 = 109.51**, with **2 details**, plus one header and one footer: **4 × 132 = 528 bytes**. The two log records occupy **264 bytes**; their timestamps vary. Mock descriptions stay Portuguese, while statuses and generated labels are English.

For the same file the COBOL mock is expected, from source, to consume `2026/CSG001` at initialization and process only `2026/CPE001`: **1 detail**, underlying accumulated balance **100.50**, 3 statement records (396 bytes), subject to legacy numeric editing in the displayed fields. This is a first-fetch illustration, not a DB2 selection or byte-parity test; displayed numeric and log output can depend on the runtime.

## Run the corrected Java demo

Requirements: Java 21, Maven 3.9+, Bash, `mktemp`, `wc`, `fold` and `nl`. Commands below start at the **repository root**. If this sandbox uses mise, activate it in the same shell as shown; otherwise use your installed Java 21/Maven and omit that line. Maven downloads dependencies as needed and writes its build products under `extrato-java/target`; statement and log are written only to a new temporary directory. The CLI replaces its output files, so never point it at production files. Stop if any command fails.

```sh
eval "$(mise activate bash)"   # sandbox setup; omit if not using mise
cd extrato-java
mvn -B -ntp verify
DEMO_DIR="$(mktemp -d)"
java -jar target/quarkus-app/quarkus-run.jar \
  --source mock --mock demo-operacoes.dat \
  --account 0042123456 --branch 0042 --date 20260508 \
  --output "$DEMO_DIR/SAIDA" --log "$DEMO_DIR/LOGFILE"
wc -c "$DEMO_DIR/SAIDA" "$DEMO_DIR/LOGFILE"
fold -b -w132 "$DEMO_DIR/SAIDA" | nl -ba
fold -b -w132 "$DEMO_DIR/LOGFILE" | nl -ba
```

Check the byte counts and the ordered details and footer against the independent expectation above. `fold -b` splits on bytes, not newlines: these files contain consecutive fixed 132-byte records without line separators. The Java default output encoding is ISO-8859-1. The CLI returns 0 on success and 12 on fatal errors; do not distribute partial outputs after an error.

**Observed in this checkout, 2026-11-23:** OpenJDK 21.0.2 and Maven 3.9.16; `mvn -B -ntp verify` succeeded with 19 unit tests and 1 packaged CLI integration test, 0 failures/errors/skips. The demo CLI returned 0; `wc -c` reported `SAIDA` 528 and `LOGFILE` 264 bytes. The report showed `2026/CPE001` / `100.50`, then `2026/CSG001` / `9.01`, and footer `TOTAL OPERATIONS: 2` / `TOTAL BALANCE: 109.51`. Both log records were present; timestamps vary by run. These are local Java observations, not COBOL or host results.

## Optional local COBOL mock comparison

Requires GnuCOBOL `cobc`, `awk` and the same shell tools. GnuCOBOL 3.2 is available in this sandbox after activating its isolated environment. Starting in `extrato-java` in the same shell immediately after the Java demo, compile copied sources and a generated include in a temporary subdirectory; this does not modify the tracked `mock-operacoes.dat` or generated files in the repository. Output remains in that subdirectory. Inspect the COBOL report rather than asserting exact legacy bytes or numeric display.

For this sandbox, activate GnuCOBOL before the commands below; elsewhere use your installed `cobc`:

```sh
export MAMBA_ROOT_PREFIX=/tmp/aicockpit/mamba
eval "$(/tmp/aicockpit/bin/micromamba shell hook -s bash)"
micromamba activate /tmp/aicockpit/cobol
```

```sh
ROOT="$(cd .. && pwd)"
mkdir "$DEMO_DIR/cobol"
cp "$ROOT/PGMEXTRT.cbl" "$ROOT/PGMFMTEXT.cbl" \
  "$ROOT/PGMGRVEXT.cbl" "$ROOT/PGMLECRD_mock.cbl" "$DEMO_DIR/cobol/"
awk -f "$ROOT/scripts/gen-mock-from-dat.awk" demo-operacoes.dat > "$DEMO_DIR/cobol/mock-generated.inc"
(
  set -e
  cd "$DEMO_DIR/cobol"
  cobc -std=mvs -x -o extrato PGMEXTRT.cbl PGMFMTEXT.cbl PGMGRVEXT.cbl PGMLECRD_mock.cbl
  ./extrato
  wc -c SAIDA LOGFILE
  fold -b -w132 SAIDA | nl -ba
  fold -b -w132 LOGFILE | nl -ba
)
```

**Independently observed, 2026-11-23, with GnuCOBOL 3.2.0:** the commands above completed successfully. `SAIDA` was 396 bytes (3 records): only `2026/CPE001` appeared as a detail, and the footer displayed count `0,01` and total `1,00`, rather than faithfully displaying the underlying count 1 and balance 100.50. `LOGFILE` was 132 bytes, containing one start record and no completion record. Compilation emitted two optional-period warnings and four `_FORTIFY_SOURCE` optimization warnings; execution emitted `libcob: warning: implicit CLOSE of ARQ-SAIDA-GRV ('SAIDA')`. These are local mock observations, not IBM COBOL or DB2 results.

When finished, from the same shell remove only the directory created above: `rm -r -- "$DEMO_DIR"`. Maven build products remain in `extrato-java/target`.

## Verification boundaries and next steps

The Java and GnuCOBOL commands are synthetic local demonstrations, not evidence of DB2, IBM COBOL, z/OS, JCL, EBCDIC conversion, scheduler or production readiness. Mock selection and Java ordering are not proof of DB2 execution or collation. Java tests do not establish host integration.

The historical tools target the old CLI and byte-parity behavior and are **not acceptance tests** for the corrected batch. After packaging, from `extrato-java`, `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -q test_homologacao_mock` ran 24 tests with 3 failures. From the repository root, with GnuCOBOL 3.2 and Java available, `PYTHONDONTWRITEBYTECODE=1 python3 scripts/test-diferencial.py` ran 14 tests: 13 failed and 1 passed. These tools use old CLI flags that the corrected CLI rejects; the differential suite also assumes legacy byte parity. These failures do not validate the corrected implementation.

Next, independently approve expected business results and add synthetic 12-field witnesses for account, branch, status and cutoff exclusion and ordering; validate the real DB2 query and host encoding in an authorized isolated environment; reconcile intentional report and numeric differences with downstream consumers; exercise failures, scheduling and rollback; obtain business and operations approval before any migration. See the [Java README](README.md) for detailed deployment prerequisites and the opt-in DB2 test profile.
