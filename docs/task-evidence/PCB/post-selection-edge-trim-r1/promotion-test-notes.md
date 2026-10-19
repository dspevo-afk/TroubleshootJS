# Current15 promotion test notes

The replay migration keeps the old canvas-owner interpretation available as
explicit placement14 and makes current placement15 the default. The inspected
production mapping defines canvas14/current15 and maps them to density13/14,
respectively; both retain role-width routing. The contracts therefore assert
distinct replay identities while preserving each version's own owner and
session framing.

Coverage now retains explicit placements1–14, adds current15, round-trips saved14
through the existing model-1 session frame, and checks 14↔15 rejection in both
directions. Malformed15 and future16 identities are rejected alongside existing
negative14 fixtures. Owner checks retain saved14 density13 behavior and add
current15 density14 behavior, exact-seed/family checks, and saved13/saved14/
current15 restore mismatch guards. Width-profile checks compare seeded topology,
routing seeds and package inventory through current15.

Original test bytes are archived under `promotion-originals/`; each archived file
was SHA-256 checked against its pre-edit source:

- `RelBSessionIdentityContractTest.java.pre-current15.20261019T081159440Z` — `5640a07a33366ff585bd790ef5e3d401b578cbc941175a3dedc000351a1b87fb`
- `U06SessionSaveContractTest.java.pre-current15.20261019T081159440Z` — `7b0c5328f7d746b958d91360d37bfdd4bd99114e6acee860d88dc8baf8b6b2bd`
- `PcbRoleWidthOwnerIdentityContractTest.java.pre-current15.20261019T081159440Z` — `ec65ccf7dc09c6670c64571e89e6cd477f268ef66f7991f5e9aff3b812ea9ac7`
- `PcbTraceWidthProfileContractTest.java.pre-current15.20261019T081210038Z` — `61bfc199bd781db1e54c5a35a10f3c270f4e59b1543d71f6858f04dbffbb8144`

Static diff review and `git diff --check` are the worker's validation scope.
Runtime contracts and the native/production build were not run here; root owns
the final execution and acceptance gate.
