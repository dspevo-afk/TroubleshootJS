# Raw operands and independent oracle R1

Status: FROZEN before producer work. This defines the raw operand and literal
oracle contract; companion source, native, host, reader and controls contracts
are bound by CONTRACT_SET_R1.json. No runtime evidence is asserted.

## Question and scope

Reconstruct the contributor nets for one existing mixed reservation at parent query 11: selected layer `1`, directed bit `8`, `(382,238)->(381,238)`, horizontal slot `[381][238]`. Bind it to the authored BASE identity: root `9007199254740993`, placement `-6587958367974952701`, routing `8445301674744508279`, request `0`, attempt `4`, pass `2`, receipt ordinal `307`, lineage ordinal `77`, prefix H48, prefix hash beginning `6b49cfb2`, graph hash beginning `ff6466d2`. These are identifiers only; the complete immutable values must come from frozen pins.

Do not open a route, change a reservation, re-evaluate a live predicate, or infer routing/physical feasibility. Capture only the existing nullable `String` slot value for comparison; independently compute its expected aggregation from raw operands. No Java contributor list is an oracle.

## Minimum raw observation

One bounded JSON header binds the exact request/query/slot and carries:

- `grid=10`; selected layer and requested query net in the header; actual `activeNetId`/`activeTraceWidth` in the OBSERVE footer; candidate profile alias `ROLE_WIDTHS` and runtime profile identity `ROLE_TRACE@1`.
- Outline rectangle `(x,y,width,height)`, plus actual `minX`, `minY`, `gridWidth`, `gridHeight`; independently verify `minX=outline.x+10`, `minY=outline.y+10`, `gridWidth=(outline.width-20)/10+1`, `gridHeight=(outline.height-20)/10+1` using Java integer division. Include active layer, `emptyComponentFace`, `checkProfiledPadTouches`, and `escapeOutletLand=6` with `escapeOutletLandOrigin="PINNED_FULLER_SOURCE"`. This is source-derived metadata, not a runtime Router field: the authenticated P8 selected context is `FULLER_TWO_LAYER` (`transitions=4`), so the live Search constructor passes `PcbTwoLayerRules.VIA_LAND=6`. The observation-copy constructor passes zero and copies the original prepared corridor array; zero therefore does not describe the original operands.
- Exactly 246 pad records in `Router.pads[]` order, each with its array index, `padId`, `componentId`, `netId`, actual `BoardNet.RoutingRole.name()`, center `x/y`, `mountingSide`, attachment, escape `dx/dy/length`, `padBounds{x,y,width,height}`, owner `routingCourtyard{x,y,width,height}`, actual prepared `escapeCorridor{x,y,width,height}` for comparison only, and actual prepared `padWidths[index]`. Capture the courtyard from the actual owning component; never use the prepared corridor as oracle input.
- Actual final slot `String` (`null`, `""`, or net ID), read at the existing `canTraverse` reservation lookup after `prepareNet` and before `allowed`. Include the active cache identity/width at that lookup so same-net and unchanged-width cache reuse are visible.

Every geometry, grid, and width value is a JSON integer (reject booleans/floats); enum/string fields must be exact members; IDs must be unique and the 246 records complete. `outline`, `padBounds`, and `ownerCourtyard` must have positive width/height. `preparedEscapeCorridor` and derived nominal corridors must have nonnegative dimensions; zero width or height is valid, including the opposite-face 0x0 point. Derived expansions must fit signed int32 arithmetic, with no additional ±100,000,000 cap; that board-coordinate cap applies to source board coordinates/endpoints. Require all rows for the same component to carry one identical owner courtyard. Require Router `hasCopper` for every pad: `PLATED_THROUGH_HOLE` permits either layer; `SURFACE_PAD` requires `mountingSide` to match the active layer face. `mountingSide` is `TOP` or `BOTTOM`; attachment is `PLATED_THROUGH_HOLE` or `SURFACE_PAD`. `emptyComponentFace` is true exactly when no component mounts on the active face. When `emptyComponentFace` is true, the target adjacent move uses the cached reservation path; `checkProfiledPadTouches` is true for ROLE_WIDTHS and controls whether per-pad finite-width touch edges are added during cache preparation. Rectangle reservations remain active regardless of that flag. Enforce signed integer board coordinates/endpoints within ±100,000,000 and validate escape endpoints. Escape vectors are cardinal or zero; length is nonnegative and a positive length requires a nonzero vector. Widths are positive odd integers and must equal the pinned ROLE_WIDTHS mapping. These bounds reject malformed operands; they do not claim electrical or routing feasibility.

