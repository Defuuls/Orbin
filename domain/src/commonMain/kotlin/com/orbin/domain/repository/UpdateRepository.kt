package com.orbin.domain.repository

import com.orbin.core.common.result.OrbinResult
import com.orbin.core.model.UpdateStatus

/** Checks GitHub Releases for a build newer than the one currently running. */
interface UpdateRepository {
    /**
     * @param currentVersionName the running build's `versionName`, e.g. `61-Achernar`.
     */
    suspend fun checkForUpdate(currentVersionName: String): OrbinResult<UpdateStatus>
}
