"""Bounded byte authentication for the frozen Full-D V2 normal-prior roles.

This module does no work at import time and has no command-line entry point.
Calling :func:`read_normal_prior` is a byte reader only; the caller owns the
separate operational phase record that authorizes opening those roles.
"""

import hashlib
import json
import os
import re
import stat
import sys
from pathlib import Path, PureWindowsPath
from typing import NamedTuple


ALLOWLIST_SHA256 = "c487eae6ed0e070aaad75dccd14d23313ed9da2c6bda7f93bfeb886109f20f91"
ALLOWLIST_MAX_BYTES = 1024 * 1024
MAX_ROLE_BYTES = 8 * 1024 * 1024
REPARSE_POINT = 0x400
EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
HEX64 = re.compile(r"[0-9a-f]{64}\Z")

_SCHEMA = "tsj-full-d-v2-normal-oracle-allowlist-v1"
_STATUS = "FROZEN_PINS_UNREAD_READER_GATES_REQUIRED"
_AUTHORITY_SOURCE_SHA256 = "271db07f1b1dbd0a30e8016024b4fe87f89b3050447dd4bab64ff8a135751321"
_HELPER_SOURCE_SHA256 = "2cebcf6a26c61a3600c9ffcf5e1f1268e62bd966598a57e7d71587173d5b8724"
_QUALIFICATION_AUTHORITY = "PINNED_SOURCE_HASH_NO_INITIAL_DATA_SNAPSHOT"


class ReaderError(Exception):
    """Stable path-free reader failure."""

    def __init__(self, code):
        self.code = code
        super().__init__(code)


class _RoleSpec(NamedTuple):
    role: str
    root: str
    relative_path: str
    max_bytes: int
    expected_bytes: object
    sha256: str
    exact_content_authority: object = None
    summary_parse_max_bytes: object = None


