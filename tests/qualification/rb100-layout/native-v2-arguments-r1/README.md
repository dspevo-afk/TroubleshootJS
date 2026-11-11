# V2 argument preflight

This checked Source transition migrates the held native Study argument owner to
the normal V2 profile and distinct source/wire/context/output roles. It also
retires its old constructor adapter and descriptor loader. Both normal
entrypoints remain disabled. See CONTRACT_R1.md for scope and ownership limits.

Run the eight Source tests with an explicitly supplied pinned current Study:

    python -I -B test_v2_arguments_source.py --study-source <pinned-current-Study.java> -v

Input236974B/SHA4e0563854a2282b10d5804da9e9843814ba8363b23400eea63a2459ca14de680;
output235615B/SHA3fe46f10773b5f04c1c7a4b89f2390f21681658568c8ce0de4f32faee1193890.
The pure derive API takes Source bytes and fragment bytes; it owns no I/O/CLI.

Native replay requires the exact retained current dependency closure, selected
JDK8, source7/target7, explicit classpath and empty sourcepath. The canary takes
SMOKE or SUITE, an owned fixture root, that classpath and an explicit read-only
real symlink path. Source and fixture preparation is separate; this directory
does not promise a standalone clean-clone full-D producer build.

The finite evidence packet is in docs/task-evidence/Q100/native-v2-arguments-r1.
Shape/path checks do not authenticate hash values, sample producer-start time,
reserve an output directory or qualify normal producer/host execution.
