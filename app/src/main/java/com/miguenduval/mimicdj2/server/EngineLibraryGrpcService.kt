package com.miguenduval.mimicdj2.server

import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.EngineLibraryServiceGrpc
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.EventStreamRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.EventStreamResponse
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetCredentialsRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetCredentialsResponse
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetHistoryPlayedTracksRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetHistoryPlayedTracksResponse
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetHistorySessionsRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetHistorySessionsResponse
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetLibrariesRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetLibrariesResponse
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetLibraryRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetLibraryResponse
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetSearchFiltersRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetSearchFiltersResponse
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetTrackRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetTrackResponse
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetTracksRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.GetTracksResponse
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.Library
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.LibraryLogo
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.ListTrack
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.ListType
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.PlaylistMetadata
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.PutEventsRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.PutEventsResponse
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.SearchTracksRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.SearchTracksResponse
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.TrackBlob
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.TrackBlobUrl
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.TrackMetadata
import io.grpc.stub.StreamObserver

class EngineLibraryGrpcService(
    private val library: MediaLibrary,
    private val serverHost: () -> String
) : EngineLibraryServiceGrpc.EngineLibraryServiceImplBase() {

    companion object {
        const val LIBRARY_ID = "mimicdj2"
        const val PLAYLIST_ID = "mimicdj2-all"
    }

    override fun getLibraries(
        request: GetLibrariesRequest,
        responseObserver: StreamObserver<GetLibrariesResponse>
    ) {
        responseObserver.onNext(
            GetLibrariesResponse.newBuilder()
                .addLibraries(
                    Library.newBuilder()
                        .setId(LIBRARY_ID)
                        .setTitle("Mimic DJ")
                        .setLogo(LibraryLogo.LIBRARY_LOGO_ENGINE)
                        .build()
                )
                .build()
        )
        responseObserver.onCompleted()
    }

    override fun getLibrary(
        request: GetLibraryRequest,
        responseObserver: StreamObserver<GetLibraryResponse>
    ) {
        val tracks = library.snapshot()
        responseObserver.onNext(
            GetLibraryResponse.newBuilder()
                .addPlaylists(
                    PlaylistMetadata.newBuilder()
                        .setId(PLAYLIST_ID)
                        .setTitle("All Tracks")
                        .setTrackCount(tracks.size)
                        .setListType(ListType.LIST_TYPE_PLAY)
                        .build()
                )
                .build()
        )
        responseObserver.onCompleted()
    }

    override fun getTracks(
        request: GetTracksRequest,
        responseObserver: StreamObserver<GetTracksResponse>
    ) {
        val tracks = library.snapshot()
        val filtered = if (request.hasPlaylistId() && request.playlistId != PLAYLIST_ID) {
            emptyList()
        } else {
            tracks
        }
        val pageSize = if (request.hasPageSize && request.pageSize > 0) request.pageSize else filtered.size
        val limited = filtered.take(pageSize)

        val response = GetTracksResponse.newBuilder()
        limited.forEach { track ->
            response.addTracks(
                ListTrack.newBuilder()
                    .setMetadata(toMetadata(track))
                    .build()
            )
        }

        responseObserver.onNext(response.build())
        responseObserver.onCompleted()
    }

    override fun searchTracks(
        request: SearchTracksRequest,
        responseObserver: StreamObserver<SearchTracksResponse>
    ) {
        val query = request.query.trim()
        val matches = if (query.isEmpty()) {
            library.snapshot()
        } else {
            val needle = query.lowercase()
            library.snapshot().filter {
                sequenceOf(it.title, it.artist, it.album, it.displayName, it.pathKey)
                    .any { value -> value.lowercase().contains(needle) }
            }
        }

        val response = SearchTracksResponse.newBuilder()
        matches.forEach { response.addTracks(ListTrack.newBuilder().setMetadata(toMetadata(it)).build()) }
        responseObserver.onNext(response.build())
        responseObserver.onCompleted()
    }

    override fun getSearchFilters(
        request: GetSearchFiltersRequest,
        responseObserver: StreamObserver<GetSearchFiltersResponse>
    ) {
        responseObserver.onNext(GetSearchFiltersResponse.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun getTrack(
        request: GetTrackRequest,
        responseObserver: StreamObserver<GetTrackResponse>
    ) {
        val track = library.findById(request.trackId)
        if (track == null) {
            responseObserver.onNext(GetTrackResponse.getDefaultInstance())
            responseObserver.onCompleted()
            return
        }

        val host = serverHost().ifBlank { "0.0.0.0" }
        val url = track.httpUrl(host)

        val blob = TrackBlob.newBuilder()
            .setUrl(
                TrackBlobUrl.newBuilder()
                    .setUrl(url)
                    .setFileSize(track.sizeBytes.coerceAtMost(0xFFFF_FFFFL).toInt())
                    .build()
            )
            .build()

        responseObserver.onNext(
            GetTrackResponse.newBuilder()
                .setBlob(blob)
                .setMetadata(toMetadata(track))
                .build()
        )
        responseObserver.onCompleted()
    }

    override fun getHistoryPlayedTracks(
        request: GetHistoryPlayedTracksRequest,
        responseObserver: StreamObserver<GetHistoryPlayedTracksResponse>
    ) {
        responseObserver.onNext(GetHistoryPlayedTracksResponse.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun getHistorySessions(
        request: GetHistorySessionsRequest,
        responseObserver: StreamObserver<GetHistorySessionsResponse>
    ) {
        responseObserver.onNext(GetHistorySessionsResponse.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun eventStream(
        request: EventStreamRequest,
        responseObserver: StreamObserver<EventStreamResponse>
    ) {
        responseObserver.onNext(EventStreamResponse.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun putEvents(
        request: PutEventsRequest,
        responseObserver: StreamObserver<PutEventsResponse>
    ) {
        responseObserver.onNext(PutEventsResponse.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun getCredentials(
        request: GetCredentialsRequest,
        responseObserver: StreamObserver<GetCredentialsResponse>
    ) {
        responseObserver.onNext(GetCredentialsResponse.getDefaultInstance())
        responseObserver.onCompleted()
    }

    private fun toMetadata(track: MediaLibrary.Track): TrackMetadata =
        TrackMetadata.newBuilder()
            .setId(track.id)
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbum(track.album)
            .setLengthSeconds((track.durationMs / 1000L).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .setYear(track.year.coerceAtLeast(0))
            .setDateAdded(
                com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(track.dateAddedSeconds)
                    .build()
            )
            .build()
}
