package com.gsrepo

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.json.JSONObject
import org.jsoup.Jsoup

class DiziMag : MainAPI() {
    override var mainUrl = "https://dizimag.onl"
    override var name = "DiziMag"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 250L
    override var sequentialMainPageScrollDelay = 250L

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Son Eklenenler",
        "$mainUrl/diziler" to "Diziler",
        "$mainUrl/filmler" to "Filmler",
        "$mainUrl/tum-diziler" to "Tüm Diziler",
        "$mainUrl/imdbtop250" to "IMDb Top 250",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) {
            request.data
        } else {
            val base = request.data.removeSuffix("/")
            if (base.contains("?")) {
                "$base&page=$page"
            } else {
                "$base?page=$page"
            }
        }

        val document = app.get(url, headers = mapOf("User-Agent" to "Mozilla/5.0")).document
        val home = mutableListOf<SearchResponse>()

        document.select("a[href*='/dizi/'], a[href*='/film/']").forEach { aTag ->
            val rawHref = aTag.attr("href")
            val href = fixUrlNull(rawHref) ?: return@forEach
            if (href == "$mainUrl/" || href.endsWith("/diziler") || href.endsWith("/filmler")) return@forEach

            val parent = aTag.parent()
            val imgTag = aTag.selectFirst("img") ?: parent?.selectFirst("img")
            val posterUrl = fixUrlNull(imgTag?.attr("data-src")?.takeIf { it.isNotBlank() } ?: imgTag?.attr("src"))

            var title = aTag.attr("title").trim().takeIf { it.isNotBlank() }
                ?: imgTag?.attr("alt")?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("/") }
                ?: aTag.text().trim().takeIf { it.isNotBlank() }
                ?: return@forEach

            if (title.startsWith("/")) title = title.removePrefix("/").trim()

            val isTvSeries = href.contains("/dizi/") || !href.contains("/film/")
            if (isTvSeries) {
                home.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                })
            } else {
                home.add(newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                })
            }
        }

        return newHomePageResponse(request.name, home.distinctBy { it.url }, hasNext = home.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        try {
            val responseText = app.post(
                "$mainUrl/search",
                data = mapOf("query" to query),
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to "$mainUrl/"
                )
            ).text

            val htmlContent = try {
                JSONObject(responseText).optString("theme", responseText)
            } catch (_: Exception) {
                responseText
            }

            val document = Jsoup.parse(htmlContent)

            document.select("a[href*='/dizi/'], a[href*='/film/']").forEach { aTag ->
                val href = fixUrlNull(aTag.attr("href")) ?: return@forEach
                val imgTag = aTag.selectFirst("img")
                val posterUrl = fixUrlNull(imgTag?.attr("data-src")?.takeIf { it.isNotBlank() } ?: imgTag?.attr("src"))

                val title = aTag.text().trim().takeIf { it.isNotBlank() }
                    ?: imgTag?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@forEach

                val isTvSeries = href.contains("/dizi/")
                if (isTvSeries) {
                    results.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = posterUrl
                    })
                } else {
                    results.add(newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = posterUrl
                    })
                }
            }
        } catch (_: Exception) {}

        return results.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val originalDocument = app.get(url, referer = "$mainUrl/").document

        // If an episode URL is passed (from search/home), find parent series URL
        val isEpisodeUrl = url.contains("/sezon-") || url.contains("/bolum-") || url.contains("-bolum-")
        val seriesUrl = if (isEpisodeUrl) {
            originalDocument.select("a[href*='/dizi/']").mapNotNull { fixUrlNull(it.attr("href")) }
                .firstOrNull { !it.contains("/sezon-") && !it.contains("/bolum-") && !it.contains("-bolum-") }
        } else null

        val targetUrl = seriesUrl ?: url
        val document = if (targetUrl != url) app.get(targetUrl, referer = "$mainUrl/").document else originalDocument

        val titleRaw = document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("meta[property='og:title']")?.attr("content")
            ?: return null

        val title = titleRaw.substringBefore(" izle").substringBefore(" |").trim()

        val poster = fixUrlNull(
            document.selectFirst("meta[property='og:image']")?.attr("content")
                ?: document.selectFirst("img")?.attr("data-src")
                ?: document.selectFirst("img")?.attr("src")
        )

        val description = document.selectFirst("meta[name='description']")?.attr("content")
            ?: document.selectFirst("meta[property='og:description']")?.attr("content")

        val ratingText = document.selectFirst("div:contains(IMDB :)")?.text()
            ?: document.select("div.episode-date, div.imdb").text()
        val rating = Regex("""\b(\d+(?:\.\d+)?)\b""").find(ratingText ?: "")?.value

        val actorsText = document.select("div:contains(Oyuncular :)").lastOrNull()?.ownText()?.trim()
        val actors = actorsText?.split(",")?.mapNotNull {
            val name = it.trim()
            if (name.isNotEmpty()) ActorData(Actor(name, null)) else null
        }

        val isTvSeries = targetUrl.contains("/dizi/")

        if (isTvSeries) {
            val episodes = mutableListOf<Episode>()

            document.select("a[href*='/sezon-'], a[href*='/bolum-'], a[href*='-sezon-'], a[href*='-bolum-']").forEach { aTag ->
                val epHref = fixUrlNull(aTag.attr("href")) ?: return@forEach
                if (!epHref.contains("sezon") && !epHref.contains("bolum")) return@forEach

                val epText = aTag.text().trim()

                val seasonNum = Regex("""sezon-(\d+)""", RegexOption.IGNORE_CASE).find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(\d+)\.\s*Sezon""", RegexOption.IGNORE_CASE).find(epText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1

                val episodeNum = Regex("""bolum-(\d+)""", RegexOption.IGNORE_CASE).find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""(\d+)\.\s*Bölüm""", RegexOption.IGNORE_CASE).find(epText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: 1

                episodes.add(
                    newEpisode(epHref) {
                        this.name = epText.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: "Sezon $seasonNum Bölüm $episodeNum"
                        this.season = seasonNum
                        this.episode = episodeNum
                    }
                )
            }

            return newTvSeriesLoadResponse(title, targetUrl, TvType.TvSeries, episodes.distinctBy { it.data }) {
                this.posterUrl = poster
                this.plot = description
                this.score = Score.from10(rating)
                this.actors = actors
            }
        } else {
            return newMovieLoadResponse(title, targetUrl, TvType.Movie, targetUrl) {
                this.posterUrl = poster
                this.plot = description
                this.score = Score.from10(rating)
                this.actors = actors
            }
        }
    }

    private fun unescapeUnicode(str: String): String {
        return Regex("""\\u([0-9a-fA-F]{4})""").replace(str) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, referer = "$mainUrl/").document

        val iframes = mutableSetOf<String>()

        document.select("iframe").forEach { iframe ->
            fixUrlNull(iframe.attr("src")?.takeIf { it.isNotBlank() } ?: iframe.attr("data-src"))?.let { iframes.add(it) }
        }

        document.select("[data-frame], [data-hhs], [data-video], [data-src], [data-url]").forEach { el ->
            for (attr in listOf("data-frame", "data-hhs", "data-video", "data-src", "data-url")) {
                val value = el.attr(attr)
                if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//")) {
                    fixUrlNull(value)?.let { iframes.add(it) }
                }
            }
        }

        var foundLinks = false

        for (iframe in iframes) {
            try {
                var targetUrl = iframe
                var referer = "$mainUrl/"

                if (iframe.contains("yabancidizim.com/rplayer/")) {
                    val hash = iframe.substringAfter("rplayer/").substringBefore(".html")
                    if (hash.isNotBlank()) {
                        targetUrl = "https://ksdpictures.site/ch/$hash?st=ZGl6aW1hZy5vbmU="
                        referer = "https://yabancidizim.com/"
                    }
                }

                val playerResponse = app.get(targetUrl, headers = mapOf("Referer" to referer), timeout = 10)
                val playerHtml = playerResponse.text

                val videoMatch = Regex("""videoFile\s*=\s*["']([^"']+)["']""").find(playerHtml)
                    ?: Regex("""var\s+SOURCE\s*=\s*["']([^"']+)["']""").find(playerHtml)
                    ?: Regex("""file:\s*["']([^"']+)["']""").find(playerHtml)

                if (videoMatch != null) {
                    val sourceUrl = videoMatch.groupValues[1].replace("\\/", "/")
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = this.name,
                            url = sourceUrl,
                            type = if (sourceUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = targetUrl
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    foundLinks = true
                }

                Regex("""\{"file"\s*:\s*"([^"]+)"\s*,\s*"label"\s*:\s*"([^"]+)"""").findAll(playerHtml).forEach { match ->
                    val file = fixUrlNull(match.groupValues[1].replace("\\/", "/")) ?: return@forEach
                    val label = unescapeUnicode(match.groupValues[2]).replace("\\/", "/")
                    subtitleCallback(newSubtitleFile(label, file))
                }
            } catch (_: Exception) { }

            loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
        }

        return foundLinks || iframes.isNotEmpty()
    }
}
