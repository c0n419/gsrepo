package com.gsrepo

import com.fasterxml.jackson.annotation.JsonProperty

data class SineSonuc(
    @JsonProperty("data") val data: List<SineData>
)

data class SineData(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("vote_average") val vote: Double? = null,
)

data class SineMovie(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("tmdb_id") val tmdb: Int? = null,
    @JsonProperty("imdb_external_id") val imdb: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("vote_average") val vote: Double? = null,
    @JsonProperty("runtime") val runtime: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("preview_path") val trailer: String? = null,
    @JsonProperty("casterslist") val cast: List<SineCast>? = null,
    @JsonProperty("genres") val genres: List<SineGenre>? = null,
    @JsonProperty("videos") val videos: List<SineVideo>? = null,
)

data class SineSerie(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("tmdb_id") val tmdb: Int? = null,
    @JsonProperty("imdb_external_id") val imdb: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("vote_average") val vote: String? = null,
    @JsonProperty("runtime") val runtime: String? = null,
    @JsonProperty("first_air_date") val releaseDate: String? = null,
    @JsonProperty("preview_path") val trailer: String? = null,
    @JsonProperty("casterslist") val cast: List<SineCast>? = null,
    @JsonProperty("genreslist") val genres: List<String>? = null,
    @JsonProperty("seasons") val seasons: List<SineSeason>? = null,
)

data class SineCast(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("profile_path") val profilePath: String? = null,
)

data class SineGenre(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("movie_id") val movieId: Int? = null,
    @JsonProperty("name") val name: String? = null,
)

data class SineVideo(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("movie_id") val movieId: Int? = null,
    @JsonProperty("header") val header: String? = null,
    @JsonProperty("useragent") val userAgent: String? = null,
    @JsonProperty("link") val link: String? = null,
    @JsonProperty("lang") val lang: String? = null,
    @JsonProperty("hls") val hls: Int? = null,
    @JsonProperty("supported_hosts") val supHosts: Int? = null,
)

data class SineSeason(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("tmdb_id") val tmdb: Int? = null,
    @JsonProperty("serie_id") val serieId: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("episodes") val episodes: List<SineEpisode>? = null,
)

data class SineEpisode(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("tmdb_id") val tmdb: Int? = null,
    @JsonProperty("season_id") val seasonId: Int? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("still_path") val stillPath: String? = null,
    @JsonProperty("videos") val videos: List<SineVideo>? = null,
)

data class SineSearch(
    @JsonProperty("search") val search: List<SineData>? = null,
)

// TMDB Models
data class TmdbPageResult(
    @JsonProperty("results") val results: List<TmdbItem>? = null
)

data class TmdbItem(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("media_type") val mediaType: String? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null
)

data class TmdbMovieDetail(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("genres") val genres: List<TmdbGenre>? = null,
    @JsonProperty("credits") val credits: TmdbCredits? = null,
    @JsonProperty("videos") val videos: TmdbVideos? = null,
    @JsonProperty("external_ids") val externalIds: TmdbExternalIds? = null
)

data class TmdbTvDetail(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("original_name") val originalName: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("backdrop_path") val backdropPath: String? = null,
    @JsonProperty("vote_average") val voteAverage: Double? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("genres") val genres: List<TmdbGenre>? = null,
    @JsonProperty("credits") val credits: TmdbCredits? = null,
    @JsonProperty("videos") val videos: TmdbVideos? = null,
    @JsonProperty("seasons") val seasons: List<TmdbSeasonSummary>? = null,
    @JsonProperty("external_ids") val externalIds: TmdbExternalIds? = null
)

data class TmdbGenre(
    @JsonProperty("name") val name: String? = null
)

data class TmdbCredits(
    @JsonProperty("cast") val cast: List<TmdbCast>? = null
)

data class TmdbCast(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("profile_path") val profilePath: String? = null
)

data class TmdbVideos(
    @JsonProperty("results") val results: List<TmdbVideo>? = null
)

data class TmdbVideo(
    @JsonProperty("key") val key: String? = null,
    @JsonProperty("site") val site: String? = null,
    @JsonProperty("type") val type: String? = null
)

data class TmdbSeasonSummary(
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("episode_count") val episodeCount: Int? = null,
    @JsonProperty("name") val name: String? = null
)

data class TmdbSeasonDetail(
    @JsonProperty("episodes") val episodes: List<TmdbEpisode>? = null
)

data class TmdbEpisode(
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("still_path") val stillPath: String? = null
)

data class TmdbExternalIds(
    @JsonProperty("imdb_id") val imdbId: String? = null
)
