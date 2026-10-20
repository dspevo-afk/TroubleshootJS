# Normal packing census worker notes

Status: **IMPLEMENTED, STATIC REVIEW ONLY**.  This leaf owns the normal census
contract, its seven range wrappers, and the verifier registration rows.  It did
not edit production generation, placement, routing, identity, replay, or UI
sources.

The source boundary is base commit `2a73b949a02de718bd968c2920ba6045d7406c5a`.
The prepared baseline audit is `preparation-audit.json` with
`baseSealSha256=9ff8de2abae0d3e555bc57c84ff7231d2aa8abd5dfdc2383a65398915074da9a`,
`caseCount=44`, and `nativeBaseline=NOT_RUN`.  The accepted1467 baseline COPY is
referenced by the packet-local scratch token `tsj-normal-packing-baseline-nhun4ix8`.
This worker placed the eight census sources and eight verifier registration rows
in that COPY; the main worktree contains neither those sources nor those rows
after the ownership correction.

The census uses the current registered normal request path:
`PlayerLaunchRequest.random(family, exactSignedRootText, profile).generation()`
followed by `candidate(0)`.  `NORMAL_MEDIUM` requests use
`Prepared.beginConstruction()` and check the unchanged
`GenerationCoordinator.MAX_JOB_STEPS`; other active families use the prepared
construction owner.  Accepted owners require normal physical admission, run
full `validateGeometry`, compare live graph hashes before and after validation,
retain logical component/pad/net inventory, record layout/outline/copper/hole/
via/generation-work metrics, and invoke the existing GRID10 whitespace sampler
with version1.  Only `PcbRoutingRejectedException`, `SupportedEnvelope.Rejected`,
and `GenerationJob.Rejected` become visible typed rejection rows.  Assertions,
unknown failures, cancellation, and cleanup failures propagate.

The fixed catalog order is the nine base families, then
`COMPOSED_CONTROLLED_INDICATOR`, then `RB30_CONTROL`.  Each uses roots `0`, `17`,
`13`, and `-9223372036854775808` in that order.  The eight suites are partitioned
as base families0–2, base families3–5, base families6–8, the composed family,
and one RB30 suite for each fixed root.  The helper emits one strict
`PCB_NORMAL_PACKING_CASE ` JSON row per attempted case and each suite emits a
`PASS: PCB normal packing census ...` marker.

Static checks completed:

* exactly eight owned Java census sources are present, with one helper and seven
  thin wrappers;
* the verifier script has eight matching ordinary-suite registrations, all using
  the existing default budget path;
* the source constants contain exactly eleven active families, four exact roots,
  and no RB56 case; the helper asserts eleven active versus twelve registered
  families and the held RB56 registration;
* source inspection confirms one shared construction/admission path and no
  alternate factory, seed substitution, route request, or production edit.

Current leaf-byte pins (before root integration) are: helper
`PcbNormalPackingCensusContractTest.java` 15754 bytes,
`989088f27655064833c19ff6f5d5dbab8d648eb1d8bfb97462af60b4c9a4d954`;
wrappers Group2–Group8 respectively
`bd420e4671f6842f15ec455c0f2ec775a28afba53255689064538c5775815a06`,
`552fad16987d870ffd2deb748d15cdcbfd3dc8b0c3d4ce45a740d0475c92727d`,
`a5863222f708e4b6192a92674799325a70166976b25cf338b646bbee5a7f8d0b`,
`224938ce604a903f0d9dcee88a2e72411f9bd8ff9aff117a9170469e5e031928`,
`f69c062ae4070dfd01e0e046dff0ac91752dcfc7b46dc2cb2e3c70243b5970f7`,
`18142ad3a9ecd65426f72e1698ae3b12c9b130949d2600253bb4242d36355882`, and
`59461ac49e28f055e889ba3c64badde69ab9099254e077e52dc33aa81a013e51`;
the registration COPY is
`abb3f0182f3e83f68b8b84540e1b31545d605bd46d7c793843e5b841ac85b300`.
The shared helper deliberately uses only APIs present in the accepted baseline
and does not reference the candidate17 packing-version API; the newer
candidate-specific packing identity remains owned by the root integration.

NOT RUN by this leaf: Java/native execution, the 44-case baseline run, fresh
candidate comparison, JDK8/GWT build, browser or visible New Board interaction,
screenshots, replay/save/load gates, and final reader/schema verification.  Root
owns those gates and must align the reader with the final packet protocol before
freezing the COPY.
