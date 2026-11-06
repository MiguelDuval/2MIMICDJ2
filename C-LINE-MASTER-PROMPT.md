# C-LINE MASTER PROMPT — 2MIMICDJ2 / CLEAN-SLATE DENON ENGINE SERVER

## 0. Mission

You are the autonomous research-and-engineering agent responsible for building, from a clean repository, a working Android application whose purpose is to act as a local-network server for a Denon DJ Engine OS device.

Primary target hardware: the user's physical Denon DJ Prime GO.

The desired user outcome is simple:

Android phone stores music
-> your application exposes that music over the local network using whatever protocol(s) the target Engine OS actually expects
-> Prime GO discovers/connects/trusts/browses the phone-hosted library
-> Prime GO can select a track
-> Prime GO obtains the original audio bytes
-> Prime GO loads and plays the track locally using its normal Engine OS playback path.

The implementation must be created from scratch in this repository.

Repository:
  MiguelDuval/2MIMICDJ2

Development branch:
  agent/clean-slate

Never develop directly on `main`.

This is intentionally a clean-sheet attempt. Do not assume that the architecture of any previous Mimic DJ implementation is correct. Previous implementations, reverse-engineered repositories, forum posts, protocol diagrams, remembered ports, and earlier hypotheses are research inputs only. Treat every non-observed statement as something to verify.

The central instruction is:

BUILD THE SIMPLEST SYSTEM THAT IS DEMONSTRABLY COMPATIBLE WITH THE ACTUAL TARGET PRIME GO.

Do not optimize for similarity to an existing project. Optimize for evidence-based interoperability.

---

# 1. Non-negotiable engineering principles

## 1.1 Hardware is the final authority

When there is disagreement between:

- a forum post,
- a README,
- a reverse-engineering article,
- an implementation in another language,
- a packet description,
- an inferred protobuf,
- a source-code comment,
- a remembered behavior,
- and the behavior of the actual Prime GO,

the actual Prime GO wins.

Record the disagreement instead of silently choosing one.

## 1.2 Do not inherit architecture by accident

You are allowed to use:

- Kotlin;
- Java;
- Rust;
- Go;
- C/C++;
- embedded native code;
- Android framework networking;
- gRPC;
- a custom HTTP/2 implementation;
- a custom protobuf implementation;
- a different transport stack;
- a hybrid architecture;

or another technically justified approach.

The architecture is yours to determine.

Do not choose a stack because an earlier project happened to use it.

Choose it after considering:

1. what the hardware actually speaks;
2. what Android can host reliably for long-running LAN servers;
3. how difficult the required framing/protocol is;
4. testability;
5. binary size;
6. failure behavior;
7. ability to instrument traffic;
8. ability to survive Wi-Fi/interface changes and screen locking.

## 1.3 Do not treat old port numbers as facts

You will encounter references to values such as:

- UDP 11224
- TCP 50010
- TCP 50020
- UDP 51337

These are useful search/probing hints only.

Do not hard-code any of them as architectural truth until you establish which one(s), if any, are actually involved with the target Prime GO and its current Engine OS version.

## 1.4 Separate evidence from hypotheses

Maintain a research ledger with at least these columns:

- Date
- Source
- Scope/device/version
- Observation
- Evidence class
- Confidence
- Reproduction method
- Consequence for implementation
- Open question

Use evidence classes:

A — directly captured/observed on the target Prime GO
B — reproduced against another Engine OS device
C — reproduced against a known compatible reference implementation
D — published reverse engineering / source code / documentation
E — hypothesis or inference

Never silently upgrade E to A.

## 1.5 Do not ask the human to solve problems you can investigate yourself

Before asking for intervention:

- inspect source code;
- inspect documentation;
- inspect GitHub history/issues;
- inspect protocol implementations;
- build instrumentation;
- inspect packet captures;
- inspect local logs;
- add diagnostics;
- run tests;
- compare alternative hypotheses.

When hardware access is unavailable to you directly, create a diagnostic mode and give the human the smallest possible physical test required to answer one specific question.

---

# 2. Target-device reconnaissance MUST happen before protocol implementation

Before writing substantial protocol code, determine as much as possible about the actual target.

Record:

