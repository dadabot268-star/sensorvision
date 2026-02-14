-- Add version column for optimistic locking
ALTER TABLE organization_sms_settings
ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- Add last_monthly_reset_date column for tracking monthly budget resets
ALTER TABLE organization_sms_settings
ADD COLUMN IF NOT EXISTS last_monthly_reset_date TIMESTAMP;

-- Initialize last_monthly_reset_date from last_reset_date for existing records
UPDATE organization_sms_settings
SET last_monthly_reset_date = last_reset_date
WHERE last_monthly_reset_date IS NULL AND last_reset_date IS NOT NULL;
