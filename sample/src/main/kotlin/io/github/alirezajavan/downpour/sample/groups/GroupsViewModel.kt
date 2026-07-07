package io.github.alirezajavan.downpour.sample.groups

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.alirezajavan.downpour.api.DownloadItem
import io.github.alirezajavan.downpour.api.GroupProgress
import io.github.alirezajavan.downpour.sample.core.SampleDownpour
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val EMPTY_PROGRESS =
    GroupProgress(total = 0, completed = 0, failed = 0, running = 0, queued = 0, downloadedBytes = 0, totalBytes = 0)

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val manager = SampleDownpour.getInstance(application)

    val allDownloads: StateFlow<List<DownloadItem>> =
        manager
            .observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _selectedTag = MutableStateFlow(DEFAULT_TAG)
    val selectedTag: StateFlow<String> = _selectedTag

    val groupProgress: StateFlow<GroupProgress> =
        _selectedTag
            .flatMapLatest { tag -> manager.observeGroupProgress(tag) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), EMPTY_PROGRESS)

    val taggedDownloads: StateFlow<List<DownloadItem>> =
        _selectedTag
            .flatMapLatest { tag -> manager.observeByTag(tag) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun selectTag(tag: String) {
        _selectedTag.value = tag.ifBlank { DEFAULT_TAG }
    }

    fun pauseTag() = launch { manager.pauseByTag(_selectedTag.value) }

    fun resumeTag() = launch { manager.resumeByTag(_selectedTag.value) }

    fun cancelTag() = launch { manager.cancelByTag(_selectedTag.value) }

    fun removeTag() = launch { manager.removeByTag(_selectedTag.value, deleteFiles = true) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_TAG = "sample"
    }
}
