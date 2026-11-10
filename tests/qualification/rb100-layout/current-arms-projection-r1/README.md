# Synthetic current arms projection contracts

This fixture tests `CurrentResultProjections.arms`; the authoritative helper is
in `../current-result-projections-r1/CurrentResultProjections.java`. The old
projection canary also remains in that owner directory. Native compilation uses
those two Sources plus `source/CurrentArmsProjectionCanary.java`.

Run the pure oracle tests with `python -I -B test_arms_oracle.py -v`. Verify a
native summary with `python -I -B verify_arms_projection_r1.py --case smoke
--input-json <summary> --reference-root <this-directory>` (one command line).
Use suite for its seventeen cases. Four pinned reader files are authenticated
before the oracle's raw bytes are compiled. Reference Sources under source are
immutable reader evidence fixtures; they do not introduce new production owners.

Native gates in the evidence packet require the exact retained candidate and
accepted dependency closure. PUBLIC_COPY_MAP_R1.json maps candidate Sources to
repository owners. Those private host scripts are not a standalone clean-clone
build command. No producer dispatch, fake owner/arm object, graph, Session,
routing or genuine envelope is exercised. The formatter is testbench transport;
its bytes do not certify production export accounting. See the Q100 evidence
packet for gates, predecessor failure and remaining exclusions.