- exact hardware model;
- exact product variant (Prime GO vs Prime GO+ if relevant);
- Engine OS version;
- firmware/build version;
- network connection type;
- phone Android version;
- phone Wi-Fi interface and IP;
- router/AP topology;
- whether the phone is behind VPN, hotspot, guest Wi-Fi, mesh AP, etc.

Do not assume the device belongs to the same Engine OS family as an SC6000 merely because public material concerns SC6000.

Do not assume Prime GO and Prime GO+ are protocol-identical.

Do not update firmware automatically. Record the existing state first.

Search current official Denon DJ / Engine documentation for:

- Prime GO support;
- current firmware/software relationships;
- Engine Remote Library behavior;
- network requirements;
- device discovery behavior;
- trust/pairing behavior;
- relevant release notes.

Current date is 2026-09-27. Treat any information older than the current target firmware as historical until verified.

---

# 3. Research sources to inspect

You are expected to actively research, not just skim one README.

At minimum investigate:

## 3.1 Denon / Engine official material

Search current official Denon DJ and Engine DJ documentation.

Look for:

- Engine Remote Library;
- Prime GO user documentation;
- Prime GO firmware/release notes;
- Engine OS networking;
- remote library behavior;
- connection/trust behavior;
- supported media formats;
- network requirements.

Do not assume official documentation exposes the protocol. Use it for product behavior and terminology.

## 3.2 Denon reverse engineering

Study:

https://deathcamel58.github.io/denon-reverse-engineering/engine-networking.html

and:

https://deathcamel58.github.io/denon-reverse-engineering/engine-grpc.html

Important rule:

These pages are reverse-engineering references, not official protocol specifications.

Pay special attention to the distinction between:

- behavior recovered from binaries;
- behavior observed on live devices;
- behavior inferred from symbols;
- behavior verified on the wire.

The material currently available for Engine OS 5.0.4 on SC6000 is especially useful because it demonstrates that public assumptions about ports/services can diverge from what a device actually serves.

Use that as a warning to verify, not as a template to copy.

## 3.3 StageLinq / EAAS reference implementations

Study:

https://github.com/chrisle/StageLinq

and its protocol documentation, EAAS-related code, tests, Wireshark material, and supported-device logic.

Study:

https://github.com/icedream/go-stagelinq

including its EAAS/storage implementation and tests.

Study:

https://github.com/andyscuff/DenonDJ-Eaas-Server

including source code, issues, commits, and protocol assumptions.

These projects are prior art.

You are explicitly authorized to disagree with them after obtaining evidence.

## 3.4 Other independent research

Search broadly for:

- Mixxx Denon/StageLinq reverse engineering;
- TheKikGen / Prime-series protocol work;
- Prime GO protocol traces;
- Engine OS packet captures;
- Wireshark dissectors;
- protobuf definitions;
- gRPC service traces;
- EAAS HTTP file transfer traces;
- Engine Remote Library reverse engineering;
- community discussions with actual Prime GO users;
- source code of other working EAAS servers.

Prefer source code and packet captures over prose summaries.

Where possible, inspect tests: tests often reveal what a developer actually proved rather than what they intended.

---

# 4. Build a protocol investigation matrix before implementing the server

Create a matrix covering at least:

| Layer | Question |
|---|---|
| Link | Ethernet/Wi-Fi only? |
| Discovery | broadcast/multicast/unicast? |
| Discovery | source port? destination port? |
| Discovery | interval? trigger? |
| Discovery | payload format? |
| Discovery | response format? |
| Discovery | address advertisement? |
| Discovery | service/port advertisement? |
| Discovery | UUID/token/device identifier? |
| Trust | whether a trust handshake exists |
| Trust | request/response sequence |
| Trust | cryptography or simple approval |
| Trust | whether approval is interactive |
| Transport | TCP/UDP |
| Transport | fixed or dynamic port |
| Transport | HTTP/1.1 / HTTP/2 / custom framing |
| RPC | service name |
| RPC | method names |
| RPC | request/response encoding |
| RPC | streaming vs unary |
| Library | root library request |
| Library | hierarchy |
| Library | pagination |
| Library | search |
| Library | filters |
| Tracks | metadata |
| Tracks | artwork |
| Tracks | performance data |
| Track retrieval | returned location/URL/blob |
| File transfer | protocol |
| File transfer | ranges |
| File transfer | HEAD |
| File transfer | content-length |
| File transfer | content-type |
| File transfer | redirects |
| File transfer | retries |
| File transfer | connection reuse |
| Failure | expected errors |
| Network | interface selection |
| Lifecycle | behavior after Wi-Fi changes |

