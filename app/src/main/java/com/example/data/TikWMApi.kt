package com.example.data

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface TikWMApi {
    @GET("api/")
    suspend fun getVideoData(@Query("url") url: String): Response<TikWMResponse>
}

@JsonClass(generateAdapter = true)
data class TikWMResponse(
    val code: Int,
    val msg: String,
    val data: TikVMData?
)

@JsonClass(generateAdapter = true)
data class TikVMData(
    val id: String?,
    val region: String?,
    val title: String?,
    val cover: String?,
    val origin_cover: String?,
    val duration: Int?,
    val play: String?,
    val wmplay: String?,
    val size: Long?,
    val author: AuthorData?,
    val music_info: MusicData?
)

@JsonClass(generateAdapter = true)
data class MusicData(
    val title: String?,
    val play: String?,
    val author: String?
)

@JsonClass(generateAdapter = true)
data class AuthorData(
    val id: String?,
    val unique_id: String?,
    val nickname: String?,
    val avatar: String?
)

@JsonClass(generateAdapter = true)
data class HistoryWrapper(
    val items: List<TikVMData>
)

@JsonClass(generateAdapter = true)
data class CobaltRequest(
    val url: String,
    val videoQuality: String = "1080",
    val downloadMode: String = "auto"
)

@JsonClass(generateAdapter = true)
data class CobaltPickerItem(
    val type: String?,
    val url: String?,
    val thumb: String?
)

@JsonClass(generateAdapter = true)
data class CobaltResponse(
    val status: String,
    val url: String?,
    val text: String?,
    val picker: List<CobaltPickerItem>?
)

interface CobaltApi {
    @retrofit2.http.POST
    @retrofit2.http.Headers(
        "Accept: application/json",
        "Content-Type: application/json",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        "Origin: https://cobalt.tools",
        "Referer: https://cobalt.tools/"
    )
    suspend fun getMediaData(
        @retrofit2.http.Url url: String,
        @retrofit2.http.Body request: CobaltRequest
    ): Response<CobaltResponse>
}

object NetworkClient {
    val cobaltServers = listOf(
        "https://api.cobalt.tools/" to "Oficial (api.cobalt.tools)",
        "https://cobalt.api.ryon.services/" to "Ryon Services (Recomendado)",
        "https://cobalt.meowing.de/" to "Meowing DE (Altamente estable)",
        "https://cobalt.hyper.rip/" to "Hyper Rip (Premium/Veloz)",
        "https://co.wukko.me/" to "Wukko Direct",
        "https://api.cobalt.black/" to "Cobalt Black"
    )

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.tikwm.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val cobaltRetrofit = Retrofit.Builder()
        .baseUrl("https://api.cobalt.tools/")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val api: TikWMApi = retrofit.create(TikWMApi::class.java)
    val cobaltApi: CobaltApi = cobaltRetrofit.create(CobaltApi::class.java)
}
