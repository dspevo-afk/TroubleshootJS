# Public held-family admission fence

Base/current verified origin `fc88419b4e4dfe5f58cede8e23ce9a9cfab388f4` on
`codex/q60-faulted-profile-counts`. Independent of the pending product decision
about MEDIUM versus future HARD content; no family or profile is enabled here.

Current exact public launch/replay parsing checks registration and global profile
availability, allowing a held RB56 family with the already-available MEDIUM band
to reach session.begin before the coordinator rejects it. The coordinator keeps
the electrical owner safe; the session can still unnecessarily enter PREPARING/
ERROR. Broad Quick Play support also omits the normal-enabled registration flag.

Reproduce with a current-epoch available-profile negative before the production
fix. Reject held public families at their existing launch/parser owner before
session mutation, and make broad Quick Play support honor that same catalog flag.
Keep registration separate for developer qualification. Preserve enabled exact
requests' existing actual-evidence profile assessment, signed-long transport,
canonical candidate order, public wire formats and private qualification identity.
Do not substitute a broad-seed profile declaration for actual diagnosis.

Root owns all files. Expected production owners: PlayerLaunchRequest,
PlayerFamilyCatalog.RegistrationBoundary and QuickPlayAdmission only as required.
Keep coordinator's pre-cancel and publish guards. Tests cover current-epoch
EASY/MEDIUM held-family requests, public replay/save refusal, independent copied
registration disabled/enabled with an available profile, valid public/private
round trips, exact signed-long and no unintended global catalog change. Retire
the old-epoch HARD false falsifier by using the current epoch.

Run focused native parser/catalog/session/dependency contracts and the actual
final-source JDK8/GWT build. Use real production player input to compare held
replay refusal with a valid exact replay, preserve the current owner/view and
inspect useful screenshots. Keep preparation failures distinct from cleanup.
Record owned preview/browser lifecycle and retained pre-fix failures. One expensive
build/test at a time. Public HARD/RB56/save gates and protected regression stay held.
