"""Synthetic reproduction of r3's stale CDP frontend-node-ID filter."""
import asyncio, hashlib, importlib.util, json, sys, tempfile, time, unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
CANDIDATE = HERE.parent / "root75-metrics-offline-r3"
PINS = {"renderer_capture.py": "a3554aa8e850640863c1b829b46882664d54245e6f7f8b59945d1aefe0a63851",
        "instrumented_host.py": "22df7ae2ae039b4d47337c5ef1d72d010b8549ed8b73609a03f0ee4522f7a2ab",
        "headful_attribute_host.py": "669c9d1bcd5b221b970809ae17545c86ef79b67edf904ec25df12c26badec5be"}
URL = "http://127.0.0.1:12345/circuitjs.html?lang=en&tsjNormalMode=cold&tsjNormalSeed=75"
ATTR, RUNNING, DONE = "data-tsj-q30-normal-state", "SCREEN_RUNNING:fixture", "SCREEN_DONE:fixture"
FIXTURES = Path(tempfile.mkdtemp(prefix="q30-r3-frontend-id-repro-"))


def pinned(filename, name):
    path = CANDIDATE / filename
    if hashlib.sha256(path.read_bytes()).hexdigest() != PINS[filename]:
        raise ValueError("pinned r3 source changed: " + filename)
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


capture = pinned("renderer_capture.py", "renderer_capture")
adapter = pinned("instrumented_host.py", "instrumented_host")


class Session:
    """Synthetic Chromium 154 CDP: frontend IDs refresh; backend IDs stay 2/4."""
    def __init__(self):
        self.calls, self.metrics, self.documents = [], 0, 0
        self.state, self.document_id, self.html_id = "INITIAL", None, None

    async def send(self, method, params):
        self.calls.append(method)
        if method == "Browser.getVersion":
            return {"protocolVersion": "fixture", "product": "Chromium/154 synthetic"}
        if method == "Target.getTargetInfo":
            return {"targetInfo": {"targetId": "frame", "type": "page", "url": URL,
                                   "browserContextId": "context"}}
        if method == "Performance.enable":
            assert params == {"timeDomain": "threadTicks"}
            return {}
        if method == "Performance.disable":
            return {}
        if method == "Performance.getMetrics":
            self.metrics += 1
            return {"metrics": [{"name": "Timestamp", "value": 100 + self.metrics},
                {"name": "ThreadTime", "value": 2 + self.metrics / 10},
                {"name": "TaskDuration", "value": 1 + self.metrics / 10}]}
        if method == "Page.getFrameTree":
            return {"frameTree": {"frame": {"id": "frame", "loaderId": "loader", "url": URL}}}
        if method == "DOM.getDocument":
            self.documents += 1
            self.document_id, self.html_id = 2 + 10 * (self.documents - 1), 4 + 10 * (self.documents - 1)
            return {"root": {"nodeId": self.document_id, "backendNodeId": 2, "documentURL": URL,
                "children": [{"nodeName": "HTML", "nodeId": self.html_id, "backendNodeId": 4,
                              "attributes": [ATTR, self.state]}]}}
        raise AssertionError("unexpected synthetic CDP command: " + method)

    async def detach(self):
        self.calls.append("detach")


class Repro(unittest.IsolatedAsyncioTestCase):
    async def fixture(self, label, rebind):
        output = FIXTURES / label
        output.mkdir()
        owner = adapter.imported(CANDIDATE / "headful_attribute_host.py",
                                 "r3_frontend_owner_" + label, PINS["headful_attribute_host.py"])
        adapter.install(owner, output)
        class Page:
            url = URL
            main_frame = object()
            async def unroute(self, pattern, handler): pass
        page, session = Page(), Session()
        observer = owner.HtmlAttributeObserver(page, URL, ATTR, time.monotonic() + 5, [])
        observer.session = session
        await observer.acquire_document(observer.version)
        snapshots, original_read = [], observer.read_document_scope
        async def read_scope():
            snapshot = await original_read()
            snapshots.append(snapshot)
            return snapshot
        observer.read_document_scope = read_scope
        class Route:
            def __init__(self):
                self.request = type("Request", (), {"frame": page.main_frame, "resource_type": "script"})()
                self.actions = []
            async def abort(self): self.actions.append("abort")
            async def continue_(self):
                if rebind:
                    document, html, scope = snapshots[-1]
                    assert html is not None and all(observer.script_attachment.get(k) == v for k, v in scope.items())
                    observer.document_id, observer.document_backend = document["nodeId"], document["backendNodeId"]
                    observer.document_scope = scope
                    observer.attach_html(html)
                self.actions.append("continue")
        return observer, session, Route(), output

    async def terminal_events(self, observer, session):
        for state in (RUNNING, DONE):
            session.state = state
            observer.attribute_modified({"nodeId": session.html_id, "name": ATTR, "value": state})
        await observer.verify_terminal_scope(dict(observer.document_scope), observer.navigation_epoch,
                                             DONE, observer.state_changes)

    async def test_pinned_r3_adapter_drops_events_for_refreshed_html_frontend_id(self):
        observer, session, route, output = await self.fixture("r3-current", False)
        await observer.hold_compiled_script(route)
        self.assertEqual((observer.html_id, session.html_id), (4, 14))
        await self.terminal_events(observer, session)
        self.assertEqual(observer.event_count, 0)
        self.assertEqual(observer.state, "INITIAL")
        self.assertFalse(observer.terminal_verified)
        await observer.close()
        self.assertEqual(route.actions, ["continue"])
        self.assertFalse(observer.route_tasks)
        receipt = json.loads((output / "renderer-capture.json").read_text(encoding="utf-8"))
        self.assertEqual(receipt["status"], "FAIL_CAPTURE")

    async def test_control_rebinds_validated_setup_nodes_before_original_continue(self):
        observer, session, route, output = await self.fixture("control-rebind", True)
        await observer.hold_compiled_script(route)
        self.assertEqual((observer.document_id, observer.html_id), (12, 14))
        self.assertEqual((observer.document_backend, observer.html_backend), (2, 4))
        self.assertEqual(observer.document_scope["documentBackend"], 2)
        self.assertEqual(observer.document_scope["htmlBackend"], 4)
        self.assertEqual(route.actions, ["continue"])
        await self.terminal_events(observer, session)
        self.assertEqual(observer.event_count, 2)
        self.assertEqual(observer.state, DONE)
        self.assertTrue(observer.terminal_verified)
        await observer.close()
        self.assertEqual(route.actions, ["continue"])
        self.assertFalse(observer.route_tasks)
        receipt = json.loads((output / "renderer-capture.json").read_text(encoding="utf-8"))
        self.assertEqual(receipt["status"], "CPU_MEASURED")
        self.assertTrue(receipt["binding"]["finalMetricScopeVerified"])


if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__])
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    receipt = {"schema": 1, "status": "SYNTHETIC_REPRO_ONLY",
        "assertionStatus": "PASS" if result.wasSuccessful() else "FAIL",
        "qualification": False, "liveExecution": False, "browserLaunched": False,
        "nativeProcessLaunched": False, "applicationOutcome": None, "sourcePins": PINS,
        "tests": result.testsRun, "failures": len(result.failures), "errors": len(result.errors),
        "fixtureRoot": str(FIXTURES)}
    (FIXTURES / "frontend-id-repro-receipt.json").write_text(json.dumps(receipt, indent=2) + "\n",
                                                              encoding="utf-8")
    print(json.dumps(receipt, indent=2))
    raise SystemExit(0 if result.wasSuccessful() else 1)