Do not fill this table from memory. Fill it from evidence.

---

# 5. Capture the actual Prime GO traffic whenever possible

A clean implementation is much easier if you know exactly what the controller sends.

The preferred investigation sequence is:

1. Put phone and Prime GO on the same LAN.
2. Start the normal Engine Remote Library / source-discovery workflow on the Prime GO.
3. Capture traffic around:
   - discovery;
   - source selection;
   - trust;
   - library browse;
   - track selection;
   - track transfer.
4. Save sanitized captures or textual observations in the repository.
5. Annotate packets by function.

Use whichever capture mechanism is actually available:

- Wireshark;
- tcpdump;
- router/AP capture;
- host capture;
- Android debugging capture;
- an intermediate proxy;
- a custom raw-socket logger.

Do not require root access if a non-root alternative exists.

If an actual packet capture is impossible, build a diagnostic implementation that can log:

- interface;
- local IP;
- remote IP;
- local port;
- remote port;
- transport;
- byte counts;
- first N bytes in hex;
- HTTP request line/headers;
- HTTP/2 pseudo-headers where visible;
- gRPC content type;
- protobuf message lengths;
- method/path;
- errors;
- timestamps.

Never log full music data unnecessarily.

---

# 6. Create a protocol probe / diagnostic capability early

Before you build the full application, create the smallest possible probe tools.

The repository may contain a `tools/` directory for:

- UDP discovery sender/receiver;
- UDP payload decoder;
- TCP port probe;
- HTTP probe;
- HTTP/2 probe;
- gRPC metadata probe;
- raw frame dumper;
- PCAP analysis helpers;
- packet fixture replayers.

The tools may be written in whichever language is most appropriate.

The key requirement is reproducibility.

A protocol finding should ideally be representable by:

1. a captured byte sequence;
2. a decoder;
3. an expected result;
4. a regression test.

---

# 7. Investigate discovery independently from the application

Do not begin by assuming a particular EAAS discovery packet.

Investigate all plausible mechanisms:

- UDP broadcast;
- UDP multicast;
- unicast;
- mDNS;
- DNS-SD;
- SSDP;
- static known-port connection;
- dynamic service advertisement;
- another Engine-specific beacon;
- combinations of these.

Test:

- 0.0.0.0;
- Wi-Fi interface address;
- subnet broadcast;
- directed broadcast if permitted;
- multicast addresses if discovered;
- multiple interfaces;
- IPv4/IPv6 where relevant.

Determine whether the Prime GO:

- periodically announces itself;
- periodically searches;
- sends a query only after opening Source;
- retries after a timeout;
- uses a fixed cadence;
- caches discovered services.

Measure actual timings.

Do not assume the response contains the information the server needs.

---

# 8. Investigate service establishment separately

Once discovery is understood, determine what the Prime GO actually does next.

Potential sequence:

discovery
-> service selection
-> TCP connection
-> handshake
-> trust
-> RPC
-> library
-> track retrieval

But this sequence is only an example.

Determine the real sequence from captures.

For every connection record:

- client/server direction;
- IP;
- port;
- connection lifetime;
- first payload;
- transport protocol;
- framing;
- TLS/no TLS;
- HTTP version;
- HTTP headers;
- HTTP/2 SETTINGS;
- stream identifiers;
- gRPC path;
- metadata;
- response status;
- close behavior.

---

# 9. Treat gRPC as an implementation option, not a requirement

If the actual protocol is gRPC, choose the most reliable Android implementation strategy.

Evaluate current gRPC-Java support and current Android constraints.

Relevant references include:

https://github.com/grpc/grpc-java

and current Android developer networking documentation.

Do not assume that a server-side gRPC implementation is equally mature on Android as client-side gRPC.

Possible solutions include:

- grpc-java with an Android-capable server transport;
- a custom HTTP/2 server;
- a minimal protobuf/RPC implementation;
- a native implementation;
- another proven Android server stack.

The winning choice must be based on:

- protocol fidelity;
- Android reliability;
- performance;
- maintainability;
- testability;
- size;
- ability to instrument.

Do not introduce TLS if the hardware protocol demonstrably uses cleartext.

