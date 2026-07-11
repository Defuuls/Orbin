package com.orbin.data.links

import com.orbin.core.model.LinkStatus
import com.orbin.domain.repository.LinkVerificationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compatibility implementation retained only while callers migrate away from link verification.
 * Orbin no longer performs network requests to validate third-party file-host links.
 */
@Singleton
class LinkVerificationRepositoryImpl
    @Inject
    constructor() : LinkVerificationRepository {
        override suspend fun verify(url: String): LinkStatus = LinkStatus.UNKNOWN
    }
