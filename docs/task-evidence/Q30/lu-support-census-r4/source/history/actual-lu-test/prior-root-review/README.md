# Q30 actual-LU support-census canary

`Q30LuSupportCensusActualLuContractTest` is a native-only contract for the real `CirSim.lu_factor` general path, the private `lu_factorNonlinearOwned` path reached by reflection, and the `Q30LuSupportCensus` draft. It factors independent control and observed clones, compares finite block factors, `ipvt`, solutions, row identities, and failure outcomes, and wraps the observed factor in the collector.

The finite fixture uses active size 3 in full 5-by-5 allocations. Its active support is `[A,B,A]` rows by `[B,A,A]` columns; inactive row/column tails carry sentinels that must survive. The test also covers a zero-row false result, a finite overflow `NUMERICAL_FAILURE`, a nonzero-row singular matrix whose real LU writes `1e-18`, collector failed/incomplete flags, an undeclared zero-pivot hook producing `exact == false`, cadence 8 selection, and cold/warm isolation. The existing native collector contract remains the owner of the default 4096 cadence check.

The test targets the author’s fixed collector API: `recordPreFactor` and `recordPostFactorZeroRisk` must accept full row allocations when the active `n` is smaller, while scanning only the active lower bound. It does not add a second LU implementation, install runtime instrumentation, or certify `runCircuitOwned` wiring; root must register it with the future fixed overlay and run the native gates. This leaf performed no execution, build, host, stage, commit, or push.

The maintained native driver may recognize the success prefix `PASS: Q30 actual-LU support-census contracts assertions=`. A passing run must still be interpreted with the independent native and GWT gates for the registered overlay.

The assigned support-source SHA was `fb03adeaa0d6fbf1f3d4ef7f82611a354da4e7940702831f490d3eed9d99960a`; the frozen support source with the final GWT defensive-copy repair was observed as `1c2aeaa150a766f4e6dcddacaa9f8865b58274f9e160294d90b8073646bfd5b5`. The source was not edited here.
