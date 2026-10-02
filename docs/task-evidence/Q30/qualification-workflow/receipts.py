"""Fail-closed receipt validation for serialized qualification steps."""
from __future__ import annotations

import json
import copy
import os
from pathlib import Path
import re
from typing import Iterable


LIMITS = {"jobMillis": 90000, "sharedWork": 640, "activeOperationMillis": 5000}
HASH64 = re.compile(r"[0-9a-f]{64}\Z")
HASH40 = re.compile(r"[0-9a-f]{40}\Z")
SEED = re.compile(r"-?(0|[1-9][0-9]*)\Z")


class ReceiptError(ValueError):
    """Raised when a receipt is malformed or a case sequence is ambiguous."""


def _need(ok: bool, message: str) -> None:
    if not ok:
        raise ReceiptError(message)


def _object(value, name: str, fields: set[str], *, exact: bool = True) -> dict:
    _need(isinstance(value, dict), f"{name} must be an object")
    keys = set(value)
    missing = fields - keys
    extra = keys - fields
    _need(not missing, f"{name} missing fields: {sorted(missing)}")
    _need(not exact or not extra, f"{name} has unsupported fields: {sorted(extra)}")
    return value


def _int_or_none(value, name: str, *, nonnegative: bool = True) -> None:
    _need(value is None or type(value) is int, f"{name} must be an integer or null")
    if value is not None and nonnegative:
        _need(value >= 0, f"{name} must be nonnegative")


def _seed(value, name: str = "rootSeed") -> str:
    _need(isinstance(value, str) and SEED.fullmatch(value) is not None,
          f"{name} must be a canonical signed decimal string")
    number = int(value)
    _need(-(2**63) <= number <= 2**63 - 1 and str(number) == value,
          f"{name} is outside signed 64-bit range or noncanonical")
    return value


def _status(value, choices: set[str], name: str) -> None:
    _need(isinstance(value, str) and value in choices, name + " is unsupported")


def _timed_fields(obj: dict, name: str) -> None:
    _int_or_none(obj["elapsedMs"], name + ".elapsedMs")
    note = obj["elapsedLimitations"]
    _need(note is None or isinstance(note, str), name + ".elapsedLimitations must be string or null")
    if obj["elapsedMs"] is None:
        _need(isinstance(note, str) and bool(note.strip()),
              name + ".elapsedLimitations must explain an unrecorded time")


