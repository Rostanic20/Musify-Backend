-- Add buffer metrics table for tracking streaming performance
CREATE TABLE IF NOT EXISTS buffer_metrics (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    session_id VARCHAR(255) NOT NULL,
    device_type VARCHAR(50) NOT NULL,
    average_buffer_health DOUBLE PRECISION NOT NULL,
    average_buffer_level DOUBLE PRECISION DEFAULT 0.0,
    total_starvations INTEGER NOT NULL,
    total_rebuffer_time INTEGER NOT NULL, -- seconds
    average_bitrate INTEGER NOT NULL, -- kbps
    quality_distribution TEXT NOT NULL, -- JSON: quality -> seconds
    network_conditions TEXT NOT NULL, -- JSON: NetworkProfile
    session_start TIMESTAMP NOT NULL,
    session_end TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_buffer_metrics_session_id ON buffer_metrics(session_id);
CREATE INDEX idx_buffer_metrics_user_session ON buffer_metrics(user_id, session_start DESC);

-- Add buffer-related columns to streaming_sessions table if not exists
ALTER TABLE streaming_sessions 
ADD COLUMN IF NOT EXISTS buffer_health DOUBLE PRECISION DEFAULT 1.0,
ADD COLUMN IF NOT EXISTS rebuffer_count INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS quality_changes INTEGER DEFAULT 0;