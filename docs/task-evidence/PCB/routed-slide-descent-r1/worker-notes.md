# Routed slide descent worker notes

Status: the leaf-owned COPY sources and bounded adapters are ready for root
review. Native execution, build, transport, reader and publication gates are
**NOT RUN** by this worker. Root owns integration, freezing and publication.

The implementation is aligned to protocol SHA
e047c6b87e72538b7b161449b7938b80c8e1ec9c081bea27ba40c26d3c4b73ba as read
from the packet. It uses version 15, the selected-after-edge-trim phase, roots
5 then 2, limits of four accepted steps and 128 evaluated moves, first strict
selection, independent current-source cohorts, all-four metric guards, and the
four declared termination statuses. The summary field order and nested source,
metrics, control, structural, feasibility and compaction objects follow that
protocol.

The passive-leg COPY adapter captures one complete current-layout
analyzeTarget snapshot and derives the canonical nonincreasing ten-unit move
vector from that same snapshot. Structural JSON contains every part, including
ineligible parts, and the complete pad/net inventory; the existing analyzer
provides incident details for eligible parts while preserving its established
ineligible-part behavior. A move's affected trace count deduplicates local
layout trace indices.

The slide-compaction COPY adapter exposes the existing full compaction and proof
owner through a small reversible adapter. It returns the normalized private
layout, restores routing and recovery statistics, reframes the original plan
with its metadata, and checks every component and pad placement against the
normalized frame. It does not duplicate the physical proof or select a winner.

The new descent driver is shared by the runtime backend and the literal unit
backend. It captures one control per source, evaluates one canonical cohort
from the same source, increments compaction counts for every validated move,
advances only on the first strict result, retains every step source for final
immutability checks, and compares freshly captured final metrics with the
cached result. Unknown failures, cancellation, and final source verification
failures propagate.

The six unit fixture groups are:

* canonicalFirstImprovementAndTransitions
* strictDescentAndInitialGuards
* exhaustionAndEmptyCohort
* acceptedStepAndEvaluationCaps
* failureAndCancellationPropagation
* selectedObserverOwnershipAndAdapterIsolation

The unit marker is evaluator=selectedRoutedSlideDescent. The fixtures use
independent literal outcomes through the shared driver for first-selection,
source transition, metric guards, rejected and exhausted cohorts, cap priority,
and private metadata transition. They exercise the real generation scope
cancellation checkpoint and the selected-observer invalid-root, null-dispatch
and owner restoration seams. No fixture starts generation or routing.

Static checks completed:

* All three COPY Java files and this note use one LF line ending at EOF with no
  trailing whitespace.
* Java source remains source-7 compatible; no lambda or method-reference syntax
  was added.
* The three Java sources have balanced braces and parentheses.
* The obsolete literal driver helpers and the duplicate descent comparator were
  removed; the new driver owns the strict-improvement comparison.
* Adapter blocks contain no route request or selected mutation call.

Current COPY fingerprints and byte sizes:

* PcbRoutedPassiveLegCensusContractTest.java — 56,490 bytes —
  8e616f97e6a3f987cff1399368e312555634233c95dab4021921be8a8bfd0cbe
* PcbRoutedSlideCompactionContractTest.java — 73,465 bytes —
  9db10e401c9d51bdafc97803756d0eb812c61532b65ae2677cee394fbced4747
* PcbRoutedSlideDescentContractTest.java — 51,499 bytes —
  ff0741611ad7240948eaa3b5aead35f9b5587b5a9a73e11ac714ecda1f2ec8eb

These are source-only pre-freeze values. Root integration, protocol or reader
changes require fresh fingerprints and gate records. This worker did not run
javac, the maintained native driver, the route wrapper, PowerShell transport,
the independent reader, GWT, the production build, browser/player checks or the
full qualification matrix.