P4 PAD rows preserve pad/net/component/center identity and a serialized role token, but their reader checks token consistency per net rather than independently proving that token equals the live `BoardNet` enum. Capture actual role and width from the prepared Router operands; do not substitute old PAD role strings.

## Independent calculation

Use an offline integer implementation with independent literal fixtures. Do not call the Java reservation predicate or construct expected contributors from its output.

1. For each pad, compute initial escape length `ceil(length/10)*10`. Use the source-derived outlet land 6 (`PINNED_FULLER_SOURCE`), not the copy-constructor zero. If outlet>0, length>0, and mounting side equals active face, find the owner courtyard edge along the cardinal escape vector; compute `distance=(edge-padCoordinate)*direction`, `outlet=distance+outletLand+1`, and extend length to `max(initialLength,ceil(outlet/10)*10)`. Otherwise retain the initial rounded length. Form the axis-aligned center-to-end corridor; if mounting side differs from active face, replace it with the zero-size corridor at pad center. Independently compare this derived rectangle with the captured prepared corridor, without using the captured rectangle to derive it.
2. Compute `clearance=(activeWidth+padWidth+1)/2+6`, then `margin=clearance-1`; expand corridor by that margin on all sides.
3. For rectangle reservation, reproduce the source candidate bounds exactly with Java integer division truncating toward zero: `x0=max(0,trunc((r.x-minX)/10)-1)`, `x1=min(gridWidth-1,trunc((r.x+r.width-minX)/10)+1)`, and analogous y bounds. The source loops both inclusive. For this one target, an O(1)-per-pad membership check is equivalent: first require its target grid indices to lie in those four bounds, then test only the target horizontal edge `(minX+slotX*10,minY+slotY*10,10,1)` with strict `Rectangle.intersects`. This preserves `reserveRectangle` behavior without scanning the whole grid. Boundary-only contact does not intersect.
4. For profiled pad-touch reservation, reproduce the source’s inclusive ranges. Let `half=activeWidth/2`. The same one-target optimization is allowed: compute the source start/end grid-index bounds and call closed-edge `touch` only if the target slot lies inside both inclusive ranges; do not enumerate all grid cells. Horizontal physical limits are `x0=bounds.x-10-half-1`, `x1=bounds.x+bounds.width+half`, `y0=bounds.y-half-1`, `y1=bounds.y+bounds.height+half`; vertical limits are `x0=bounds.x-half-1`, `x1=bounds.x+bounds.width+half`, `y0=bounds.y-10-half-1`, `y1=bounds.y+bounds.height+half`. Convert start with Java truncation-toward-zero division by 10, increment if `delta>0` and remainder is nonzero, then clamp to `[0,size]`. Convert end similarly, decrement if `delta<0` and remainder is nonzero, then clamp to `[-1,size-1]`. Iterate inclusive.
5. Each horizontal stroke has rectangle `(min(x1,x2)-half, y-half, abs(x2-x1)+width, width)`; vertical is the corresponding transposed rectangle. Pad touch is closed-edge overlap (`>=` on all four sides). Include fixtures for outlet 0 and 6, owner-edge extension rounding, negative-coordinate division, exact strict-intersection boundary, closed-touch boundary, odd widths, same-net deduplication, and distinct nets producing the empty-string mixed sentinel.
6. Merge reservations exactly: null becomes the net; a repeated same net stays that net; any distinct net changes the slot to `""` and it remains mixed. Compare only this computed nullable string with the actual captured slot.

## Source pins

These are the 14 required Router/role/geometry owners, relative to the frozen source repository. The coordinate-system class is declared inside `PcbPhysicalTransform.java`; there is no separate `PcbCoordinateSystem.java` source file.

