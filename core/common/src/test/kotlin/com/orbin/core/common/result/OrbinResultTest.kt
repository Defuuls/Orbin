package com.orbin.core.common.result

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OrbinResultTest {
    @Test
    fun `map transforms success value`() {
        val result = 21.asSuccess().map { it * 2 }
        assertThat(result).isEqualTo(OrbinResult.Success(42))
    }

    @Test
    fun `map preserves failure`() {
        val failure: OrbinResult<Int> = OrbinResult.Failure(DataError.Offline())
        val mapped = failure.map { it * 2 }
        assertThat(mapped).isSameInstanceAs(failure)
    }

    @Test
    fun `fold branches on case`() {
        val success: OrbinResult<Int> = 5.asSuccess()
        val failure: OrbinResult<Int> = OrbinResult.Failure(DataError.Timeout())

        assertThat(success.fold(onSuccess = { "ok" }, onFailure = { "err" })).isEqualTo("ok")
        assertThat(failure.fold(onSuccess = { "ok" }, onFailure = { "err" })).isEqualTo("err")
    }

    @Test
    fun `onSuccess and onFailure invoke the matching side only`() {
        var successHits = 0
        var failureHits = 0

        5
            .asSuccess()
            .onSuccess { successHits++ }
            .onFailure { failureHits++ }

        assertThat(successHits).isEqualTo(1)
        assertThat(failureHits).isEqualTo(0)
    }
}
