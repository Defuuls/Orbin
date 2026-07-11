package com.orbin.core.model

/**
 * Legacy display state retained temporarily for compatibility with existing comment rendering.
 * Link verification is no longer performed, so production code only returns [UNKNOWN].
 */
enum class LinkStatus {
    UNKNOWN,
    VALID,
    INVALID,
}
