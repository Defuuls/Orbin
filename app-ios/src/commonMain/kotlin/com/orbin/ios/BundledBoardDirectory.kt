package com.orbin.ios

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Post
import com.orbin.core.model.PostComment
import com.orbin.core.model.PostId
import com.orbin.core.model.PostNode
import com.orbin.core.model.SearchQuery
import com.orbin.core.model.SearchResult
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.ThreadKey
import com.orbin.core.model.ThreadStats
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The test-only switch the native UI tests launch with (`iosApp/OrbinUITests`). When it is set,
 * the board directory, catalogs and threads come from [BUNDLED_BOARD_DIRECTORY_JSON] instead of
 * the sites, so screens can be captured without the network. Release builds never read it, and
 * nothing changes when it is absent.
 */
internal object UiTestLaunchFlags {
    /** Passed as `-OrbinUITestBundledDirectory YES`; a value keeps UIKit's argument pairs intact. */
    const val BUNDLED_DIRECTORY_ARGUMENT = "-OrbinUITestBundledDirectory"

    /** The same switch as an environment variable set to `1`. */
    const val BUNDLED_DIRECTORY_ENVIRONMENT = "ORBIN_UI_TEST_BUNDLED_DIRECTORY"

    fun bundledDirectoryRequested(
        arguments: List<String>,
        environment: Map<String, String>,
    ): Boolean = BUNDLED_DIRECTORY_ARGUMENT in arguments || environment[BUNDLED_DIRECTORY_ENVIRONMENT] == "1"
}

/**
 * The sites the app reads from: the live providers, or, under the UI-test flag, each live provider
 * that the fixture covers answered from the fixture. Sites the fixture does not cover are left out
 * so no request reaches the network.
 */
internal fun boardSources(
    live: List<ImageBoardProvider>,
    bundledDirectory: Boolean,
    fixture: BundledBoardDirectory = BundledBoardDirectory.default,
): List<ImageBoardProvider> =
    if (bundledDirectory) {
        live.mapNotNull { provider ->
            fixture.sites[provider.metadata.id.value]?.let { site -> BundledDirectoryProvider(provider, site) }
        }
    } else {
        live
    }

/** A site answered entirely from the fixture; only its identity comes from the live provider. */
internal class BundledDirectoryProvider(
    live: ImageBoardProvider,
    private val site: BundledSite,
) : ImageBoardProvider {
    override val metadata: ProviderMetadata = live.metadata
    override val capabilities: ProviderCapabilities = live.capabilities

    override suspend fun getBoards(): List<Board> = site.boards.map { it.toBoard() }

    override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> =
        site.threads
            .filter { it.board == request.board.value }
            .map { thread ->
                CatalogThread(
                    key = thread.key(),
                    originalPost = thread.originalPost(),
                    stats = thread.stats(),
                    previewReplies = thread.replies.map { thread.reply(it) }.toImmutableList(),
                )
            }

    override suspend fun getThread(
        board: BoardId,
        thread: ThreadId,
    ): Thread {
        val bundled =
            site.threads.firstOrNull { it.board == board.value && it.number == thread.value }
                ?: throw ProviderException.NotFound("No bundled thread /${board.value}/${thread.value}")
        return Thread(
            key = bundled.key(),
            originalPost = bundled.originalPost(),
            replies = bundled.replies.map { bundled.reply(it) }.toImmutableList(),
            stats = bundled.stats(),
        )
    }

    override suspend fun search(query: SearchQuery): List<SearchResult> = throw ProviderException.Unsupported("search")

    private fun BundledThread.key() = ThreadKey(metadata.id, BoardId(board), ThreadId(number))

    private fun BundledThread.originalPost() =
        Post(
            id = PostId(number),
            board = BoardId(board),
            threadId = ThreadId(number),
            isOriginalPost = true,
            subject = subject,
            comment = textComment(comment),
            createdAtMillis = postedAtMillis,
        )

    private fun BundledThread.reply(post: BundledPost) =
        Post(
            id = PostId(post.number),
            board = BoardId(board),
            threadId = ThreadId(number),
            isOriginalPost = false,
            comment = textComment(post.comment),
            createdAtMillis = post.postedAtMillis,
        )

    private fun BundledThread.stats() =
        ThreadStats(
            replyCount = replies.size,
            uniquePosterCount = replies.size + 1,
            lastModifiedMillis = replies.maxOfOrNull { it.postedAtMillis } ?: postedAtMillis,
        )

    private fun textComment(text: String) = PostComment(raw = text, nodes = persistentListOf(PostNode.Text(text)))
}