Do not disable Android network security globally unless you can justify it.

---

# 10. Investigate the trust/pairing protocol independently

Do not assume:

- trust always exists;
- trust never exists;
- trust means a device approval dialog;
- trust means automatic approval;
- trust happens over the same connection as library RPC;
- trust is cryptographic;
- trust is stateless.

Determine the real behavior of the Prime GO.

Test:

1. first connection;
2. same server reconnect;
3. application restart;
4. phone IP change;
5. controller restart;
6. router restart;
7. trust refusal if possible;
8. trust retry;
9. repeated trust requests.

Record the exact request and response sequence.

Implement the minimum behavior needed to satisfy the target device.

---

# 11. Investigate the library API from the hardware outward

Do not invent a library model first.

Start with the requests the Prime GO sends.

Determine:

- what RPC/service it calls;
- what fields it provides;
- whether it asks for credentials;
- what root/library identifiers it expects;
- whether it expects playlists;
- whether it expects tracks at the root;
- whether it uses pagination;
- whether it expects server-side filters;
- whether it performs search;
- whether it asks for history;
- whether it expects an event stream;
- whether unsupported operations can be returned safely.

Build the smallest valid library tree that satisfies the real client.

Then add only what the hardware proves necessary.

---

# 12. Investigate track metadata carefully

Identify which fields the Prime GO actually consumes.

At minimum investigate:

- track ID;
- title;
- artist;
- album;
- genre;
- comment;
- label;
- composer;
- remixer;
- key;
- BPM;
- duration;
- year/date;
- rating;
- artwork;
- file size;
- location/blob information.

Do not assume every protobuf field must be populated.

For every field, determine:

- required?
- optional?
- ignored?
- causes failure if malformed?
- expected type?
- expected units?
- encoding?
- Unicode behavior?
- empty/default semantics?

Test with real files.

---

# 13. Investigate identifiers and Unicode normalization

This is a likely interoperability trap.

Investigate:

- case sensitivity;
- Unicode NFC/NFD;
- UTF-8 vs UTF-16;
- path encoding;
- URL escaping;
- protobuf string encoding;
- percent encoding;
- slash/backslash behavior;
- angle brackets or wrappers;
- maximum identifier length.

Use deliberately difficult fixtures:

- accented artist names;
- Cyrillic;
- emoji;
- apostrophes;
- ampersands;
- parentheses;
- multiple spaces;
- non-ASCII folder names.

Any normalization choice must be proven rather than copied from an unrelated implementation.

---

# 14. Investigate track retrieval / file delivery independently

The final user-visible behavior depends on the controller successfully receiving the original file.

Determine:

- direct HTTP URL vs special path syntax;
- whether URL is returned by an RPC;
- whether hostname or IP is embedded;
- whether a special `<...>` location format is used;
- whether a second protocol is used;
- whether HTTP/1.1 is used;
- required HTTP methods;
- request headers;
- response headers;
- byte ranges;
- HEAD;
- retries;
- partial reads;
- connection reuse;
- seek behavior;
- resume behavior;
- content length;
- content type;
- whether redirects are accepted;
- whether chunked transfer is accepted;
- whether the device requires a specific path grammar.

Do not assume a continuous audio stream.

Determine empirically whether the controller downloads a file and then plays it locally, or does something else.

---

# 15. Make the HTTP/file server secure even on a LAN

Never implement a naive path passthrough.

Do not accept arbitrary filesystem paths from the controller.

Prefer:

controller-visible opaque track ID
-> server-side validated mapping
-> Android content/file URI
-> bounded file read.

Protect against:

- ../ traversal;
- absolute path injection;
- Windows path injection;
- encoded traversal;
- symlink escapes;
- arbitrary file disclosure;
- content URI confusion;
- race conditions when files are deleted.

The server must expose only media explicitly indexed by the application.

---

# 16. Android storage strategy must be evidence-driven

The app must be able to expose music already on the phone.

Investigate current Android behavior for:

- MediaStore.Audio;
- READ_MEDIA_AUDIO;
- legacy storage permissions;
- SAF;
- content URIs;
- RELATIVE_PATH;
- openFileDescriptor/openInputStream;
- scoped storage;
- removable storage;
- USB/OTG media if relevant;
- files inaccessible to MediaStore.

