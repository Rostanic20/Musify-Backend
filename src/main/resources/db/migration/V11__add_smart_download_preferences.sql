-- Add smart download preferences table
CREATE TABLE IF NOT EXISTS user_smart_download_preferences (
    user_id INT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    enabled BOOLEAN DEFAULT TRUE,
    wifi_only BOOLEAN DEFAULT TRUE,
    max_storage_percent INT DEFAULT 20,
    preferred_quality VARCHAR(20) DEFAULT 'HIGH',
    auto_delete_after_days INT DEFAULT 30,
    enable_predictions BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add index for quick lookups
CREATE INDEX idx_smart_download_prefs_user_id ON user_smart_download_preferences(user_id);

-- Add prediction accuracy tracking table
CREATE TABLE IF NOT EXISTS smart_download_predictions (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    song_id INT NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
    device_id VARCHAR(255) NOT NULL,
    prediction_type VARCHAR(50) NOT NULL,
    confidence DECIMAL(3,2) NOT NULL,
    downloaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    played_at TIMESTAMP,
    days_to_play INT,
    CONSTRAINT confidence_range CHECK (confidence >= 0 AND confidence <= 1)
);

-- Add indexes for prediction tracking
CREATE INDEX idx_smart_predictions_user_id ON smart_download_predictions(user_id);
CREATE INDEX idx_smart_predictions_song_id ON smart_download_predictions(song_id);
CREATE INDEX idx_smart_predictions_type ON smart_download_predictions(prediction_type);
CREATE INDEX idx_smart_predictions_downloaded_at ON smart_download_predictions(downloaded_at);