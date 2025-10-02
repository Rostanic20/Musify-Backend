package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object SongAudioFeatures : IntIdTable() {
    val songId = reference("song_id", Songs, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val energy = double("energy") // 0.0 to 1.0
    val valence = double("valence") // 0.0 to 1.0 (musical positivity)
    val danceability = double("danceability") // 0.0 to 1.0
    val acousticness = double("acousticness") // 0.0 to 1.0
    val instrumentalness = double("instrumentalness") // 0.0 to 1.0
    val speechiness = double("speechiness") // 0.0 to 1.0
    val liveness = double("liveness") // 0.0 to 1.0
    val loudness = double("loudness") // -60 to 0 dB
    val tempo = integer("tempo") // BPM
    val key = integer("key") // 0-11 (pitch class)
    val mode = integer("mode") // 0 or 1 (minor/major)
    val timeSignature = integer("time_signature") // 3-7
}