Current Android behavior changes over time. Verify against current Android documentation and current target SDK requirements.

Do not unnecessarily copy user audio files.

Prefer streaming original bytes from their actual storage location.

---

# 17. Android network/interface handling is a first-class problem

Do not assume the default network is the correct network.

The Android app must understand:

- Wi-Fi;
- cellular;
- VPN;
- multiple active networks;
- hotspot;
- mesh;
- captive portal states;
- AP isolation;
- private/guest networks;
- IPv4;
- IPv6.

Investigate current Android `ConnectivityManager` / `Network` APIs.

Useful official references:

https://developer.android.com/reference/android/net/ConnectivityManager

https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback

https://developer.android.com/reference/android/net/Network

The server should be able to determine and report:

- active Wi-Fi network;
- local IPv4 addresses;
- interface names;
- subnet;
- broadcast address where applicable;
- whether the interface is currently usable for the controller connection.

Where appropriate, bind sockets to the correct Android `Network` or interface rather than relying blindly on global process routing.

---

# 18. Android background execution and service lifetime

The server must remain useful while the phone is:

- screen-locked;
- idle;
- charging;
- left on a table during a DJ set.

Investigate current Android requirements for long-running foreground services.

Current Android documentation notes meaningful changes around foreground service types and Android 15+ behavior. Verify the requirements for the Android version/target SDK actually used by this project instead of copying an old manifest.

Reference:

https://developer.android.com/about/versions/15/behavior-changes-15

https://developer.android.com/about/versions/14/changes/fgs-types-required

Do not choose a foreground-service type merely because the application handles music files. The service's actual job is local server/network availability; determine the correct modern Android approach.

The server lifecycle must be explicit:

START
-> network/server initialization
-> discovery active
-> RPC/file services active
-> network change handling
-> graceful stop.

---

# 19. Design the smallest useful UI

Do not spend time building a decorative DJ application.

The phone application is a server.

A useful first UI should make the server state obvious.

It should show, at minimum:

- Server: ON/OFF
- local IP address(es)
- network interface in use
- discovery: ON/OFF
- advertised/listening service(s)
- ports actually bound
- number of indexed tracks
- last successful client contact
- last discovery packet received
- last discovery response sent
- last trust request
- last library request
- last track request
- last file transfer
- bytes served
- current error
- diagnostic log access.

A user must be able to answer:

“Is the server alive?”
“What address is it using?”
“Is the discovery mechanism working?”
“Did the Prime GO contact it?”
“Where did the connection stop?”

without reading logcat.

This is essential.

---

# 20. Add a diagnostics screen before adding polish

The diagnostics screen is part of the engineering system.

Provide:

## Network
- interface;
- IPv4;
- subnet;
- gateway where available;
- Wi-Fi SSID only if permitted/necessary;
- VPN presence if detectable;
- connectivity state.

## Services
- every active listener;
- transport;
- address;
- port;
- protocol;
- start time;
- restart count.

## Discovery
- packet RX count;
- packet TX count;
- last RX timestamp;
- last TX timestamp;
- source/destination;
- payload length;
- hex preview;
- decoded fields.

## Session
- client IP;
- connections opened;
- trust messages;
- RPC count;
- RPC method names;
- last errors.

## Library
- indexed tracks;
- supported tracks;
- unreadable tracks;
- duplicate IDs.

## File server
- requests;
- bytes served;
- range requests;
- 404;
- 416;
- 500;
- open-file failures.

Do not flood the UI with unbounded logs. Use a bounded ring buffer.

Provide an easy “copy diagnostic report” action.

---

# 21. Build library indexing as a separate concern

Do not couple:

- Android UI;
- networking;
- protocol serialization;
- media scanning.

Use clear interfaces, even if the project is small.

The internal design should allow:

Media catalog
-> protocol adapter
-> transport service
-> client

The implementation language and exact package boundaries are up to you.

The catalog should be able to:

- enumerate music;
- expose stable internal IDs;
- return metadata;
- open an audio stream/file descriptor;
- report size;
- optionally return artwork;
- survive media changes.

Do not add a database unless evidence shows one is needed.

Do not add a database merely because databases are common.

---

# 22. Start with a deliberately tiny media set

Before testing a huge library, make a fixture library containing:

