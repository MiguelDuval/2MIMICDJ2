import os
import sys
import socket
import threading
import time
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

from eaas_bridge import TcpForwarder


class TcpForwarderTest(unittest.TestCase):
    def test_forwards_bytes_in_both_directions(self):
        target = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        target.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        target.bind(("127.0.0.1", 0))
        target.listen(1)
        target_port = target.getsockname()[1]

        received = []
        ready = threading.Event()

        def target_worker():
            ready.set()
            conn, _ = target.accept()
            with conn:
                received.append(conn.recv(4096))
                conn.sendall(b"TARGET->PHONE")
        thread = threading.Thread(target=target_worker, daemon=True)
        thread.start()
        ready.wait(timeout=2)

        forwarder = TcpForwarder(
            listen_host="127.0.0.1",
            listen_port=0,
            target_host="127.0.0.1",
            target_port=target_port,
            name="test"
        )
        forwarder.start()
        self.assertGreater(forwarder.listen_port, 0)

        with socket.create_connection(("127.0.0.1", forwarder.listen_port), timeout=2) as client:
            client.sendall(b"PHONE->TARGET")
            self.assertEqual(client.recv(4096), b"TARGET->PHONE")

        thread.join(timeout=2)
        target.close()
        self.assertEqual(received, [b"PHONE->TARGET"])

        forwarder.stop()

    def test_allows_long_lived_binary_stream_without_message_framing(self):
        target = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        target.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        target.bind(("127.0.0.1", 0))
        target.listen(1)
        target_port = target.getsockname()[1]

        def target_worker():
            conn, _ = target.accept()
            with conn:
                while True:
                    chunk = conn.recv(8192)
                    if not chunk:
                        break
                    conn.sendall(chunk)
        threading.Thread(target=target_worker, daemon=True).start()

        forwarder = TcpForwarder(
            listen_host="127.0.0.1",
            listen_port=0,
            target_host="127.0.0.1",
            target_port=target_port,
            name="stream-test"
        )
        forwarder.start()

        payload = bytes(range(256)) * 1024
        with socket.create_connection(("127.0.0.1", forwarder.listen_port), timeout=2) as client:
            client.sendall(payload)
            received = bytearray()
            client.settimeout(2)
            while len(received) < len(payload):
                received.extend(client.recv(65536))

        target.close()
        forwarder.stop()
        self.assertEqual(bytes(received), payload)


if __name__ == "__main__":
    unittest.main()
