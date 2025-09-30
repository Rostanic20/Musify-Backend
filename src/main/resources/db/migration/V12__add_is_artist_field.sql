-- Add is_artist field to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_artist BOOLEAN DEFAULT FALSE;

-- Create index for filtering artist users
CREATE INDEX IF NOT EXISTS idx_users_artist ON users(is_artist) WHERE is_artist = true;

-- Update existing users based on any artist-related logic if needed
-- For now, all existing users will default to is_artist = false