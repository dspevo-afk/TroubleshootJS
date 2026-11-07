# Focused canary cleanup accounting clarification

Frozen before focused host implementation. This supersedes only the CANARY
native-residual subtraction in CANARY_HOST_R1.md; other limits and contracts stay.
Both independent source reviews support the parent measured host-tail formula.

Charge the FULL measured wall of each native canary child as its conservative
cleanup residual. These literal processes have no actual BASE ArmResult cleanup
partition. Their reported work/groups remain checked separately; subtract no
canary work from cleanup. This intentionally overcounts ordinary literal work
rather than infer a native owner-cleanup partition that was not observed.

The final independently derived cleanup upper bound is sum(full measured native
canary child walls) + max(0,parent full selected host elapsedMs minus host-local
cleanupStartOffsetMs), under the original120000ms. Offset is measured once from
host monotonic entry to post-native source/scratch closure start. Parent wall
starts immediately before the selected host call and ends after proven exit,
stream drain and module cleanup. The host tail includes final receipt/output
writes. Compiler/preparation stays outside this tail and receives no invented
120s full-host cap. The consumer's180s bounded source/output closure is reported
separately. No overlapping callback/unit time is subtracted or double credited.

Full P8 BASE modes will freeze their own exact accounting before implementation:
NULL/OBSERVE may subtract only actual inclusive bounded operation work;
CALLBACK_THROW/REASON_THROW charge full native wall. This is not authorization to
implement the unfinalized BASE helper/JSON transport.