/** The fixture: board metadata and a few text-only threads per site, keyed by provider id. */
@Serializable
internal data class BundledBoardDirectory(
    val sites: Map<String, BundledSite>,
) {
    companion object {
        val default: BundledBoardDirectory by lazy { Json.decodeFromString(serializer(), BUNDLED_BOARD_DIRECTORY_JSON) }
    }
}

@Serializable
internal data class BundledSite(
    val boards: List<BundledBoard>,
    val threads: List<BundledThread> = emptyList(),
)

@Serializable
internal data class BundledBoard(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = "",
    val nsfw: Boolean = false,
) {
    fun toBoard() =
        Board(
            id = BoardId(id),
            title = title,
            description = description,
            category = category,
            isNsfw = nsfw,
        )
}

@Serializable
internal data class BundledThread(
    val board: String,
    val number: Long,
    val subject: String,
    val comment: String,
    val postedAtMillis: Long,
    val replies: List<BundledPost> = emptyList(),
)

@Serializable
internal data class BundledPost(
    val number: Long,
    val comment: String,
    val postedAtMillis: Long,
)

/**
 * Four SFW boards in the real directory's order, so /3/ (3DCG) comes first as it does live, and
 * text-only threads: nothing is fetched, and screenshots never show site content.
 */
internal const val BUNDLED_BOARD_DIRECTORY_JSON = """
{
  "sites": {
    "fourchan": {
      "boards": [
        { "id": "3", "title": "3DCG", "description": "3D modeling, rendering and animation", "category": "Creative" },
        { "id": "ck", "title": "Food & Cooking", "description": "Recipes and kitchen technique", "category": "Interests" },
        { "id": "diy", "title": "Do It Yourself", "description": "Building, fixing and making", "category": "Creative" },
        { "id": "g", "title": "Technology", "description": "Computers, phones and software", "category": "Interests" }
      ],
      "threads": [
        {
          "board": "3", "number": 900101, "subject": "Bundled fixture: low-poly lighthouse",
          "comment": "Finished the lighthouse scene. Looking for feedback on the lamp glow.",
          "postedAtMillis": 1763200000000,
          "replies": [
            { "number": 900102, "comment": "Nice silhouette. The glow could use a softer falloff.", "postedAtMillis": 1763200600000 },
            { "number": 900103, "comment": "Try a second, dimmer light for the fog.", "postedAtMillis": 1763201200000 }
          ]
        },
        {
          "board": "3", "number": 900111, "subject": "Retopology workflow thread",
          "comment": "Share how you clean up sculpts before UV unwrapping.",
          "postedAtMillis": 1763190000000,
          "replies": [
            { "number": 900112, "comment": "Quad draw, then a quick pass with relax.", "postedAtMillis": 1763190600000 }
          ]
        },
        {
          "board": "ck", "number": 900201, "subject": "Weeknight soup ideas",
          "comment": "Post a soup you can make in under forty minutes.",
          "postedAtMillis": 1763180000000
        },
        {
          "board": "diy", "number": 900301, "subject": "Workbench build log",
          "comment": "Day two: the frame is square and the top is glued up.",
          "postedAtMillis": 1763170000000
        },
        {
          "board": "g", "number": 900401, "subject": "Home server thread",
          "comment": "What are you running at home this month?",
          "postedAtMillis": 1763160000000
        }
      ]
    }
  }
}
"""
