# Whole-body ownership implementation worksheet R1

Status: worker draft, unreleased pending root review. The pinned raw Source
remains at its pinned hash; no generated Study output exists yet.
An earlier unbounded-reader emitter draft was source-only, unreleased and never
executed; it was replaced in place and is not retained as a separate artifact.

## Frozen authority and source pins

- Ownership contract SHA-256: `1c273a5e3591e5a662f0486fd68475ad75dc6b8a9b2464502f38a6aae3c09153`
- `SOURCE_INPUT_R1.json` SHA-256: `42ac5fd96c609dbc3caeb65856243519f2dd35ed065fef85f8911b2c62db5997`
- The pinned raw Study is 234,907 bytes with SHA-256
  `524318f4023fa3b884494657a24dffaac063352cc6e21b4c71bd84e41c0e69e4`.
  It remains unchanged; insertion is only in the emitter's future output.
- The component helper is 3,860 bytes with SHA-256
  `8a7a5b0dfe82d4e230adcd1d531e6942664afbd96563dc5d21f40167b41c3832`; this is
  the helper pin embedded in the source emitter.
- The exact nine manifest rows audited before implementation are:

| Source | Bytes | SHA-256 |
|---|---:|---|
| `CURRENT_STUDY.java` | 234907 | `524318f4023fa3b884494657a24dffaac063352cc6e21b4c71bd84e41c0e69e4` |
| `SHARED_OWNER.java` | 184250 | `99993fc4f2c32e38fc85d4cea79b7662841a744f35816b95b0b2e8579483915b` |
| `CURRENT_RECEIPT_BINDING.java` | 14458 | `1df7e06085165d025ff79bdd0cdaa7f603c110cc26b6ccf280d73f52e8d2e41` |
| `CurrentV2InputSetup.java` | 22747 | `7baab4caf45741f00d311e3b3854c794204356bfa03c0f3271399d44087df6a4` |
| `CurrentV2CanonicalCodec.java` | 18580 | `76fb7bbc8af73e41fd8d79a26823a0687f61e2a812e4b9de251bc953c04c7522` |
| `PREPAYMENT_R1.md` | 4567 | `2d6968320ad1707e3eb913429f7b23c6ebd7ee27a1ad1d3b05fec5197e2fed39` |
| `WIRE_R1.md` | 7363 | `1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed` |
| `CurrentAssemblyMetadata.java` | 5704 | `e2b6b05bc95b9295d15aba5305a188c1eb98cb39db4b689747e2f126a6835198` |
| `context_codec.py` | 23259 | `de21e233e27f93c8183101a89ed6fdb9a00812d69e1fe38cc47c3f3e573b3eb8` |

The emitter pins the Study and this helper independently before it opens its
exclusive output file. It reads only `CURRENT_STUDY.java` from `--source-root`
and `CurrentBodyOwnership.java` from `--component-root`; it does not enumerate
directories or read DATA. Input reads are capped at 4 MiB, reject symlink and
reparse ancestors, and compare path and open-handle identity/content snapshots
without treating access-time changes as content changes. The output root must
already exist and pass the same path checks.

## Implementation and exact accounting

`CurrentBodyOwnership.validate(CohortTerminalTelemetryStudy.SetupMeter,Object)`
rejects a null meter before observing the body, prepays two actual units before
constructing one traversal state and one `IdentityHashMap`, then performs a
point-in-time traversal. It borrows the input and leaves it untouched. Repeated
Map/List identities fail, while repeated scalar references are permitted. It
does not finish the shared meter or retain traversal state after return.

For `V=1+2Em+El`, the independent successful census is:

```text
actualUnits    = 2 + 3V + 6M + 5L + 4Em + 2El
               = 5 + 6M + 5L + 10Em + 5El
copiedEntries  = M + L
copyChunks     = M + L
```

| Shape | Actual units | Copies | Chunks |
|---|---:|---:|---:|
| null | 5 | 0 | 0 |
| empty Map | 11 | 1 | 1 |
| empty List | 10 | 1 | 1 |
| one-key/null Map | 21 | 1 | 1 |
| three-null List | 25 | 1 | 1 |
| Map `a` to two-null List | 36 | 2 | 2 |

The source emitter's sole insertion is
`    CurrentBodyOwnership.validate(meter,body);\n` immediately before the
unique LF anchor in the pinned Study. The exact original bytes outside that
insertion are preserved. It does not modify `main`, `assemble`, or any other
Study location. The output name is exclusively created as
`CohortTerminalTelemetryStudy.java`.

## Source-only checks and current gate status

The fixed suite has 11 `unittest` methods: pinned inputs and insertion behavior;
Python AST/CLI shape; Java API, stable errors, charge ordering, alias handling,
metered map/list observations and independent census examples; exclusive output
and pin failures; plus cap, link/reparse and changed-snapshot reader fixtures.
`--source-root` selects the pinned Source parent and defaults to the adjacent
`source` directory; `parse_known_args` passes the remaining arguments to
`unittest`. Tests call emitter functions directly in isolated temporary
directories; the mocked link/reparse metadata checks are Source-only (the suite
also attempts a temporary symlink where the host allows it), and it does not
start a process or run Java.

| Gate | Status | Evidence / limit |
|---|---|---|
| Frozen contract and nine Source manifest hashes | PASS | Hashes and sizes recorded above; source bytes remain unchanged. |
| Source-only test suite | NOT RUN | Root owns run admission. |
| Emitter CLI positive/wrong-parent/missing-component canaries | NOT RUN | Root will run these through its selected process wrapper. |
| Generated Study output | NOT RUN | Pins, source logic and tests are draft; no output file was created. |
| JDK 8 / target 7 closure | NOT RUN | Root owns execution and admission. |
| Actual-Meter smoke and suite | NOT RUN | No Java execution occurred. |
| Native, DATA, producer, Original/Session/routing, or genuine-envelope work | NOT RUN | Excluded by the frozen contract; genuine envelope remains `UNSPENT_GATED`. |

Preserved limits: 90-second deadline, 640 groups, 5-second per-group cap,
32,768 actual units, 1,048,576 copied entries, 4,096 chunks, depth 64, and
1,000,000 key-inclusive visits. The existing shared Meter remains responsible
for group, deadline, and copy-limit enforcement; rejected work is not refunded.

## Root handoff

Review the four owned files, then run the admitted Source-only tests, selected
emitter CLI canaries, fresh target-7 closure, and actual-Meter smoke/suite in the
root-controlled order. Recompute the helper pin if its source changes before
running the emitter. No staging, commit, push, or notification was performed.
