package com.musify.routes

import com.musify.utils.getUserId
import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.*
import com.musify.domain.entities.*
import com.musify.presentation.dto.PodcastShow
import com.musify.presentation.dto.PodcastEpisode
import com.musify.presentation.dto.EpisodeProgress
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

fun Route.podcastRoutes() {
    route("/podcasts") {
        get {
            val category = call.request.queryParameters["category"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            
            val shows = dbQuery {
                val query = if (category != null) {
                    PodcastShows.select { PodcastShows.category eq category }
                } else {
                    PodcastShows.selectAll()
                }
                
                query.limit(limit).map { row ->
                    val episodeCount = PodcastEpisodes
                        .select { PodcastEpisodes.showId eq row[PodcastShows.id] }
                        .count()
                    
                    val latestEpisode = PodcastEpisodes
                        .select { PodcastEpisodes.showId eq row[PodcastShows.id] }
                        .orderBy(PodcastEpisodes.publishedAt, SortOrder.DESC)
                        .limit(1)
                        .singleOrNull()
                    
                    PodcastShow(
                        id = row[PodcastShows.id].value,
                        title = row[PodcastShows.title],
                        description = row[PodcastShows.description],
                        author = row[PodcastShows.author],
                        coverArt = row[PodcastShows.coverArt],
                        category = row[PodcastShows.category],
                        language = row[PodcastShows.language],
                        rssUrl = row[PodcastShows.rssUrl],
                        websiteUrl = row[PodcastShows.websiteUrl],
                        explicit = row[PodcastShows.explicit],
                        episodeCount = episodeCount.toInt(),
                        latestEpisodeDate = latestEpisode?.get(PodcastEpisodes.publishedAt)?.toString(),
                        createdAt = row[PodcastShows.createdAt].toString(),
                        updatedAt = row[PodcastShows.updatedAt].toString()
                    )
                }
            }
            
            call.respond(shows)
        }
        
        get("/{showId}") {
            val showId = call.parameters["showId"]?.toIntOrNull()
            if (showId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            
            val show = dbQuery {
                PodcastShows.select { PodcastShows.id eq showId }
                    .map { row ->
                        val episodeCount = PodcastEpisodes
                            .select { PodcastEpisodes.showId eq row[PodcastShows.id] }
                            .count()
                        
                        PodcastShow(
                            id = row[PodcastShows.id].value,
                            title = row[PodcastShows.title],
                            description = row[PodcastShows.description],
                            author = row[PodcastShows.author],
                            coverArt = row[PodcastShows.coverArt],
                            category = row[PodcastShows.category],
                            language = row[PodcastShows.language],
                            rssUrl = row[PodcastShows.rssUrl],
                            websiteUrl = row[PodcastShows.websiteUrl],
                            explicit = row[PodcastShows.explicit],
                            episodeCount = episodeCount.toInt(),
                            createdAt = row[PodcastShows.createdAt].toString(),
                            updatedAt = row[PodcastShows.updatedAt].toString()
                        )
                    }.singleOrNull()
            }
            
            if (show == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(show)
            }
        }
        
        get("/{showId}/episodes") {
            val showId = call.parameters["showId"]?.toIntOrNull()
            val userId = call.getUserId()
            
            if (showId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            
            val episodes = dbQuery {
                (PodcastEpisodes innerJoin PodcastShows)
                    .select { PodcastEpisodes.showId eq showId }
                    .orderBy(PodcastEpisodes.publishedAt, SortOrder.DESC)
                    .map { row ->
                        val progress = if (userId != null) {
                            PodcastProgress
                                .select {
                                    (PodcastProgress.userId eq userId) and
                                    (PodcastProgress.episodeId eq row[PodcastEpisodes.id])
                                }
                                .singleOrNull()?.let {
                                    EpisodeProgress(
                                        position = it[PodcastProgress.position],
                                        completed = it[PodcastProgress.completed],
                                        updatedAt = it[PodcastProgress.updatedAt].toString()
                                    )
                                }
                        } else null
                        
                        PodcastEpisode(
                            id = row[PodcastEpisodes.id].value,
                            showId = row[PodcastEpisodes.showId].value,
                            showTitle = row[PodcastShows.title],
                            title = row[PodcastEpisodes.title],
                            description = row[PodcastEpisodes.description],
                            audioUrl = row[PodcastEpisodes.audioUrl],
                            duration = row[PodcastEpisodes.duration],
                            episodeNumber = row[PodcastEpisodes.episodeNumber],
                            season = row[PodcastEpisodes.season],
                            publishedAt = row[PodcastEpisodes.publishedAt].toString(),
                            progress = progress,
                            createdAt = row[PodcastEpisodes.createdAt].toString()
                        )
                    }
            }
            
            call.respond(episodes)
        }
        
        authenticate("auth-jwt") {
            get("/subscriptions") {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                
                val subscriptions = dbQuery {
                    (PodcastSubscriptions innerJoin PodcastShows)
                        .select { PodcastSubscriptions.userId eq userId }
                        .map { row ->
                            val episodeCount = PodcastEpisodes
                                .select { PodcastEpisodes.showId eq row[PodcastShows.id] }
                                .count()
                            
                            PodcastShow(
                                id = row[PodcastShows.id].value,
                                title = row[PodcastShows.title],
                                description = row[PodcastShows.description],
                                author = row[PodcastShows.author],
                                coverArt = row[PodcastShows.coverArt],
                                category = row[PodcastShows.category],
                                language = row[PodcastShows.language],
                                rssUrl = row[PodcastShows.rssUrl],
                                websiteUrl = row[PodcastShows.websiteUrl],
                                explicit = row[PodcastShows.explicit],
                                episodeCount = episodeCount.toInt(),
                                createdAt = row[PodcastShows.createdAt].toString(),
                                updatedAt = row[PodcastShows.updatedAt].toString()
                            )
                        }
                }
                
                call.respond(subscriptions)
            }
            
            post("/subscribe/{showId}") {
                val userId = call.getUserId()
                val showId = call.parameters["showId"]?.toIntOrNull()
                
                if (userId == null || showId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                
                dbQuery {
                    val exists = PodcastSubscriptions.select {
                        (PodcastSubscriptions.userId eq userId) and
                        (PodcastSubscriptions.showId eq showId)
                    }.count() > 0
                    
                    if (!exists) {
                        PodcastSubscriptions.insert {
                            it[PodcastSubscriptions.userId] = userId
                            it[PodcastSubscriptions.showId] = showId
                            it[createdAt] = LocalDateTime.now()
                        }
                    }
                }
                
                call.respond(HttpStatusCode.OK)
            }
            
            delete("/subscribe/{showId}") {
                val userId = call.getUserId()
                val showId = call.parameters["showId"]?.toIntOrNull()
                
                if (userId == null || showId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                
                dbQuery {
                    PodcastSubscriptions.deleteWhere {
                        (PodcastSubscriptions.userId eq userId) and
                        (PodcastSubscriptions.showId eq showId)
                    }
                }
                
                call.respond(HttpStatusCode.OK)
            }
            
            post("/episodes/{episodeId}/progress") {
                val userId = call.getUserId()
                val episodeId = call.parameters["episodeId"]?.toIntOrNull()
                val position = call.request.queryParameters["position"]?.toIntOrNull()
                val completed = call.request.queryParameters["completed"]?.toBoolean() ?: false
                
                if (userId == null || episodeId == null || position == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                
                dbQuery {
                    val exists = PodcastProgress.select {
                        (PodcastProgress.userId eq userId) and
                        (PodcastProgress.episodeId eq episodeId)
                    }.count() > 0
                    
                    if (exists) {
                        PodcastProgress.update({
                            (PodcastProgress.userId eq userId) and
                            (PodcastProgress.episodeId eq episodeId)
                        }) {
                            it[PodcastProgress.position] = position
                            it[PodcastProgress.completed] = completed
                            it[updatedAt] = LocalDateTime.now()
                        }
                    } else {
                        PodcastProgress.insert {
                            it[PodcastProgress.userId] = userId
                            it[PodcastProgress.episodeId] = episodeId
                            it[PodcastProgress.position] = position
                            it[PodcastProgress.completed] = completed
                            it[updatedAt] = LocalDateTime.now()
                        }
                    }
                }
                
                call.respond(HttpStatusCode.OK)
            }
            
            get("/continue-listening") {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                
                val episodes = dbQuery {
                    (PodcastProgress innerJoin PodcastEpisodes innerJoin PodcastShows)
                        .select {
                            (PodcastProgress.userId eq userId) and
                            (PodcastProgress.completed eq false) and
                            (PodcastProgress.position greater 0)
                        }
                        .orderBy(PodcastProgress.updatedAt, SortOrder.DESC)
                        .limit(10)
                        .map { row ->
                            PodcastEpisode(
                                id = row[PodcastEpisodes.id].value,
                                showId = row[PodcastEpisodes.showId].value,
                                showTitle = row[PodcastShows.title],
                                title = row[PodcastEpisodes.title],
                                description = row[PodcastEpisodes.description],
                                audioUrl = row[PodcastEpisodes.audioUrl],
                                duration = row[PodcastEpisodes.duration],
                                episodeNumber = row[PodcastEpisodes.episodeNumber],
                                season = row[PodcastEpisodes.season],
                                publishedAt = row[PodcastEpisodes.publishedAt].toString(),
                                progress = EpisodeProgress(
                                    position = row[PodcastProgress.position],
                                    completed = row[PodcastProgress.completed],
                                    updatedAt = row[PodcastProgress.updatedAt].toString()
                                ),
                                createdAt = row[PodcastEpisodes.createdAt].toString()
                            )
                        }
                }
                
                call.respond(episodes)
            }
        }
    }
}