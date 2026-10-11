package com.gsrepo

import android.util.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class SineWix : MainAPI() {
    override var mainUrl = "https://ydfvfdizipanel.ru/public/api"
    override var name = "SineWix"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private val tmdbApiKey = "e6333b32409e02a4a6eba6fb7ff866bb"
    private val tmdbBaseUrl = "https://api.themoviedb.org/3"

    override val mainPage = mainPageOf(
        "top10" to "Top 10 Listesi",
        "latest_movies" to "Son Eklenen Filmler",
        "latest_series" to "Son Eklenen Diziler",
        "latest_animes" to "Son Eklenen Animeler",
        "action_movies" to "Aksiyon Filmleri",
        "comedy_movies" to "Komedi Filmleri",
        "horror_movies" to "Korku Filmleri",
        "scifi_movies" to "Bilim Kurgu Filmleri",
        "drama_series" to "Dram Dizileri",
        "crime_series" to "Suç Dizileri",
        "animes" to "Animeler",
    )

    private val headers = mapOf(
        "signature" to "308202c3308201aba0030201020204075cec01300d06092a864886f70d01010b050030123110300e0603550403130753696e65776978301e1" +
                "70d3231303932313233333334395a170d3436303931353233333334395a30123110300e0603550403130753696e6577697830820122300d06092a864" +
                "886f70d01010105000382010f003082010a0282010100b0a2a1bc5c3f16f19c3b2456cfd0a6128ced9f5e2e2c4cca1a100e17b07b86256258f372e76" +
                "a95a17e9e4a1c048e364835723a95e8ef6d5bdfb5694b50277c65a64f7b012fdf164e5dc93629561f6ca29b7dc82ebb3d6f3c8e8fc6795847fe331ad" +
                "4a13ed6c059a83804c43d3747526d769580f3a4153752eb22dac66dd15f1582caa43305dc49f55ac7b1b89013e654d2ca8c94c30956659674cc67325" +
                "6c04208f09118bae14cdd72d78f9ee2aece958084a8c2e315deff45726d4fc1f18ec39569ff1abe4f36a8d01090e5f68c07c28763513b88208bcac1a" +
                "6e1941f6fd8bfdd52f832098ddb2154c8f565bc5d58c7106a19e03787e75c7f34997000e3bcf30203010001a321301f301d0603551d0e04160414b54" +
                "5fc18e74a791d9402b53940ae38b96e9e209c300d06092a864886f70d01010b05000382010100a8a64d9e7c8b5db102af15d3caf94ff8d3e9be9008b" +
                "b0021117ca2f0762e68583354b126a041bb1fb6e6308e421e4b5a71f779cde63e5d2fc5976bff966c3c4034e852c077d8e74458fbae2ec1db74b1f40" +
                "82e188bf8ef7c42a44e3fbfb693bb00ee2a727096b42360ddce1bdcd3536f50c8693bcc62a7b7204bcefe2ecf1f7c820bcd63e1d7a6acc8bf6163086" +
                "915fc5f607cf51bc7a8635f98bb4c65a8f24b7b5a82c7b06868f565cb0d6ac4775c4aac777536ddd1a565f990fd8cbe539185fa7aab610b7855a687a" +
                "00f4e55536d72873444552c50fd10727dbf298a9be6ed6ae62148dd1de365f3729915dd31975e28a472d752ac14db3db548405cc31e1e",
        "hash256" to "f4d4bc98a3fc4600e7f2c2bab7533f1f03d8a70ff03c256bb11dc57050536bd0",
        "user-agent" to "EasyPlex (Android 13; SM-A546E; samsung; tr)"
    )

    private fun fixUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val clean = url.trim()
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
        if (clean.startsWith("/")) return "https://image.tmdb.org/t/p/w500$clean"
        return "https://image.tmdb.org/t/p/w500/$clean"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val tmdbUrl = when (request.data) {
            "top10" -> "${tmdbBaseUrl}/trending/all/day?api_key=${tmdbApiKey}&language=tr-TR&page=$page"
            "latest_movies" -> "${tmdbBaseUrl}/movie/now_playing?api_key=${tmdbApiKey}&language=tr-TR&page=$page"
            "latest_series" -> "${tmdbBaseUrl}/tv/on_the_air?api_key=${tmdbApiKey}&language=tr-TR&page=$page"
            "latest_animes" -> "${tmdbBaseUrl}/discover/tv?api_key=${tmdbApiKey}&language=tr-TR&with_genres=16&with_original_language=ja&page=$page"
            "action_movies" -> "${tmdbBaseUrl}/discover/movie?api_key=${tmdbApiKey}&language=tr-TR&with_genres=28&sort_by=popularity.desc&page=$page"
            "comedy_movies" -> "${tmdbBaseUrl}/discover/movie?api_key=${tmdbApiKey}&language=tr-TR&with_genres=35&sort_by=popularity.desc&page=$page"
            "horror_movies" -> "${tmdbBaseUrl}/discover/movie?api_key=${tmdbApiKey}&language=tr-TR&with_genres=27&sort_by=popularity.desc&page=$page"
            "scifi_movies" -> "${tmdbBaseUrl}/discover/movie?api_key=${tmdbApiKey}&language=tr-TR&with_genres=878&sort_by=popularity.desc&page=$page"
            "drama_series" -> "${tmdbBaseUrl}/discover/tv?api_key=${tmdbApiKey}&language=tr-TR&with_genres=18&sort_by=popularity.desc&page=$page"
            "crime_series" -> "${tmdbBaseUrl}/discover/tv?api_key=${tmdbApiKey}&language=tr-TR&with_genres=80&sort_by=popularity.desc&page=$page"
            "animes" -> "${tmdbBaseUrl}/discover/tv?api_key=${tmdbApiKey}&language=tr-TR&with_genres=16&with_original_language=ja&sort_by=popularity.desc&page=$page"
            else -> "${tmdbBaseUrl}/trending/all/day?api_key=${tmdbApiKey}&language=tr-TR&page=$page"
        }

        val tmdbRes = try {
            app.get(tmdbUrl, timeout = 10).parsedSafe<TmdbPageResult>()
        } catch (_: Throwable) {
            null
        }

        val items = tmdbRes?.results?.mapNotNull { it.toSearchResponse() } ?: emptyList()

        return newHomePageResponse(request.name, items)
    }

    private fun TmdbItem.toSearchResponse(): SearchResponse? {
        val title = this.title ?: this.name ?: return null
        val id = this.id ?: return null
        val posterUrl = fixUrl(this.posterPath ?: this.backdropPath) ?: return null
        val score = this.voteAverage
        val isTv = this.mediaType == "tv" || (this.name != null && this.title == null)

        if (isTv) {
            val href = "https://www.themoviedb.org/tv/$id"
            return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.score = Score.from10(score)
            }
        } else {
            val href = "https://www.themoviedb.org/movie/$id"
            return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.score = Score.from10(score)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val tmdbUrl = "${tmdbBaseUrl}/search/multi?api_key=${tmdbApiKey}&language=tr-TR&query=${query}"
        val tmdbRes = try {
            app.get(tmdbUrl, timeout = 10).parsedSafe<TmdbPageResult>()
        } catch (_: Throwable) {
            null
        }

        return tmdbRes?.results?.mapNotNull { it.toSearchResponse() } ?: emptyList()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        if (url.contains("/movie/")) {
            val id = url.substringAfter("/movie/").substringBefore("/").substringBefore("?").toIntOrNull()
            if (id != null) return loadTmdbMovie(id)
        }

        if (url.contains("/tv/") || url.contains("/series/") || url.contains("/animes/")) {
            val id = (if (url.contains("/series/show/")) url.substringAfter("/series/show/")
                     else if (url.contains("/animes/show/")) url.substringAfter("/animes/show/")
                     else url.substringAfter("/tv/")).substringBefore("/").substringBefore("?").toIntOrNull()
            if (id != null) return loadTmdbTv(id)
        }

        if (url.contains("/media/detail/")) {
            return movieDetail(url)
        }

        return null
    }

    private suspend fun loadTmdbMovie(id: Int): LoadResponse? {
        val url = "${tmdbBaseUrl}/movie/${id}?api_key=${tmdbApiKey}&language=tr-TR&append_to_response=credits,videos,external_ids"
        val res = try {
            app.get(url, timeout = 10).parsedSafe<TmdbMovieDetail>()
        } catch (_: Throwable) {
            null
        } ?: return null

        val originalName = res.originalTitle
        val name = res.title ?: ""
        val title = if (!originalName.isNullOrBlank() && originalName != name) "$originalName - $name" else name
        val poster = fixUrl(res.posterPath ?: res.backdropPath)
        val description = res.overview
        val year = res.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
        val tags = res.genres?.mapNotNull { it.name }
        val rating = res.voteAverage
        val duration = res.runtime
        val actors = res.credits?.cast?.mapNotNull { if (it.name != null) Actor(it.name, fixUrl(it.profilePath)) else null }
        val trailerKey = res.videos?.results?.firstOrNull { it.site == "YouTube" }?.key
        val imdbId = res.externalIds?.imdbId

        val streamData = if (!imdbId.isNullOrBlank()) "https://vidsrc.me/embed/movie/$imdbId" else "https://vidsrc.me/embed/movie/$id"

        return newMovieLoadResponse(title, "https://www.themoviedb.org/movie/$id", TvType.Movie, streamData) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.score = Score.from10(rating)
            this.duration = duration
            addActors(actors)
            if (!trailerKey.isNullOrBlank()) {
                addTrailer("https://www.youtube.com/embed/${trailerKey}")
            }
        }
    }

    private suspend fun loadTmdbTv(id: Int): LoadResponse? {
        val url = "${tmdbBaseUrl}/tv/${id}?api_key=${tmdbApiKey}&language=tr-TR&append_to_response=credits,videos,external_ids"
        val res = try {
            app.get(url, timeout = 10).parsedSafe<TmdbTvDetail>()
        } catch (_: Throwable) {
            null
        } ?: return null

        val originalName = res.originalName ?: ""
        val name = res.name ?: ""
        val title = if (originalName.isNotEmpty() && originalName != name) "$originalName - $name" else name
        val poster = fixUrl(res.posterPath ?: res.backdropPath)
        val description = res.overview
        val year = res.firstAirDate?.split("-")?.firstOrNull()?.toIntOrNull()
        val tags = res.genres?.mapNotNull { it.name }
        val rating = res.voteAverage
        val actors = res.credits?.cast?.mapNotNull { if (it.name != null) Actor(it.name, fixUrl(it.profilePath)) else null }
        val trailerKey = res.videos?.results?.firstOrNull { it.site == "YouTube" }?.key
        val imdbId = res.externalIds?.imdbId

        val episodes = mutableListOf<Episode>()
        res.seasons?.forEach { seasonItem ->
            val seasonNumber = seasonItem.seasonNumber ?: return@forEach
            if (seasonNumber == 0) return@forEach // skip specials
            val seasonUrl = "${tmdbBaseUrl}/tv/${id}/season/${seasonNumber}?api_key=${tmdbApiKey}&language=tr-TR"
            val seasonDetail = try {
                app.get(seasonUrl, timeout = 10).parsedSafe<TmdbSeasonDetail>()
            } catch (_: Throwable) {
                null
            }
            seasonDetail?.episodes?.forEach { ep ->
                val epNumber = ep.episodeNumber ?: return@forEach
                val epData = if (!imdbId.isNullOrBlank()) "https://vidsrc.me/embed/tv/$imdbId/$seasonNumber/$epNumber"
                             else "https://vidsrc.me/embed/tv/$id/$seasonNumber/$epNumber"
                episodes.add(newEpisode(epData) {
                    this.name = ep.name
                    this.season = seasonNumber
                    this.episode = epNumber
                    this.description = ep.overview
                    this.posterUrl = fixUrl(ep.stillPath)
                })
            }
        }

        return newTvSeriesLoadResponse(title.trim(), "https://www.themoviedb.org/tv/$id", TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.score = Score.from10(rating)
            addActors(actors)
            if (!trailerKey.isNullOrBlank()) {
                addTrailer("https://www.youtube.com/embed/${trailerKey}")
            }
        }
    }

    private suspend fun movieDetail(url: String): LoadResponse? {
        val id = url.substringAfter("/media/detail/").substringBefore("/").toIntOrNull()
        if (id != null) {
            return loadTmdbMovie(id)
        }
        return null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("SWX", "data » $data")
        var cleanData = data.trim()

        if (cleanData.contains("/public/api/")) {
            cleanData = cleanData.substringAfter("/public/api/").trim()
        } else if (cleanData.startsWith(mainUrl)) {
            cleanData = cleanData.removePrefix(mainUrl).removePrefix("/").trim()
        }

        Log.d("SWX", "cleanData » $cleanData")

        if (cleanData.contains("snwaxdop")) {
            callback.invoke(
                newExtractorLink(
                    source = "SineWix",
                    name = "SineWix",
                    url = cleanData,
                    ExtractorLinkType.VIDEO
                ) {
                    this.quality = Qualities.Unknown.value
                    this.headers = headers
                }
            )
            return true
        }

        if (cleanData.contains("vidsrc.me/embed/")) {
            val embedUrlMe = cleanData
            val embedUrlTo = cleanData.replace("vidsrc.me", "vidsrc.to")
            val embedUrlAuto = if (cleanData.contains("/movie/")) {
                cleanData.replace("https://vidsrc.me/embed/movie/", "https://autoembed.co/movie/imdb/")
            } else {
                val match = Regex("""vidsrc\.me/embed/tv/([^/]+)/(\d+)/(\d+)""").find(cleanData)
                if (match != null) {
                    val id = match.groupValues[1]
                    val s = match.groupValues[2]
                    val e = match.groupValues[3]
                    "https://autoembed.co/tv/imdb/$id-$s-$e"
                } else {
                    cleanData
                }
            }

            listOf(embedUrlMe, embedUrlTo, embedUrlAuto).forEach { embedUrl ->
                try {
                    loadExtractor(embedUrl, mainUrl, subtitleCallback, callback)
                } catch (_: Throwable) {}
            }
            return true
        }

        if (cleanData.startsWith("tt")) {
            // IMDb ID format: "tt2185037" or "tt2185037:1:2"
            val parts = cleanData.split(":")
            if (parts.size == 1) { // movie
                val imdbId = parts[0]
                listOf(
                    "https://vidsrc.me/embed/movie/$imdbId",
                    "https://vidsrc.to/embed/movie/$imdbId",
                    "https://autoembed.co/movie/imdb/$imdbId"
                ).forEach { embedUrl ->
                    try { loadExtractor(embedUrl, mainUrl, subtitleCallback, callback) } catch (_: Throwable) {}
                }
            } else if (parts.size >= 3) { // tv
                val imdbId = parts[0]
                val season = parts[1]
                val episode = parts[2]
                listOf(
                    "https://vidsrc.me/embed/tv/$imdbId/$season/$episode",
                    "https://vidsrc.to/embed/tv/$imdbId/$season/$episode",
                    "https://autoembed.co/tv/imdb/$imdbId-$season-$episode"
                ).forEach { embedUrl ->
                    try { loadExtractor(embedUrl, mainUrl, subtitleCallback, callback) } catch (_: Throwable) {}
                }
            }
            return true
        }

        if (cleanData.startsWith("tmdb:")) {
            // TMDB ID format: "tmdb:12345" or "tmdb:12345:1:2"
            val parts = cleanData.split(":")
            if (parts.size == 2) { // movie
                val tmdbId = parts[1]
                listOf(
                    "https://vidsrc.me/embed/movie/$tmdbId",
                    "https://vidsrc.to/embed/movie/$tmdbId",
                    "https://autoembed.co/movie/tmdb/$tmdbId"
                ).forEach { embedUrl ->
                    try { loadExtractor(embedUrl, mainUrl, subtitleCallback, callback) } catch (_: Throwable) {}
                }
            } else if (parts.size >= 4) { // tv
                val tmdbId = parts[1]
                val season = parts[2]
                val episode = parts[3]
                listOf(
                    "https://vidsrc.me/embed/tv/$tmdbId/$season/$episode",
                    "https://vidsrc.to/embed/tv/$tmdbId/$season/$episode",
                    "https://autoembed.co/tv/tmdb/$tmdbId-$season-$episode"
                ).forEach { embedUrl ->
                    try { loadExtractor(embedUrl, mainUrl, subtitleCallback, callback) } catch (_: Throwable) {}
                }
            }
            return true
        }

        if (cleanData.startsWith("http://") || cleanData.startsWith("https://")) {
            loadExtractor(cleanData, "${mainUrl}/", subtitleCallback, callback)
            return true
        }

        if (cleanData.isNotEmpty()) {
            listOf(
                "https://vidsrc.me/embed/movie/$cleanData",
                "https://vidsrc.to/embed/movie/$cleanData",
                "https://autoembed.co/movie/imdb/$cleanData"
            ).forEach { embedUrl ->
                try { loadExtractor(embedUrl, mainUrl, subtitleCallback, callback) } catch (_: Throwable) {}
            }
        }

        return true
    }
}