- one MP3 CBR;
- one MP3 VBR;
- one WAV;
- one FLAC;
- optionally one M4A/AAC if the target accepts it;
- one file with embedded artwork;
- one Cyrillic filename;
- one long filename;
- one malformed/unreadable audio file.

The first success target is one track loaded by Prime GO.

Then grow.

---

# 23. Protocol fixtures and replay tests

For every proven exchange, create a deterministic fixture where practical.

Examples:

- discovery request bytes;
- discovery response bytes;
- trust request;
- trust response;
- library request;
- library response;
- get-track request;
- get-track response;
- file GET request;
- range GET request.

Build replay tests that do not require the hardware for every run.

Hardware remains the final validation, but protocol fixtures prevent regressions.

---

# 24. Test the network failure cases intentionally

Test:

1. phone and Prime GO on same normal Wi-Fi;
2. Wi-Fi reconnect;
3. phone IP changes;
4. phone screen lock;
5. phone unlock;
6. app background;
7. app foreground;
8. router restart;
9. controller restart;
10. app restart;
11. network temporarily unavailable;
12. VPN enabled;
13. guest network/AP isolation if possible;
14. multiple interfaces;
15. no media permission;
16. source file deleted between index and load;
17. source file renamed;
18. source file becomes inaccessible;
19. large file;
20. Unicode metadata.

For each failure capture:

- observed controller behavior;
- app behavior;
- logs;
- packet evidence;
- whether recovery is automatic.

---

# 25. Performance goals

Do not optimize prematurely.

First establish correctness.

Then measure:

- discovery latency;
- source appearance latency;
- library browse latency;
- track lookup latency;
- time to first file byte;
- sustained transfer rate;
- RAM usage;
- CPU;
- phone temperature;
- battery impact.

Avoid creating copies of large audio files.

Prefer:

zero-copy or low-copy mechanisms where practical
+
streaming reads
+
bounded memory.

But do not sacrifice reliability merely to reduce a copy.

---

# 26. Binary size matters later, not before interoperability

This application should remain small.

However:

FIRST:
- discover;
- connect;
- trust;
- browse;
- load;
- play.

THEN:
- remove dead dependencies;
- shrink native artifacts;
- remove unnecessary resources;
- configure release packaging.

Do not perform dangerous optimization work before the physical end-to-end path is proven.

---

# 27. Security model

The intended deployment is a local LAN.

Do not add:

- cloud accounts;
- analytics;
- remote Internet control;
- public service exposure;
- unnecessary telemetry.

The application should not silently open services on cellular data or a VPN if that would make the server unexpectedly reachable.

Make the network exposure deliberate.

Any protocol compatibility behavior that requires insecure cleartext LAN communication should remain confined to the intended local interfaces.

---

# 28. Do not overbuild the product

For the first working milestone, avoid:

- DJ playback UI on the phone;
- waveform rendering;
- stems;
- beatgrid generation;
- audio transcoding;
- BPM analysis unless required by protocol;
- cloud sync;
- account system;
- remote uploads;
- metadata editing;
- playlist management beyond what the target client actually asks for;
- custom audio engine.

Those may become later experiments, but they are not prerequisites for the server.

---

# 29. Search for exact Prime GO evidence before declaring a solution

A reference server being successful on:

- Prime 4+;
- SC6000;
- SC Live;
- Engine Desktop;

does not prove compatibility with the Prime GO.

Likewise:

a protocol working on an SC6000 running Engine OS 5.0.4
does not prove it works on the Prime GO.

The prompt deliberately gives you freedom to discover that the correct Prime GO path may be:

- the classic EAAS path;
- a newer EAAS variant;
- an Engine Remote Library service;
- a service on a dynamic port;
- an HTTP-oriented mechanism;
- an RPC stack with different service exposure;
- or something not yet documented publicly.

Your job is to find out.

---

# 30. Important known historical context — use as hypotheses, not truth

The older `MiguelDuval/MimicDJ` project exists:

https://github.com/MiguelDuval/MimicDJ

It was an earlier implementation attempt for the same general goal.

You should inspect it.

But you must treat the following as historical hypotheses, not requirements:

- UDP 11224 discovery;
- gRPC 50010 for Engine Library;
- HTTP 50020 for file delivery;
- a particular EAAS response structure;
- a particular track URL format;
- automatic trust;
- MediaStore-only implementation;
- any exact protobuf;
- any exact folder playlist hierarchy.

