-- V10__add_development_seed_data.sql
-- Development seed data for testing
-- Only inserts data if it doesn't already exist

-- Create test users only if they don't exist
INSERT INTO users (username, email, password_hash, display_name, is_verified, is_active, is_artist, created_at, updated_at)
VALUES 
    -- Regular user account (password: test1234)
    ('testuser', 'testuser@example.com', '$2a$12$Lk7W9CJn0C2dKyD0FjzYOeUZmQH7gKyPU6H0SSz1QKQdpGSFyK2Oy', 'Test User', true, true, false, NOW(), NOW()),
    -- Artist account (password: artist1234)
    ('testartist', 'testartist@example.com', '$2a$12$4xq3Lc1QOxPxXf5B.fXPgeE5sJz7Rf8hMXUgPSdqPMqYm1INAjhOK', 'Test Artist', true, true, true, NOW(), NOW()),
    -- Premium user account (password: premium1234)
    ('premiuser', 'premium@example.com', '$2a$12$vX3kGLR9kS7BZiU.pTr86uzTVQS7r5AV3Hw.jBBQJDy4O0OxKfFZW', 'Premium User', true, true, false, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Add subscription for premium user
INSERT INTO subscriptions (user_id, plan, status, created_at, updated_at, expires_at)
SELECT id, 'PREMIUM', 'ACTIVE', NOW(), NOW(), NOW() + INTERVAL '30 days'
FROM users WHERE username = 'premiuser'
ON CONFLICT DO NOTHING;

-- Create some test artists
INSERT INTO artists (user_id, artist_name, bio, created_at, updated_at)
SELECT id, display_name, 'A talented test artist for development', NOW(), NOW()
FROM users WHERE username = 'testartist'
ON CONFLICT (user_id) DO NOTHING;

-- Log the seed data creation
DO $$
BEGIN
    RAISE NOTICE 'Development seed data created:';
    RAISE NOTICE '  - testuser / test1234 (Regular user)';
    RAISE NOTICE '  - testartist / artist1234 (Artist account)';
    RAISE NOTICE '  - premiuser / premium1234 (Premium user)';
END $$;