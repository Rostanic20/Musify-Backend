package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.database.DatabaseFactory
import com.musify.database.tables.UserTasteProfiles
import com.musify.domain.entities.*
import com.musify.domain.repository.UserTasteProfileRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlinx.coroutines.Dispatchers
import java.time.LocalDateTime

/**
 * Database implementation of UserTasteProfileRepository using PostgreSQL/H2
 */
class UserTasteProfileRepositoryImpl : UserTasteProfileRepository {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Serializable versions for JSON storage
    @Serializable
    data class SerializableAudioPreferences(
        val energyMin: Double,
        val energyMax: Double,
        val valenceMin: Double,
        val valenceMax: Double,
        val danceabilityMin: Double,
        val danceabilityMax: Double,
        val acousticnessMin: Double,
        val acousticnessMax: Double,
        val instrumentalnessMin: Double,
        val instrumentalnessMax: Double,
        val tempoMin: Int,
        val tempoMax: Int,
        val loudnessMin: Double,
        val loudnessMax: Double
    )
    
    @Serializable
    data class SerializableMusicPreference(
        val preferredGenres: List<String>,
        val preferredEnergy: Double,
        val preferredValence: Double,
        val preferredTempo: Int
    )
    
    override suspend fun findByUserId(userId: Int): UserTasteProfile? {
        return dbQuery {
            UserTasteProfiles.select { UserTasteProfiles.userId eq userId }
                .mapNotNull { rowToUserTasteProfile(it) }
                .singleOrNull()
        }
    }
    
    override suspend fun save(profile: UserTasteProfile): Result<UserTasteProfile> {
        return try {
            dbQuery {
                val existingProfile = UserTasteProfiles.select { UserTasteProfiles.userId eq profile.userId }.singleOrNull()
                
                if (existingProfile != null) {
                    // Update existing profile
                    UserTasteProfiles.update({ UserTasteProfiles.userId eq profile.userId }) {
                        it[topGenres] = json.encodeToString(profile.topGenres)
                        it[topArtists] = json.encodeToString(profile.topArtists)
                        it[audioFeaturePreferences] = json.encodeToString(audioPreferencesToSerializable(profile.audioFeaturePreferences))
                        it[timePreferences] = json.encodeToString(
                            profile.timePreferences.mapKeys { entry -> entry.key.name }
                                .mapValues { entry -> musicPreferenceToSerializable(entry.value) }
                        )
                        it[activityPreferences] = json.encodeToString(
                            profile.activityPreferences.mapKeys { entry -> entry.key.name }
                                .mapValues { entry -> musicPreferenceToSerializable(entry.value) }
                        )
                        it[discoveryScore] = profile.discoveryScore.toBigDecimal()
                        it[mainstreamScore] = profile.mainstreamScore.toBigDecimal()
                        it[lastUpdated] = profile.lastUpdated
                    }
                } else {
                    // Insert new profile
                    UserTasteProfiles.insert {
                        it[userId] = profile.userId
                        it[topGenres] = json.encodeToString(profile.topGenres)
                        it[topArtists] = json.encodeToString(profile.topArtists)
                        it[audioFeaturePreferences] = json.encodeToString(audioPreferencesToSerializable(profile.audioFeaturePreferences))
                        it[timePreferences] = json.encodeToString(
                            profile.timePreferences.mapKeys { entry -> entry.key.name }
                                .mapValues { entry -> musicPreferenceToSerializable(entry.value) }
                        )
                        it[activityPreferences] = json.encodeToString(
                            profile.activityPreferences.mapKeys { entry -> entry.key.name }
                                .mapValues { entry -> musicPreferenceToSerializable(entry.value) }
                        )
                        it[discoveryScore] = profile.discoveryScore.toBigDecimal()
                        it[mainstreamScore] = profile.mainstreamScore.toBigDecimal()
                        it[lastUpdated] = profile.lastUpdated
                        it[createdAt] = LocalDateTime.now()
                    }
                }
            }
            Result.Success(profile)
        } catch (e: Exception) {
            Result.Error("Failed to save user taste profile: ${e.message}")
        }
    }
    
    override suspend fun update(profile: UserTasteProfile): Result<UserTasteProfile> {
        return save(profile) // Same implementation as save handles both insert and update
    }
    
