# Prime GO compatibility gateway

## Why the gateway exists

Direct hardware testing on the target Android phone showed that the phone can
open normal TCP listeners, but the Engine-standard ports are rejected by the
phone kernel with EPERM:

- TCP 50010: rejected
- TCP 50020: rejected
- TCP 50100: allowed
- TCP 50110: expected paired high port for local HTTP

The Prime GO capture shows that the Engine Remote Library client actually uses
TCP 50010 for gRPC and TCP 50020 for the audio download stream.

That means a stock, unprivileged Android app cannot make the Prime GO connect
by changing only the Android socket implementation. The practical standalone
workaround is a tiny LAN gateway running on a normal Linux, macOS, or Windows
machine.

## Topology

    Prime GO
      |
      | TCP 50010 / 50020
      v
    [LAN compatibility gateway]
      |                         |
      | TCP 50100 / 50110       |
      v                         |
    Android phone / 2MIMICDJ2 <-

The gateway is a byte-for-byte TCP forwarder. It does not understand gRPC or
HTTP, so HTTP/2 streams and long binary audio transfers pass through unchanged.

## Android setup

1. Start 2MIMICDJ2 on the phone.
2. Enter the LAN IPv4 address of the gateway in the "Engine compatibility
   bridge (optional)" field.
3. Start the server.
4. The Android diagnostics should show a local high-port listener such as
   50100 and an "Advertised to Prime GO" endpoint of GATEWAY_IP:50010.
5. Keep the phone and gateway on the same LAN as the Prime GO.

When the bridge-host field is empty, the application keeps advertising its
actual local gRPC port. That mode remains useful for diagnostics but is not
expected to work with the Prime GO standard Engine Remote Library path.

## Gateway setup

The repository contains a dependency-free Python implementation:

    tools/eaas_bridge.py

On Linux/macOS:

    python3 tools/eaas_bridge.py --target-host PHONE_IP

On Windows:

    py tools\\eaas_bridge.py --target-host PHONE_IP

Defaults:

    LAN TCP 50010 -> PHONE TCP 50100
    LAN TCP 50020 -> PHONE TCP 50110

For a different local high-port pair, override the targets:

    python3 tools/eaas_bridge.py \
      --target-host PHONE_IP \
      --grpc-target-port 60000 \
      --http-target-port 60010

The Windows/Linux host firewall must allow inbound TCP 50010 and 50020 from
the Prime GO.

## Why the standard ports stay in discovery

The Prime GO uses the gRPC port advertised by EAAS discovery and then opens
the standard Engine session on the advertised endpoint. The gateway therefore
needs the Android application to advertise:

    grpc://GATEWAY_IP:50010

while the Android server itself listens on its permitted high port.

The same mapping is used by GetTrack: the returned HTTP URL points at the
gateway's standard TCP 50020, which the gateway forwards to the phone's paired
HTTP port.

## Current limitations

This is a compatibility gateway, not a claim that stock Android can bind the
Engine-standard ports directly.

The current app still needs the next hardware-validation pass for:

1. NetworkTrustService/CreateTrust through the gateway;
2. GetLibrary / GetTracks against the real Prime GO;
3. GetTrack blob URL;
4. /download/<encoded-key> over the forwarded TCP 50020 stream;
5. actual track loading and playback.

The repository's direct packet-capture evidence already establishes the target
transport sequence; the gateway removes the Android kernel port restriction
from that sequence.
