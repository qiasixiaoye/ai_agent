import json
import os
import time
import uuid
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


PORT = int(os.getenv("PORT", "8090"))
DIFY_BASE_URL = os.getenv("DIFY_BASE_URL", "").rstrip("/")
DIFY_API_KEY = os.getenv("DIFY_API_KEY", "")
DIFY_DEFAULT_WORKFLOW_ID = os.getenv("DIFY_DEFAULT_WORKFLOW_ID", "")


class Handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def do_GET(self):
        if self.path == "/health":
            self._json({
                "configured": configured(),
                "baseUrl": DIFY_BASE_URL,
                "defaultWorkflowId": DIFY_DEFAULT_WORKFLOW_ID,
                "apiKeyConfigured": bool(DIFY_API_KEY),
            })
            return
        self._json({"error": "not found"}, status=404)

    def do_POST(self):
        if self.path != "/run":
            self._json({"error": "not found"}, status=404)
            return
        body = self._read_json()
        self._json(run_workflow(body))

    def log_message(self, fmt, *args):
        print("[dify-bridge] " + fmt % args)

    def _read_json(self):
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            return json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError:
            return {}

    def _json(self, payload, status=200):
        encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self._cors()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type,Authorization")


def configured():
    return bool(DIFY_BASE_URL and DIFY_API_KEY)


def run_workflow(req):
    if not configured():
        return {
            "success": False,
            "errorMessage": "Dify Bridge 未配置：请设置 DIFY_BASE_URL / DIFY_API_KEY",
        }

    workflow_id = req.get("workflowId") or DIFY_DEFAULT_WORKFLOW_ID
    payload = {
        "inputs": req.get("inputs") or {},
        "response_mode": req.get("responseMode") or "blocking",
        "user": req.get("user") or "bridge-" + uuid.uuid4().hex,
    }
    if workflow_id:
        payload["workflow_id"] = workflow_id

    start = time.time()
    url = DIFY_BASE_URL + "/v1/workflows/run"
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + DIFY_API_KEY,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read().decode("utf-8")
            node = json.loads(raw) if raw else {}
            data_node = node.get("data") or {}
            status = data_node.get("status") or "succeeded"
            ok = 200 <= response.status < 300 and status.lower() != "failed"
            return {
                "success": ok,
                "workflowId": workflow_id,
                "workflowRunId": node.get("workflow_run_id"),
                "status": status,
                "outputs": data_node.get("outputs"),
                "elapsedMs": int((time.time() - start) * 1000),
                "rawResponse": raw,
                "errorMessage": None if ok else "Dify returned non-success status: " + status,
            }
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        return {
            "success": False,
            "workflowId": workflow_id,
            "elapsedMs": int((time.time() - start) * 1000),
            "rawResponse": raw,
            "errorMessage": f"Dify HTTP {exc.code}: {raw[:500]}",
        }
    except Exception as exc:
        return {
            "success": False,
            "workflowId": workflow_id,
            "elapsedMs": int((time.time() - start) * 1000),
            "errorMessage": str(exc),
        }


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"[dify-bridge] listening on 0.0.0.0:{PORT}")
    server.serve_forever()
