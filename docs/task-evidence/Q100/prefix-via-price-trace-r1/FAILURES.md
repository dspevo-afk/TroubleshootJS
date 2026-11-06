# Preserved failures and limits

FAIL first CLI canary: the initial reader compared ctime across path and handle
providers. It rejected unchanged pinned input as FILE_CHANGED before output.
Its pre-fix source6aa62f1662e0962b26e35a4a6c127e48ad5e5bbb397bbb19ac0a6f7b42901f42
and process receipt are retained externally. A real selected-Python canary showed
matching device/inode/size/mtime/link identities, stable path ctime and stable
handle ctime, but different cross-provider ctime. The fix uses the existing
maintained-reader comparison boundary and preserves both provider checks/hashes.
Final real CLI gates pass; no input changed and the initial failure stays FAIL.

FAIL first unit import: a literal backtick-n in the supplied fixture caused a
SyntaxError. The source was corrected, with original05c9472f26998764195a9bafbd12b9175cc51121adb0d9ef78dcfa76a6241c01
bytes reconstructed/preserved and verified against its recorded hash. No expected
assertion changed. Final nine methods pass under the selected Python.

FAIL first independent PS5 oracle: Measure-Object does not accept the supplied
scriptblock as a property selector on this host. The source/failed invocation are
preserved; projecting integer values before summing corrects the selected path.
The corrected independent parser/count oracle passes without changing expectations.

The first staged whitespace check rejected CRLF bytes under the packet's raw
attributes, including one mixed-EOL fixture line. Public metadata/attributes and
the fixture were normalized to LF; original bytes/pins remain external, JSON
values and test statements are unchanged. Final selected tests were rerun after
normalization; no assertion or whitespace rule was suppressed.

No routing treatment is adopted. RAMP remains TOTAL_SEARCH_LIMIT with incomplete
comparison. Missing geometry strings/search-state equality, causal proof,
fixed-prefix reachability and playable100 are not supplied by trace digests.
No new native/GWT/Player/electrical/population renewal. Full30 owner-excluded;
HARD/PSYCHOTIC unavailable/unread/unrun here; historical boundary UNCERTIFIED.
The previous optional link/junction fixture remains NOT_RUN after policy rejection/
unavailable support; no such action is attempted here. Old failures, worktrees
and uncertain browser/preview resources are preserved. No new persistent resource.
