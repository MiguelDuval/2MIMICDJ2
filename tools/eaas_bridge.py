#!/usr/bin/env python3
"""Small TCP compatibility bridge for Android 2MIMICDJ2.

The Android phone cannot bind the Engine-standard TCP ports on some firmware
builds. This bridge runs on a normal Linux/macOS/Windows host on the same LAN,
where 50010/tcp and 50020/tcp are bindable, and forwards them byte-for-byte to
the phone's high-port listeners.

Default mapping:
  LAN 50010 -> phone 50100 (EAAS gRPC / HTTP/2)
  LAN 50020 -> phone 50110 (EAAS HTTP file transfer)

The bridge is intentionally transport-only. It does not decode gRPC or HTTP.
"""

from __future__ import annotations

import argparse
import logging
import select
import signal
import socket
import socketserver
import threading
from typing import Optional, Tuple


LOG = logging.getLogger("eaas-bridge")


class _RelayHandler(socketserver.BaseRequestHandler):
    def handle(self) -> None:
        forwarder: "TcpForwarder" = self.server.forwarder  # type: ignore[attr-defined]
        client = self.request
        target = None
        try:
            target = socket.create_connection(
                (forwarder.target_host, forwarder.target_port),
                timeout=forwarder.connect_timeout,
            )
            target.settimeout(None)
            client.settimeout(None)

            LOG.info(
                "%s %s:%d -> %s:%d",
                forwarder.name,
                *self.client_address[:2],
                forwarder.target_host,
                forwarder.target_port,
            )
            self._relay(client, target, forwarder)
        except OSError as exc:
            LOG.warning(
                "%s target connection failed for %s:%d -> %s:%d: %s",
                forwarder.name,
                *self.client_address[:2],
                forwarder.target_host,
                forwarder.target_port,
                exc,
            )
        finally:
            if target is not None:
                _safe_close(target)

    @staticmethod
    def _relay(
        client: socket.socket,
        target: socket.socket,
        forwarder: "TcpForwarder",
    ) -> None:
        sockets = (client, target)
        peers = {client: target, target: client}

        half_closed = set()
        while len(half_closed) < 2:
            readable, _, exceptional = select.select(sockets, (), sockets, 30.0)
            if exceptional:
                break
            if not readable:
                continue

            for source in readable:
                data = source.recv(forwarder.buffer_size)
                destination = peers[source]
                if not data:
                    if source not in half_closed:
                        try:
                            destination.shutdown(socket.SHUT_WR)
                        except OSError:
                            pass
                        half_closed.add(source)
                    continue

                destination.sendall(data)


class _ThreadingTCPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(
        self,
        server_address: Tuple[str, int],
        handler_class,
        forwarder: "TcpForwarder",
    ) -> None:
        self.forwarder = forwarder
        super().__init__(server_address, handler_class)


class TcpForwarder:
    """One bidirectional TCP byte-stream forwarder."""

    def __init__(
        self,
        listen_host: str,
        listen_port: int,
        target_host: str,
        target_port: int,
        *,
        name: str = "forwarder",
        buffer_size: int = 64 * 1024,
        connect_timeout: float = 5.0,
    ) -> None:
        if not 0 <= listen_port <= 65535:
            raise ValueError(f"invalid listen port: {listen_port}")
        if not 1 <= target_port <= 65535:
            raise ValueError(f"invalid target port: {target_port}")
        if buffer_size < 1024:
            raise ValueError("buffer_size must be at least 1024")

        self.listen_host = listen_host
        self.listen_port = listen_port
        self.target_host = target_host
        self.target_port = target_port
        self.name = name
        self.buffer_size = buffer_size
        self.connect_timeout = connect_timeout

        self._server: Optional[_ThreadingTCPServer] = None
        self._thread: Optional[threading.Thread] = None
        self._lock = threading.Lock()

    def start(self) -> None:
        with self._lock:
            if self._server is not None:
                return

            server = _ThreadingTCPServer(
                (self.listen_host, self.listen_port),
                _RelayHandler,
                self,
            )
            self._server = server
            self.listen_port = server.server_address[1]
            self._thread = threading.Thread(
                target=server.serve_forever,
                name=f"EAAS-{self.name}",
                daemon=True,
            )
            self._thread.start()

        LOG.info(
            "%s listening on %s:%d -> %s:%d",
            self.name,
            self.listen_host,
            self.listen_port,
            self.target_host,
            self.target_port,
        )

    def stop(self) -> None:
        with self._lock:
            server = self._server
            thread = self._thread
            self._server = None
            self._thread = None

        if server is None:
            return

        server.shutdown()
        server.server_close()
        if thread is not None:
            thread.join(timeout=2.0)
        LOG.info("%s stopped", self.name)


class EaasBridge:
    def __init__(
        self,
        target_host: str,
        *,
        listen_host: str = "0.0.0.0",
        grpc_listen_port: int = 50010,
        http_listen_port: int = 50020,
        grpc_target_port: int = 50100,
        http_target_port: int = 50110,
    ) -> None:
        self.grpc = TcpForwarder(
            listen_host,
            grpc_listen_port,
            target_host,
            grpc_target_port,
            name="gRPC",
        )
        self.http = TcpForwarder(
            listen_host,
            http_listen_port,
            target_host,
            http_target_port,
            name="HTTP",
        )

    def start(self) -> None:
        self.grpc.start()
        try:
            self.http.start()
        except Exception:
            self.grpc.stop()
            raise

    def stop(self) -> None:
        self.http.stop()
        self.grpc.stop()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Bridge Engine-standard ports to a 2MIMICDJ2 phone server."
    )
    parser.add_argument("--target-host", required=True, help="Android phone LAN IPv4 address")
    parser.add_argument("--listen-host", default="0.0.0.0")
    parser.add_argument("--grpc-listen-port", type=int, default=50010)
    parser.add_argument("--http-listen-port", type=int, default=50020)
    parser.add_argument("--grpc-target-port", type=int, default=50100)
    parser.add_argument("--http-target-port", type=int, default=50110)
    parser.add_argument("--log-level", default="INFO")

    args = parser.parse_args()
    logging.basicConfig(
        level=getattr(logging, args.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    bridge = EaasBridge(
        args.target_host,
        listen_host=args.listen_host,
        grpc_listen_port=args.grpc_listen_port,
        http_listen_port=args.http_listen_port,
        grpc_target_port=args.grpc_target_port,
        http_target_port=args.http_target_port,
    )

    bridge.start()
    LOG.info(
        "EAAS bridge active: TCP/%d -> %s:%d; TCP/%d -> %s:%d",
        bridge.grpc.listen_port,
        args.target_host,
        args.grpc_target_port,
        bridge.http.listen_port,
        args.target_host,
        args.http_target_port,
    )

    stop_event = threading.Event()

    def stop_handler(_signum, _frame) -> None:
        stop_event.set()

    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, stop_handler)

    try:
        while not stop_event.wait(1.0):
            pass
    except KeyboardInterrupt:
        pass
    finally:
        bridge.stop()

    return 0


def _safe_close(sock: socket.socket) -> None:
    try:
        sock.close()
    except OSError:
        pass


if __name__ == "__main__":
    raise SystemExit(main())
