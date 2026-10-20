# Paired-pocket diagnostic exporter

The file export-source/SparseNormalBoardDiagnostic.java is derived from the
pinned NPN sparse-board task exporter at SHA-256
14c472a9b8a28d648a8be17d4a4ae0d6b66ecac3e286fb475808d98f71d20b36. The
paired-probe factory pins that source hash and the derived exporter hash
55658de039f165c621f8062a3b938b8d4e8d16adf1d3009c8dfa975fdf327eef.

The adaptation changes only the class comment, family constant and its guard,
usage text, and the exact signed-long seed guard. The accepted normal replay is
tsj-alpha/6/EASY/PARALLEL_DUAL_INDICATOR/-9223372036854775808. The seed stays
a canonical text argument through Long.parseLong/Long.toString; the exporter
pins it with Long.MIN_VALUE, avoiding an overflowing positive Java literal.

The exporter keeps the existing sparse-normal-board@1 JSON schema, namespaced
row, final marker, construct-once replay path, normal physical admission,
geometry/routing/access/quality checks, independent whitespace and metric
comparisons, live-graph/selected-geometry immutability assertions, and nested
owner/CircuitJS cleanup. It remains a task-only developer artifact that emits
raw physical declarations and selected geometry; it is not a normal-player UI
surface.

This file records source derivation only. It contains no runtime or qualification
claim; compilation and execution are owned by the root task.
