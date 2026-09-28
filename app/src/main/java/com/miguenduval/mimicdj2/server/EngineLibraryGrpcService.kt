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
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.ListType
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.PlaylistMetadata
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.PutEventsRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.PutEventsResponse
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.SearchTracksRequest
import com.miguenduval.mimicdj2.eaas.enginelibrary.v1.SearchTracksResponse
import io.grpc.stub.StreamObserver

class EngineLibraryGrpcService : EngineLibraryServiceGrpc.EngineLibraryServiceImplBase() {
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
        responseObserver.onNext(
            GetLibraryResponse.newBuilder()
                .addPlaylists(
                    PlaylistMetadata.newBuilder()
                        .setId(PLAYLIST_ID)
                        .setTitle("All Tracks")
                        .setTrackCount(0)
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
        responseObserver.onNext(GetTracksResponse.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun searchTracks(
        request: SearchTracksRequest,
        responseObserver: StreamObserver<SearchTracksResponse>
    ) {
        responseObserver.onNext(SearchTracksResponse.getDefaultInstance())
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
        responseObserver.onNext(GetTrackResponse.getDefaultInstance())
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
}
