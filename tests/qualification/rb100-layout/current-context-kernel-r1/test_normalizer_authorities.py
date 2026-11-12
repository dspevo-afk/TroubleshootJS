"""Synthetic-only tests for prior-normalizer authority and host-call seams.

These fixtures do not construct the published role map, read an allowlist,
follow a manifest path, or pass through public role admission.  Their paths,
tool rows, process identifiers, receipts, and resource evidence are synthetic.
"""

from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import unittest


_SOURCE = Path(__file__).with_name("normalize_prior.py")
_SPEC = importlib.util.spec_from_file_location("_tsj_normalizer_authority_subject", _SOURCE)
if _SPEC is None or _SPEC.loader is None:
    raise RuntimeError("NORMALIZER_SOURCE_LOAD")
_NORMALIZER = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = _NORMALIZER
_SPEC.loader.exec_module(_NORMALIZER)


def _sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def _canonical_digest(value: dict, field: str) -> str:
    material = {key: child for key, child in value.items() if key != field}
    encoded = json.dumps(material, ensure_ascii=True, sort_keys=False,
                         separators=(",", ":"), allow_nan=False).encode("ascii")
    return _sha(encoded)


def _freeze(value: dict, field: str) -> dict:
    value[field] = _canonical_digest(value, field)
    return value


def _resource_fact(tag: str) -> dict:
    root = rf"C:\synthetic-resource\{tag}\process-root"
    stdout = rf"C:\synthetic-resource\{tag}\stdout.log"
    stderr = rf"C:\synthetic-resource\{tag}\stderr.log"
    return {
        "directoryNames": list(_NORMALIZER.CALL_DIRECTORY_NAMES),
        "evidenceSha256": "b" * 64,
        "processRoot": {"path": root, "absent": True},
        "stderrLog": {"path": stderr, "absent": True},
        "stdoutLog": {"path": stdout, "absent": True},
    }


def _current_execution_tool_facts(source_sha256: str) -> dict:
    return {
        "sourceSha256": source_sha256,
        "toolFacts": {
            "qualificationPython": {
                "path": r"C:\synthetic-current-tools\python.exe",
                "bytes": 171744,
                "sha256": "8" * 64,
            },
            "rootCallPowerShell": {
                "path": r"C:\synthetic-current-tools\pwsh.exe",
                "bytes": 495616,
                "sha256": "9" * 64,
            },
            "nativeJava": {
                "path": r"C:\synthetic-current-tools\java.exe",
                "bytes": 654321,
                "sha256": "a" * 64,
            },
            "rootCallHarness": {
                "path": r"C:\synthetic-current-tools\root-call.ps1",
                "bytes": 2941,
                "sha256": "b" * 64,
            },
            "processModule": {
                "path": r"C:\synthetic-current-tools\process-module.psm1",
                "bytes": 1040144,
                "sha256": "c" * 64,
            },
        },
    }