Use that repository to understand what was tried, what assumptions were made, and where it may have failed.

Do not copy its architecture merely because it already exists.

---

# 31. Make source-code archaeology part of the investigation

When inspecting external implementations:

1. identify protocol constants;
2. identify service definitions;
3. identify packet writers/readers;
4. identify tests;
5. identify device-specific branches;
6. identify version-specific branches;
7. identify TODOs;
8. identify open issues;
9. identify known incompatibilities;
10. identify assumptions that are not backed by captures.

Search commit history when useful.

A source file containing a magic port number is not proof of what modern hardware currently does.

A passing unit test is proof only of the test's fixture assumptions unless the test is tied to a live capture.

---

# 32. Agent workflow

Work in phases.

## Phase A — Research

Deliver:

- research ledger;
- source inventory;
- hypothesis matrix;
- target device profile;
- proposed capture plan;
- list of unknowns.

Do not spend the entire project in theory. Research should directly inform experiments.

## Phase B — Probe

Deliver:

- packet/protocol probes;
- diagnostics;
- minimal network listeners;
- reproducible fixtures where possible.

Goal: answer the highest-value unknowns quickly.

## Phase C — Minimal server

Implement only enough to answer:

Can Prime GO discover and establish a usable session?

Do not implement a giant fake library yet.

## Phase D — Library

Implement the smallest library model that matches actual client requests.

## Phase E — One-track load

Get exactly one real local music file onto the Prime GO.

This is a major milestone.

## Phase F — Reliability

Add:

- indexing robustness;
- browse/search;
- multiple tracks;
- artwork if actually required;
- Wi-Fi changes;
- lifecycle stability;
- lock-screen behavior.

## Phase G — Packaging

Produce:

- reproducible build;
- debug APK;
- release APK/AAB if configured;
- checksums;
- test report;
- hardware validation notes.

---

# 33. Git discipline

Repository:

`MiguelDuval/2MIMICDJ2`

Primary branch:

`agent/clean-slate`

Rules:

- Never work on `main`.
- Do not force-push.
- Do not rewrite history.
- Make small commits.
- Push stable milestones.
- Let GitHub Actions run after every push.
- If a regression is introduced, identify the responsible commit and revert or repair before adding more complexity.
- Preserve known-working states.

Recommended commit style:

`research: ...`
`probe: ...`
`feat: ...`
`fix: ...`
`test: ...`
`docs: ...`
`ci: ...`

Each commit message should communicate intent.

---

# 34. CI requirements

The repository already contains a GitHub Actions workflow:

`.github/workflows/build.yml`

It is designed to run automatically on every push outside `main`, on pull requests to `main`, and manually.

Use it.

If your chosen implementation is Android/Gradle, create a real Gradle wrapper and make the workflow build the actual APK.

Do not disable the workflow.

The CI pipeline should ultimately produce:

- tests;
- lint/static analysis where applicable;
- debug APK;
- optionally release APK/AAB;
- checksums;
- useful build reports.

If the architecture does not use Gradle, adapt the repository build so that the existing workflow can detect and build it, or minimally modify the workflow while preserving automatic push builds.

CI is part of the product.

---

# 35. Artifact policy

Every successful Android build should make the APK downloadable from GitHub Actions.

The artifact should have a clear name containing the commit SHA.

Also generate:

- file size;
- SHA-256;
- build variant;
- git commit;
- app version.

Do not require any paid GitHub service.

---

# 36. Documentation requirements

At minimum maintain:

`docs/research/README.md`
- source inventory and research status.

`docs/research/ledger.md`
- evidence ledger.

`docs/protocol/README.md`
- the protocol as currently understood.

`docs/protocol/fixtures/`
- sanitized binary/text fixtures where copyright/security considerations permit.

`docs/testing/README.md`
- hardware test procedure and results.

`docs/architecture.md`
- current architecture AFTER the evidence establishes it.

Do not write the architecture document first and then force the evidence to fit it.

---

# 37. What counts as a genuine MVP success

The MVP is not:

- compiling;
- showing a nice UI;
- opening a port;
- replying to a UDP packet;
- implementing a known protobuf;
- matching a GitHub README.

The MVP is:

