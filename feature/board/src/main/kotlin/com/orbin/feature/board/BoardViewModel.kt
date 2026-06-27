package com.orbin.feature.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.orbin.core.model.BoardId
import com.orbin.core.model.CatalogSort
import com.orbin.core.model.CatalogThread
import com.orbin.core.model.ProviderId
import com.orbin.domain.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Backs the board catalog screen. Navigation arguments are read from [SavedStateHandle] by the
 * field names of the type-safe route, so this feature does not depend on the app's route types.
 */
@HiltViewModel
class BoardViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        catalogRepository: CatalogRepository,
    ) : ViewModel() {
        val providerId: String = savedStateHandle.get<String>("provider").orEmpty()
        val boardId: String = savedStateHandle.get<String>("board").orEmpty()
        val title: String = savedStateHandle.get<String>("title").orEmpty()

        val catalog: Flow<PagingData<CatalogThread>> =
            catalogRepository
                .catalogStream(ProviderId(providerId), BoardId(boardId), CatalogSort.BUMP_ORDER)
                .cachedIn(viewModelScope)
    }