| Relative source | Bytes | SHA-256 |
|---|---:|---|
| `docs/task-evidence/Q100/canonical-geometric-reasons-r1/overlays-r1/PcbNetRouter.java` | 142148 | `3bc847e015e1b379dcafcb1e39eb4964e65e7a57d6ef06ed2c710cca1ff48f2f` |
| `src/com/lushprojects/circuitjs1/client/BoardNet.java` | 1073 | `6eb74c6aaf7f109ecb692c33bb69e7d2de0faee5846dfae9b107de261036afa5` |
| `src/com/lushprojects/circuitjs1/client/PcbTraceWidthProfile.java` | 1778 | `c8a9737240b77f13ee769a2a6fcc5f6b645f834cd640a9dc9cab132799052d65` |
| `src/com/lushprojects/circuitjs1/client/PcbPadPlacement.java` | 5202 | `bfe7f77b9ca77250a1b08ff35a1a7cd5c469ac6e13deb26011814a7cce2b27b7` |
| `src/com/lushprojects/circuitjs1/client/PcbCopperAccess.java` | 1450 | `a31d5e74b60615fb30bb57c1ea4f2f787d4550e4a9027a9e839a32f4abe59b8c` |
| `src/com/lushprojects/circuitjs1/client/PcbCopperLayer.java` | 495 | `e84912595c66892b9440678e2d6637763c957af02f2f7963c06dd26de75944a6` |
| `src/com/lushprojects/circuitjs1/client/PcbTerminalAttachment.java` | 207 | `18383d3ffc54ae3db7bf57ff79c8e1e2fa22a178706a4dd0135e3f760482f8b4` |
| `src/com/lushprojects/circuitjs1/client/PcbTraceRules.java` | 899 | `ffd7848e0016bc726d9098d583ba958c49395a72d95648250a2f6209e9f65149` |
| `src/com/lushprojects/circuitjs1/client/PcbConductorBuilder.java` | 22611 | `c253b133070cbfdec2b0d5b917476ea6262c3edfd2bbacb847218f5165165d37` |
| `src/com/lushprojects/circuitjs1/client/Rectangle.java` | 5017 | `2e003f39f35b26e4c06f90bb1eb55ab3ccf0290fc6c1848631283a7f6c265ba7` |
| `src/com/lushprojects/circuitjs1/client/PcbPhysicalTransform.java` (including `PcbCoordinateSystem`, ±100M board-coordinate contract) | 12131 | `f35036fc36a8f098725b8a39e068fd1c02b0403e86160c4a36e6dd766f8e3c2d` |
| `src/com/lushprojects/circuitjs1/client/PcbComponentPlacement.java` (owner routing courtyard) | 20492 | `3f3225e9652c022cb454a77f7dd62d795b6263e387b7e8e98da24add45a171cc` |
| `src/com/lushprojects/circuitjs1/client/PcbLayerRoutingPrototype.java` (live FULLER policy → Router outlet argument) | 169802 | `759831c91345aedab9e78d8c83785019f7e59287a18f4f99f7af14876776c474` |
| `src/com/lushprojects/circuitjs1/client/PcbTwoLayerRules.java` (`VIA_LAND=6`) | 24212 | `b04f9906d726117412d62b6022ea5b8e2f26bf111063292b0d7560f51b0fb8cf` |

Existing immutable operation/graph-reader dependencies retain their authenticated P4/P5 pins. NATIVE_WORK_R1 and HOST_R1 govern transport, work and cleanup; these contracts do not loosen old readers.

## Sidecar framing and exact mode-dependent rows

`reservation-operands.jsonl` uses UTF-8 without BOM, compact sorted-key JSON with unique keys, one LF per line, no CR/blank lines, maximum 65,536 bytes per encoded line, and exact EOF. `schema="tsj-q100-reservation-operands-v1"`. NULL and TAP_THROW each contain exactly header+footer (2 lines). OBSERVE contains exactly header+246 PAD rows+footer (248 lines). The per-operation 8 MiB cap remains inclusive of actual P8/P9 payloads and emitted streams; no budget is enlarged.

The common exact header keys for all three modes are:

```text
kind,schema,runId,inputSha256,sourceSha256,wireSha256,wrapperMode,p8Mode,parentPrefixSha256,parentGraphSha256,queryOrdinal,parentOrdinal,captureState
```