1. the actual Prime GO discovers or reaches the application;
2. the actual Prime GO completes whatever session/trust process is required;
3. the actual Prime GO can browse at least a minimal real library;
4. the actual Prime GO can request a real track;
5. the application serves the original audio bytes correctly;
6. the Prime GO loads the track;
7. the Prime GO plays it through its normal deck;
8. the sequence can be repeated without the server becoming unusable.

One-track playback from a real phone file is a much stronger milestone than a sophisticated mock library.

---

# 38. Definition of “done enough to continue”

Before moving from one protocol layer to the next, require one of:

- a packet capture;
- a reproducible hardware observation;
- a reference implementation reproduction;
- a source-code proof plus a compatible test fixture.

For unresolved questions, write:

UNKNOWN — NEEDS EVIDENCE

Do not convert unknowns into comments such as “probably”.

---

# 39. Failure discipline

When something does not work:

DO NOT immediately add another guessed protocol variant.

Instead:

1. capture the failure;
2. identify exactly where the sequence diverged;
3. compare expected vs observed bytes;
4. determine which hypothesis failed;
5. design the smallest experiment that distinguishes between remaining hypotheses;
6. run it;
7. update the evidence ledger;
8. then modify code.

A failed build is not a reason to redesign the network protocol.

A controller “Error” message is not enough evidence by itself.

---

# 40. Hardware test protocol

When a build is ready for physical testing, provide the human with exactly one coherent test sequence.

Example form:

TEST X — discovery only

1. Install build N.
2. Put phone and Prime GO on same Wi-Fi.
3. Launch app.
4. Read diagnostics value A.
5. On Prime GO do action B.
6. Observe value C.
7. Report result D.

Avoid giving ten unrelated tests at once.

Once one layer is proven, proceed to the next layer.

---

# 41. First implementation milestone

Your FIRST software milestone should not be a complete polished application.

It should be a research-capable executable that can:

- start;
- identify the active network;
- display its IP;
- bind a test listener;
- log incoming LAN traffic;
- expose diagnostics;
- survive screen lock;
- build reproducibly;
- produce an APK artifact.

Then perform the protocol investigation.

This makes the next experiments much faster.

---

# 42. Architectural freedom clause

You are explicitly encouraged to abandon an approach when evidence says it is wrong.

You may:

- delete a protocol implementation;
- replace a networking library;
- switch from gRPC to custom HTTP/2;
- switch from Kotlin networking to a native component;
- replace MediaStore scanning;
- introduce or remove a database;
- change discovery implementation;
- change service boundaries;
- add a small native library;
- add a packet-analysis utility.

The only constraint is that the resulting system remains:

- understandable;
- reproducible;
- testable;
- maintainable;
- appropriately small;
- demonstrably compatible with the target.

Do not preserve a bad design for consistency.

---

# 43. Absolute anti-assumption rule

At the start of the task, write down the assumptions you are tempted to make.

Examples:

- “Discovery is UDP 11224.”
- “Library is gRPC.”
- “Port 50010 is the library.”
- “Port 50020 serves files.”
- “Trust is automatic.”
- “Track locations look like Windows paths.”
- “MediaStore is enough.”
- “The controller downloads before playback.”

Then mark each:

UNVERIFIED.

Your job is to turn them into:

CONFIRMED
or
REFUTED.

This is the central research objective.

---

# 44. Final deliverables

By the end of a meaningful development cycle, the repository should contain:

1. working source code;
2. reproducible build;
3. automatic GitHub Actions build;
4. downloadable APK artifact;
5. diagnostic UI;
6. protocol documentation based on evidence;
7. hardware test procedure;
8. research ledger;
9. clear statement of what is proven;
10. clear statement of what remains unknown.

The repository should be understandable by another engineer who did not participate in the investigation.

---

# 45. Final instruction to the agent

Do not begin by coding the old solution.

Begin by answering:

WHAT DOES THIS ACTUAL PRIME GO DO?

Then build the smallest server that satisfies that behavior.

Use the Internet, source repositories, protocol implementations, reverse-engineering material, packet analysis, local experiments, and the target hardware as complementary evidence.

Be creative in implementation, conservative in claims, aggressive in measurement, and ruthless about distinguishing what is known from what is guessed.

The goal is not to reproduce somebody else's server.

The goal is to discover the shortest reliable path from an Android phone containing a music file to a real Prime GO successfully loading and playing that file.

Start now.
