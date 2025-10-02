-- Add song audio features table for content-based recommendations
CREATE TABLE IF NOT EXISTS song_audio_features (
    id SERIAL PRIMARY KEY,
    song_id INTEGER NOT NULL UNIQUE REFERENCES songs(id) ON DELETE CASCADE,
    energy DOUBLE PRECISION NOT NULL CHECK (energy >= 0 AND energy <= 1),
    valence DOUBLE PRECISION NOT NULL CHECK (valence >= 0 AND valence <= 1),
    danceability DOUBLE PRECISION NOT NULL CHECK (danceability >= 0 AND danceability <= 1),
    acousticness DOUBLE PRECISION NOT NULL CHECK (acousticness >= 0 AND acousticness <= 1),
    instrumentalness DOUBLE PRECISION NOT NULL CHECK (instrumentalness >= 0 AND instrumentalness <= 1),
    speechiness DOUBLE PRECISION NOT NULL CHECK (speechiness >= 0 AND speechiness <= 1),
    liveness DOUBLE PRECISION NOT NULL CHECK (liveness >= 0 AND liveness <= 1),
    loudness DOUBLE PRECISION NOT NULL CHECK (loudness >= -60 AND loudness <= 0),
    tempo INTEGER NOT NULL CHECK (tempo > 0 AND tempo < 300),
    key INTEGER NOT NULL CHECK (key >= 0 AND key <= 11),
    mode INTEGER NOT NULL CHECK (mode IN (0, 1)),
    time_signature INTEGER NOT NULL CHECK (time_signature >= 3 AND time_signature <= 7),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index for faster lookups
CREATE INDEX idx_song_audio_features_energy ON song_audio_features(energy);
CREATE INDEX idx_song_audio_features_valence ON song_audio_features(valence);
CREATE INDEX idx_song_audio_features_tempo ON song_audio_features(tempo);
CREATE INDEX idx_song_audio_features_acousticness ON song_audio_features(acousticness);

-- Add some sample audio features for existing songs (for testing)
-- In production, these would be populated by an audio analysis service
INSERT INTO song_audio_features (song_id, energy, valence, danceability, acousticness, instrumentalness, speechiness, liveness, loudness, tempo, key, mode, time_signature)
SELECT 
    id,
    0.5 + (RANDOM() * 0.5), -- energy (0.5-1.0)
    RANDOM(), -- valence (0-1)
    0.4 + (RANDOM() * 0.6), -- danceability (0.4-1.0)
    RANDOM(), -- acousticness (0-1)
    RANDOM() * 0.3, -- instrumentalness (0-0.3)
    RANDOM() * 0.2, -- speechiness (0-0.2)
    RANDOM() * 0.3, -- liveness (0-0.3)
    -20 + (RANDOM() * 15), -- loudness (-20 to -5)
    80 + FLOOR(RANDOM() * 100)::INTEGER, -- tempo (80-180)
    FLOOR(RANDOM() * 12)::INTEGER, -- key (0-11)
    ROUND(RANDOM())::INTEGER, -- mode (0 or 1)
    4 -- time_signature (4/4 for simplicity)
FROM songs
LIMIT 100; -- Only populate for first 100 songs for testing