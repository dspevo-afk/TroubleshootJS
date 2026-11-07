# P8 selected Python import isolation

Frozen before final focused-host/source execution. Independent source review
found that hashing an explicitly executed helper does not isolate its transitive
ordinary imports from script-directory/PYTHONPATH shadowing.

Every new P8 selected Python invocation uses -I -B: maker, input auditor, static
tests and independent consumer, including negative controls. -I excludes current/
script-directory and user/environment module lookup; -B preserves no-bytecode
behavior. Reusable P8 entry points check sys.flags.isolated and
sys.dont_write_bytecode immediately after importing builtin sys, before other
imports. This prevents unsupported direct invocation from masquerading as the
qualified path. Imports of pinned P6 helper/overlay/auditor source still execute
the exact authenticated bytes with proper module metadata. Do not fall back to
uncontrolled normal sibling imports if isolated mode reveals a dependency.

Update actual argument identity checks to the two flags in the original order
-I,-B. Keep genuine historical P6/P7 receipts/source bytes unchanged and do not
claim this new gate retroactively certifies historical imports. Current source
counts, tool hashes, all work/process/cleanup/output limits, literals and oracles
stay unchanged. Rerun current static tests under this selected invocation and
perform all required focused host/native/consumer gates on final source. This
is a process/import boundary correction, not a new route or physics policy.