For NULL, `captureState="NOT_REGISTERED"`; no operand fields are present. For TAP_THROW, `captureState="DTO_DISCARDED"`; no operand fields are present. For OBSERVE, `captureState="CAPTURED"` and the header adds exactly `identity,grid,outline,minX,minY,gridWidth,gridHeight,selectedLayer,requestedNetId,traceWidthProfile,profileIdentity,emptyComponentFace,checkProfiledPadTouches,escapeOutletLand,escapeOutletLandOrigin`. `identity` has exactly P8's fields `layer,fromState,toState,bit,selected,fromGridX,fromGridY,toGridX,toGridY,fromX,fromY,toX,toY,direction,netId,startPadId,rootPadId,reachedPthPadIds`; bind to P8 query row 10 and authenticated P6 stage/cut identity. For this exact target, `reachedPthPadIds` is exactly the empty array; root `DA.A` remains a separate participating endpoint. Reject a nonempty list rather than broadening this case. This is why the native header scalar-leaf count is exactly47. `grid=10`, `traceWidthProfile="ROLE_WIDTHS"`, `profileIdentity="ROLE_TRACE@1"`, `escapeOutletLand=6`, and origin is `PINNED_FULLER_SOURCE`. These are source-derived metadata: the authenticated P8 policy is FULLER_TWO_LAYER (`transitions=4`), so Search passes `PcbTwoLayerRules.VIA_LAND=6`; the observation copy's zero constructor argument does not describe the original corridor. `outline` has exactly `x,y,width,height`.

Each OBSERVE PAD row has exactly:

```text
kind,index,padId,componentId,netId,routingRole,x,y,mountingSide,attachment,escapeDx,escapeDy,escapeLength,padBounds,ownerCourtyard,preparedEscapeCorridor,padTraceWidth
```

Indices are actual `Router.pads[]` indices in strict `0..245` order. Each nested rectangle has exactly `x,y,width,height`. Capture one owner courtyard per actual component and require all PAD rows with that `componentId` to agree. P4 has no courtyard field: validate rectangle bounds and per-component consistency, then compare each independently derived corridor with its captured prepared corridor. Do not claim the courtyard itself is P4-bound. `preparedEscapeCorridor` is comparison-only and never an oracle operand. Bind the P4 PAD census as a map by pad ID and compare exact `netId,componentId,x,y`; P4 PAD order is sorted and must not be substituted for Router array order. Capture actual `BoardNet.RoutingRole.name()` and prepared width; old P4 role text alone does not prove typed runtime role.

OBSERVE footer has exactly `kind,complete,runId,wrapperMode,p8Mode,recordCount,padCount,requestedNetId,activeNetId,activeTraceWidth,slotValue`. It has `kind="footer"`, `complete=true`, `recordCount=248`, `padCount=246`; `slotValue` is exactly JSON null, empty string, or net ID. NULL footer has exactly `kind,complete,runId,wrapperMode,p8Mode,recordCount,captureState`, with complete=true, recordCount=2 and `captureState="NOT_REGISTERED"`. TAP footer has exactly those same fields plus `tapThrow`; `tapThrow` has exactly `attempted,caught,sameThrowable,exceptionClass,message,count,dtoPadCount,dtoDiscarded,slotDiscarded,queryContinued`. Expected TAP values are true/true/true, `java.lang.IllegalStateException`, `FIRST_RESERVATION_OPERANDS_CALLBACK`, 1, 246, true/true/true. No slot value or PAD record survives TAP.

The native index schema, key set, timing/work/cleanup fields, and final-index semantics are owned exclusively by the frozen NATIVE_WORK contract. The P9 reader must use that exact pinned validator; this contract intentionally does not define a competing index schema. The normal host tree has P8's exact seven data files and the two P9 files under `runs/MODE/p9`; the exact names/counts/operation refs remain those in HOST_R1.

All geometric/grid/width/index/count fields are JSON integers, never bool or float. Enforce ±100,000,000 board coordinates/endpoints; require positive board-bounded dimensions for the outline, pad bounds, and owner courtyards; allow nonnegative dimensions, including zero by zero, for captured prepared escape corridors. Derived corridors and clearance expansions must remain safe signed-int32 values. Also enforce exact enum domains, unique pad IDs, valid cardinal/zero escape vectors, nonnegative escape length, odd supported widths, and derived grid dimensions. Require `hasCopper` for every pad (PTH on either layer; surface pad only on mounting face). `emptyComponentFace` means no component mounts on the active face. When true, the target adjacent step takes the cached reservation branch. `checkProfiledPadTouches` is true for ROLE_WIDTHS and gates construction of finite-width touch-step reservations; rectangle reservations remain active either way. Opposite-face escape is a zero-size point corridor, then still clearance-inflated for rectangle reservation.

Grid validation requires gridWidth>=1, gridHeight>=1 and
gridWidth*gridHeight<=700000, preserving the pinned existing grid limit.
