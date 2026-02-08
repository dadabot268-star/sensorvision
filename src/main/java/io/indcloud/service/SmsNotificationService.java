package io.indcloud.service;

import com.twilio.Twilio;
import com.twilio.exception.TwilioException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import io.indcloud.model.Alert;
import io.indcloud.model.OrganizationSmsSettings;
import io.indcloud.model.SmsDeliveryLog;
import io.indcloud.repository.OrganizationSmsSettingsRepository;
import io.indcloud.repository.SmsDeliveryLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service for sending SMS notifications via AWS SNS or Twilio.
 * Handles SMS delivery, budget controls, and delivery tracking.
 */
@Service
@Slf4j
public class SmsNotificationService {

    @Value("${notification.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${notification.sms.provider:sns}")
    private String smsProvider;

    @Value("${notification.sms.from:}")
    private String fromNumber;

    // Twilio configuration (legacy)
    @Value("${notification.sms.twilio.account-sid:}")
    private String accountSid;

    @Value("${notification.sms.twilio.auth-token:}")
    private String authToken;

    // AWS SNS configuration
    @Value("${notification.sms.aws.region:us-west-2}")
    private String awsRegion;

    @Value("${notification.sms.aws.access-key:}")
    private String awsAccessKey;

    @Value("${notification.sms.aws.secret-key:}")
    private String awsSecretKey;

    @Value("${notification.sms.aws.sender-id:IndCloud}")
    private String snsSenderId;

    @Value("${notification.sms.cost-per-message:0.00645}")
    private BigDecimal costPerMessage;

    // Timeout configuration
    @Value("${notification.sms.timeout.connection-seconds:10}")
    private int connectionTimeoutSeconds;

    @Value("${notification.sms.timeout.socket-seconds:30}")
    private int socketTimeoutSeconds;

    // Retry configuration
    @Value("${notification.sms.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${notification.sms.retry.initial-delay-ms:1000}")
    private long initialRetryDelayMs;

    @Value("${notification.sms.retry.max-delay-ms:10000}")
    private long maxRetryDelayMs;

    @Value("${notification.sms.retry.multiplier:2.0}")
    private double retryMultiplier;

    private volatile SnsClient snsClient;

    private final SmsDeliveryLogRepository smsDeliveryLogRepository;
    private final OrganizationSmsSettingsRepository smsSettingsRepository;
    private final EmailNotificationService emailNotificationService;
    private final MeterRegistry meterRegistry;

    // Metrics
    private final Counter smsSentCounter;
    private final Counter smsFailedCounter;
    private final ConcurrentHashMap<Long, AtomicInteger> orgDailyCountGauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> orgMonthlyCountGauges = new ConcurrentHashMap<>();

    public SmsNotificationService(SmsDeliveryLogRepository smsDeliveryLogRepository,
                                   OrganizationSmsSettingsRepository smsSettingsRepository,
                                   EmailNotificationService emailNotificationService,
                                   MeterRegistry meterRegistry) {
        this.smsDeliveryLogRepository = smsDeliveryLogRepository;
        this.smsSettingsRepository = smsSettingsRepository;
        this.emailNotificationService = emailNotificationService;
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.smsSentCounter = meterRegistry.counter("sms_sent_total");
        this.smsFailedCounter = meterRegistry.counter("sms_failed_total");
    }

    @PostConstruct
    public void init() {
        if (!smsEnabled) {
            log.info("SMS notifications disabled");
            return;
        }

        if ("sns".equalsIgnoreCase(smsProvider)) {
            initializeSns();
        } else if ("twilio".equalsIgnoreCase(smsProvider)) {
            initializeTwilio();
        } else {
            log.warn("Unknown SMS provider: {}. SMS notifications will be disabled.", smsProvider);
            smsEnabled = false;
        }
    }

