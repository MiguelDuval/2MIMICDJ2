# Denon Engine Remote Library capture — 2026-09-29

## Scope

Direct Wireshark packet-list capture of Engine DJ Desktop Remote Library traffic between:

- Prime GO: 10.93.187.191
- Windows / Engine DJ Desktop: 10.93.187.49

The capture starts before track loading and includes one actual track load.

## Observed sequence

At approximately 11.03 s:

1. Prime GO opens TCP/50010 to the Engine Desktop host.
2. Cleartext HTTP/2/gRPC session is established.
3. Engine Library calls observed:
   - `EventStream`
   - `GetLibrary`
   - `GetTracks`
   - repeated `GetLibrary` / `GetTracks`
4. At 37.58 s Prime GO calls:
   - `POST /enginelibrary.v1.EngineLibraryService/GetTrack`
5. Approximately 0.31 s later Prime GO opens a **new TCP connection to port 50020**.
6. Prime GO sends:
   `GET /download/%3CC%3A%2FUsers%2Fmigue%2FDownloads%2F%D0%9F%D0%BE%D0%B4%D0%BE%D0%B6%D0%B4%D1%91%D0%BC.flac%3E HTTP/1.1`
7. Engine host replies with HTTP `200 OK`.
8. The 50020 connection then carries a large continuous response stream; the capture reaches sequence numbers above 41.5 million bytes before the capture ends.

## Evidence class

A — direct target-hardware observation through packet capture.

## Consequences

- TCP/50010 is definitely used by this Prime GO Remote Library session for cleartext HTTP/2/gRPC Engine Library traffic.
- `GetTrack` is definitely part of the track-load path.
- TCP/50020 is definitely used for the actual audio download request.
- The HTTP download path is based on the track's file-location/key representation and is URL-encoded.
- The transfer is not performed on the high ports scanned by the earlier Mimic DJ diagnostic; 50020 is the observed file-transfer port in this session.
- A compatible Android server therefore needs, at minimum:
  1. a non-empty track inventory returned by `GetTracks`;
  2. a valid `GetTrackResponse` containing the track blob/location expected by Prime GO;
  3. an HTTP/1.1 `/download/<encoded-key>` endpoint on TCP/50020 that streams the selected local file;
  4. correct `Content-Length` / connection handling suitable for long binary transfers.

## Current implementation gap

On `agent/clean-slate` at the time of this capture:

- `EngineLibraryGrpcService.getTracks()` returns an empty/default response.
- `EngineLibraryGrpcService.getTrack()` returns an empty/default response.
- Native HTTP/50020 handles only `GET /ping`; other paths return 404.

Thus the remaining work is now sharply defined as **library inventory -> GetTrack blob URL -> HTTP 50020 file stream**, rather than further port guessing.
