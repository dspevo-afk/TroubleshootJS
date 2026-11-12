# Source preflight and historical tool compatibility

Current source preflight required obsolete PowerShell, harness and codec
identities. Historical tool incompatibilities were checked only after prior
metadata had been decoded. The caller now authenticates current Source/tools for
preflight and applies the preserved historical tool checks before prior reads.

`preflight()` authenticates eight explicit Source/metadata files and five tools,
the pinned public normal allowlist, config/root identities and empty data-output
directory. It writes the Source manifest in scratch outside the data-output root.
Its return is `PASS_SOURCE_PREFLIGHT_ONLY` with `roleRead: NOT_RUN`. It creates no
operational journal, read-attempt marker, context or producer Source.

Current caller pins select PS 495616B/d8f182, current derived harness2937B/abd79f,
and unchanged module1092245B/418da6. The private harness is derived from the
authenticated old2941B/28aab script by exactly three checked bindings: selected
module path/hash and PS hash. Its invocation/receipt/cleanup code is conserved.
Actual PS/child0/child7 canaries use a controller/children-local builtin Modules
path restored in finally; user/machine settings are untouched. This qualifies
finite CODE calls through the preserved default process path, not normal native
role caps or complete environment/descendant closure.

The current Python codec DE21 preserves the entire old C1 Source as an exact byte
prefix and appends only qualified-prior result helpers. Input schemas/functions
are unchanged. The caller binds that current provider and the new normalizer
Source hash; complete synthetic codec and qualified-prior helper tests run.

`normalize_prior.validate_prior_tool_facts(tool_facts)` owns the existing exact
five-row shape and four fixed historical tuple checks. It returns the same dict.
Historical PS60c9, harness28aab and modulefb07 remain unchanged, as do Python's
historical tuple, check order and stable errors. `_validate_history_authority()`
delegates to this helper; its nativeJava-to-prior-manifest binding stays after
decoding because that linkage requires prior metadata. The extraction can be
reversed to reconstruct the entire original normalizer byte exactly.

Immediately after `_preflight_state()`, `_qualify_impl()` calls the authenticated
helper on its actual observed tool facts. This occurs before reading the supplied
Source manifest/journal, reserving an attempt, or reading a prior role. Current
host facts reject with
`PRIOR_TOOL_COMPATIBILITY_ROOT_CALL_POWERSHELL_PIN`. Preflight can succeed while
prior compatibility remains closed. A synthetic tuple, pending evidence or this
early rejection cannot grant a real qualifier PASS or producer Source token.

Twenty fixed roles are prior artifacts, not twenty cases. No actual prior body,
oldD/held-out/protected DATA or generated DATA factory is read/admitted by these
gates. Empty owned N/D directories, real reader/tool observations, exact typed
rejections and output/root closure bound the finite tests. Authorized historical
input/tool-context provenance and full semantic/receipt/lifecycle closure remain
necessary before successful prior qualification or genuine execution.

Both Study holds, original->NULL/OBSERVE gate, all product seeds/epochs/order,
CircuitJS truth and budgets remain unchanged. Full30 EXCLUDED; HARD/PSYCHOTIC
unavailable/unrun; protected-unread UNCERTIFIED; oldnormal20 NOT REREAD;
playable100=0; beta HELD. GWT/Player N/A; electrical/population NOT RENEWED;
CI NOT RUN/self-hosted runner prerequisite.
