-- V7: Add search and discovery tables

-- Search history table
CREATE TABLE IF NOT EXISTS search_history (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    query TEXT NOT NULL,
    context VARCHAR(50) DEFAULT 'general',
    result_count INTEGER NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    click_count INTEGER DEFAULT 0,
    session_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_search_history_user_timestamp ON search_history(user_id, timestamp);
CREATE INDEX idx_search_history_query ON search_history(query);

-- Search clicks tracking
CREATE TABLE IF NOT EXISTS search_clicks (
    id SERIAL PRIMARY KEY,
    search_history_id INTEGER NOT NULL REFERENCES search_history(id) ON DELETE CASCADE,
    item_type VARCHAR(50) NOT NULL,
    item_id INTEGER NOT NULL,
    position INTEGER NOT NULL,
    click_time TIMESTAMP NOT NULL,
    dwell_time INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_search_clicks_history ON search_clicks(search_history_id);
CREATE INDEX idx_search_clicks_item ON search_clicks(item_type, item_id);

-- Search analytics
CREATE TABLE IF NOT EXISTS search_analytics (
    id SERIAL PRIMARY KEY,
    search_id VARCHAR(100) UNIQUE NOT NULL,
    user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    query TEXT NOT NULL,
    filters TEXT,
    result_count INTEGER NOT NULL,
    click_through_rate DOUBLE PRECISION DEFAULT 0.0,
    avg_click_position DOUBLE PRECISION DEFAULT 0.0,
    time_to_first_click BIGINT,
    session_duration BIGINT NOT NULL,
    refinement_count INTEGER DEFAULT 0,
    timestamp TIMESTAMP NOT NULL,
    device_type VARCHAR(50),
    client_info TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_search_analytics_timestamp ON search_analytics(timestamp);
CREATE INDEX idx_search_analytics_user ON search_analytics(user_id);

-- Trending searches
CREATE TABLE IF NOT EXISTS trending_searches (
    id SERIAL PRIMARY KEY,
    query VARCHAR(500) NOT NULL,
    count INTEGER NOT NULL,
    previous_count INTEGER DEFAULT 0,
    trend VARCHAR(20) NOT NULL,
    percentage_change DOUBLE PRECISION DEFAULT 0.0,
    category VARCHAR(100),
    period VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_trending_searches_unique ON trending_searches(query, period, timestamp);
CREATE INDEX idx_trending_searches_timestamp ON trending_searches(timestamp);
CREATE INDEX idx_trending_searches_category ON trending_searches(category);

-- Search suggestions
CREATE TABLE IF NOT EXISTS search_suggestions (
    id SERIAL PRIMARY KEY,
    text TEXT NOT NULL,
    suggestion_type VARCHAR(50) NOT NULL,
    weight DOUBLE PRECISION DEFAULT 1.0,
    metadata TEXT,
    language VARCHAR(10) DEFAULT 'en',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_search_suggestions_text ON search_suggestions(text);
CREATE INDEX idx_search_suggestions_type ON search_suggestions(suggestion_type);

-- User search preferences
CREATE TABLE IF NOT EXISTS user_search_preferences (
    id SERIAL PRIMARY KEY,
    user_id INTEGER UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    preferred_genres TEXT,
    excluded_genres TEXT,
    explicit_content BOOLEAN DEFAULT true,
    include_local_content BOOLEAN DEFAULT true,
    search_language VARCHAR(10) DEFAULT 'en',
    autoplay_enabled BOOLEAN DEFAULT true,
    search_history_enabled BOOLEAN DEFAULT true,
    personalized_results BOOLEAN DEFAULT true,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Saved searches
CREATE TABLE IF NOT EXISTS saved_searches (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    query TEXT NOT NULL,
    filters TEXT,
    notifications_enabled BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used TIMESTAMP
);

CREATE UNIQUE INDEX idx_saved_searches_user_name ON saved_searches(user_id, name);

-- Song audio features for advanced search
CREATE TABLE IF NOT EXISTS song_audio_features (
    song_id INTEGER PRIMARY KEY REFERENCES songs(id) ON DELETE CASCADE,
    tempo DOUBLE PRECISION NOT NULL,
    energy DOUBLE PRECISION NOT NULL CHECK (energy >= 0 AND energy <= 1),
    danceability DOUBLE PRECISION NOT NULL CHECK (danceability >= 0 AND danceability <= 1),
    valence DOUBLE PRECISION NOT NULL CHECK (valence >= 0 AND valence <= 1),
    acousticness DOUBLE PRECISION NOT NULL CHECK (acousticness >= 0 AND acousticness <= 1),
    instrumentalness DOUBLE PRECISION NOT NULL CHECK (instrumentalness >= 0 AND instrumentalness <= 1),
    speechiness DOUBLE PRECISION NOT NULL CHECK (speechiness >= 0 AND speechiness <= 1),
    liveness DOUBLE PRECISION NOT NULL CHECK (liveness >= 0 AND liveness <= 1),
    loudness DOUBLE PRECISION NOT NULL,
    key INTEGER NOT NULL CHECK (key >= 0 AND key <= 11),
    mode INTEGER NOT NULL CHECK (mode IN (0, 1)),
    time_signature INTEGER NOT NULL,
    analyzed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_song_audio_features_tempo ON song_audio_features(tempo);
CREATE INDEX idx_song_audio_features_energy ON song_audio_features(energy);
CREATE INDEX idx_song_audio_features_danceability ON song_audio_features(danceability);
CREATE INDEX idx_song_audio_features_valence ON song_audio_features(valence);

-- Search index for optimized full-text search
CREATE TABLE IF NOT EXISTS search_index (
    id SERIAL PRIMARY KEY,
    item_type VARCHAR(50) NOT NULL,
    item_id INTEGER NOT NULL,
    search_text TEXT NOT NULL,
    popularity INTEGER DEFAULT 0,
    boost_score DOUBLE PRECISION DEFAULT 1.0,
    language VARCHAR(10) DEFAULT 'en',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_search_index_item ON search_index(item_type, item_id);
CREATE INDEX idx_search_index_text ON search_index USING gin(to_tsvector('english', search_text));
CREATE INDEX idx_search_index_popularity ON search_index(popularity);

-- Voice search history
CREATE TABLE IF NOT EXISTS voice_search_history (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    audio_url TEXT,
    transcription TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    language VARCHAR(10) NOT NULL,
    search_history_id INTEGER REFERENCES search_history(id) ON DELETE SET NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_voice_search_history_user ON voice_search_history(user_id, timestamp);

-- Add full-text search capabilities to existing tables
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Add trigram indexes for fuzzy search
CREATE INDEX IF NOT EXISTS idx_songs_title_trgm ON songs USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_artists_name_trgm ON artists USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_albums_title_trgm ON albums USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_playlists_name_trgm ON playlists USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_users_username_trgm ON users USING gin (username gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_users_display_name_trgm ON users USING gin (display_name gin_trgm_ops) WHERE display_name IS NOT NULL;