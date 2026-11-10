# Synthetic original DTO metering contracts

The authoritative fragment, pure Source transform and its Source tests live in
../original-prepayment-r1. This directory owns the synthetic native canary,
independent literal reader and immutable reference fixture. SOURCE_MANIFEST_R1
maps every admitted copy to that existing owner or this testbench.

Run the Source suite with python -I -B ../original-prepayment-r1/test_original_dto_metering.py
--study-source <pinned-current-R7-Study.java> -v (one command line). The external
Source must be exactly 235025 bytes with SHA256
48f67e37f5603fdf9eade38d41cd86c95fb7171b16edc7a6f6cd8b13637e0b00.
Run the independent pure suite with python -I -B test_original_dto_meter_oracle.py -v.
The Source suite includes actual nested CLI and Windows cmd junction controls.

Emit Source with python -I -B ../original-prepayment-r1/generate_original_dto_metering.py
--source-root <directory-containing-CohortTerminalTelemetryStudy.java>
--component-root ../original-prepayment-r1 --output-root <empty-existing-directory>
(one command line). Output is exclusive; canonical stdout provenance follows
successful close/readback. Preserve exact Source bytes and reference pins.

Verify a native report with python -I -B verify_original_dto_meter_r1.py --case smoke
--input-json <native-summary.json> --reference-root <this-directory> (one command
line); use suite for its eight cases. The reader authenticates four files before
compiling oracle bytes, then the frozen contract and reference fragment. Nonzero
rejections must match the specific expected code; ORACLE_FAILURE is never a
successful negative control. Contract and fixture hashes are independent pins.

Native replay requires the exact retained current Source and authenticated native
dependency closure described in the Q100 evidence packet; private host scripts
are not a standalone clean-clone build entrypoint. Native SMOKE/SUITE are uppercase.
Only generic Map/List/SetupResult/Arguments metadata is exercised. No positive
originalDto, real/fake OriginalArm/ArmResult, graph, Session, routing or genuine
envelope runs. The formatter is testbench transport, not production export proof.
