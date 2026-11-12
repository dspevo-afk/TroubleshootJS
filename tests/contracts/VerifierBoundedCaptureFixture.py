"""Deterministic child fixture for the bounded verifier-capture contract."""

import json
import runpy
import sys
import time


_CHUNK_BYTES = 4096
_MAX_OUTPUT_BYTES = 1024 * 1024
_FLOOD_BYTES = 1024 * 1024


def _count(value, name):
    if (not value or any(char < "0" or char > "9" for char in value)):
        raise ValueError("invalid " + name)
    number = int(value, 10)
    if number > _MAX_OUTPUT_BYTES or str(number) != value:
        raise ValueError("invalid " + name)
    return number


def _exit_code(value):
    if not value or any(char < "0" or char > "9" for char in value):
        raise ValueError("invalid exit code")
    number = int(value, 10)
    if number > 255 or str(number) != value:
        raise ValueError("invalid exit code")
    return number


def _write_chunks(stream, pattern, count):
    units_per_chunk = max(1, _CHUNK_BYTES // len(pattern))
    while count:
        size = min(units_per_chunk, count)
        stream.write(pattern * size)
        stream.flush()
        count -= size


def _emit_duplex(stdout_bytes, stderr_bytes, stdout_pattern, stderr_pattern):
    stdout = sys.stdout.buffer
    stderr = sys.stderr.buffer
    stdout_units_per_chunk = max(1, _CHUNK_BYTES // len(stdout_pattern))
    stderr_units_per_chunk = max(1, _CHUNK_BYTES // len(stderr_pattern))
    while stdout_bytes or stderr_bytes:
        if stdout_bytes:
            size = min(stdout_units_per_chunk, stdout_bytes)
            stdout.write(stdout_pattern * size)
            stdout.flush()
            stdout_bytes -= size
        if stderr_bytes:
            size = min(stderr_units_per_chunk, stderr_bytes)
            stderr.write(stderr_pattern * size)
            stderr.flush()
            stderr_bytes -= size


def main():
    arguments = sys.argv[1:]
    if len(arguments) not in (6, 7):
        raise ValueError("expected witness, mode, state, nonce, byte counts, and optional exit code")
    witness_path, mode, state_path, nonce, stdout_text, stderr_text = arguments[:6]
    if not nonce:
        raise ValueError("invalid nonce")
    stdout_bytes = _count(stdout_text, "stdout byte count")
    stderr_bytes = _count(stderr_text, "stderr byte count")
    exit_code = _exit_code(arguments[6]) if len(arguments) == 7 else 0

    if mode not in ("emit", "utf8", "hold", "flood"):
        raise ValueError("invalid fixture mode")
    if mode == "utf8" and stdout_bytes % 2:
        raise ValueError("utf8 stdout byte count must be even")
    if mode == "hold" and (stdout_bytes or stderr_bytes):
        raise ValueError("hold mode requires zero output counts")
    if mode == "flood" and (stdout_bytes, stderr_bytes) not in (
            (_FLOOD_BYTES, 0), (0, _FLOOD_BYTES)):
        raise ValueError("flood mode requires one 1MiB output stream")

    witness = runpy.run_path(witness_path, run_name="verifier_capture_witness")
    observe_process = witness.get("observe_process")
    if not callable(observe_process):
        raise ValueError("trusted witness lacks observe_process")
    observation = observe_process(nonce)
    encoded_state = json.dumps(
        observation, ensure_ascii=True, separators=(",", ":")
    ) + "\n"
    with open(state_path, "x", encoding="ascii", newline="\n") as state:
        state.write(encoded_state)
        state.flush()

    if mode == "emit":
        _emit_duplex(stdout_bytes, stderr_bytes, b"O", b"E")
    elif mode == "utf8":
        _emit_duplex(stdout_bytes // 2, stderr_bytes, b"\xc3\xa9", b"E")
    elif mode == "flood":
        if stdout_bytes:
            _write_chunks(sys.stdout.buffer, b"F", stdout_bytes)
        else:
            _write_chunks(sys.stderr.buffer, b"F", stderr_bytes)
        time.sleep(30)
    else:
        time.sleep(30)
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
