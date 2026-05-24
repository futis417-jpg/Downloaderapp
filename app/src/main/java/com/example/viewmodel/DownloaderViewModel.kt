package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HistoryWrapper
import com.example.data.NetworkClient
import com.example.data.TikVMData
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class DownloadUiState {
    object Idle : DownloadUiState()
    object Loading : DownloadUiState()
    data class Success(val data: TikVMData) : DownloadUiState()
    data class Error(val message: String) : DownloadUiState()
}

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState

    private val _history = MutableStateFlow<List<TikVMData>>(emptyList())
    val history: StateFlow<List<TikVMData>> = _history

    private val moshi = Moshi.Builder().build()
    private val historyAdapter = moshi.adapter(HistoryWrapper::class.java)
    private val historyFile = File(application.filesDir, "downloader_history.json")

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                if (historyFile.exists()) {
                    val json = historyFile.readText()
                    val wrapper = historyAdapter.fromJson(json)
                    if (wrapper != null) {
                        _history.value = wrapper.items
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveHistory(list: List<TikVMData>) {
        viewModelScope.launch {
            try {
                val wrapper = HistoryWrapper(list)
                val json = historyAdapter.toJson(wrapper)
                historyFile.writeText(json)
                _history.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addHistoryItem(item: TikVMData) {
        val currentList = _history.value.toMutableList()
        // Remove existing to avoid duplicates, then add to head
        currentList.removeAll { it.id == item.id || (it.title == item.title && it.cover == item.cover) }
        currentList.add(0, item)
        // Capped history list length
        val cappedList = currentList.take(15)
        saveHistory(cappedList)
    }

    fun deleteHistoryItem(item: TikVMData) {
        val currentList = _history.value.toMutableList()
        currentList.removeAll { it.id == item.id || (it.title == item.title && it.cover == item.cover) }
        saveHistory(currentList)
    }

    fun selectHistoryItem(item: TikVMData) {
        _uiState.value = DownloadUiState.Success(item)
    }

    fun loadVideoInfo(url: String) {
        if (url.isBlank()) {
            _uiState.value = DownloadUiState.Error("Introduce un enlace válido")
            return
        }

        _uiState.value = DownloadUiState.Loading

        viewModelScope.launch {
            try {
                if (url.contains("tiktok", ignoreCase = true)) {
                    // Optimized direct native TikTok save
                    val response = NetworkClient.api.getVideoData(url)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        if (body.code == 0 && body.data != null) {
                            val videoData = body.data
                            _uiState.value = DownloadUiState.Success(videoData)
                            addHistoryItem(videoData)
                        } else {
                            _uiState.value = DownloadUiState.Error(body.msg.ifEmpty { "No se pudo obtener el video" })
                        }
                    } else {
                        _uiState.value = DownloadUiState.Error("Error de conexión con TikTok: ${response.code()}")
                    }
                } else {
                    // Universal service using Cobalt API for YouTube, IG, Facebook, X, etc.
                    val cobaltRequest = com.example.data.CobaltRequest(url = url)
                    val response = NetworkClient.cobaltApi.getMediaData(cobaltRequest)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        if (body.status == "error") {
                            _uiState.value = DownloadUiState.Error(body.text ?: "Cobalt no pudo resolver la dirección")
                        } else {
                            val mappedData = mapCobaltToTikVMData(url, body)
                            _uiState.value = DownloadUiState.Success(mappedData)
                            addHistoryItem(mappedData)
                        }
                    } else {
                        // Fallback attempt or display descriptive error
                        _uiState.value = DownloadUiState.Error("Servidor universal no responde (${response.code()}). Verifica el link.")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = DownloadUiState.Error("Error al obtener contenido: ${e.localizedMessage}")
            }
        }
    }

    private fun mapCobaltToTikVMData(url: String, cobalt: com.example.data.CobaltResponse): TikVMData {
        val id = url.hashCode().toString()
        val isYoutube = url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)
        val isInstagram = url.contains("instagram.com", ignoreCase = true)
        val platformName = when {
            isYoutube -> "YouTube"
            isInstagram -> "Instagram"
            url.contains("facebook.com", ignoreCase = true) || url.contains("fb.watch", ignoreCase = true) -> "Facebook"
            url.contains("twitter.com", ignoreCase = true) || url.contains("x.com", ignoreCase = true) -> "Twitter / X"
            else -> "Sitio Web"
        }

        val downloadUrl = cobalt.url ?: cobalt.picker?.firstOrNull()?.url ?: url
        val title = if (!cobalt.text.isNullOrBlank()) {
            cobalt.text
        } else {
            "Contenido de $platformName"
        }

        val coverUrl = cobalt.picker?.firstOrNull()?.thumb ?: when {
            isYoutube -> "https://img.youtube.com/vi/${extractYoutubeId(url)}/0.jpg"
            isInstagram -> "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=500"
            else -> "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=500"
        }

        return TikVMData(
            id = id,
            region = platformName,
            title = title,
            cover = coverUrl,
            origin_cover = coverUrl,
            duration = 0,
            play = downloadUrl,
            wmplay = downloadUrl,
            size = 0L,
            author = com.example.data.AuthorData(
                id = id,
                unique_id = platformName.lowercase().replace(" ", ""),
                nickname = "Descargador de $platformName",
                avatar = "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=128"
            ),
            music_info = com.example.data.MusicData(
                title = "Audio Extraído - $platformName",
                play = downloadUrl,
                author = "izi"
            )
        )
    }

    private fun extractYoutubeId(url: String): String {
        return try {
            val pattern = "(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?)\\/|.*[?&]v=)|youtu\\.be\\/)([^\"&?\\/\\s]{11})".toRegex()
            val match = pattern.find(url)
            match?.groupValues?.get(1) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun resetState() {
        _uiState.value = DownloadUiState.Idle
    }
}