    private void initializeSns() {
        try {
            // Configure HTTP client with timeouts
            software.amazon.awssdk.http.apache.ApacheHttpClient.Builder httpClientBuilder = 
                software.amazon.awssdk.http.apache.ApacheHttpClient.builder()
                    .connectionTimeout(java.time.Duration.ofSeconds(connectionTimeoutSeconds))
                    .socketTimeout(java.time.Duration.ofSeconds(socketTimeoutSeconds))
                    .maxConnections(100)
                    .connectionMaxIdleTime(java.time.Duration.ofSeconds(60));

            // Use explicit credentials if provided, otherwise use default credential chain
            if (awsAccessKey != null && !awsAccessKey.isBlank()
                && awsSecretKey != null && !awsSecretKey.isBlank()) {
                snsClient = SnsClient.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(awsAccessKey, awsSecretKey)
                    ))
                    .httpClientBuilder(httpClientBuilder)
                    .build();
                log.info("SMS notifications enabled via AWS SNS with explicit credentials (region: {}, timeout: {}s)", 
                    awsRegion, socketTimeoutSeconds);
            } else {
                snsClient = SnsClient.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .httpClientBuilder(httpClientBuilder)
                    .build();
                log.info("SMS notifications enabled via AWS SNS with default credentials (region: {}, timeout: {}s)", 
                    awsRegion, socketTimeoutSeconds);
            }
        } catch (Exception e) {
            log.error("Failed to initialize AWS SNS: {}", e.getMessage(), e);
            smsEnabled = false;
            // Notify administrators about initialization failure
            notifySmsInitializationFailure("AWS SNS", e.getMessage());
        }
    }

    private void initializeTwilio() {
        if (accountSid == null || accountSid.isBlank() || authToken == null || authToken.isBlank()) {
            log.warn("SMS enabled but Twilio credentials not configured. SMS notifications will be disabled.");
            smsEnabled = false;
            return;
        }
        try {
            Twilio.init(accountSid, authToken);
            log.info("SMS notifications enabled via Twilio (from: {})", fromNumber);
        } catch (Exception e) {
            log.error("Failed to initialize Twilio: {}", e.getMessage(), e);
            smsEnabled = false;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (snsClient != null) {
            try {
                snsClient.close();
                log.info("AWS SNS client closed");
            } catch (Exception e) {
                log.error("Error closing SNS client: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Send SMS notification for an alert
     */
    @Transactional
    public SmsDeliveryLog sendSms(Alert alert, String phoneNumber, String message) {
        if (!smsEnabled) {
            log.warn("SMS notifications disabled, skipping SMS for alert {}", alert.getId());
            return null;
        }

        // Validate phone number
        if (!isValidPhoneNumber(phoneNumber)) {
            log.error("Invalid phone number format: {}", phoneNumber);
            return createFailedLog(alert, phoneNumber, message, "INVALID_PHONE_NUMBER", 
                "Phone number must be in E.164 format (e.g., +15551234567)");
        }

        // Get organization from alert
        Long organizationId = alert.getRule().getDevice().getOrganization().getId();

        // Check organization SMS settings
        OrganizationSmsSettings settings = smsSettingsRepository
            .findByOrganizationId(organizationId)
            .orElse(null);

        if (settings == null || !settings.getEnabled()) {
            log.warn("SMS not enabled for organization {}", organizationId);
            return null;
        }

        // Reset daily counter if it's a new day
        resetDailyCounterIfNeeded(settings);

        // Check daily limit
        if (settings.getCurrentDayCount() >= settings.getDailyLimit()) {
            log.warn("Daily SMS limit reached for organization {}", organizationId);
            return createFailedLog(alert, phoneNumber, message, "DAILY_LIMIT_EXCEEDED", null);
        }

        // Check monthly budget
        if (settings.getCurrentMonthCost().compareTo(settings.getMonthlyBudget()) >= 0) {
            log.warn("Monthly SMS budget exceeded for organization {}", organizationId);
            return createFailedLog(alert, phoneNumber, message, "BUDGET_EXCEEDED", null);
        }

        // Try sending SMS with retry logic
        return sendSmsWithRetry(alert, phoneNumber, message, settings, organizationId);
    }

    /**
     * Validate phone number format (E.164)
     * Basic validation: starts with +, followed by 1-15 digits
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return false;
        }
        
        // E.164 format: +[country code][subscriber number]
        // Maximum 15 digits after the +
        String e164Pattern = "^\\+[1-9]\\d{1,14}$";
        
        return phoneNumber.matches(e164Pattern);
    }

    /**
     * Send SMS with retry logic for transient failures
     */
    private SmsDeliveryLog sendSmsWithRetry(Alert alert, String phoneNumber, String message, 
                                           OrganizationSmsSettings settings, Long organizationId) {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < maxRetryAttempts) {
            attempt++;
            try {
                String messageId;
                String status;

                if ("sns".equalsIgnoreCase(smsProvider)) {
                    // Send SMS via AWS SNS
                    PublishResponse response = sendViaSns(phoneNumber, message);
                    messageId = response.messageId();
                    status = "SENT";
                } else {
                    // Send SMS via Twilio
                    Message twilioMessage = Message.creator(
                        new PhoneNumber(phoneNumber),
                        new PhoneNumber(fromNumber),
                        message
                    ).create();
                    messageId = twilioMessage.getSid();
                    status = twilioMessage.getStatus().name();
                }

                // Log delivery
                SmsDeliveryLog deliveryLog = SmsDeliveryLog.builder()
                    .alert(alert)
                    .phoneNumber(phoneNumber)
                    .messageBody(message)
                    .twilioSid(messageId)  // Reusing field for SNS message ID
                    .status(status)
                    .cost(costPerMessage)
                    .sentAt(Instant.now())
                    .retryAttempts(attempt - 1)  // Number of retries before success
                    .build();

                deliveryLog = smsDeliveryLogRepository.save(deliveryLog);

                // Update organization SMS stats
                updateOrganizationStats(settings, costPerMessage);

                // Update Prometheus metrics
                smsSentCounter.increment();
                updateOrganizationMetrics(organizationId, settings);

                log.info("SMS sent to {} for alert {} via {}: ID={} (attempt {})", 
                    phoneNumber, alert.getId(), smsProvider, messageId, attempt);
                return deliveryLog;

            } catch (SnsException e) {
                lastException = e;
                if (isTransientFailure(e) && attempt < maxRetryAttempts) {
                    log.warn("Transient SNS failure sending SMS to {} (attempt {}/{}): {}", 
                        phoneNumber, attempt, maxRetryAttempts, e.getMessage());
                    waitBeforeRetry(attempt);
                } else {
                    break;
                }
            } catch (TwilioException e) {
                lastException = e;
                if (isTransientFailure(e) && attempt < maxRetryAttempts) {
                    log.warn("Transient Twilio failure sending SMS to {} (attempt {}/{}): {}", 
                        phoneNumber, attempt, maxRetryAttempts, e.getMessage());
                    waitBeforeRetry(attempt);
                } else {
                    break;
                }
            } catch (Exception e) {
                lastException = e;
                log.error("Unexpected error sending SMS to {} (attempt {}): {}", 
                    phoneNumber, attempt, e.getMessage(), e);
                break;
            }
        }

        // All retries failed
        String errorType = getErrorType(lastException);
        String errorMessage = lastException != null ? lastException.getMessage() : "Unknown error";
        
        log.error("Failed to send SMS to {} after {} attempts: {}", 
            phoneNumber, attempt, errorMessage);
        
        return createFailedLog(alert, phoneNumber, message, errorType, errorMessage);
    }

    /**
     * Determine if an exception represents a transient failure that can be retried
     */
    private boolean isTransientFailure(Exception e) {
        if (e == null) return false;
        
        String message = e.getMessage().toLowerCase();
        
        // Transient failures that can be retried
        String[] transientPatterns = {
            "timeout", "timed out", "connection", "network", "temporarily",
            "rate limit", "too many requests", "service unavailable",
            "internal server error", "gateway timeout", "bad gateway"
        };
        
        for (String pattern : transientPatterns) {
            if (message.contains(pattern)) {
                return true;
            }
        }
        
        // Check for specific status codes for SNS
        if (e instanceof software.amazon.awssdk.services.sns.model.SnsException) {
            SnsException snsEx = (SnsException) e;
            int statusCode = snsEx.statusCode();
            // 5xx errors are typically transient
            return statusCode >= 500 && statusCode < 600;
        }
        
        return false;
    }

    /**
     * Wait before next retry attempt with exponential backoff
     */
    private void waitBeforeRetry(int attempt) {
        try {
            long delay = (long) (initialRetryDelayMs * Math.pow(retryMultiplier, attempt - 1));
            delay = Math.min(delay, maxRetryDelayMs);
            
            log.debug("Waiting {}ms before retry attempt {}", delay, attempt + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", e);
        }
    }

    /**
     * Get error type from exception
     */
    private String getErrorType(Exception e) {
        if (e instanceof SnsException) {
            return "SNS_ERROR";
        } else if (e instanceof TwilioException) {
            return "TWILIO_ERROR";
        } else {
            return "SYSTEM_ERROR";
        }
    }
    }

    /**
     * Send SMS to multiple recipients
     */
    @Transactional
    public List<SmsDeliveryLog> sendSmsToMultiple(Alert alert, List<String> phoneNumbers, String message) {
        return phoneNumbers.stream()
            .map(phone -> sendSms(alert, phone, message))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Format alert message for SMS (160 char limit for single segment)
     */
    public String formatAlertMessage(Alert alert) {
        String deviceName = alert.getRule().getDevice().getName();
        String ruleName = alert.getRule().getName();
        String severity = alert.getSeverity().name();
        String message = alert.getMessage();

        // Target 160 characters to avoid multi-segment costs
        // Format: [SEVERITY] Device: Rule - Message
        String formatted = String.format(
            "[%s] %s: %s - %s",
            severity,
            deviceName,
            ruleName,
            message
        );

        // Truncate if too long
        if (formatted.length() > 160) {
            int maxMessageLength = 160 - severity.length() - deviceName.length() - ruleName.length() - 10; // 10 for formatting chars
            if (maxMessageLength > 3) {
                String truncatedMessage = message.length() > maxMessageLength
                    ? message.substring(0, maxMessageLength - 3) + "..."
                    : message;
                formatted = String.format(
                    "[%s] %s: %s - %s",
                    severity,
                    deviceName,
                    ruleName,
                    truncatedMessage
                );
            } else {
                // Extreme case: just show severity and device
                formatted = String.format("[%s] %s alert", severity, deviceName);
            }
        }

        return formatted;
    }

    /**
     * Create a failed delivery log entry
     */
    private SmsDeliveryLog createFailedLog(Alert alert, String phoneNumber, String message, String errorCode, String errorMessage) {
        SmsDeliveryLog deliveryLog = SmsDeliveryLog.builder()
            .alert(alert)
            .phoneNumber(phoneNumber)
            .messageBody(message)
            .status("FAILED")
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .sentAt(Instant.now())
            .build();

        // Update failed counter metric
        smsFailedCounter.increment();

        return smsDeliveryLogRepository.save(deliveryLog);
    }

    /**
     * Reset daily counter if it's a new day
     * Uses synchronized block to prevent race conditions
     */
    private void resetDailyCounterIfNeeded(OrganizationSmsSettings settings) {
        Instant now = Instant.now();
        Instant lastReset = settings.getLastResetDate();

        // If never reset or if it's been more than 24 hours, reset the daily counter
        if (lastReset == null || now.isAfter(lastReset.plus(24, java.time.temporal.ChronoUnit.HOURS))) {
            // Use synchronized block to prevent multiple threads from resetting simultaneously
            synchronized (this) {
                // Re-fetch settings to ensure we have the latest version
                OrganizationSmsSettings freshSettings = smsSettingsRepository
                    .findByOrganizationId(settings.getOrganization().getId())
                    .orElse(settings);
                
                // Double-check condition after synchronization
                Instant freshLastReset = freshSettings.getLastResetDate();
                if (freshLastReset == null || now.isAfter(freshLastReset.plus(24, java.time.temporal.ChronoUnit.HOURS))) {
                    freshSettings.setCurrentDayCount(0);
                    freshSettings.setLastResetDate(now);
                    smsSettingsRepository.save(freshSettings);
                    log.info("Daily SMS counter reset for organization {}", freshSettings.getOrganization().getId());
                }
            }
        }
    }

    /**
     * Update organization SMS statistics with optimistic locking
     */
    private void updateOrganizationStats(OrganizationSmsSettings settings, BigDecimal cost) {
        boolean updated = false;
        int retryCount = 0;
        final int maxRetries = 3;
        
        while (!updated && retryCount < maxRetries) {
            try {
                // Re-fetch settings to get latest version
                OrganizationSmsSettings freshSettings = smsSettingsRepository
                    .findByOrganizationId(settings.getOrganization().getId())
                    .orElse(settings);
                
                // Calculate previous cost before updating
                BigDecimal previousCost = freshSettings.getCurrentMonthCost();

                // Update stats
                freshSettings.setCurrentMonthCount(freshSettings.getCurrentMonthCount() + 1);
                freshSettings.setCurrentMonthCost(previousCost.add(cost));
                freshSettings.setCurrentDayCount(freshSettings.getCurrentDayCount() + 1);

                // Alert if approaching budget threshold
                if (freshSettings.getAlertOnBudgetThreshold()) {
                    BigDecimal threshold = freshSettings.getMonthlyBudget()
                        .multiply(new BigDecimal(freshSettings.getBudgetThresholdPercentage()))
                        .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);

                    // Check if we just crossed the threshold (to avoid repeated emails)
                    boolean wasUnderThreshold = previousCost.compareTo(threshold) < 0;
                    boolean isNowOverThreshold = freshSettings.getCurrentMonthCost().compareTo(threshold) >= 0;

                    if (wasUnderThreshold && isNowOverThreshold) {
                        log.warn("Organization {} has reached {}% of SMS budget ({}/{})",
                            freshSettings.getOrganization().getId(),
                            freshSettings.getBudgetThresholdPercentage(),
                            freshSettings.getCurrentMonthCost(),
                            freshSettings.getMonthlyBudget());

                        // Send admin notification via email
                        try {
                            emailNotificationService.sendSmsBudgetThresholdAlert(freshSettings);
                        } catch (Exception e) {
                            log.error("Failed to send budget threshold alert email for org {}: {}",
                                freshSettings.getOrganization().getId(), e.getMessage(), e);
                        }
                    }
                }

                smsSettingsRepository.save(freshSettings);
                updated = true;
                
            } catch (Exception e) {
                retryCount++;
                if (retryCount < maxRetries) {
                    log.warn("Failed to update SMS stats (attempt {}/{}), retrying: {}",
                        retryCount, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(100 * retryCount); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    log.error("Failed to update SMS stats after {} attempts: {}",
                        maxRetries, e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Send SMS via AWS SNS
     */
    private PublishResponse sendViaSns(String phoneNumber, String message) {
        Map<String, MessageAttributeValue> smsAttributes = new HashMap<>();

        // Set SMS type to Transactional for high deliverability
        smsAttributes.put("AWS.SNS.SMS.SMSType", MessageAttributeValue.builder()
            .stringValue("Transactional")
            .dataType("String")
            .build());

        // Set sender ID if configured (may not work in all regions/countries)
        if (snsSenderId != null && !snsSenderId.isBlank()) {
            smsAttributes.put("AWS.SNS.SMS.SenderID", MessageAttributeValue.builder()
                .stringValue(snsSenderId)
                .dataType("String")
                .build());
        }

        PublishRequest request = PublishRequest.builder()
            .phoneNumber(phoneNumber)
            .message(message)
            .messageAttributes(smsAttributes)
            .build();

        return snsClient.publish(request);
    }

    /**
     * Check if SMS is enabled globally
     */
    public boolean isSmsEnabled() {
        return smsEnabled;
    }

    /**
     * Check if SMS is enabled for a specific organization
     */
    public boolean isSmsEnabledForOrganization(Long organizationId) {
        if (!smsEnabled) {
            return false;
        }
        return smsSettingsRepository.existsByOrganizationIdAndEnabledTrue(organizationId);
    }

    /**
     * Get SMS settings for an organization
     */
    public OrganizationSmsSettings getOrganizationSettings(Long organizationId) {
        return smsSettingsRepository.findByOrganizationId(organizationId).orElse(null);
    }

    /**
     * Update Prometheus metrics for organization SMS usage
     */
    private void updateOrganizationMetrics(Long organizationId, OrganizationSmsSettings settings) {
        // Update or create daily count gauge
        orgDailyCountGauges.computeIfAbsent(organizationId, id -> {
            AtomicInteger gauge = new AtomicInteger(0);
            meterRegistry.gauge("sms_daily_count",
                Tags.of("organization_id", String.valueOf(id)),
                gauge);
            return gauge;
        }).set(settings.getCurrentDayCount());

        // Update or create monthly count gauge
        orgMonthlyCountGauges.computeIfAbsent(organizationId, id -> {
            AtomicInteger gauge = new AtomicInteger(0);
            meterRegistry.gauge("sms_monthly_count",
                Tags.of("organization_id", String.valueOf(id)),
                gauge);
            return gauge;
        }).set(settings.getCurrentMonthCount());

        // Also track monthly cost as a gauge
        meterRegistry.gauge("sms_monthly_cost",
            Tags.of("organization_id", String.valueOf(organizationId)),
            settings.getCurrentMonthCost());

        // Track budget utilization percentage
        if (settings.getMonthlyBudget().compareTo(BigDecimal.ZERO) > 0) {
            double budgetUtilization = settings.getCurrentMonthCost()
                .divide(settings.getMonthlyBudget(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .doubleValue();

            meterRegistry.gauge("sms_budget_utilization_percent",
                Tags.of("organization_id", String.valueOf(organizationId)),
                budgetUtilization);
        }
    }

    /**
     * Send verification SMS (bypasses organization budget checks)
     * Used for phone number verification via OTP
     */
    public boolean sendVerificationSms(String phoneNumber, String message) {
        if (!smsEnabled) {
            log.warn("SMS notifications disabled, cannot send verification SMS to {}", phoneNumber);
            return false;
        }

        // Validate phone number
        if (!isValidPhoneNumber(phoneNumber)) {
            log.error("Invalid phone number format for verification: {}", phoneNumber);
            return false;
        }

        try {
            String messageId;

            if ("sns".equalsIgnoreCase(smsProvider)) {
                // Send SMS via AWS SNS
                PublishResponse response = sendViaSns(phoneNumber, message);
                messageId = response.messageId();
            } else {
                // Send SMS via Twilio
                Message twilioMessage = Message.creator(
                    new PhoneNumber(phoneNumber),
                    new PhoneNumber(fromNumber),
                    message
                ).create();
                messageId = twilioMessage.getSid();
            }

            log.info("Verification SMS sent to {} via {}: ID={}", phoneNumber, smsProvider, messageId);
            return true;

        } catch (SnsException e) {
            log.error("Failed to send verification SMS via SNS to {}: {}", phoneNumber, e.getMessage(), e);
            return false;
        } catch (TwilioException e) {
            log.error("Failed to send verification SMS via Twilio to {}: {}", phoneNumber, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error sending verification SMS to {}: {}", phoneNumber, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Notify administrators about SMS service initialization failure
     */
    private void notifySmsInitializationFailure(String provider, String errorMessage) {
        try {
            log.error("SMS service initialization failed for {}: {}", provider, errorMessage);
            
            // In a production system, you might want to:
            // 1. Send an email to administrators
            // 2. Create a system alert
            // 3. Log to monitoring system
            
            // For now, we'll just log at ERROR level which should trigger alerts
            // if proper monitoring is set up
            
        } catch (Exception e) {
            log.error("Failed to send SMS initialization failure notification: {}", e.getMessage(), e);
        }
    }
}