# Ordered exactly as the frozen contract. Data paths are never discovered by
# walking a directory or by following a manifest.
_ROLE_PINS = (
    _RoleSpec("N_PRIOR_CALL_OWNER_JSON", "N", "native-observe-r3/owner.json", 262144, 547,
              "1559355863aae9b680eeab3334c35953b4d239f42ab774b83173053b25808e79"),
    _RoleSpec("N_PRIOR_CALL_PARENT_JSON", "N", "native-observe-r3/parent.json", 262144, 1811,
              "8ac7a7ffc92dfb316be50a9da490d5ad51c494dd986fb18b7e821ca27e32c857"),
    _RoleSpec("N_PRIOR_CALL_PROCESS_JSON", "N", "native-observe-r3/process.json", 262144, 2049,
              "f07359e62cb1fb0b0dd51b4334b9cdc79d9a51db8f358e672ac2c5e86ee58aef"),
    _RoleSpec("N_PRIOR_CALL_REQUEST_JSON", "N", "native-observe-r3/request.json", 262144, 885,
              "29a725862bd01151b67ef3d839de066b3e05d845f1744acf2153a51754ca244a"),
    _RoleSpec("N_PRIOR_CALL_RESULT_JSON", "N", "native-observe-r3/result.json", 262144, 178,
              "c2dd1f6267219e8055cd043f803c1c836fcad7e51849de8862693fba5e089a7c"),
    _RoleSpec("N_PRIOR_CALL_STDERR_TXT", "N", "native-observe-r3/stderr.txt", 262144, 0,
              EMPTY_SHA256),
    _RoleSpec("N_PRIOR_CALL_STDOUT_TXT", "N", "native-observe-r3/stdout.txt", 262144, 328,
              "42dacdb5c346b4595a2d4d07436723f4be8206ff778d31a233906318d484d0a5"),
    _RoleSpec("D_PRIOR_QUALIFICATION_OWNER_JSON", "D", "prior-input-qualify-r2/owner.json", 524288,
              None, "e7b6b298e318b9c38b6148b58e247444918e4508c277491a9b1621e36d9165b9",
              _QUALIFICATION_AUTHORITY, None),
    _RoleSpec("D_PRIOR_QUALIFICATION_PARENT_JSON", "D", "prior-input-qualify-r2/parent.json", 524288,
              None, "bbb69795cb76739d8add30c7d4c4b753707a9763c6069573d9897d794be25c28",
              _QUALIFICATION_AUTHORITY, None),
    _RoleSpec("D_PRIOR_QUALIFICATION_PROCESS_JSON", "D", "prior-input-qualify-r2/process.json", 524288,
              None, "04580d43849a0beb6867ef69b4e67eb1fbb6454e03a065bd0b9163dd3e041acd",
              _QUALIFICATION_AUTHORITY, None),
    _RoleSpec("D_PRIOR_QUALIFICATION_REQUEST_JSON", "D", "prior-input-qualify-r2/request.json", 524288,
              None, "cb4529a85507a59a70c0ff978488743e192cf8f0cb44982971b8c6a32bccdaa6",
              _QUALIFICATION_AUTHORITY, None),
    _RoleSpec("D_PRIOR_QUALIFICATION_RESULT_JSON", "D", "prior-input-qualify-r2/result.json", 524288,
              None, "efb8ad987d12ad771afe4114059878b15c4ae9682819a46a7b4e7b8847782fef",
              _QUALIFICATION_AUTHORITY, None),
    _RoleSpec("D_PRIOR_QUALIFICATION_STDERR_TXT", "D", "prior-input-qualify-r2/stderr.txt", 524288,
              None, EMPTY_SHA256, _QUALIFICATION_AUTHORITY, None),
    _RoleSpec("D_PRIOR_QUALIFICATION_STDOUT_TXT", "D", "prior-input-qualify-r2/stdout.txt", 524288,
              None, "a08ca3ccfd3390d39ec37314a86f42f1a1b33c1b6d8104e417447e95f47d1d52",
              _QUALIFICATION_AUTHORITY, 1024),
    _RoleSpec("N_PRIOR_OPERATION1", "N", "native-data-r3/op-1.jsonl", 8388608, 377138,
              "cc2463e37c9636f1407434fd2f0d69fda660699e4899059273d1b72a71e02e58"),
    _RoleSpec("N_PRIOR_RESULT", "N", "native-data-r3/result.json", 8388608, 46323,
              "cd44ad1c0db81a8f05d8620f49b78a9e06c2274e6739066b438607f532cf1431"),
    _RoleSpec("D_PRIOR_OBSERVATION_CROSSCHECK", "D", "PRIOR_INPUT_QUALIFICATION_R2.json", 131072,
              3916, "d2dd656567031515aa520528f2cb8343c9252558b3108bcc4eb840c887e9edcb"),
    _RoleSpec("N_PRIOR_RUN_MANIFEST", "N", "j8-failure-cohort-r3/run-manifest.json", 4194304,
              274475, "1274185600f9da79412f133ba569fb727eba673c7dd672e53abd8dcc5fd28a56"),
    _RoleSpec("N_PRIOR_SOURCE_MANIFEST", "N", "j8-failure-cohort-r3/source-manifest.json", 4194304,
              273546, "c04a7579d042bcb8d4b2a9b4799c3e03ec3ad34eda228856205b7deaa1ba6bc4"),
    _RoleSpec("N_PRIOR_CLASS_INVENTORY", "N", "j8-failure-cohort-r3/class-inventory.json", 4194304,
              717708, "753705026b76b13a6218eadd22bd8942d9fc5cc5cde47dae6a26038613217691"),
)

_ROLE_NAMES = frozenset(spec.role for spec in _ROLE_PINS)
_ZERO_ROLES = frozenset(("N_PRIOR_CALL_STDERR_TXT", "D_PRIOR_QUALIFICATION_STDERR_TXT"))


