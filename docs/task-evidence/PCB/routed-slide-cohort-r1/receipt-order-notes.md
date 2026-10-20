# Receipt-order reader handoff

The frozen route reader at `assess.py` is pinned to
`4e4da013ea0d489f73d8806d8016e6548fe47fb65e323e7c8ba021ccdfd94a0c`.
The native routes phase produced seven two-root partitions: six report lines
for each partition, followed by that partition's suite `PASS` marker. The
frozen reader expected all 42 reports before all seven markers and therefore
returned exit 2 after the native phase completed. The final route receipt and
the original failed receipt remain unchanged.

`receipt_order_reader.py` loads the frozen source in memory and replaces one
exact textual block. Its bounded adapter requires each six-report partition to
occur after the previous suite marker and before its matching marker in both
the terminal log and child receipt. The report parser, source/physics checks,
matched-root checks, proof checks, and aggregate validation remain supplied by
the pinned reader.

`receipt-order-canary-results.json` is a pure replay receipt with 107 cases:
the unchanged original 93 cases (9 positive, 84 negative), one actual final
seven-pair route positive, and 13 fresh framing negatives covering every pair
boundary, all-before-all markers, suite movement/reordering/missing markers,
terminal mismatch, and child-receipt mismatch. No `unit-validation.json` or
`assessment.json` was written by this repair. Root owns release of those
artifacts after reviewing the adapter and the unchanged native evidence.

`native-reuse-audit.json` and `reader-framing-failure.json` pin the completed
unit/routes inputs, unchanged source and gate boundary, and the original
frozen-reader exit-2 rejection. `review_packet.py` checks those pins before
accepting the post-native adapter status.

Native final unit and routes process receipts, source seals, Java inputs, and
the original exit-2 failure archive remain outside this adapter's mutation
scope. This is a receipt framing repair only; it does not establish production
fitness, GWT/player coverage, or a policy change.
