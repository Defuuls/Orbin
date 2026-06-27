package com.orbin.provider.vichan.api

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit service for the vichan/4chan JSON API. The base URL is supplied per provider instance
 * (see [com.orbin.provider.vichan.VichanSite]); paths here are relative to it.
 */
interface VichanApi {

    /** Site-wide board list. Not all engines expose this; callers handle a 404 gracefully. */
    @GET("boards.json")
    suspend fun boards(): VichanBoardsResponse

    /** Full catalog (all pages) for a board. */
    @GET("{board}/catalog.json")
    suspend fun catalog(@Path("board") board: String): List<VichanCatalogPage>

    /** A single thread by its OP number. */
    @GET("{board}/thread/{no}.json")
    suspend fun thread(
        @Path("board") board: String,
        @Path("no") no: Long,
    ): VichanThreadResponse
}
