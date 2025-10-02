-- Add indexes for all foreign key relationships to improve join performance
-- These indexes are crucial for query performance in production

-- Songs table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_artist_id ON songs(artist_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_album_id ON songs(album_id);

-- Albums table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_albums_artist_id ON albums(artist_id);

-- Playlists table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_playlists_user_id ON playlists(user_id);

-- Playlist_songs table foreign keys (composite index for both directions)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_playlist_songs_playlist_id ON playlist_songs(playlist_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_playlist_songs_song_id ON playlist_songs(song_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_playlist_songs_composite ON playlist_songs(playlist_id, song_id);

-- User_favorites table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_favorites_user_id ON user_favorites(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_favorites_song_id ON user_favorites(song_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_favorites_composite ON user_favorites(user_id, song_id);

-- Listening_history table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_listening_history_user_id ON listening_history(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_listening_history_song_id ON listening_history(song_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_listening_history_composite ON listening_history(user_id, song_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_listening_history_played_at ON listening_history(played_at DESC);

-- User_queues table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_queues_user_id ON user_queues(user_id);

-- Queue_items table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_queue_items_queue_id ON queue_items(queue_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_queue_items_song_id ON queue_items(song_id);

-- User_follows table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_follows_follower_id ON user_follows(follower_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_follows_followed_id ON user_follows(followed_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_follows_composite ON user_follows(follower_id, followed_id);

-- Artist_follows table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_artist_follows_user_id ON artist_follows(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_artist_follows_artist_id ON artist_follows(artist_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_artist_follows_composite ON artist_follows(user_id, artist_id);

-- Playlist_follows table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_playlist_follows_user_id ON playlist_follows(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_playlist_follows_playlist_id ON playlist_follows(playlist_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_playlist_follows_composite ON playlist_follows(user_id, playlist_id);

-- Shared_items table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_shared_items_shared_by ON shared_items(shared_by);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_shared_items_shared_with ON shared_items(shared_with);

-- Activity_feed table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_activity_feed_user_id ON activity_feed(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_activity_feed_target_user_id ON activity_feed(target_user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_activity_feed_created_at ON activity_feed(created_at DESC);

-- Podcast tables foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_podcast_episodes_show_id ON podcast_episodes(show_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_podcast_subscriptions_user_id ON podcast_subscriptions(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_podcast_subscriptions_show_id ON podcast_subscriptions(show_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_podcast_progress_user_id ON podcast_progress(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_podcast_progress_episode_id ON podcast_progress(episode_id);

-- Content_reports table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_content_reports_reported_by ON content_reports(reported_by);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_content_reports_reviewed_by ON content_reports(reviewed_by);

-- Audit_log table indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_log_user_id ON audit_log(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at DESC);

-- OAuth_providers table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_oauth_providers_user_id ON oauth_providers(user_id);

-- Subscriptions table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_subscriptions_plan_id ON subscriptions(plan_id);

-- Payment_methods table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_methods_user_id ON payment_methods(user_id);

-- Payment_history table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_history_user_id ON payment_history(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_history_subscription_id ON payment_history(subscription_id);

-- Playlist_collaborators table foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_playlist_collaborators_playlist_id ON playlist_collaborators(playlist_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_playlist_collaborators_user_id ON playlist_collaborators(user_id);

-- Search tables foreign keys
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_search_history_user_id ON search_history(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_search_clicks_search_history_id ON search_clicks(search_history_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_saved_searches_user_id ON saved_searches(user_id);

-- Streaming session tables
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_streaming_sessions_user_id ON streaming_sessions(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_streaming_sessions_song_id ON streaming_sessions(song_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_streaming_session_events_session_id ON streaming_session_events(session_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_buffer_metrics_session_id ON buffer_metrics(session_id);

-- Offline download tables
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_offline_downloads_user_id ON offline_downloads(user_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_offline_downloads_song_id ON offline_downloads(song_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_smart_download_preferences_user_id ON user_smart_download_preferences(user_id);

-- Performance indexes for common queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_created_at ON songs(created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_albums_release_date ON albums(release_date DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_created_at ON users(created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_artists_monthly_listeners ON artists(monthly_listeners DESC);

-- Composite indexes for performance-critical queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_genre_play_count ON songs(genre, play_count DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_listening_time ON listening_history(user_id, played_at DESC);