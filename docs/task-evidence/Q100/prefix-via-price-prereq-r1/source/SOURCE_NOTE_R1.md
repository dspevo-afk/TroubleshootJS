# Prefix VIA ramp source overlay note (Phase 1)

Scope: private native-only BASE/RAMP source overlays for the frozen-prefix VIA-price prerequisite. No production checkout files are changed by the builder. This source package does not run the authored generator study and does not qualify a board layout or route preference.

## Frozen inputs

- Parent checkout: `OS-temp/tsj-q100-outf-graph-20261105-r1`
- Branch / HEAD: `codex/q100-prefix-via-ramp-20261105` / `30b2a1cbbb02b39dbc275a82755902e61043b714`
- Router source SHA-256: `759831c91345aedab9e78d8c83785019f7e59287a18f4f99f7af14876776c474`
- Frozen plan `PLAN_R1.md` SHA-256: `2d640b3d36cca2ae2613718e356e84ed603abf3ccf813f4a1372f35b28e72bce`
- Phase 1 boundary `PHASE1_BOUNDARY_R1.md` SHA-256: `9fae9c7ab6e2404c9aa157122f4c0e331830766de336fc3ff6dfd0b36b6ca68f`

## Builder use

From PowerShell or a Python 3 host, invoke:

`python -B <QR>/source/prepare_overlay.py <repoRoot> <new-output-root-under-OS-temp>`

The output root must not exist. The builder pins and checks the parent checkout, exact router-source bytes, and frozen plan. It does not use global worktree cleanliness as an input gate; the native host separately validates its selected source closure. It writes `BASE/` and `RAMP/` Java source overlays, a readable patch, and a hash manifest. Output status is `OVERLAY_PREPARED_NOT_RUN`; compilation and execution belong to the root-owned native host. Do not run the builder against the production checkout until the root host is ready.

## Overlay contract

- `BASE` keeps the frozen branch VIA price equal to the Search owner's scalar.
- `RAMP` applies `base + floor((long)base * min(H,M) / M)` to normal base-60 branches. `M=0` returns the base scalar before division; the real path guard must still disallow flips when no capacity remains.
- Each actual `BranchSearch` freezes a final branch price from the hole count captured at construction. Transition `g` cost and the relaxed VIA-distance bound consume that same immutable branch price. Session/Search cost fields and the 48-hole / four-transition caps remain untouched. The separate turn lower bound ignores VIA cost, so it remains a lower bound when the surcharge is nonnegative; the relaxed Dijkstra uses the exact frozen VIA edge price. The search heuristic takes the maximum of these lower bounds, which remains admissible.
- A detached copy recomputes the branch price from its copied owner's base scalar and copied frozen prefix. An explicit raised scalar such as 120 remains constant.
- `PrefixViaRampSupport` records actual branch and copy observations only while observation is active. JSON rows use schema `tsj-prefix-via-ramp-price-v1`, a 1-based ordinal, actual net/pad identities, pass, frozen prefix/cap, base scalar, branch price, and copy flag. Limits are 4096 rows, 64 KiB per JSON row, and 8 MiB aggregate UTF-8 row bytes.
- The canary independently checks literal price values and competing weighted paths, monotonicity, zero-cap arithmetic, actual source/copy branch prices, actual `pathViaFits` zero/saturated guards, source immutability, and an 8 MiB shared stdout/stderr producer cap. `--capture-overflow` must be run as a real child negative case by the maintained host.
- Real H=2/M=48 copy evidence uses `P07DetachedBranchReplayContractTest.freshSource(48)`, which yields the actual branch after prior Publishers. The native compile closure must include that existing contract fixture. If it is unavailable, the canary reports the copy and guard gates as `NOT_RUN`; the strict host reader must reject it as incomplete.

## Acceptance boundaries

The native host owns compilation, the positive BASE/RAMP child runs, the overflow negative child runs, process termination proof, manifest/provenance validation, and strict canary receipt reading. This source-only handoff makes no runtime or generator claim. Any authored study, authorization coverage, generator preference, full layout qualification, and Phase 2 result remain `NOT_RUN`.