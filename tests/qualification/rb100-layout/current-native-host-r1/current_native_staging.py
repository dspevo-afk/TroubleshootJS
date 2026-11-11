"""Thin opaque-byte staging for the current native producer source."""

import ntpath


MAX_SOURCE_BYTES = 1048576
SOURCE_BASENAME = "current-input.java"
DESTINATION_BASENAME = "CohortTerminalTelemetryInputV2.java"
_LOWER_HEX = "0123456789abcdef"
_READER_FUNCTIONS = (
    "_inspect_path",
    "_read_file_once",
    "_token_identity",
    "_check_windows_local_lexical_path",
)
_OWNER_FUNCTIONS = (
    "_root_bindings",
    "_check_directories",
    "_verify_root_bindings",
    "_read_with_reader",
    "_write_exclusive",
    "_readback_output",
    "_require_same_input_document",
    "_reader_token_identity",
)


class StagingError(ValueError):
    """Stable, path-free local rejection from the staging adapter."""

    def __init__(self, code, phase):
        self.code = code
        self.phase = phase
        super().__init__(code)


def _reject(code, phase):
    raise StagingError(code, phase)


def _valid_sha256(value):
    return (type(value) is str and len(value) == 64
            and all(char in _LOWER_HEX for char in value))


def _directory_fact(result, phase):
    if type(result) is not tuple or len(result) != 3:
        _reject("STAGING_DIRECTORY_RESULT", phase)
    try:
        real_path = ntpath.normcase(ntpath.normpath(str(result[1])))
    except (OSError, TypeError, ValueError):
        _reject("STAGING_DIRECTORY_RESULT", phase)
    if not real_path or not ntpath.isabs(real_path):
        _reject("STAGING_DIRECTORY_RESULT", phase)

    identity = result[2]
    if type(identity) is not tuple or not identity:
        _reject("STAGING_DIRECTORY_IDENTITY", phase)
    leaf = identity[-1]
    if (type(leaf) is not tuple or len(leaf) != 2
            or type(leaf[0]) is not str or type(leaf[1]) is not tuple
            or len(leaf[1]) != 7
            or any(type(value) is not int for value in leaf[1])):
        _reject("STAGING_DIRECTORY_IDENTITY", phase)
    snapshot = leaf[1]
    device, file_id = snapshot[0], snapshot[1]
    if device < 0 or file_id <= 0:
        _reject("STAGING_DIRECTORY_IDENTITY", phase)
    return {"path": real_path, "identity": (device, file_id)}


def _endpoint_snapshot(reader, io_owner, config, phase):
    root_results, scratch_result, output_result = io_owner._check_directories(
        reader, config)
    if type(root_results) is not dict or set(root_results) != {"N", "D"}:
        _reject("STAGING_ROOT_RESULTS", phase)
    return {
        "roots": {
            "N": _directory_fact(root_results["N"], phase),
            "D": _directory_fact(root_results["D"], phase),
        },
        "scratch": _directory_fact(scratch_result, phase),
        "output": _directory_fact(output_result, phase),
    }


def _overlaps(left, right, code, phase):
    try:
        common = ntpath.normcase(ntpath.commonpath((left["path"], right["path"])))
    except (OSError, TypeError, ValueError):
        _reject(code, phase)
    return common == left["path"] or common == right["path"]


def _validate_endpoints(source, destination, phase):
    for label in ("N", "D"):
        if source["roots"][label] != destination["roots"][label]:
            _reject("STAGING_REFERENCE_IDENTITY", phase)

    source_output = source["output"]
    destination_output = destination["output"]
    if (source_output["identity"] == destination_output["identity"]
            or _overlaps(source_output, destination_output,
                         "STAGING_ENDPOINT_OVERLAP", phase)):
        _reject("STAGING_ENDPOINT_OVERLAP", phase)

    for output in (source_output, destination_output):
        for endpoint in (source, destination):
            for label in ("N", "D"):
                reference = endpoint["roots"][label]
                if output["identity"] == reference["identity"]:
                    _reject("STAGING_REFERENCE_IDENTITY", phase)
                if _overlaps(output, reference, "STAGING_REFERENCE_OVERLAP", phase):
                    _reject("STAGING_REFERENCE_OVERLAP", phase)


def _config_path(config, key, code, phase):
    if type(config) is not dict:
        _reject(code, phase)
    value = config.get(key)
    if type(value) is not str or not value:
        _reject(code, phase)
    return value


def _verify_endpoints(reader, io_owner, source_config, source_roots_sha256,
                      destination_config, destination_roots_sha256, phase):
    source = _endpoint_snapshot(reader, io_owner, source_config, phase)
    destination = _endpoint_snapshot(reader, io_owner, destination_config, phase)
    _validate_endpoints(source, destination, phase)
    io_owner._verify_root_bindings(
        reader, source_config, source_roots_sha256, phase + "-source-roots")
    io_owner._verify_root_bindings(
        reader, destination_config, destination_roots_sha256,
        phase + "-destination-roots")


