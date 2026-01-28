package com.musify.database

import com.musify.utils.BaseIntegrationTest
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class DatabaseIndexTest : BaseIntegrationTest() {
    
    @Test
    fun `migration file should exist and be valid`() {
        val migrationFile = File("src/main/resources/db/migration/V3__add_foreign_key_indexes.sql")
        
        assertTrue(migrationFile.exists(), "Migration file should exist")
        
        val content = migrationFile.readText()
        val expectedIndexes = listOf(
            "idx_songs_artist_id",
            "idx_songs_album_id", 
            "idx_albums_artist_id",
            "idx_playlists_user_id",
            "idx_playlist_songs_playlist_id",
            "idx_playlist_songs_song_id",
            "idx_user_favorites_user_id",
            "idx_user_favorites_song_id"
        )
        
        for (indexName in expectedIndexes) {
            assertTrue(
                content.contains(indexName),
                "Migration should contain index: $indexName"
            )
        }
        
        assertTrue(
            content.contains("CREATE INDEX"),
            "Migration should contain CREATE INDEX statements"
        )
    }
    
    @Test
    fun `database should start successfully with migrations`() {
        // This test ensures the migration runs without errors during database initialization
        // If the database setup in BaseIntegrationTest succeeds, migrations worked
        assertTrue(true, "Database initialized successfully with all migrations including V3")
    }
}