def validate_case(receipt: dict) -> str:
    """Return PASS, FAIL, or NOT_RUN from one complete version-1 case receipt.

    All schema keys are required. Top-level provenance extensions are retained;
    nested gate records are exact so misspelled contract fields cannot pass.
    """
    fields = {"schema", "rootSeed", "sourceIdentity", "baseHead", "planSha256",
              "limits", "application", "host", "cleanup", "operation",
              "sourceInputsUnchanged"}
    _object(receipt, "receipt", fields, exact=False)
    _need(type(receipt["schema"]) is int and receipt["schema"] == 1,
          "receipt.schema must be integer version 1")
    _seed(receipt["rootSeed"])
    _need(isinstance(receipt["sourceIdentity"], str) and
          HASH64.fullmatch(receipt["sourceIdentity"]) is not None,
          "receipt.sourceIdentity must be lowercase SHA-256")
    _need(isinstance(receipt["baseHead"], str) and HASH40.fullmatch(receipt["baseHead"]) is not None,
          "receipt.baseHead must be lowercase 40-character commit SHA")
    _need(isinstance(receipt["planSha256"], str) and
          HASH64.fullmatch(receipt["planSha256"]) is not None,
          "receipt.planSha256 must be lowercase SHA-256")
    _need(type(receipt["sourceInputsUnchanged"]) is bool,
          "receipt.sourceInputsUnchanged must be boolean")

    limits = _object(receipt["limits"], "limits", set(LIMITS))
    _need(all(type(limits[k]) is int for k in LIMITS) and limits == LIMITS,
          "limits must exactly match 90000/640/5000")

    app = _object(receipt["application"], "application",
                  {"status", "elapsedMs", "workUnits", "maxActiveOperationMs",
                   "maxCoordinatorAdvanceMs"})
    _status(app["status"], {"PASS", "FAIL", "TIMEOUT", "NOT_RUN"}, "application.status")
    for key in ("elapsedMs", "workUnits", "maxActiveOperationMs", "maxCoordinatorAdvanceMs"):
        _int_or_none(app[key], "application." + key)

    host = _object(receipt["host"], "host",
                   {"status", "observationElapsedMs", "deadlineMs", "exitCode", "terminal"})
    _status(host["status"], {"PASS", "FAIL", "TIMEOUT", "NOT_RUN"}, "host.status")
    _int_or_none(host["observationElapsedMs"], "host.observationElapsedMs")
    _need(type(host["deadlineMs"]) is int and host["deadlineMs"] > 0,
          "host.deadlineMs must be a positive integer")
    _int_or_none(host["exitCode"], "host.exitCode", nonnegative=False)
    _need(type(host["terminal"]) is bool, "host.terminal must be boolean")

    cleanup = _object(receipt["cleanup"], "cleanup",
                      {"status", "elapsedMs", "elapsedLimitations", "serverStopped", "ownedSurvivors"})
    _status(cleanup["status"], {"PASS", "FAIL", "NOT_RUN"}, "cleanup.status")
    _timed_fields(cleanup, "cleanup")
    _need(type(cleanup["serverStopped"]) is bool, "cleanup.serverStopped must be boolean")
    _need(isinstance(cleanup["ownedSurvivors"], list), "cleanup.ownedSurvivors must be a list")

    operation = _object(receipt["operation"], "operation", {"elapsedMs", "elapsedLimitations"})
    _timed_fields(operation, "operation")

    if not receipt["sourceInputsUnchanged"]:
        return "FAIL"
    statuses = (app["status"], host["status"], cleanup["status"])
    if statuses == ("NOT_RUN", "NOT_RUN", "NOT_RUN"):
        return "NOT_RUN"
    if statuses != ("PASS", "PASS", "PASS"):
        return "FAIL"

    app_metrics = (app["elapsedMs"], app["workUnits"], app["maxActiveOperationMs"],
                   app["maxCoordinatorAdvanceMs"])
    if any(x is None for x in app_metrics):
        return "FAIL"
    if (app["elapsedMs"] > LIMITS["jobMillis"] or app["workUnits"] > LIMITS["sharedWork"] or
            app["maxActiveOperationMs"] > LIMITS["activeOperationMillis"] or
            app["maxCoordinatorAdvanceMs"] > LIMITS["activeOperationMillis"]):
        return "FAIL"
    if (host["observationElapsedMs"] is None or host["observationElapsedMs"] > host["deadlineMs"] or
            host["exitCode"] != 0 or host["terminal"] is not True):
        return "FAIL"
    if cleanup["serverStopped"] is not True or cleanup["ownedSurvivors"]:
        return "FAIL"
    return "PASS"


def write_exclusive(path, value) -> Path:
    """Write new UTF-8 JSON without replacing any existing file or symlink."""
    target = Path(path)
    raw = (json.dumps(value, sort_keys=True, separators=(",", ":"),
                       ensure_ascii=False, allow_nan=False) + "\n").encode("utf-8")
    with target.open("xb") as stream:
        stream.write(raw)
        stream.flush()
        os.fsync(stream.fileno())
    return target


def summarize_cases(expected_seeds: Iterable[str], receipts: Iterable[dict]) -> dict:
    """Validate ordered receipts and make every omitted expected seed explicit."""
    expected = [_seed(seed, "expected seed") for seed in expected_seeds]
    _need(bool(expected), "expected_seeds must not be empty")
    _need(len(set(expected)) == len(expected), "expected_seeds contains duplicates")
    position = {seed: index for index, seed in enumerate(expected)}
    found = {}
    previous = -1
    binding = None
    for receipt in receipts:
        _need(isinstance(receipt, dict), "each receipt must be an object")
        seed = _seed(receipt.get("rootSeed"))
        _need(seed in position, "unknown rootSeed: " + seed)
        _need(seed not in found, "duplicate rootSeed: " + seed)
        _need(position[seed] > previous, "receipts are out of expected order")
        previous = position[seed]
        status = validate_case(receipt)
        current_binding = tuple(receipt[key] for key in ("sourceIdentity", "baseHead", "planSha256"))
        if binding is None:
            binding = current_binding
        _need(current_binding == binding, "case receipts mix source, base HEAD, or plan identities")
        found[seed] = {"seed": seed, "status": status, "receipt": copy.deepcopy(receipt)}
    rows = [found.get(seed, {"seed": seed, "status": "NOT_RUN", "receipt": None})
            for seed in expected]
    complete = len(found) == len(expected) and all(row["status"] != "NOT_RUN" for row in rows)
    outcome = "PASS" if complete and all(row["status"] == "PASS" for row in rows) else "FAIL"
    return {"complete": complete, "outcome": outcome, "rows": rows}
