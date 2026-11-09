# Bounded iteration control clarification

Root Source decision before any fixture generation, compile or native execution.
The operation contract R1 and helper 7baab4caf45741f00d311e3b3854c794204356bfa03c0f3271399d44087df6a4
remain preserved. This defines the logical operation boundary explicitly; it
does not change a cap, data observation, validation predicate or debit.

Bounded per-item loop control (initializing, comparing and incrementing a local
index) belongs to the precharged item operation. This applies to ASCII/hex/UUID
character inspection, ordered-key equality including advancing its position,
unique-list element inspection, sameList element comparison, and fixed schema
field selection. These local induction variables neither inspect additional
input nor accumulate checked resource state. Switch selection and primitive VM
instructions remain unmeasured. This is a logical operation census, not a CPU
instruction count.

Checked visit, container/edge inventory, depth and tracker-admission arithmetic
remain separately charged before their updates. Every actual map/list/field/
character read, iterator call, allocation, unboxing, admission and declared
predicate remains charged as in R1. A loop may not conceal an extra source
observation, traversal, allocation or checked arithmetic operation.

Root independently recomputed generic work 2699+L and strict work 3044 from
the actual helper. The literal synthetic Source has L3397 (keys913, scalar
string values1146, list strings1338). V9140 plus fixed867 is 10007 actual
units; copies478 and chunks3. This expectation is frozen before execution,
not fitted to the subject Meter. Runtime evidence remains NOT RUN.
