-- Migration to add phone number verification support
-- Version: V14
-- Description: Adds phone_number, phone_verified, sms_verification_code, and sms_code_expiry fields to users table

-- Add phone number field (nullable for backward compatibility)
ALTER TABLE users
ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20) NULL;

-- Add phone verified flag
ALTER TABLE users
ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN DEFAULT FALSE;

-- Add SMS verification code field
ALTER TABLE users
ADD COLUMN IF NOT EXISTS sms_verification_code VARCHAR(10) NULL;

-- Add SMS code expiry timestamp
ALTER TABLE users
ADD COLUMN IF NOT EXISTS sms_code_expiry TIMESTAMP NULL;

-- Create unique index on phone_number (partial index to allow NULL values)
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone_number
ON users(phone_number)
WHERE phone_number IS NOT NULL;

-- Make email nullable since users can now register with phone number instead
ALTER TABLE users
ALTER COLUMN email DROP NOT NULL;

-- Add check constraint to ensure either email or phone number is provided
ALTER TABLE users
ADD CONSTRAINT check_email_or_phone
CHECK (email IS NOT NULL OR phone_number IS NOT NULL);

-- Create index for SMS verification lookups
CREATE INDEX IF NOT EXISTS idx_users_sms_code
ON users(sms_verification_code)
WHERE sms_verification_code IS NOT NULL;

-- Update existing users to have email_verified = TRUE if they have an email
-- This prevents breaking existing user accounts
UPDATE users
SET email_verified = TRUE
WHERE email IS NOT NULL AND email_verified IS NULL;

-- Comment on new columns
COMMENT ON COLUMN users.phone_number IS 'User phone number in international format (e.g., +1234567890)';
COMMENT ON COLUMN users.phone_verified IS 'Whether the user has verified their phone number via SMS';
COMMENT ON COLUMN users.sms_verification_code IS 'Current SMS verification code (6 digits)';
COMMENT ON COLUMN users.sms_code_expiry IS 'Expiry timestamp for the SMS verification code';