    override suspend fun delete(userId: Int): Result<Unit> {
        return try {
            dbQuery {
                UserTasteProfiles.deleteWhere { UserTasteProfiles.userId eq userId }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to delete user taste profile: ${e.message}")
        }
    }
    
    override suspend fun exists(userId: Int): Result<Boolean> {
        return try {
            val exists = dbQuery {
                UserTasteProfiles.select { UserTasteProfiles.userId eq userId }
                    .count() > 0
            }
            Result.Success(exists)
        } catch (e: Exception) {
            Result.Error("Failed to check if user taste profile exists: ${e.message}")
        }
    }
    
    private fun rowToUserTasteProfile(row: ResultRow): UserTasteProfile? {
        return try {
            val topGenres = json.decodeFromString<Map<String, Double>>(row[UserTasteProfiles.topGenres])
            val topArtists = json.decodeFromString<Map<Int, Double>>(row[UserTasteProfiles.topArtists])
            val audioPrefs = json.decodeFromString<SerializableAudioPreferences>(row[UserTasteProfiles.audioFeaturePreferences])
            val timePrefs = json.decodeFromString<Map<String, SerializableMusicPreference>>(row[UserTasteProfiles.timePreferences])
            val activityPrefs = json.decodeFromString<Map<String, SerializableMusicPreference>>(row[UserTasteProfiles.activityPreferences])
            
            UserTasteProfile(
                userId = row[UserTasteProfiles.userId],
                topGenres = topGenres,
                topArtists = topArtists,
                audioFeaturePreferences = serializableToAudioPreferences(audioPrefs),
                timePreferences = timePrefs.mapKeys { TimeOfDay.valueOf(it.key) }
                    .mapValues { serializableToMusicPreference(it.value) },
                activityPreferences = activityPrefs.mapKeys { UserActivityContext.valueOf(it.key) }
                    .mapValues { serializableToMusicPreference(it.value) },
                discoveryScore = row[UserTasteProfiles.discoveryScore].toDouble(),
                mainstreamScore = row[UserTasteProfiles.mainstreamScore].toDouble(),
                lastUpdated = row[UserTasteProfiles.lastUpdated]
            )
        } catch (e: Exception) {
            null // Return null if data is corrupted or can't be parsed
        }
    }
    
    private fun audioPreferencesToSerializable(prefs: AudioPreferences): SerializableAudioPreferences {
        return SerializableAudioPreferences(
            energyMin = prefs.energy.start,
            energyMax = prefs.energy.endInclusive,
            valenceMin = prefs.valence.start,
            valenceMax = prefs.valence.endInclusive,
            danceabilityMin = prefs.danceability.start,
            danceabilityMax = prefs.danceability.endInclusive,
            acousticnessMin = prefs.acousticness.start,
            acousticnessMax = prefs.acousticness.endInclusive,
            instrumentalnessMin = prefs.instrumentalness.start,
            instrumentalnessMax = prefs.instrumentalness.endInclusive,
            tempoMin = prefs.tempo.first,
            tempoMax = prefs.tempo.last,
            loudnessMin = prefs.loudness.start,
            loudnessMax = prefs.loudness.endInclusive
        )
    }
    
    private fun serializableToAudioPreferences(serializable: SerializableAudioPreferences): AudioPreferences {
        return AudioPreferences(
            energy = serializable.energyMin..serializable.energyMax,
            valence = serializable.valenceMin..serializable.valenceMax,
            danceability = serializable.danceabilityMin..serializable.danceabilityMax,
            acousticness = serializable.acousticnessMin..serializable.acousticnessMax,
            instrumentalness = serializable.instrumentalnessMin..serializable.instrumentalnessMax,
            tempo = serializable.tempoMin..serializable.tempoMax,
            loudness = serializable.loudnessMin..serializable.loudnessMax
        )
    }
    
    private fun musicPreferenceToSerializable(pref: MusicPreference): SerializableMusicPreference {
        return SerializableMusicPreference(
            preferredGenres = pref.preferredGenres,
            preferredEnergy = pref.preferredEnergy,
            preferredValence = pref.preferredValence,
            preferredTempo = pref.preferredTempo
        )
    }
    
    private fun serializableToMusicPreference(serializable: SerializableMusicPreference): MusicPreference {
        return MusicPreference(
            preferredGenres = serializable.preferredGenres,
            preferredEnergy = serializable.preferredEnergy,
            preferredValence = serializable.preferredValence,
            preferredTempo = serializable.preferredTempo
        )
    }
    
    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, DatabaseFactory.database) { block() }
}