def stage_producer_source(*, reader, io_owner, source_config,
                          source_roots_sha256, destination_config,
                          destination_roots_sha256, expected_bytes,
                          expected_sha256, expected_source_token):
    """Copy one authenticated producer source as opaque exact bytes.

    The caller supplies already authenticated modules, configs and the expected
    source token. This function does not perform admission or source discovery.
    """
    if type(expected_bytes) is not int or not 1 <= expected_bytes <= MAX_SOURCE_BYTES:
        _reject("STAGING_EXPECTED_BYTES", "stage-expectation")
    if (not _valid_sha256(source_roots_sha256)
            or not _valid_sha256(destination_roots_sha256)
            or not _valid_sha256(expected_sha256)):
        _reject("STAGING_SHA256", "stage-expectation")

    io_owner._assert_module_functions(
        reader, _READER_FUNCTIONS, "stage-reader-bindings")
    io_owner._assert_module_functions(
        io_owner, _OWNER_FUNCTIONS, "stage-owner-bindings")

    expected_source_identity = io_owner._reader_token_identity(
        reader, expected_source_token, "STAGING_SOURCE", "stage-expectation")

    source_data_root = _config_path(
        source_config, "dataOutputRoot", "STAGING_SOURCE_CONFIG", "stage-source-path")
    destination_data_root = _config_path(
        destination_config, "dataOutputRoot", "STAGING_DESTINATION_CONFIG",
        "stage-destination-path")
    destination_scratch_root = _config_path(
        destination_config, "scratchRoot", "STAGING_DESTINATION_CONFIG",
        "stage-destination-path")
    source_path = ntpath.join(source_data_root, SOURCE_BASENAME)
    destination_path = ntpath.join(destination_data_root, DESTINATION_BASENAME)

    _verify_endpoints(
        reader, io_owner, source_config, source_roots_sha256,
        destination_config, destination_roots_sha256, "stage-initial")

    raw, source_token = io_owner._read_with_reader(
        reader, source_path, MAX_SOURCE_BYTES, expected_bytes, expected_sha256,
        "STAGING_SOURCE", "stage-source-read")
    actual_source_identity = io_owner._reader_token_identity(
        reader, source_token, "STAGING_SOURCE", "stage-source-identity")
    if actual_source_identity != expected_source_identity:
        _reject("STAGING_SOURCE_IDENTITY", "stage-source-identity")

    _verify_endpoints(
        reader, io_owner, source_config, source_roots_sha256,
        destination_config, destination_roots_sha256, "stage-before-write")
    write_fact = io_owner._write_exclusive(
        reader, destination_config, destination_path, raw, MAX_SOURCE_BYTES,
        destination_scratch_root, destination_roots_sha256,
        "stage-producer-source", with_readback_token=True)
    if (type(write_fact) is not dict
            or type(write_fact.get("bytes")) is not int
            or write_fact["bytes"] != expected_bytes
            or write_fact.get("sha256") != expected_sha256
            or "readbackToken" not in write_fact):
        _reject("STAGING_WRITE_FACT", "stage-producer-source")

    writer_token = write_fact["readbackToken"]
    io_owner._reader_token_identity(
        reader, writer_token, "STAGING_DESTINATION", "stage-writer-readback")
    first_target_token = io_owner._readback_output(
        reader, destination_config, destination_path, raw, MAX_SOURCE_BYTES,
        destination_roots_sha256, "stage-target-readback")
    io_owner._require_same_input_document(
        reader, "STAGING_DESTINATION", raw, writer_token, raw,
        first_target_token, "stage-target-readback-identity")

    current_source, current_source_token = io_owner._read_with_reader(
        reader, source_path, MAX_SOURCE_BYTES, expected_bytes, expected_sha256,
        "STAGING_SOURCE", "stage-source-recheck")
    io_owner._require_same_input_document(
        reader, "STAGING_SOURCE", raw, source_token, current_source,
        current_source_token, "stage-source-recheck")

    final_target_token = io_owner._readback_output(
        reader, destination_config, destination_path, raw, MAX_SOURCE_BYTES,
        destination_roots_sha256, "stage-target-final-readback")
    io_owner._require_same_input_document(
        reader, "STAGING_DESTINATION", raw, writer_token, raw,
        final_target_token, "stage-target-final-identity")

    _verify_endpoints(
        reader, io_owner, source_config, source_roots_sha256,
        destination_config, destination_roots_sha256, "stage-final")

    return {
        "bytes": write_fact["bytes"],
        "sha256": write_fact["sha256"],
        "sourceToken": source_token,
        "destinationToken": writer_token,
        "sourceBasename": ntpath.basename(source_path),
        "destinationBasename": ntpath.basename(destination_path),
    }
