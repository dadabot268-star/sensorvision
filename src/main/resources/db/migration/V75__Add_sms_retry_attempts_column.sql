-- Add retry_attempts column to sms_delivery_log table
-- Tracks number of retry attempts before successful delivery or final failure

ALTER TABLE sms_delivery_log
ADD COLUMN IF NOT EXISTS retry_attempts INTEGER DEFAULT 0;

-- Add index for analytics queries on retry attempts
CREATE INDEX IF NOT EXISTS idx_sms_delivery_log_retry_attempts
ON sms_delivery_log(retry_attempts);

COMMENT ON COLUMN sms_delivery_log.retry_attempts IS 'Number of retry attempts before success or final failure';
