# Port 50020 Investigation

## Why this document exists

The original MimicDJ project spent a separate investigation branch on Android binding failures for EAAS HTTP port 50020. This history is preserved here so 2MIMICDJ2 does not repeat the same guesses.

## Legacy sequence

1. Java `ServerSocket` on `0.0.0.0:50020`.
2. Java bind candidates: advertised LAN IPv4, then wildcard.
3. Java NIO `ServerSocketChannel` with IPv4.
4. Java dual-stack `[::]:50020`, then IPv4 fallback.
5. Native Android/Linux socket: `android.system.Os.socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)`, `SO_REUSEADDR`, `Os.bind(0.0.0.0, 50020)`, `Os.listen(...)`.
6. The native implementation became the final HTTP listener in the old `fix/android-lan-discovery` branch.
7. The branch also tested per-socket `ConnectivityManager.Network.bindSocket(...)`; this was later removed because the project found that binding the listener to a selected Android Network could break ingress on local/hotspot interfaces.
8. A temporary HTTP move to 50021 was made as a diagnostic split and later reverted to 50020.

## Diagnostics learned from the old project

The old app added direct Java bind probes for several neighboring ports and inspected:

- 49999, 50000, 50019, 50020, 50021, 50022
- 50100 and 60000
- an ephemeral port
- `/proc/sys/net/ipv4/ip_local_port_range`
- `/proc/sys/net/ipv4/ip_local_reserved_ports`
- `/proc/sys/net/ipv4/ip_local_unbindable_ports`

Those probes distinguish a port-specific/kernel policy problem from a general TCP bind failure.

## 2MIMICDJ2 decision

`2MIMICDJ2` uses the legacy native `Os` listener as the primary 50020 experiment. It does not move production traffic to 50021 and does not bind the listener to a `ConnectivityManager` Network.

On native bind failure, the app records a Java-vs-native port diagnostic matrix so the next hardware result is actionable.

## Evidence status

The historical branch proves that this was the next implementation path we deliberately pursued; it does not, by itself, prove that native bind succeeds on the current target phone. That remains a device-level test result.