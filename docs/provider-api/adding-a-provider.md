# Adding a new provider

Orbin can support any image board engine through the `ImageBoardProvider` SPI. This guide walks
through adding a new provider end to end. No changes to the domain, data, or UI layers are needed.

## 1. Create the module

Add a module under `provider/`, e.g. `provider/lynxchan`, and register it in
`settings.gradle.kts`:

```kotlin
include(":provider:lynxchan")
```

`provider/lynxchan/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.orbin.android.library)
    alias(libs.plugins.orbin.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android { namespace = "com.orbin.provider.lynxchan" }

dependencies {
    api(project(":provider:api"))
    implementation(project(":network"))
    implementation(project(":core:common"))
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)
}
```

## 2. Model the wire format

Create `@Serializable` DTOs that mirror the engine's JSON exactly. Keep them separate from domain
models — the shared lenient `Json` ignores unknown fields, so you only declare what you use.

## 3. Map DTOs to domain models

Write a mapper from DTOs to `core:model` types (`Board`, `CatalogThread`, `Thread`, `Post`,
`MediaAttachment`). Parse post markup into a `PostComment` tree (you can reuse or adapt
`VichanCommentParser` if the engine uses similar HTML). Build absolute media URLs here.

## 4. Implement `ImageBoardProvider`

```kotlin
class LynxChanProvider(
    private val api: LynxChanApi,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageBoardProvider {

    override val metadata = ProviderMetadata(
        id = ProviderId("lynxchan-example"),
        displayName = "Example LynxChan",
        baseUrl = "https://example.org",
        engine = EngineKind.LYNXCHAN,
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
  (`Network`, `Http`, `NotFound`, `Parse`, `RateLimited`, `Unsupported`).
- Only advertise a capability in `capabilities` if the corresponding method is implemented.
- Return fully resolved models (absolute URLs, parsed comments).

## 5. Register with Hilt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object LynxChanProviderModule {

    @Provides
    @IntoSet
    @Singleton
    fun providesProvider(
        @BaseOkHttp client: OkHttpClient,
        json: Json,
        @Dispatcher(OrbinDispatcher.IO) io: CoroutineDispatcher,
    ): ImageBoardProvider {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.org/api/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return LynxChanProvider(retrofit.create(LynxChanApi::class.java), io)
    }
}
```

Add the module to the app's dependencies in `app/build.gradle.kts`. The provider now appears in
the `ProviderRegistry` and the provider picker automatically.

## 6. Test it

- Unit-test the mapper and comment parser with representative fixtures.
- Use `MockWebServer` to test the provider against recorded responses, asserting that error
  statuses map to the right `ProviderException`.

## Checklist

- [ ] Module created and included in `settings.gradle.kts`
- [ ] DTOs cover the responses you use
- [ ] Mapper produces domain models with absolute URLs and parsed comments
- [ ] `ImageBoardProvider` implemented; all failures mapped to `ProviderException`
- [ ] `capabilities` reflect only implemented features
- [ ] Hilt `@IntoSet` registration added; module wired into `:app`
- [ ] Unit + MockWebServer tests passing
