package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiVoicePreviewPlayer
import com.example.ai.RitLiveVoiceEngine
import com.example.ai.VoiceSessionStatus
import com.example.auth.GoogleSignInManager
import com.example.auth.GoogleSignInResult
import com.example.data.local.AppSettingsEntity
import com.example.data.local.ConversationWithSummary
import com.example.data.local.MemoryCategory
import com.example.data.local.MemoryEntity
import com.example.data.local.MessageEntity
import com.example.data.local.UserEntity
import com.example.data.local.VoiceLanguage
import com.example.data.local.VoicePreferenceEntity
import com.example.data.repository.RitRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class DateFilterType(val label: String) {
    ALL("All"),
    TODAY("Today"),
    LAST_7_DAYS("7 Days"),
    CUSTOM_DAY("Date")
}

data class DateFilterState(
    val type: DateFilterType = DateFilterType.ALL,
    val customDayStartMs: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class RitViewModel(
    private val repository: RitRepository,
    private val voiceEngine: RitLiveVoiceEngine,
    private val previewPlayer: GeminiVoicePreviewPlayer
) : ViewModel() {

    val settings: StateFlow<AppSettingsEntity> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppSettingsEntity())

    val user: StateFlow<UserEntity?> = repository.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val voices: StateFlow<List<VoicePreferenceEntity>> = repository.voicesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val memories: StateFlow<List<MemoryEntity>> = repository.memoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val pendingSensitiveMemories: StateFlow<List<MemoryEntity>> =
        repository.pendingSensitiveMemoriesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val voiceSession: StateFlow<VoiceSessionStatus> = voiceEngine.sessionStatus

    val playingPreviewVoice: StateFlow<String?> = previewPlayer.playingVoiceName
    val previewError: StateFlow<String?> = previewPlayer.previewError

    private val _historyQuery = MutableStateFlow("")
    val historyQuery: StateFlow<String> = _historyQuery.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateFilterState())
    val dateFilter: StateFlow<DateFilterState> = _dateFilter.asStateFlow()

    val conversations: StateFlow<List<ConversationWithSummary>> =
        combine(_historyQuery, _dateFilter) { q, df -> q to df }
            .flatMapLatest { (query, dateFilter) ->
                val (startMs, endMs) = computeDateBounds(dateFilter)
                repository.searchConversations(query, startMs, endMs)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _authUserEmail = MutableStateFlow(Firebase.auth.currentUser?.email)
    val authUserEmail: StateFlow<String?> = _authUserEmail.asStateFlow()

    private val _authUserName = MutableStateFlow(Firebase.auth.currentUser?.displayName)
    val authUserName: StateFlow<String?> = _authUserName.asStateFlow()

    private val _isSignedIn = MutableStateFlow(Firebase.auth.currentUser != null)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val fbUser = firebaseAuth.currentUser
        _isSignedIn.value = fbUser != null
        _authUserEmail.value = fbUser?.email
        _authUserName.value = fbUser?.displayName
    }

    init {
        Firebase.auth.addAuthStateListener(authListener)
        viewModelScope.launch {
            repository.initializeDefaults()
        }
    }

    override fun onCleared() {
        super.onCleared()
        Firebase.auth.removeAuthStateListener(authListener)
        previewPlayer.stopPreview()
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun dismissVoiceError() {
        voiceEngine.clearError()
    }

    fun dismissPreviewError() {
        previewPlayer.clearError()
    }

    fun dismissMemoryNotice() {
        voiceEngine.clearMemoryNotice()
    }

    fun startVoiceCall(continueFromConversationId: String? = null) {
        previewPlayer.stopPreview()
        voiceEngine.startVoiceCall(continueFromConversationId)
    }

    fun endVoiceCall() {
        voiceEngine.endVoiceCall()
    }

    fun toggleMute() {
        voiceEngine.toggleMute()
    }

    fun selectVoice(voiceName: String) {
        viewModelScope.launch {
            repository.updateSelectedVoice(voiceName)
            _statusBanner.value = "Rit's voice changed to $voiceName"
        }
    }

    fun playVoicePreview(voiceName: String) {
        viewModelScope.launch {
            val lang = VoiceLanguage.fromName(settings.value.selectedLanguage)
            previewPlayer.playPreview(voiceName, lang)
        }
    }

    fun stopVoicePreview() {
        previewPlayer.stopPreview()
    }

    fun selectLanguage(language: VoiceLanguage) {
        viewModelScope.launch {
            repository.updateSelectedLanguage(language)
        }
    }

    fun updateAutoExtract(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(allowAutoMemoryExtraction = enabled) }
        }
    }

    fun updateAskBeforeSensitive(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(askBeforeSensitiveMemory = enabled) }
        }
    }

    fun updateStoreAudioRecordings(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(storeAudioRecordings = enabled) }
        }
    }

    fun updateBargeIn(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(bargeInEnabled = enabled) }
        }
    }

    fun setHistoryQuery(query: String) {
        _historyQuery.value = query
    }

    fun setDateFilter(type: DateFilterType, customDayStartMs: Long? = null) {
        _dateFilter.value = DateFilterState(type, customDayStartMs)
    }

    fun observeConversation(conversationId: String): Flow<ConversationWithSummary?> =
        repository.observeConversationWithSummary(conversationId)

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        repository.observeMessages(conversationId)

    fun addManualMemory(content: String, category: MemoryCategory, isSensitive: Boolean = false) {
        if (content.isBlank()) return
        viewModelScope.launch {
            repository.saveOrUpdateMemory(
                content = content,
                category = category,
                importance = 4,
                isSensitive = isSensitive,
                userConfirmed = true
            )
            _statusBanner.value = "Saved to Rit's Brain"
        }
    }

    fun editMemory(memory: MemoryEntity, newContent: String, newCategory: MemoryCategory) {
        if (newContent.isBlank()) return
        viewModelScope.launch {
            repository.updateMemoryContent(memory, newContent, newCategory)
            _statusBanner.value = "Memory updated"
        }
    }

    fun confirmSensitiveMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            repository.confirmSensitiveMemory(memory)
            _statusBanner.value = "Confirmed and saved to Rit's Brain"
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(memoryId)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
            _statusBanner.value = "Conversation deleted"
        }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            repository.deleteAllConversations()
            _statusBanner.value = "All voice history cleared"
        }
    }

    fun clearAllData(alsoDeleteCloud: Boolean) {
        viewModelScope.launch {
            _isSyncing.value = true
            runCatching {
                repository.clearAllData(alsoDeleteCloud)
            }.onSuccess {
                _statusBanner.value = "All RIT AI data cleared"
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Failed to clear all data"
            }
            _isSyncing.value = false
        }
    }

    fun attemptSilentSignIn(context: Context) {
        viewModelScope.launch {
            val fbUser = GoogleSignInManager.attemptSilentSignIn(context)
            if (fbUser != null) {
                repository.onGoogleUserSignedIn(fbUser)
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _isSyncing.value = true
            when (val res = GoogleSignInManager.signInInteractive(context)) {
                is GoogleSignInResult.Success -> {
                    repository.onGoogleUserSignedIn(res.user)
                    _statusBanner.value = "Signed in as ${res.user.email ?: res.user.displayName}"
                }
                is GoogleSignInResult.Cancelled -> {
                    _statusBanner.value = "Google Sign-In cancelled"
                }
                is GoogleSignInResult.Error -> {
                    _statusBanner.value = res.message
                }
            }
            _isSyncing.value = false
        }
    }

    fun signOutGoogle(context: Context) {
        viewModelScope.launch {
            _isSyncing.value = true
            GoogleSignInManager.signOut(context)
            repository.onGoogleUserSignedOut()
            _statusBanner.value = "Signed out • Local memories kept on device"
            _isSyncing.value = false
        }
    }

    fun setCloudBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && Firebase.auth.currentUser == null) {
                _statusBanner.value = "Please sign in with Google first to enable Cloud Backup"
                return@launch
            }
            repository.updateSettings { it.copy(cloudBackupEnabled = enabled) }
            if (enabled) {
                syncNow()
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            runCatching {
                repository.syncAllToCloud()
            }.onSuccess {
                _statusBanner.value = "Cloud backup synced"
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Cloud sync failed"
            }
            _isSyncing.value = false
        }
    }

    fun restoreNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            runCatching {
                repository.restoreFromCloud()
            }.onSuccess { count ->
                _statusBanner.value = "Restored $count records from cloud backup"
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Cloud restore failed"
            }
            _isSyncing.value = false
        }
    }

    suspend fun exportAllDataJson(): String = repository.buildExportJson()

    private fun computeDateBounds(filter: DateFilterState): Pair<Long?, Long?> {
        val cal = Calendar.getInstance()
        return when (filter.type) {
            DateFilterType.ALL -> null to null
            DateFilterType.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                start to (start + DAY_MS - 1L)
            }
            DateFilterType.LAST_7_DAYS -> {
                val end = System.currentTimeMillis()
                (end - 7L * DAY_MS) to end
            }
            DateFilterType.CUSTOM_DAY -> {
                val start = filter.customDayStartMs
                if (start != null) start to (start + DAY_MS - 1L) else null to null
            }
        }
    }

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}
