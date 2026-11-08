# Preserved failures and corrections

Before Java execution, root rejected the worker's reported draft hash because
it did not identify the actual handoff bytes. Reversing only root's two qualified
assertion calls reconstructs93437d6e2209297edf7151f5ef6ba126b3eabefb538dcbc6d536c44ed1122fc4,
not the reported2d68e0e5676eac3da16fc715d859b2281c3a04e96d42db3592712711b9acdd84.
LF/CRLF comparison did not reconcile them. The reported draft was not run or
accepted. Actual bytes were reviewed/pinned before execution. Two Watch.check
calls needed explicit BranchLimitSelectionCanary.check qualification to avoid
method shadowing; root corrected them before the first compile.

R1 compiled successfully, then FAILED exit1 on full field-for-field terminal
context parity: frontierCount differed. The new-marker frame retained a later
queue record and a three-entry index; the old-marker comparison frame did not.
This was a mismatched fixture. Root corrected the old frame to the same stale,
chosen and later nodes, replacement index entries and two one-unit advances.
The full equality oracle was retained. Production/derived owner, caps and
terminal semantics did not change. Failure-progress counters were added for
future failed canaries; R1's partial Session count was not returned.

Failed source5d80f8075accbb59e140bd4e8589fc30a510d8a283725749a047eecef51767ca,
original classes and raw receipts remain private and immutable. Final R2 source
52101ac91adca2227cdf72ee5d247382cfd4571e5cfeacdf806059269cd0ce0c
compiled into a new empty stage and passed. FINAL_GATES retains both receipts'
hashes and separate FAIL/PASS results. A later pass does not relabel R1.

All earlier D failures remain in their accepted packet. No additional authored
run occurred to obtain a greener result. No budget, assertion or independent
oracle was weakened.