def _authority_fixture() -> tuple[dict, dict]:
    native_java = {
        "path": r"C:\synthetic-tools\java.exe",
        "bytes": 123456,
        "sha256": "c" * 64,
    }
    current_source = _freeze({
        "schema": _NORMALIZER.CURRENT_SOURCE_AUTHORITY_SCHEMA,
        "sourceSha256": "d" * 64,
    }, "authoritySha256")
    authority = {
        "schema": _NORMALIZER.NORMALIZER_AUTHORITY_SCHEMA,
        "authoritySha256": "0" * 64,
        "kernelProvenanceSha256": "1" * 64,
        "nRoot": r"C:\synthetic-private\N",
        "dRoot": r"C:\synthetic-private\D",
        "currentSourceAuthority": current_source,
        "sourceBindings": dict(_NORMALIZER.SOURCE_BINDING_PINS),
        "historicalToolExpectations": {
            "qualificationPython": {
                "path": r"C:\synthetic-tools\python.exe",
                "bytes": 171744,
                "sha256": _NORMALIZER.QUALIFICATION_PYTHON_SHA256,
            },
            "rootCallPowerShell": {
                "path": r"C:\synthetic-tools\pwsh.exe",
                "bytes": 495616,
                "sha256": _NORMALIZER.POWERSHELL_SHA256,
            },
            "nativeJava": native_java,
            "rootCallHarness": {
                "path": r"C:\synthetic-tools\root-call.ps1",
                "bytes": 2941,
                "sha256": _NORMALIZER.ROOT_CALL_HARNESS_SHA256,
            },
            "processModule": {
                "path": r"C:\synthetic-tools\process-module.psm1",
                "bytes": 1040144,
                "sha256": _NORMALIZER.PROCESS_MODULE_SHA256,
            },
        },
        "currentExecutionToolFacts": _current_execution_tool_facts(
            current_source["sourceSha256"]),
        "resourceFacts": {
            "qualification": _resource_fact("qualification"),
            "nativePrior": _resource_fact("native-prior"),
            "nativeData": {
                "directoryNames": list(_NORMALIZER.DATA_DIRECTORY_NAMES),
                "evidenceSha256": "e" * 64,
            },
        },
    }
    _freeze(authority, "authoritySha256")
    source_manifest = {"tools": [dict(native_java)]}
    return authority, source_manifest


def _host_fixture() -> tuple[tuple, str, dict, dict, tuple]:
    executable = r"C:\synthetic-tools\java.exe"
    owner_executable = r"C:\synthetic-tools\pwsh.exe"
    owner_script = r"C:\synthetic-tools\root-call.ps1"
    owner_request = r"C:\synthetic-private\N\native-observe-r3\request.json"
    process_root = r"C:\synthetic-private\N\native-observe-r3\run-root"
    stdout_log = r"C:\synthetic-private\N\native-observe-r3\stdout.log"
    stderr_log = r"C:\synthetic-private\N\native-observe-r3\stderr.log"
    expected_args = [
        "-ea", "-cp", r"C:\synthetic-private\N\classes;C:\synthetic-tools\gwt-user.jar;C:\synthetic-tools\gwt-dev.jar",
        "synthetic.EntryPoint", "SYNTHETIC_PRIOR",
    ]
    stdout_text = '{"status":"SYNTHETIC_COMPLETED"}\n'
    stdout_raw = stdout_text.encode("utf-8")
    stderr_raw = b""
    elapsed_ms = 12.5
    process = {
        "FilePath": executable,
        "Arguments": list(expected_args),
        "ProcessId": 101,
        "ProcessStartTicks": 9001,
        "ExitCode": 0,
        "Stdout": stdout_text,
        "Stderr": "",
        "StdoutLog": stdout_log,
        "StderrLog": stderr_log,
        "ProcessRoot": process_root,
        "TerminationProven": True,
    }
    owner = {
        "schema": _NORMALIZER.OWNER_SCHEMA,
        "pid": 88,
        "startTicks": "8001",
        "executable": owner_executable,
        "script": owner_script,
        "request": owner_request,
        "createdUtc": "SYNTHETIC-UTC",
    }
    parent = {
        "schema": _NORMALIZER.PARENT_SCHEMA,
        "elapsedMs": elapsed_ms,
        "process": {key: (list(value) if type(value) is list else value)
                    for key, value in process.items()},
    }
    request = {"FilePath": executable, "Arguments": list(expected_args), "LimitMs": 1000}
    result = {
        "status": "RETURNED",
        "elapsedMs": elapsed_ms,
        "exitCode": 0,
        "terminationProven": True,
        "stdoutBytes": len(stdout_raw),
        "stderrBytes": 0,
        "processRootAbsent": True,
        "totalElapsedMs": 19.75,
    }
    docs = {
        "owner": owner,
        "parent": parent,
        "process": process,
        "request": request,
        "result": result,
        "stdoutRaw": stdout_raw,
        "stderrRaw": stderr_raw,
    }
    tool_facts = {
        "processExecutable": {
            "path": executable, "bytes": 123456, "sha256": "c" * 64,
        },
        "ownerExecutable": {
            "path": owner_executable, "bytes": 495616,
            "sha256": _NORMALIZER.POWERSHELL_SHA256,
        },
        "ownerScript": {
            "path": owner_script, "bytes": 2941,
            "sha256": _NORMALIZER.ROOT_CALL_HARNESS_SHA256,
        },
        "processModule": {
            "path": r"C:\synthetic-tools\process-module.psm1", "bytes": 1040144,
            "sha256": _NORMALIZER.PROCESS_MODULE_SHA256,
        },
    }
    resource_facts = {
        "directoryNames": list(_NORMALIZER.CALL_DIRECTORY_NAMES),
        "evidenceSha256": "f" * 64,
        "processRoot": {"path": process_root, "absent": True},
        "stderrLog": {"path": stderr_log, "absent": True},
        "stdoutLog": {"path": stdout_log, "absent": True},
    }
    args = (docs, "SYNTHETIC", executable, expected_args, 1000,
            owner_executable, owner_script, owner_request, tool_facts, resource_facts)
    return args, stdout_text, process, parent, (tool_facts, resource_facts)


