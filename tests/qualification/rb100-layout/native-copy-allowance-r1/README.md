# Native capture copy allowance

This native-only prerequisite derives the exact historical
`FailureCohortRoutingStudy.java` parent whose SHA-256 is
`dc53245d52c7187868b40456b3952cdb2ecf20bc920b2354ff400f60ff68410d`.
The parent and its private dependency closure are required external evidence
inputs; they are not vendored here. This is not a standalone checkout test or
a production API.

Run the transformer with the selected Python in isolated mode:

```text
python -I -B generate_parent_overlay.py --source <exact-parent.java> --output-dir <existing-empty-owned-directory>
```

It checks the parent hash, applies nine exact-once byte replacements and creates
the overlay and readable diff without overwriting files. Paths containing a
symlink or Windows reparse point are rejected. Concurrent hostile path changes
are outside this task-owned-directory contract; this is not a security sandbox.
Partial failed output is retained for diagnosis.

The derived parent SHA-256 is
`c33f6f696fb52fedce583f19f47f3ff44ca01bca3c9b97410291d5284642d88b`.
Compile it and `NativeCopyAllowanceCanary.java` using JDK8, source/target1.7,
UTF-8, an empty sourcepath and the authenticated native dependency classpath.
Run `com.lushprojects.circuitjs1.client.NativeCopyAllowanceCanary` with assertions
enabled, then the derived parent's existing `--canary`. The current routing owner
must precede historical owner classes on the classpath. The evidence packet
records the actual selected tools, source hashes, class inventories and limits.

Each Meter defaults to the unchanged1,048,576-copy ceiling. A caller can set a
smaller allowance, which reaches the real capture preflight and wrapper guards.
The caller remains responsible for subtracting actual preexisting setup copies,
merging the two ledgers once and restoring the default after a guarded merge.
This prerequisite does not implement that dependent R2 integration.

See [acceptance and limits](../../../../docs/task-evidence/Q100/native-copy-allowance-r1/README.md).
