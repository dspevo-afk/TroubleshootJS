# Current arm read Source and synthetic meter contracts

This qualification adapter splices four current Study methods and one private list
reader. Existing field/map adapters retain ownership. It follows the accepted
original DTO Source emission and uses that component's authenticated IO owner.

Run python -I -B test_current_arm_reads.py --study-source <pinned-current-Study.java>
--io-source-root ../original-prepayment-r1 -v (one command line). The external input
is exactly235950 bytes/SHA256f25010169adb2e4934a1303dc076cb948ba5f0c7160603a1fecd6b060e916541.
The22 Source checks include real nested CLI and Windows junction controls. Run the
independent31-test suite with python -I -B test_current_arm_read_oracle.py -v.

Emit with python -I -B generate_current_arm_reads.py --source-root <directory-with-
CohortTerminalTelemetryStudy.java> --component-root <this-directory> --io-source-root
../original-prepayment-r1 --output-root <empty-existing-directory> (one command line;
keep the source-root placeholder on one line). The exact IO source bytes authenticate
before private execution. All output is exclusive; close/readback precede canonical
stdout provenance. Failures can retain partial output and return2.

Verify native output with python -I -B verify_current_arm_reads_r1.py --case smoke
--input-json <summary.json> --reference-root <this-directory> (one command line).
Use suite for12cases. The CLI pins three Python files and their note before oracle
execution, then checks the immutable contract/reference fragment. Require the exact
expected rejection code; ORACLE_FAILURE is never a successful negative control.

Native replay needs the exact retained current dependencies and JDK8/target7; the
private host is not a standalone clean-clone build entrypoint. Native mode names are
uppercaseSMOKE/SUITE. Tests use synthetic metadata and generic list operations only.
No actual/fake arm owner, graph, Session, routing, positive originalDTO/executeArm/
R9DTO or genuine envelope is exercised. Full-result/authority/lifetime remain gated.
The output formatter is testbench transport, not production export proof.
