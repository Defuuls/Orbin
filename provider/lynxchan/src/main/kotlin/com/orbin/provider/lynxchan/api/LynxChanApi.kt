package com.orbin.provider.lynxchan.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service for the LynxChan JSON API. The base URL is supplied per provider instance
 * (see [com.orbin.provider.lynxchan.LynxChanSite]); paths here are relative to it.
 */
interface LynxChanApi {
    /** Site-wide board list. */
    @GET("boards.js")
    suspend fun boards(
        @Query("json") json: Int = 1,
    ): LynxChanBoardsResponse

    /** Full catalog for a board, as a flat array of thread previews. */
    @GET("{board}/catalog.json")
    suspend fun catalog(
        @Path("board") board: String,
    ): List<LynxChanCatalogThread>

    /** A single thread by its OP post number. */
    @GET("{board}/res/{threadId}.json")
    suspend fun thread(
        @Path("board") board: String,
        @Path("threadId") threadId: Long,
    ): LynxChanThreadResponse
}
