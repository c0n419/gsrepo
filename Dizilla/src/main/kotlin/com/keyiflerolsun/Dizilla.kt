// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Dizilla : MainAPI() {
    override var mainUrl = "https://dizilla.now"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay       = 150L
    override var sequentialMainPageScrollDelay = 150L

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    private val privateAESKey = "9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI"

    private val baseHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
        "Referer" to "$mainUrl/"
    )

    private val seenMediaIds = Collections.synchronizedSet(mutableSetOf<String>())

    private val categoryMap = mapOf(
        "suc" to 1,
        "dram" to 2,
        "gizem" to 3,
        "komedi" to 4,
        "bilim-kurgu" to 5,
        "romantik" to 7,
        "korku" to 8,
        "aksiyon" to 9,
        "western" to 11,
        "fantastik" to 12,
        "aile" to 15,
        "animasyon" to 17,
        "gerilim" to 18,
        "macera" to 24,
        "savas" to 26,
    )

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("verifying")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val supportedSyncNames = setOf(
        SyncIdName.Simkl
    )

    override val mainPage = mainPageOf(
        // ponytail: /tum-bolumler SSR artık bölüm listesi döndürmüyor; ana sayfa secureData'sı getEpisodesOnBrandAll taşıyor
        mainUrl to "Yeni Eklenen Bölümler",
        "${mainUrl}/arsiv" to "Yeni Eklenen Diziler",
        "${mainUrl}/dizi-turu/aksiyon" to "Aksiyon",
        "${mainUrl}/dizi-turu/animasyon" to "Animasyon",
        "${mainUrl}/dizi-turu/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/dizi-turu/dram" to "Dram",
        "${mainUrl}/dizi-turu/fantastik" to "Fantastik",
        "${mainUrl}/dizi-turu/gerilim" to "Gerilim",
        "${mainUrl}/dizi-turu/gizem" to "Gizem",
        "${mainUrl}/dizi-turu/komedi" to "Komedi",
        "${mainUrl}/dizi-turu/korku" to "Korku",
        "${mainUrl}/dizi-turu/macera" to "Macera",
        "${mainUrl}/dizi-turu/romantik" to "Romantik",
        "${mainUrl}/dizi-turu/savas" to "Savaş",
        "${mainUrl}/dizi-turu/suc" to "Suç",
        "${mainUrl}/dizi-turu/western" to "Western",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            // Sadece ilk kategorinin 1. sayfasında seenMediaIds kümesini temizle
            if (page == 1 && request.data == mainPage.first().data) {
                seenMediaIds.clear()
            }

            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

            val homeItems = mutableListOf<SearchResponse>()
            val isCategoryUrl = request.data.contains("/dizi-turu/") || request.data.contains("/arsiv")

            if (isCategoryUrl && page == 1) {
                // Sayfa 1 için doğrudan kategorinin kendi sayfasından çek (GET)
                val response = app.get(request.data, headers = baseHeaders, interceptor = interceptor)
                val document = response.document
                val script = document.selectFirst("script#__NEXT_DATA__")?.data()

                if (!script.isNullOrEmpty()) {
                    val rootNode = objectMapper.readTree(script)
                    val secureData = rootNode.path("props").path("pageProps").path("secureData").asText()

                    if (secureData.isNotEmpty()) {
                        val decodedData = decryptDizillaResponse(secureData)
                        if (!decodedData.isNullOrEmpty()) {
                            val json = objectMapper.readTree(decodedData)
                            val resultArray = json.get("listItems")

                            if (resultArray != null && resultArray.isArray) {
                                resultArray.forEach { item ->
                                    val title = item.get("original_title")?.asText()?.takeIf { it.isNotBlank() }
                                        ?: item.get("culture_title")?.asText()?.takeIf { it.isNotBlank() }
                                        ?: item.get("used_title")?.asText()?.takeIf { it.isNotBlank() }
                                        ?: item.get("title")?.asText()?.takeIf { it.isNotBlank() }
                                        ?: return@forEach

                                    val rawSlug = item.get("used_slug")?.asText()
                                        ?: item.get("slug")?.asText()
                                        ?: return@forEach

                                    val mediaId = item.get("id")?.asText()?.takeIf { it.isNotBlank() } ?: rawSlug

                                    val rawPoster = item.get("poster_url")?.asText()
                                        ?: item.get("face_url")?.asText()
                                        ?: item.get("square_url")?.asText()
                                        ?: item.get("back_url")?.asText()

                                    val poster = fixPosterUrl(rawPoster) ?: return@forEach

                                    val cleanSlug = rawSlug.removePrefix("/").removeSuffix("/").lowercase()
                                    val cleanId = mediaId.lowercase()

                                    if (seenMediaIds.contains(cleanId) || seenMediaIds.contains(cleanSlug)) return@forEach
                                    seenMediaIds.add(cleanId)
                                    if (cleanSlug.isNotEmpty()) seenMediaIds.add(cleanSlug)

                                    homeItems.add(
                                        newTvSeriesSearchResponse(title, fixUrl(rawSlug), TvType.TvSeries) {
                                            this.posterUrl = poster
                                            this.posterHeaders = baseHeaders
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Çakışma nedeniyle içerik sayısı 12'den az kaldıysa 2., 3., vb. sayfaları tara
                val catSlug = request.data.substringAfter("/dizi-turu/").substringBefore("?").trim('/')
                val catId = categoryMap[catSlug]

                if (homeItems.size < 12 && catId != null) {
                    var currPage = 2
                    while (homeItems.size < 12 && currPage <= 5) {
                        val apiUrl = "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2030&imdbPointMin=0&imdbPointMax=10&categoryIdsComma=$catId&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=$currPage&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma="

                        val apiResponse = app.post(
                            apiUrl,
                            headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                                "Accept" to "application/json, text/plain, */*",
                                "X-Requested-With" to "XMLHttpRequest",
                                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                                "Origin" to mainUrl,
                                "Referer" to request.data
                            ),
                            data = mapOf("page" to currPage.toString())
                        )

                        val responseBody = apiResponse.body.string()
                        val jsonResponse = objectMapper.readTree(responseBody)
                        val success = jsonResponse.get("success")?.asBoolean() ?: false

                        if (success) {
                            val encryptedData = jsonResponse.get("response")?.asText()
                            if (!encryptedData.isNullOrEmpty()) {
                                val decodedData = decryptDizillaResponse(encryptedData)
                                if (!decodedData.isNullOrEmpty()) {
                                    val resultStart = decodedData.indexOf("\"result\":[")
                                    if (resultStart != -1) {
                                        var bracketCount = 0
                                        var resultEnd = resultStart
                                        var inString = false

                                        for (i in resultStart until decodedData.length) {
                                            val c = decodedData[i]
                                            if (c == '"' && (i == 0 || decodedData[i-1] != '\\')) {
                                                inString = !inString
                                            }
                                            if (!inString) {
                                                when (c) {
                                                    '[' -> bracketCount++
                                                    ']' -> {
                                                        bracketCount--
                                                        if (bracketCount == 0) {
                                                            resultEnd = i
                                                            break
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (bracketCount == 0) {
                                            val resultArrayJson = decodedData.substring(resultStart + 9, resultEnd + 1)
                                            val resultArray = objectMapper.readTree(resultArrayJson)

                                            if (resultArray.isArray) {
                                                for (item in resultArray) {
                                                    val title = item.get("original_title")?.asText()?.takeIf { it.isNotBlank() }
                                                        ?: item.get("culture_title")?.asText()?.takeIf { it.isNotBlank() }
                                                        ?: item.get("title")?.asText()?.takeIf { it.isNotBlank() }
                                                        ?: continue

                                                    val rawPoster = item.get("poster_url")?.asText()
                                                        ?: item.get("square_url")?.asText()
                                                        ?: item.get("face_url")?.asText()
                                                        ?: item.get("back_url")?.asText()

                                                    val poster = fixPosterUrl(rawPoster) ?: continue

                                                    val rawSlug = Regex(""""serie_site_id":0,.*?"used_slug":"(.*?)"""").find(item.toString())?.groupValues?.get(1)
                                                        ?: item.get("used_slug")?.asText()
                                                        ?: ""

                                                    val mediaId = item.get("id")?.asText()?.takeIf { it.isNotBlank() } ?: rawSlug
                                                    val cleanSlug = rawSlug.removePrefix("/").removeSuffix("/").lowercase()
                                                    val cleanId = mediaId.lowercase()

                                                    if (seenMediaIds.contains(cleanId) || seenMediaIds.contains(cleanSlug)) continue
                                                    seenMediaIds.add(cleanId)
                                                    if (cleanSlug.isNotEmpty()) seenMediaIds.add(cleanSlug)

                                                    homeItems.add(
                                                        newTvSeriesSearchResponse(title, fixUrl(rawSlug), TvType.TvSeries) {
                                                            this.posterUrl = poster
                                                            this.posterHeaders = baseHeaders
                                                        }
                                                    )

                                                    if (homeItems.size >= 12) break
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        currPage++
                    }
                }
            } else if (isCategoryUrl && page > 1) {
                // Sayfa 2+ istemciden doğrudan geliyorsa API findSeries kullan
                val catSlug = request.data.substringAfter("/dizi-turu/").substringBefore("?").trim('/')
                val catId = categoryMap[catSlug]

                if (catId != null) {
                    val apiUrl = "${mainUrl}/api/bg/findSeries?releaseYearStart=1900&releaseYearEnd=2030&imdbPointMin=0&imdbPointMax=10&categoryIdsComma=$catId&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=$page&currentPageCount=24&queryStr=&categorySlugsComma=&countryCodesComma="

                    val response = app.post(
                        apiUrl,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                            "Accept" to "application/json, text/plain, */*",
                            "X-Requested-With" to "XMLHttpRequest",
                            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                            "Origin" to mainUrl,
                            "Referer" to request.data
                        ),
                        data = mapOf("page" to page.toString())
                    )

                    val responseBody = response.body.string()
                    val jsonResponse = objectMapper.readTree(responseBody)
                    val success = jsonResponse.get("success")?.asBoolean() ?: false

                    if (success) {
                        val encryptedData = jsonResponse.get("response")?.asText()
                        if (!encryptedData.isNullOrEmpty()) {
                            val decodedData = decryptDizillaResponse(encryptedData)
                            if (!decodedData.isNullOrEmpty()) {
                                val resultStart = decodedData.indexOf("\"result\":[")
                                if (resultStart != -1) {
                                    var bracketCount = 0
                                    var resultEnd = resultStart
                                    var inString = false

                                    for (i in resultStart until decodedData.length) {
                                        val c = decodedData[i]
                                        if (c == '"' && (i == 0 || decodedData[i-1] != '\\')) {
                                            inString = !inString
                                        }
                                        if (!inString) {
                                            when (c) {
                                                '[' -> bracketCount++
                                                ']' -> {
                                                    bracketCount--
                                                    if (bracketCount == 0) {
                                                        resultEnd = i
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (bracketCount == 0) {
                                        val resultArrayJson = decodedData.substring(resultStart + 9, resultEnd + 1)
                                        val resultArray = objectMapper.readTree(resultArrayJson)

                                        if (resultArray.isArray) {
                                            resultArray.forEach { item ->
                                                val title = item.get("original_title")?.asText()?.takeIf { it.isNotBlank() }
                                                    ?: item.get("culture_title")?.asText()?.takeIf { it.isNotBlank() }
                                                    ?: item.get("title")?.asText()?.takeIf { it.isNotBlank() }
                                                    ?: return@forEach

                                                val rawPoster = item.get("poster_url")?.asText()
                                                    ?: item.get("square_url")?.asText()
                                                    ?: item.get("face_url")?.asText()
                                                    ?: item.get("back_url")?.asText()

                                                val poster = fixPosterUrl(rawPoster) ?: return@forEach

                                                val rawSlug = Regex(""""serie_site_id":0,.*?"used_slug":"(.*?)"""").find(item.toString())?.groupValues?.get(1)
                                                    ?: item.get("used_slug")?.asText()
                                                    ?: ""

                                                val mediaId = item.get("id")?.asText()?.takeIf { it.isNotBlank() } ?: rawSlug
                                                val cleanSlug = rawSlug.removePrefix("/").removeSuffix("/").lowercase()
                                                val cleanId = mediaId.lowercase()

                                                if (seenMediaIds.contains(cleanId) || seenMediaIds.contains(cleanSlug)) return@forEach
                                                seenMediaIds.add(cleanId)
                                                if (cleanSlug.isNotEmpty()) seenMediaIds.add(cleanSlug)

                                                homeItems.add(
                                                    newTvSeriesSearchResponse(title, fixUrl(rawSlug), TvType.TvSeries) {
                                                        this.posterUrl = poster
                                                        this.posterHeaders = baseHeaders
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Diğer durumlar (/tum-bolumler veya HTML parsing)
                val response = app.get("${request.data}?page=$page", headers = baseHeaders, interceptor = interceptor)
                val document = response.document

                val script = document.selectFirst("script#__NEXT_DATA__")?.data()
                if (!script.isNullOrEmpty()) {
                    val rootNode = objectMapper.readTree(script)
                    val secureData = rootNode.path("props").path("pageProps").path("secureData").asText()

                    if (secureData.isNotEmpty()) {
                        val decodedData = decryptDizillaResponse(secureData)
                        if (!decodedData.isNullOrEmpty()) {
                            val json = objectMapper.readTree(decodedData)
                            val listArray = json.get("listItems")
                                ?: json.path("content").path("result").path("FindedResult").path("result").get("getLastSeriesAll")
                                ?: json.get("getEpisodesOnBrandAll")

                            if (listArray != null && listArray.isArray) {
                                listArray.forEach { item ->
                                    val isEpisode = !item.path("episode_used_slug").isMissingNode
                                    val title = item.get("original_title")?.asText()?.takeIf { it.isNotBlank() }
                                        ?: item.get("culture_title")?.asText()?.takeIf { it.isNotBlank() }
                                        ?: item.get("used_title")?.asText()?.takeIf { it.isNotBlank() }
                                        ?: item.get("title")?.asText()?.takeIf { it.isNotBlank() }
                                        ?: return@forEach

                                    val displayTitle = if (isEpisode) {
                                        val epNo = item.path("episode_no").asInt().takeIf { it > 0 }
                                        val sezonNo = item.path("season_no").asInt().takeIf { it > 0 }
                                        "$title ${sezonNo ?: "?"}x${epNo ?: "?"}"
                                    } else title

                                    val rawSlug = item.get("used_slug")?.asText()?.takeIf { it.isNotBlank() }
                                        ?: item.get("episode_used_slug")?.asText()?.takeIf { it.isNotBlank() }
                                        ?: item.get("slug")?.asText()?.takeIf { it.isNotBlank() }
                                        ?: return@forEach

                                    val mediaId = item.get("id")?.asText()?.takeIf { it.isNotBlank() } ?: rawSlug

                                    val rawPoster = item.get("poster_url")?.asText()
                                        ?: item.get("face_url")?.asText()
                                        ?: item.get("square_url")?.asText()
                                        ?: item.get("back_url")?.asText()

                                    val poster = fixPosterUrl(rawPoster) ?: return@forEach

                                    val cleanSlug = rawSlug.removePrefix("/").removeSuffix("/").lowercase()
                                    val cleanId = mediaId.lowercase()

                                    if (seenMediaIds.contains(cleanId) || seenMediaIds.contains(cleanSlug)) return@forEach
                                    seenMediaIds.add(cleanId)
                                    if (cleanSlug.isNotEmpty()) seenMediaIds.add(cleanSlug)

                                    homeItems.add(
                                        newTvSeriesSearchResponse(displayTitle, fixUrl(rawSlug), TvType.TvSeries) {
                                            this.posterUrl = poster
                                            this.posterHeaders = baseHeaders
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (homeItems.isEmpty()) {
                    val htmlItems = if (request.data.contains("api")) {
                        document.select("span.watchlistitem-").mapNotNull { it.diziler() }
                    } else {
                        document.select("div.col-span-3 a").mapNotNull { it.sonBolumler() }
                    }
                    htmlItems.forEach { item ->
                        val cleanSlug = item.url.removePrefix(mainUrl).removePrefix("/").removeSuffix("/").lowercase()
                        if (cleanSlug.isNotEmpty() && !seenMediaIds.contains(cleanSlug) && !item.posterUrl.isNullOrBlank()) {
                            seenMediaIds.add(cleanSlug)
                            homeItems.add(item)
                        }
                    }
                }
            }

            return newHomePageResponse(request.name, homeItems)
        } catch (e: Exception) {
            Log.e("Dizilla", "getMainPage exception: ${e.message}")
            e.printStackTrace()
            newHomePageResponse(request.name, emptyList())
        }
    }

    private fun Element.diziler(): SearchResponse? {
        val title = this.selectFirst("span.font-normal")?.text()?.takeIf { it.isNotBlank() } ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixPosterUrl(this.selectFirst("img")?.attr("src")) ?: return null

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.posterHeaders = baseHeaders
        }
    }

    private suspend fun Element.sonBolumler(): SearchResponse? {
        val name = this.selectFirst("h2")?.text()?.takeIf { it.isNotBlank() } ?: return null
        val epName = this.selectFirst("div.opacity-80")?.text()?.replace(". Sezon ", "x")?.replace(". Bölüm", "") ?: ""

        val title = if (epName.isNotBlank()) "$name - $epName" else name

        val epDoc = fixUrlNull(this.attr("href"))?.let { Jsoup.parse(app.get(it, headers = baseHeaders).body.string()) }
        val href = fixUrlNull(epDoc?.selectFirst("div.poster a")?.attr("href")) ?: return null
        val posterUrl = fixPosterUrl(epDoc?.selectFirst("div.poster img")?.attr("src")) ?: return null

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.posterHeaders = baseHeaders
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchReq = app.post(
            "${mainUrl}/api/bg/searchContent?searchterm=$query",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                "Accept" to "application/json, text/plain, */*",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "${mainUrl}/"
            )
        )

        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val searchResult: SearchResult = objectMapper.readValue(searchReq.toString())
        val encryptedBlob = searchResult.response ?: throw ErrorLoadingException("API response field is null")

        val decryptedJson = decryptDizillaResponse(encryptedBlob) ?: throw ErrorLoadingException("Decryption failed")

        val fixedJson = if (!decryptedJson.startsWith("{") && decryptedJson.contains("\"essage\"")) {
            "{m\"$decryptedJson"
        } else {
            decryptedJson
        }

        val contentJson: SearchData = objectMapper.readValue(fixedJson)
        if (contentJson.state != true) {
            return emptyList()
        }

        val veriler = mutableListOf<SearchResponse>()

        contentJson.result?.forEach { item ->
            val name = item.title?.takeIf { it.isNotBlank() }
                ?: item.backupTitle?.takeIf { it.isNotBlank() }
                ?: item.originalTitle?.takeIf { it.isNotBlank() }
                ?: return@forEach

            val link = fixUrl(item.slug.toString())
            val posterLink = fixPosterUrl(item.poster ?: item.posterUrl ?: item.faceUrl ?: item.squareUrl ?: item.backUrl) ?: return@forEach

            veriler.add(
                newTvSeriesSearchResponse(name, link, TvType.TvSeries) {
                    this.posterUrl = posterLink
                    this.posterHeaders = baseHeaders
                }
            )
        }

        return veriler
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val mainReq = app.get(url, headers = baseHeaders, interceptor = interceptor)
        val document = mainReq.document

        var title: String? = null
        var poster: String? = null
        var description: String? = null
        var year: Int? = null
        var tags: List<String>? = null
        val actorsList = mutableListOf<Actor>()
        val episodeses = mutableListOf<Episode>()

        val script = document.selectFirst("script#__NEXT_DATA__")?.data()
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        if (!script.isNullOrEmpty()) {
            try {
                val rootNode = objectMapper.readTree(script)
                val secureData = rootNode.path("props").path("pageProps").path("secureData").asText()

                if (secureData.isNotEmpty()) {
                    val decodedData = decryptDizillaResponse(secureData)
                    if (!decodedData.isNullOrEmpty()) {
                        val decJson = objectMapper.readTree(decodedData)

                        val ci = decJson.path("contentItem")
                        if (!ci.isMissingNode) {
                            title = ci.path("original_title").asText().takeIf { it.isNotBlank() }
                                ?: ci.path("culture_title").asText().takeIf { it.isNotBlank() }
                                ?: ci.path("used_title").asText().takeIf { it.isNotBlank() }

                            poster = fixPosterUrl(
                                ci.path("poster_url").asText().takeIf { it.isNotBlank() }
                                    ?: ci.path("face_url").asText().takeIf { it.isNotBlank() }
                                    ?: ci.path("back_url").asText().takeIf { it.isNotBlank() }
                                    ?: ci.path("square_url").asText().takeIf { it.isNotBlank() }
                            )

                            description = ci.path("description").asText().takeIf { it.isNotBlank() }
                                ?: ci.path("used_description").asText().takeIf { it.isNotBlank() }
                                ?: ci.path("used_short_description").asText().takeIf { it.isNotBlank() }
                                ?: ci.path("used_long_description").asText().takeIf { it.isNotBlank() }

                            year = ci.path("release_year").asInt().takeIf { it > 0 }

                            tags = ci.path("categories").asText().takeIf { it.isNotBlank() }
                                ?.split(",")?.map { it.trim() }
                        }

                        // Casts
                        val rr = decJson.path("RelatedResults")
                            .takeIf { !it.isMissingNode }
                            ?: decJson.path("content").path("result").path("RelatedResults")

                        val castsArray = rr.path("getSerieCastsById").path("result")
                        if (castsArray.isArray) {
                            castsArray.forEach { cast ->
                                val castName = cast.path("name").asText().takeIf { it.isNotBlank() }
                                if (castName != null) {
                                    val castImg = fixPosterUrl(cast.path("cast_image").asText().takeIf { it.isNotBlank() })
                                    actorsList.add(Actor(castName, castImg))
                                }
                            }
                        }

                        // Seasons & Episodes
                        val seasonsArray = rr.path("getSerieSeasonAndEpisodes").path("result")
                        if (seasonsArray.isArray) {
                            seasonsArray.forEach { seasonNode ->
                                val seasonNo = seasonNode.path("season_no").asInt().takeIf { it > 0 }
                                val epsArray = seasonNode.path("episodes")
                                if (epsArray.isArray) {
                                    epsArray.forEach { epNode ->
                                        val epNo = epNode.path("episode_no").asInt().takeIf { it > 0 }
                                        val epSlug = epNode.path("used_slug").asText().takeIf { it.isNotBlank() } ?: return@forEach
                                        val epSubtitle = epNode.path("episode_subtitle").asText().takeIf { it.isNotBlank() }
                                        val epText = epNode.path("episode_text").asText().takeIf { it.isNotBlank() }
                                        val epName = epSubtitle ?: epText ?: "$epNo. Bölüm"

                                        episodeses.add(
                                            newEpisode(fixUrl(epSlug)) {
                                                this.name = epName
                                                this.season = seasonNo
                                                this.episode = epNo
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Dizilla", "Error parsing secureData in load: ${e.message}")
            }
        }

        // HTML Fallback
        if (title.isNullOrBlank()) {
            title = document.selectFirst("div.poster.poster h2")?.text()?.takeIf { it.isNotBlank() }
                ?: document.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
                ?: return null
        }

        if (poster.isNullOrBlank()) {
            poster = fixPosterUrl(
                document.selectFirst("div.w-full.page-top.relative img")?.attr("src")
                    ?: document.selectFirst("div.poster img")?.attr("src")
            )
        }

        if (description.isNullOrBlank()) {
            description = document.selectFirst("div.mt-2.text-sm")?.text()?.trim()
                ?: document.selectFirst("div.text-sm")?.text()?.trim()
        }

        if (year == null) {
            year = document.select("div.w-fit.min-w-fit").getOrNull(1)
                ?.selectFirst("span.text-sm.opacity-60")?.text()
                ?.split(" ")?.lastOrNull()?.toIntOrNull()
        }

        if (tags.isNullOrEmpty()) {
            tags = document.selectFirst("div.poster.poster h3")?.text()?.split(",")?.map { it.trim() }
        }

        if (actorsList.isEmpty()) {
            document.select("div.global-box h5").forEach {
                actorsList.add(Actor(it.text()))
            }
        }

        if (episodeses.isEmpty()) {
            for (sezon in document.select("div.flex.items-center.flex-wrap.gap-2.mb-4 a")) {
                val sezonhref = fixUrl(sezon.attr("href"))
                val sezonReq = app.get(sezonhref, headers = baseHeaders)
                val split = sezonhref.split("-")
                val season = split.getOrNull(split.size - 2)?.toIntOrNull()
                val sezonDoc = sezonReq.document
                val episodes = sezonDoc.select("div.episodes")
                for (bolum in episodes.select("div.cursor-pointer")) {
                    val epName = bolum.select("a").last()?.text() ?: continue
                    val epHref = fixUrlNull(bolum.select("a").last()?.attr("href")) ?: continue
                    val epEpisode = bolum.selectFirst("a")?.text()?.trim()?.toIntOrNull()
                    episodeses.add(
                        newEpisode(epHref) {
                            this.name = epName
                            this.season = season
                            this.episode = epEpisode
                        }
                    )
                }
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeses) {
            this.posterUrl = poster
            this.posterHeaders = baseHeaders
            this.year = year
            this.plot = description
            this.tags = tags
            addActors(actorsList)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = baseHeaders, interceptor = interceptor).document
        val script = document.selectFirst("script#__NEXT_DATA__")?.data() ?: return false

        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        return try {
            val rootNode = objectMapper.readTree(script)
            val secureData = rootNode.path("props").path("pageProps").path("secureData").asText()

            if (secureData.isEmpty()) return false

            val decodedData = decryptDizillaResponse(secureData)

            if (decodedData.isNullOrEmpty()) {
                Log.e("DizillaDebug", "HATA: Decoded data boş!")
                return false
            }

            var linkFound = false

            val contentRegex = Regex(""""source_content"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            val matches = contentRegex.findAll(decodedData)

            matches.forEach { match ->
                val rawHtml = match.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\/", "/")
                    .replace("\\\\", "\\")

                if (rawHtml.contains("iframe", ignoreCase = true)) {
                    var iframeUrl = Jsoup.parse(rawHtml).select("iframe").attr("src")

                    if (iframeUrl.startsWith("//")) {
                        iframeUrl = "https:$iframeUrl"
                    }

                    val finalUrl = fixUrlNull(iframeUrl)

                    if (!finalUrl.isNullOrEmpty()) {
                        Log.d("DizillaDebug", "BİNGO! Regex ile Kırık Veriden Alınan Link: $finalUrl")
                        loadExtractor(finalUrl, "$mainUrl/", subtitleCallback, callback)
                        linkFound = true
                    }
                }
            }

            if (!linkFound) {
                Log.e("DizillaDebug", "HATA: Regex taraması link bulamadı. Ham Veri: ${decodedData.take(500)}")
            }

            linkFound

        } catch (e: Exception) {
            Log.e("DizillaDebug", "Kritik Hata: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun fixPosterUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        var cleanUrl = url.trim()

        if (cleanUrl.startsWith("//")) {
            cleanUrl = "https:$cleanUrl"
        }

        if (cleanUrl.contains("cdn.ampproject.org")) {
            val regex = Regex("""cdn\.ampproject\.org/[^/]+/s/(.+)$""")
            val match = regex.find(cleanUrl)
            if (match != null) cleanUrl = "https://${match.groupValues[1]}"
        }

        return fixUrlNull(cleanUrl)
    }

    private fun decryptDizillaResponse(response: String): String? {
        try {
            val encryptedData = Base64.decode(response, Base64.DEFAULT)

            val ivSpec = IvParameterSpec(ByteArray(16))
            val keySpec = SecretKeySpec(privateAESKey.toByteArray(Charsets.UTF_8), "AES")

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

            val decryptedBytes = cipher.doFinal(encryptedData)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