def _pinned_allowlist_object():
    rows = []
    for spec in _ROLE_PINS:
        row = {
            "expectedBytes": spec.expected_bytes,
            "maxBytes": spec.max_bytes,
            "path": spec.relative_path,
            "role": spec.role,
            "root": spec.root,
            "sha256": spec.sha256,
        }
        if spec.exact_content_authority is not None:
            row["exactContentAuthority"] = spec.exact_content_authority
            row["summaryParseMaxBytes"] = spec.summary_parse_max_bytes
        rows.append(row)
    return {
        "authoritySourceSha256": _AUTHORITY_SOURCE_SHA256,
        "helperSourceSha256": _HELPER_SOURCE_SHA256,
        "noRecursiveManifestFollowing": True,
        "oldDDescriptorResultGeneratedInputDenied": True,
        "protectedAndHeldOutDenied": True,
        "qualificationProjectionIsCrosscheckOnly": True,
        "readingEnabled": False,
        "roleCount": 20,
        "rootPathsArePrivateAndExternallyFixed": True,
        "rows": rows,
        "schema": _SCHEMA,
        "status": _STATUS,
    }


def _same_json_exact(actual, expected):
    if type(actual) is not type(expected):
        return False
    if type(expected) is dict:
        return (set(actual.keys()) == set(expected.keys()) and
                all(_same_json_exact(actual[key], expected[key]) for key in expected))
    if type(expected) is list:
        return len(actual) == len(expected) and all(
            _same_json_exact(left, right) for left, right in zip(actual, expected))
    return actual == expected