def _invoke_host(fixture):
    args, stdout_expected, *_rest = fixture
    return _NORMALIZER._validate_host_call(*args, stdout_expected=stdout_expected)


class NormalizerAuthorityFixtureTests(unittest.TestCase):
    def test_history_authority_accepts_independently_frozen_synthetic_facts(self):
        authority, source_manifest = _authority_fixture()
        facts = _NORMALIZER._validate_history_authority(authority, source_manifest)
        self.assertEqual("tsj-q100-normalizer-authority-v2", authority["schema"])
        self.assertEqual({
            "schema", "authoritySha256", "kernelProvenanceSha256", "nRoot", "dRoot",
            "currentSourceAuthority", "sourceBindings", "historicalToolExpectations",
            "currentExecutionToolFacts", "resourceFacts",
        }, set(authority))
        self.assertEqual(facts["nRoot"], authority["nRoot"])
        self.assertEqual(facts["dRoot"], authority["dRoot"])
        self.assertEqual(facts["currentSourceAuthority"], authority["currentSourceAuthority"])
        self.assertIs(facts["historicalToolExpectations"],
                      authority["historicalToolExpectations"])
        self.assertEqual(facts["currentExecutionToolFacts"],
                         authority["currentExecutionToolFacts"])
        self.assertNotEqual(
            facts["historicalToolExpectations"]["rootCallPowerShell"]["sha256"],
            facts["currentExecutionToolFacts"]["toolFacts"]["rootCallPowerShell"]["sha256"])
        self.assertIs(facts["resourceFacts"], authority["resourceFacts"])

    def _assert_authority_rejects(self, expected_code, mutate, *, refreeze=True,
                                  mutate_source_manifest=None):
        authority, source_manifest = _authority_fixture()
        mutate(authority, source_manifest)
        if mutate_source_manifest is not None:
            mutate_source_manifest(source_manifest)
        if refreeze:
            _freeze(authority, "authoritySha256")
        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER._validate_history_authority(authority, source_manifest)
        self.assertEqual(caught.exception.code, expected_code)

    def test_history_authority_rejects_missing_and_extra_authority(self):
        self._assert_authority_rejects(
            "NORMALIZER_AUTHORITY_KEYS",
            lambda authority, _manifest: authority.pop("dRoot"), refreeze=False)
        self._assert_authority_rejects(
            "NORMALIZER_AUTHORITY_KEYS",
            lambda authority, _manifest: authority.update(extraFact=True), refreeze=False)

    def test_history_authority_rejects_legacy_alias_and_schema(self):
        self._assert_authority_rejects(
            "NORMALIZER_AUTHORITY_KEYS",
            lambda authority, _manifest: authority.pop("historicalToolExpectations"),
            refreeze=False)
        self._assert_authority_rejects(
            "NORMALIZER_AUTHORITY_KEYS",
            lambda authority, _manifest: authority.update(
                toolFacts=authority["historicalToolExpectations"]), refreeze=False)
        self._assert_authority_rejects(
            "NORMALIZER_AUTHORITY_SCHEMA",
            lambda authority, _manifest: authority.update(
                schema="tsj-q100-normalizer-authority-v1"))

    def test_history_authority_rejects_crosswired_tool_maps(self):
        def crosswire(authority, _manifest):
            historical = authority["historicalToolExpectations"]
            current = authority["currentExecutionToolFacts"]
            authority["historicalToolExpectations"] = current
            authority["currentExecutionToolFacts"] = {
                "sourceSha256": authority["currentSourceAuthority"]["sourceSha256"],
                "toolFacts": historical,
            }

        self._assert_authority_rejects("TOOL_FACTS", crosswire)

    def test_history_authority_rejects_stale_outer_self_hash(self):
        self._assert_authority_rejects(
            "NORMALIZER_AUTHORITY_BINDING",
            lambda authority, _manifest: authority.update(nRoot=r"C:\synthetic-private\other"),
            refreeze=False)

    def test_history_authority_rejects_current_source_self_hash_mutation(self):
        def mutate(authority, _manifest):
            authority["currentSourceAuthority"]["sourceSha256"] = "9" * 64

        self._assert_authority_rejects(
            "CURRENT_SOURCE_AUTHORITY_BINDING", mutate)

    def test_current_execution_tools_bind_to_unchanged_source_authority(self):
        self._assert_authority_rejects(
            "CURRENT_EXECUTION_TOOL_SOURCE_BINDING",
            lambda authority, _manifest: authority["currentExecutionToolFacts"].update(
                sourceSha256="9" * 64))

    def test_current_execution_tool_rows_are_exact_and_generic(self):
        self._assert_authority_rejects(
            "CURRENT_EXECUTION_TOOL_FACT_NAMES",
            lambda authority, _manifest: authority["currentExecutionToolFacts"][
                "toolFacts"].pop("nativeJava"))
        self._assert_authority_rejects(
            "CURRENT_EXECUTION_TOOL_ROOTCALLPOWERSHELL_PATH_ABSOLUTE",
            lambda authority, _manifest: authority["currentExecutionToolFacts"][
                "toolFacts"]["rootCallPowerShell"].update(path="pwsh.exe"))

    def test_current_only_tool_change_does_not_change_historical_expectations(self):
        authority, source_manifest = _authority_fixture()
        historical_before = dict(authority["historicalToolExpectations"])
        authority["currentExecutionToolFacts"]["toolFacts"]["nativeJava"].update(
            path=r"C:\synthetic-current-tools\different-java.exe",
            bytes=654322, sha256="f" * 64)
        _freeze(authority, "authoritySha256")

        facts = _NORMALIZER._validate_history_authority(authority, source_manifest)

        self.assertEqual(historical_before, facts["historicalToolExpectations"])
        self.assertEqual("f" * 64,
                         facts["currentExecutionToolFacts"]["toolFacts"][
                             "nativeJava"]["sha256"])

    def test_current_tools_need_not_differ_from_historical_expectations(self):
        authority, source_manifest = _authority_fixture()
        authority["currentExecutionToolFacts"]["toolFacts"] = json.loads(
            json.dumps(authority["historicalToolExpectations"]))
        _freeze(authority, "authoritySha256")

        facts = _NORMALIZER._validate_history_authority(authority, source_manifest)

        self.assertEqual(facts["historicalToolExpectations"],
                         facts["currentExecutionToolFacts"]["toolFacts"])

    def test_history_authority_rejects_stale_digest_for_either_tool_map(self):
        self._assert_authority_rejects(
            "NORMALIZER_AUTHORITY_BINDING",
            lambda authority, _manifest: authority["historicalToolExpectations"][
                "qualificationPython"].update(bytes=171745), refreeze=False)
        self._assert_authority_rejects(
            "NORMALIZER_AUTHORITY_BINDING",
            lambda authority, _manifest: authority["currentExecutionToolFacts"][
                "toolFacts"]["rootCallPowerShell"].update(sha256="0" * 64),
            refreeze=False)

    def test_history_authority_rejects_source_and_tool_pin_mutations(self):
        self._assert_authority_rejects(
            "SOURCE_BINDING_PINS",
            lambda authority, _manifest: authority["sourceBindings"].update(
                priorReader="9" * 64))
        self._assert_authority_rejects(
            "QUALIFICATION_PYTHON_TOOL_PIN",
            lambda authority, _manifest: authority["historicalToolExpectations"][
                "qualificationPython"].update(bytes=171745))

    def test_history_authority_rejects_native_tool_and_data_closure_mismatch(self):
        self._assert_authority_rejects(
            "NATIVE_JAVA_TOOL_BINDING", lambda _authority, _manifest: None,
            mutate_source_manifest=lambda manifest: manifest["tools"][0].update(sha256="9" * 64))
        self._assert_authority_rejects(
            "NATIVE_DATA_DIRECTORY_CLOSURE",
            lambda authority, _manifest: authority["resourceFacts"]["nativeData"].update(
                directoryNames=["synthetic-result.json"]))

    def test_host_call_accepts_copied_receipts_streams_finite_times_and_absence_facts(self):
        fixture = _host_fixture()
        validated = _invoke_host(fixture)
        self.assertIs(validated["parent"]["process"], fixture[0][0]["parent"]["process"])
        self.assertEqual(validated["stdoutRaw"], fixture[0][0]["stdoutRaw"])
        self.assertEqual(validated["result"]["elapsedMs"], 12.5)
        self.assertIs(type(validated["result"]["elapsedMs"]), float)

    def _assert_host_rejects(self, expected_code, mutate):
        fixture = _host_fixture()
        args, stdout_expected, *_rest = fixture
        docs = args[0]
        mutate(fixture, docs)
        with self.assertRaises(_NORMALIZER.PriorNormalizationError) as caught:
            _NORMALIZER._validate_host_call(*args, stdout_expected=stdout_expected)
        self.assertEqual(caught.exception.code, expected_code)

    def test_host_call_rejects_receipt_schema_argv_and_stream_mutations(self):
        self._assert_host_rejects(
            "SYNTHETIC_OWNER_SCHEMA",
            lambda _fixture, docs: docs["owner"].update(unexpected=True))
        self._assert_host_rejects(
            "SYNTHETIC_PROCESS_FIELDS",
            lambda _fixture, docs: docs["process"]["Arguments"].append("unbound"))
        self._assert_host_rejects(
            "SYNTHETIC_STREAM_BINDING",
            lambda _fixture, docs: docs.update(stdoutRaw=docs["stdoutRaw"] + b"x"))

    def test_host_call_rejects_types_tool_bytes_termination_and_resource_cleanup(self):
        self._assert_host_rejects(
            "SYNTHETIC_RESULT_BINDING",
            lambda _fixture, docs: docs["result"].update(stdoutBytes=True))
        self._assert_host_rejects(
            "SYNTHETIC_SOURCE_TOOL_PINS",
            lambda fixture, _docs: fixture[0][8]["ownerScript"].update(sha256="0" * 64))
        self._assert_host_rejects(
            "SYNTHETIC_PROCESS_FIELDS",
            lambda _fixture, docs: docs["process"].update(TerminationProven=False))
        self._assert_host_rejects(
            "SYNTHETIC_STDOUTLOG_ABSENCE",
            lambda fixture, _docs: fixture[0][9]["stdoutLog"].update(absent=False))

    def test_host_call_rejects_parent_copy_and_result_process_root_claim(self):
        self._assert_host_rejects(
            "SYNTHETIC_PARENT_PROCESS_COPY",
            lambda _fixture, docs: docs["parent"]["process"].update(ProcessId=102))
        self._assert_host_rejects(
            "SYNTHETIC_RESULT_BINDING",
            lambda _fixture, docs: docs["result"].update(processRootAbsent=False))


if __name__ == "__main__":
    unittest.main()
