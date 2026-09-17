import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class TossHandler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        if self.path == "/health":
            self.respond(200, {"status": "UP"})
            return
        self.respond(404, {"code": "NOT_FOUND"})

    def do_POST(self) -> None:
        if self.path != "/v1/payments/confirm":
            self.respond(404, {"code": "NOT_FOUND"})
            return

        request = json.loads(self.read_request_body())
        if not all(key in request for key in ("paymentKey", "orderId", "amount")):
            self.respond(400, {"code": "INVALID_REQUEST"})
            return
        self.respond(
            200,
            {
                "paymentKey": request.get("paymentKey", "system-payment-key"),
                "orderId": request.get("orderId"),
                "status": "DONE",
                "approvedAt": "2026-09-17T10:00:00+09:00",
                "totalAmount": request.get("amount"),
            },
        )

    def log_message(self, format: str, *args: object) -> None:
        return

    def read_request_body(self) -> bytes:
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks = []
            while True:
                size = int(self.rfile.readline().split(b";", 1)[0].strip(), 16)
                if size == 0:
                    # 빈 줄 또는 trailer 헤더를 모두 소비한다.
                    while self.rfile.readline().strip():
                        pass
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)  # CRLF
            return b"".join(chunks)
        length = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(length)

    def respond(self, status: int, payload: dict[str, object]) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8090), TossHandler).serve_forever()
