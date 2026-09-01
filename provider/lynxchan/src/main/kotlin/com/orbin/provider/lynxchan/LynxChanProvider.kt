package com.orbin.provider.lynxchan

import com.orbin.core.model.Board
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogRequest
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.Thread
import com.orbin.core.model.ThreadId
import com.orbin.core.model.comparator
import com.orbin.provider.api.EngineKind
import com.orbin.provider.api.ImageBoardProvider
import com.orbin.provider.api.ProviderCapabilities
import com.orbin.provider.api.ProviderException
import com.orbin.provider.api.ProviderMetadata
import com.orbin.provider.lynxchan.api.LynxChanApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * [ImageBoardProvider] for LynxChan engines. A single instance targets one [LynxChanSite];
 * multiple sites = multiple registered providers. The provider owns its Retrofit service (built