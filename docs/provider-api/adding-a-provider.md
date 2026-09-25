# Adding a new provider

Orbin can support any image board engine through the `ImageBoardProvider` SPI. This guide walks
through adding a new provider end to end. No changes to the domain, data, or UI layers are needed.

## 1. Create the module

Add a module under `provider/`, e.g. `provider/tinyib`, and register it in
`settings.gradle.kts`:

```kotlin
include(":provider:tinyib")
```

`provider/tinyib/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.orbin.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

// Shared with iOS: HTTP goes through Ktor, and the engine comes from the host app.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":provider:api"))
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
            implementation(libs.truth)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
```

Provider modules are Kotlin Multiplatform, so `commonMain` cannot use JVM-only APIs such as
`java.net.URI`, `java.time` or `Character`. Use `UriParts`, `kotlin.time` and
`codePointToString` from `provider:api` instead; the **Shared code (iOS targets)** CI job fails
the build if a JVM-only API slips in.

## 2. Model the wire format

Create `@Serializable` DTOs that mirror the engine's JSON exactly. Keep them separate from domain
models — the shared lenient `Json` ignores unknown fields, so you only declare what you use.

## 3. Map DTOs to domain models

Write a mapper from DTOs to `core:model` types (`Board`, `CatalogThread`, `Thread`, `Post`,
`MediaAttachment`). Parse post markup into a `PostComment` tree (you can reuse or adapt
`VichanCommentParser` if the engine uses similar HTML). Build absolute media URLs here.

## 4. Talk to the engine through Ktor

Declare the endpoints as a small interface and implement it over a Ktor `HttpClient`, as
`KtorVichanApi` does: build each URL from the site's base with `appendPathSegments`, set
`expectSuccess = true` so non-2xx responses throw `ResponseException`, and decode the body with
the shared `Json`. The interface keeps the provider testable with a fake.

## 5. Implement `ImageBoardProvider`

```kotlin
class TinyIbProvider(
    private val api: TinyIbApi,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageBoardProvider {

    override val metadata = ProviderMetadata(
        id = ProviderId("tinyib-example"),
        displayName = "Example TinyIB",
        baseUrl = "https://example.org",
        engine = EngineKind.TINYIB,
    )

    override val capabilities = ProviderCapabilities(
        supportsSearch = true, // declare only what you actually implement
    )

    override suspend fun getBoards(): List<Board> = /* ... */
    override suspend fun getCatalog(request: CatalogRequest): List<CatalogThread> = /* ... */
    override suspend fun getThread(board: BoardId, thread: ThreadId): Thread = /* ... */
    override suspend fun search(query: SearchQuery): List<SearchResult> = /* ... */
}
```

**Contract reminders**
- Run blocking work on the injected IO dispatcher.
- Never let transport exceptions escape — map them to `ProviderException`
  (`Network`, `Http`, `NotFound`, `Parse`, `RateLimited`, `Unsupported`). Ktor's
  `ResponseException` carries the status and headers; `kotlinx.io.IOException` covers transport
  failures on every engine.
- Only advertise a capability in `capabilities` if the corresponding method is implemented.
- Return fully resolved models (absolute URLs, parsed comments).

## 6. Register it in the app

The provider module carries no DI annotations. On Android, contribute it to the provider set from
`app/src/main/kotlin/com/orbin/app/di/ProvidersModule.kt`, using the shared Ktor `HttpClient`
(which runs on the app's OkHttp client, so DoH and the HTTPS-only policy apply):

```kotlin
@Provides
@IntoSet
@Singleton
fun providesTinyIbProvider(
    client: HttpClient,
    json: Json,
    @Dispatcher(OrbinDispatcher.IO) io: CoroutineDispatcher,
): ImageBoardProvider = TinyIbProvider(KtorTinyIbApi(client, "https://example.org/api/", json), io)
```

Add the module to the app's dependencies in `app/build.gradle.kts`. The provider now appears in
the `ProviderRegistry` and the provider picker automatically.

## 7. Test it

- Unit-test the mapper and comment parser with representative fixtures.
- Use Ktor's `MockEngine` to test the provider against recorded responses, asserting the request
  URLs and that error statuses map to the right `ProviderException` (see `VichanTransportTest`).

## Checklist

- [ ] Module created and included in `settings.gradle.kts`
- [ ] DTOs cover the responses you use
- [ ] Mapper produces domain models with absolute URLs and parsed comments
- [ ] `ImageBoardProvider` implemented; all failures mapped to `ProviderException`
- [ ] `capabilities` reflect only implemented features
- [ ] `@IntoSet` registration added to `ProvidersModule`; module wired into `:app`
- [ ] Unit + `MockEngine` transport tests passing
- [ ] Compiles for iOS (the Shared code CI job)