def _pairs_no_duplicates(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ReaderError("CONTRACT_CONTENT")
        result[key] = value
    return result


def _strict_contract_json(raw):
    if type(raw) is not bytes or not raw.isascii():
        raise ReaderError("CONTRACT_CONTENT")
    try:
        return json.loads(
            raw.decode("ascii"),
            object_pairs_hook=_pairs_no_duplicates,
            parse_constant=lambda _value: (_ for _ in ()).throw(ReaderError("CONTRACT_CONTENT")),
        )
    except ReaderError:
        raise
    except Exception:
        raise ReaderError("CONTRACT_CONTENT") from None


def _snapshot(st):
    return (
        st.st_dev,
        st.st_ino,
        st.st_mode,
        st.st_size,
        getattr(st, "st_mtime_ns", int(st.st_mtime * 1000000000)),
        getattr(st, "st_ctime_ns", int(st.st_ctime * 1000000000)),
        getattr(st, "st_file_attributes", 0),
    )


def _handle_identity(snapshot):
    # Windows can expose a different ctime through a file handle. Path ctime is
    # still checked before/after; the handle comparison uses stable identity.
    return tuple(snapshot[index] for index in (0, 1, 2, 3, 4, 6))


def _is_reparse(st):
    return (stat.S_ISLNK(st.st_mode) or
            bool(getattr(st, "st_file_attributes", 0) & REPARSE_POINT))


def _check_windows_local_lexical_path(raw):
    if sys.platform != "win32" or os.name != "nt":
        raise ReaderError("WINDOWS_REQUIRED")
    if type(raw) is not str or not raw or len(raw) > 32767 or "\x00" in raw:
        raise ReaderError("PATH")
    if "/" in raw or raw.startswith("\\\\"):
        raise ReaderError("NONCANONICAL")
    pure = PureWindowsPath(raw)
    if (not pure.is_absolute() or pure.root != "\\" or
            re.fullmatch(r"[A-Za-z]:", pure.drive) is None):
        raise ReaderError("PATH")
    if os.path.normcase(os.path.normpath(raw)) != os.path.normcase(raw):
        raise ReaderError("NONCANONICAL")
    parts = pure.parts[1:]
    if raw.endswith("\\") and parts:
        raise ReaderError("NONCANONICAL")
    for part in parts:
        if part in ("", ".", "..") or part.endswith((".", " ")) or ":" in part:
            raise ReaderError("NONCANONICAL")
        device_base = part.split(".", 1)[0].upper()
        if (device_base in ("CON", "PRN", "AUX", "NUL") or
                re.fullmatch(r"(?:COM|LPT)[1-9]", device_base)):
            raise ReaderError("NONCANONICAL")
    return pure


def _inspect_path(raw, expected_kind):
    pure = _check_windows_local_lexical_path(raw)
    path = Path(raw)
    parts = pure.parts
    current = Path(parts[0])
    snapshots = []
    try:
        for index, part in enumerate(parts):
            if index:
                current = current / part
            item = os.lstat(str(current))
            if _is_reparse(item):
                raise ReaderError("REPARSE")
            leaf = index == len(parts) - 1
            wants_directory = not leaf or expected_kind == "directory"
            if wants_directory and not stat.S_ISDIR(item.st_mode):
                raise ReaderError("REGULAR")
            if leaf and expected_kind == "file" and not stat.S_ISREG(item.st_mode):
                raise ReaderError("REGULAR")
            snapshots.append((str(current), _snapshot(item)))
        real = path.resolve(strict=True)
    except ReaderError:
        raise
    except (OSError, RuntimeError, ValueError):
        raise ReaderError("PATH") from None
    absolute = os.path.abspath(str(path))
    if os.path.normcase(absolute) != os.path.normcase(str(real)):
        raise ReaderError("NONCANONICAL")
    return path, real, tuple(snapshots)


def _canonical_roots(roots):
    if type(roots) is not dict or set(roots.keys()) != {"N", "D"}:
        raise ReaderError("ROOTS")
    checked = {}
    for label in ("N", "D"):
        path, real, identity = _inspect_path(roots[label], "directory")
        checked[label] = (path, real, identity)
    return checked


def _relative_parts(raw):
    if type(raw) is not str or not raw or "\\" in raw or raw.startswith("/"):
        raise ReaderError("ROLE_PATH")
    parts = raw.split("/")
    if any(part in ("", ".", "..") or ":" in part or part.endswith((".", " "))
           for part in parts):
        raise ReaderError("ROLE_PATH")
    return tuple(parts)


def _role_path(roots, spec):
    if spec.root not in ("N", "D"):
        raise ReaderError("ROLE_ROOT")
    root = roots[spec.root][1]
    target = root.joinpath(*_relative_parts(spec.relative_path))
    candidate, real, _identity = _inspect_path(str(target), "file")
    try:
        root_text = os.path.normcase(os.path.abspath(str(root)))
        candidate_text = os.path.normcase(os.path.abspath(str(real)))
        common = os.path.normcase(os.path.commonpath((root_text, candidate_text)))
    except (OSError, ValueError):
        raise ReaderError("ROLE_PATH") from None
    if common != root_text or candidate_text == root_text:
        raise ReaderError("ROLE_PATH")
    return candidate


def _read_file_once(raw_path, limit, expected_bytes, expected_sha256, role,
                    hash_error="SHA256"):
    path, real, before = _inspect_path(str(raw_path), "file")
    leaf_before = before[-1][1]
    if type(limit) is not int or not 0 <= limit <= MAX_ROLE_BYTES:
        raise ReaderError("SIZE")
    if leaf_before[3] > limit:
        raise ReaderError("SIZE")
    if expected_bytes is not None:
        if type(expected_bytes) is not int or expected_bytes < 0 or expected_bytes > limit:
            raise ReaderError("EXPECTED_BYTES")
        if leaf_before[3] != expected_bytes:
            raise ReaderError("EXPECTED_BYTES")
    if role in _ZERO_ROLES:
        if expected_sha256 != EMPTY_SHA256 or (expected_bytes is not None and expected_bytes != 0):
            raise ReaderError("ZERO_ROLE")
    elif expected_bytes == 0:
        raise ReaderError("ZERO_ROLE")

    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        fd = os.open(str(real), flags)
    except OSError:
        raise ReaderError("IO") from None
    chunks = []
    remaining = limit + 1
    try:
        opened = os.fstat(fd)
        opened_snapshot = _snapshot(opened)
        if (not stat.S_ISREG(opened.st_mode) or
                _handle_identity(leaf_before) != _handle_identity(opened_snapshot)):
            raise ReaderError("FILE_CHANGED")
        if opened.st_size > limit:
            raise ReaderError("SIZE")
        if expected_bytes is not None and opened.st_size != expected_bytes:
            raise ReaderError("EXPECTED_BYTES")
        while remaining:
            block = os.read(fd, min(65536, remaining))
            if not block:
                break
            chunks.append(block)
            remaining -= len(block)
        data = b"".join(chunks)
        after_handle = _snapshot(os.fstat(fd))
    except ReaderError:
        raise
    except OSError:
        raise ReaderError("IO") from None
    finally:
        os.close(fd)

    _after_path, after_real, after = _inspect_path(str(path), "file")
    if (after != before or os.path.normcase(str(after_real)) != os.path.normcase(str(real)) or
            _handle_identity(leaf_before) != _handle_identity(after_handle) or len(data) > limit):
        raise ReaderError("FILE_CHANGED")
    if expected_bytes is not None and len(data) != expected_bytes:
        raise ReaderError("EXPECTED_BYTES")
    if not data and role not in _ZERO_ROLES:
        raise ReaderError("ZERO_ROLE")
    digest = hashlib.sha256(data).hexdigest()
    if type(expected_sha256) is not str or not HEX64.fullmatch(expected_sha256):
        raise ReaderError(hash_error)
    if digest != expected_sha256:
        raise ReaderError(hash_error)
    token = (
        os.path.normcase(str(real)),
        before,
        _handle_identity(opened_snapshot),
        _handle_identity(after_handle),
    )
    return data, digest, token


def _read_allowlist_once(allowlist_path):
    raw, digest, token = _read_file_once(
        allowlist_path, ALLOWLIST_MAX_BYTES, None, ALLOWLIST_SHA256, "ALLOWLIST",
        hash_error="CONTRACT_SHA256")
    if digest != ALLOWLIST_SHA256:
        raise ReaderError("CONTRACT_SHA256")
    value = _strict_contract_json(raw)
    expected = _pinned_allowlist_object()
    if not _same_json_exact(value, expected):
        raise ReaderError("CONTRACT_CONTENT")
    if len(_ROLE_PINS) != 20 or len({spec.role for spec in _ROLE_PINS}) != 20:
        raise ReaderError("ROLE_ROWS")
    return raw, digest, token, _ROLE_PINS


def _load_pinned_allowlist(allowlist_path):
    """Metadata-only canary seam: authenticate the pinned contract, no role files."""
    raw, digest, token, specs = _read_allowlist_once(allowlist_path)
    raw_after, digest_after, token_after, _specs_after = _read_allowlist_once(allowlist_path)
    if raw_after != raw or digest_after != digest or token_after != token:
        raise ReaderError("FILE_CHANGED")
    evidence = {
        "scope": "PINNED_ALLOWLIST_METADATA_ONLY",
        "allowlistSha256": digest,
        "roleCount": len(specs),
        "finalSha256Recheck": "PASS",
    }
    return specs, evidence


def _validate_synthetic_rows(rows):
    if type(rows) not in (list, tuple) or not rows or len(rows) > 20:
        raise ReaderError("ROLE_ROWS")
    specs = []
    roles = set()
    required = {"role", "root", "path", "maxBytes", "expectedBytes", "sha256"}
    for row in rows:
        if type(row) is not dict or set(row.keys()) != required:
            raise ReaderError("ROLE_ROWS")
        role = row["role"]
        root = row["root"]
        limit = row["maxBytes"]
        expected = row["expectedBytes"]
        digest = row["sha256"]
        if type(role) is not str or role not in _ROLE_NAMES or role in roles:
            raise ReaderError("UNKNOWN_ROLE")
        if type(root) is not str or root not in ("N", "D"):
            raise ReaderError("ROLE_ROOT")
        if type(limit) is not int or not 0 <= limit <= MAX_ROLE_BYTES:
            raise ReaderError("SIZE")
        if expected is not None and (type(expected) is not int or not 0 <= expected <= limit):
            raise ReaderError("EXPECTED_BYTES")
        if type(digest) is not str or not HEX64.fullmatch(digest):
            raise ReaderError("SHA256")
        if (role in _ZERO_ROLES and digest != EMPTY_SHA256) or (
                role not in _ZERO_ROLES and expected == 0):
            raise ReaderError("ZERO_ROLE")
        _relative_parts(row["path"])
        specs.append(_RoleSpec(role, root, row["path"], limit, expected, digest))
        roles.add(role)
    return tuple(specs)


def _read_role_specs(roots, specs, evidence_scope):
    checked_roots = _canonical_roots(roots)
    role_bytes = {}
    role_tokens = {}
    evidence_roles = []
    for spec in specs:
        path = _role_path(checked_roots, spec)
        raw, digest, token = _read_file_once(
            path, spec.max_bytes, spec.expected_bytes, spec.sha256, spec.role)
        role_bytes[spec.role] = raw
        role_tokens[spec.role] = token
        evidence_roles.append({
            "role": spec.role,
            "root": spec.root,
            "bytes": len(raw),
            "sha256": digest,
        })

    for spec in specs:
        path = _role_path(checked_roots, spec)
        raw_after, digest_after, token_after = _read_file_once(
            path, spec.max_bytes, spec.expected_bytes, spec.sha256, spec.role)
        if (raw_after != role_bytes[spec.role] or
                token_after != role_tokens[spec.role] or
                digest_after != hashlib.sha256(role_bytes[spec.role]).hexdigest()):
            raise ReaderError("FILE_CHANGED")

    for label in ("N", "D"):
        _path, _real, identity = _inspect_path(roots[label], "directory")
        if identity != checked_roots[label][2]:
            raise ReaderError("FILE_CHANGED")

    evidence = {
        "scope": evidence_scope,
        "roles": evidence_roles,
        "roleCount": len(evidence_roles),
        "finalSha256Recheck": "PASS",
        "semanticValidation": "NOT_PERFORMED",
        "qualification": "NOT_CLAIMED",
        "fullD": "NOT_CLAIMED",
    }
    return role_bytes, evidence


def _read_synthetic_roles(*, roots, rows, synthetic_mode):
    """Private fixture seam. Its evidence is always SYNTHETIC, never normal."""
    if type(synthetic_mode) is not str or synthetic_mode != "SYNTHETIC":
        raise ReaderError("SYNTHETIC_MODE")
    specs = _validate_synthetic_rows(rows)
    return _read_role_specs(roots, specs, "SYNTHETIC")


def read_normal_prior(*, roots, allowlist_path):
    """Return exact pinned role bytes and sanitized byte-authentication evidence.

    `roots` is an explicit mapping with exactly the N and D labels. This
    function authenticates bytes only. It does not parse role content, grant
    source-role authorization, or claim qualification/full-D acceptance.
    """
    allowlist_raw, allowlist_digest, allowlist_token, specs = _read_allowlist_once(allowlist_path)
    role_bytes, evidence = _read_role_specs(roots, specs, "BYTE_AUTHENTICATION_ONLY")
    raw_after, digest_after, token_after, _specs_after = _read_allowlist_once(allowlist_path)
    if (raw_after != allowlist_raw or digest_after != allowlist_digest or
            token_after != allowlist_token):
        raise ReaderError("FILE_CHANGED")
    evidence["allowlistSha256"] = allowlist_digest
    return role_bytes, evidence


def _expected_allowlist_bytes_for_test_fixture():
    """Serialize the pinned metadata for isolated tests; never reads role data."""
    return (json.dumps(_pinned_allowlist_object(), ensure_ascii=True, sort_keys=True, indent=2,
                       allow_nan=False).encode("ascii") + b"\n")
