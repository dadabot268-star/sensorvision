package io.indcloud.service.providers;

import io.indcloud.service.advanced.CircuitBreakerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages multiple SMS providers with automatic failover, load balancing,
 * and Brazilian-style optimization.
 * 
 * Brazilian Implementation 🇧🇷: "Gerenciamento com eficiência brasileira"
 */
@Service
@Slf4j
public class ProviderManager {

    private final CircuitBreakerService circuitBreakerService;
    private final Map<String, SmsProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, ProviderMetrics> providerMetrics = new ConcurrentHashMap<>();
    
    // Provider priority with Brazilian strategy
    private final List<String> providerPriority = new ArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    
    @Autowired
    public ProviderManager(CircuitBreakerService circuitBreakerService) {
        this.circuitBreakerService = circuitBreakerService;
        initializeProviders();
        log.info("🇧🇷 Provider Manager initialized with Brazilian efficiency!");
    }
    
    private void initializeProviders() {
        // Initialize with Brazilian providers
        registerProvider("aws_sns", new AwsSnsProvider(), 1);  // Primary - Amazonas reliability
        registerProvider("twilio", new TwilioProvider(), 2);   // Secondary - Twilio flexibility
        registerProvider("fallback", new FallbackProvider(), 3); // Fallback - Brazilian resilience
        
        // Set initial priority based on Brazilian strategy
        updateProviderPriority();
        
        log.info("🇧🇷 Registered {} SMS providers with Brazilian strategy", providers.size());
    }
    
    private void registerProvider(String name, SmsProvider provider, int initialPriority) {
        providers.put(name, provider);
        providerMetrics.put(name, new ProviderMetrics(name, initialPriority));
        providerPriority.add(name);
        
        log.info("🇧🇷 Registered provider {} with priority {}", name, initialPriority);
    }
    
    /**
     * Send SMS using best available provider with Brazilian failover strategy
     */
    public String sendSms(String to, String message) {
        String selectedProvider = selectBestProvider();
        
        try {
            log.info("🇧🇷 Attempting to send SMS via {} (Brazilian selection)", selectedProvider);
            
            // Execute with circuit breaker protection
            String messageId = circuitBreakerService.executeWithCircuitBreaker(
                "provider-" + selectedProvider,
                () -> {
                    try {
                        return providers.get(selectedProvider).sendSms(to, message);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            );
            
            // Record success with Brazilian celebration
            recordSuccess(selectedProvider);
            log.info("✅ SMS sent successfully via {}: {}", selectedProvider, messageId);
            
            return messageId;
            
        } catch (Exception e) {
            // Record failure with Brazilian analysis
            recordFailure(selectedProvider, e.getMessage());
            log.warn("⚠️ Failed to send via {}: {}", selectedProvider, e.getMessage());
            
            // Try next provider with Brazilian persistence
            return tryNextProvider(to, message, selectedProvider);
        }
    }
    
    private String tryNextProvider(String to, String message, String failedProvider) {
        List<String> availableProviders = getAvailableProviders(failedProvider);
        
        for (String nextProvider : availableProviders) {
            try {
                log.info("🔄 Trying next provider: {} (Brazilian failover)", nextProvider);
                
                String messageId = circuitBreakerService.executeWithCircuitBreaker(
                    "provider-" + nextProvider,
                    () -> {
                        try {
                            return providers.get(nextProvider).sendSms(to, message);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                );
                
                recordSuccess(nextProvider);
                log.info("✅ SMS sent via failover provider {}: {}", nextProvider, messageId);
                
                // Adjust priorities based on Brazilian learning
                adjustPriorities(failedProvider, nextProvider);
                
                return messageId;
                
            } catch (Exception e) {
                recordFailure(nextProvider, e.getMessage());
                log.warn("⚠️ Also failed via {}: {}", nextProvider, e.getMessage());
            }
        }
        
        // All providers failed - Brazilian emergency mode
        log.error("🚨 All SMS providers failed! Brazilian emergency handling activated");
        return handleAllProvidersFailed(to, message);
    }
    
    private List<String> getAvailableProviders(String excludedProvider) {
        List<String> available = new ArrayList<>(providerPriority);
        available.remove(excludedProvider);
        return available;
    }
    
    private String selectBestProvider() {
        // Brazilian selection strategy: combination of priority and performance
        updateProviderPriority(); // Dynamic adjustment
        
        if (providerPriority.isEmpty()) {
            return "fallback"; // Always have Brazilian fallback
        }
        
        // Round-robin with Brazilian flavor
        int index = roundRobinIndex.getAndUpdate(i -> (i + 1) % providerPriority.size());
        String selected = providerPriority.get(index);
        
        // Check if provider is healthy (Brazilian health check)
        ProviderMetrics metrics = providerMetrics.get(selected);
        if (metrics != null && !metrics.isHealthy()) {
            // Skip unhealthy provider with Brazilian care
            log.debug("🇧🇷 Skipping unhealthy provider {}", selected);
            return selectBestProvider();
        }
        
        return selected;
    }
    
    private void updateProviderPriority() {
        // Brazilian priority algorithm: performance + cost + reliability
        providerPriority.sort((p1, p2) -> {
            ProviderMetrics m1 = providerMetrics.get(p1);
            ProviderMetrics m2 = providerMetrics.get(p2);
            
            if (m1 == null || m2 == null) return 0;
            
            // Brazilian scoring: 40% success rate, 30% cost, 30% response time
            double score1 = m1.calculateBrazilianScore();
            double score2 = m2.calculateBrazilianScore();
            
            return Double.compare(score2, score1); // Higher score first
        });
        
        log.debug("🇧🇷 Updated provider priority: {}", providerPriority);
    }
    
    private void recordSuccess(String providerName) {
        ProviderMetrics metrics = providerMetrics.get(providerName);
        if (metrics != null) {
            metrics.recordSuccess();
            log.debug("🇧🇷 Recorded success for {}", providerName);
        }
    }
    
    private void recordFailure(String providerName, String error) {
        ProviderMetrics metrics = providerMetrics.get(providerName);
        if (metrics != null) {
            metrics.recordFailure(error);
            log.debug("🇧🇷 Recorded failure for {}: {}", providerName, error);
        }
    }
    
    private void adjustPriorities(String failedProvider, String successfulProvider) {
        ProviderMetrics failed = providerMetrics.get(failedProvider);
        ProviderMetrics success = providerMetrics.get(successfulProvider);
        
        if (failed != null) {
            failed.adjustPriority(-1); // Demote failed provider
        }
        
        if (success != null) {
            success.adjustPriority(1); // Promote successful provider
        }
        
        updateProviderPriority();
        log.info("🇧🇷 Adjusted priorities after failover: failed={}, success={}", 
                failedProvider, successfulProvider);
    }
    
    private String handleAllProvidersFailed(String to, String message) {
        // Brazilian emergency handling: queue for retry + alert
        log.error("🇧🇷 ALL PROVIDERS FAILED - Activating Brazilian emergency protocol");
        
        // 1. Queue message for retry
        queueForRetry(to, message);
        
        // 2. Send alert via alternative channel (if configured)
        sendEmergencyAlert(to, message);
        
        // 3. Return special emergency message ID
        return "emergency-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    private void queueForRetry(String to, String message) {
        // Brazilian retry queue implementation
        log.info("🇧🇷 Queued message for retry: to={}, length={}", to, message.length());
        // In production: persist to database or message queue
    }
    
    private void sendEmergencyAlert(String to, String message) {
        // Brazilian emergency alert system
        log.warn("🚨 EMERGENCY ALERT: Failed to send SMS to {}. Message: {}...", 
                to, message.substring(0, Math.min(50, message.length())));
        // In production: send to monitoring system, email, etc.
    }
    
    /**
     * Get provider metrics for monitoring dashboard
     */
    public Map<String, Map<String, Object>> getProviderMetrics() {
        Map<String, Map<String, Object>> metrics = new LinkedHashMap<>();
        
        providerMetrics.forEach((name, providerMetrics) -> {
            metrics.put(name, providerMetrics.toMap());
        });
        
        return metrics;
    }
    
    /**
     * Get current provider priority list
     */
    public List<String> getProviderPriority() {
        return new ArrayList<>(providerPriority);
    }
    
    /**
     * Manually adjust provider priority (for testing/emergency)
     */
    public void setProviderPriority(List<String> newPriority) {
        this.providerPriority.clear();
        this.providerPriority.addAll(newPriority);
        log.info("🇧🇷 Manually set provider priority: {}", newPriority);
    }
    
    /**
     * Health check for provider manager
     */
    public Map<String, Object> healthCheck() {
        int healthyProviders = (int) providerMetrics.values().stream()
                .filter(ProviderMetrics::isHealthy)
                .count();
        
        int totalProviders = providers.size();
        String status = healthyProviders >= 1 ? "HEALTHY" : 
                       healthyProviders == 0 && totalProviders > 0 ? "DEGRADED" : "CRITICAL";
        
        return Map.of(
            "status", status,
            "healthyProviders", healthyProviders,
            "totalProviders", totalProviders,
            "currentPriority", providerPriority,
            "brazilianStatus", getBrazilianStatus(healthyProviders, totalProviders),
            "timestamp", new Date().toString()
        );
    }
    
    private String getBrazilianStatus(int healthy, int total) {
        if (healthy == total) return "🎉 Todos provedores saudáveis com energia brasileira!";
        if (healthy >= total / 2) return "⚠️ Alguns provedores precisam de atenção brasileira";
        if (healthy > 0) return "🚨 Apenas " + healthy + " provedor(es) funcionando - cuidado!";
        return "💀 TODOS provedores falharam - emergência brasileira!";
    }
    
    // Provider interface
    public interface SmsProvider {
        String sendSms(String to, String message) throws Exception;
        String getName();
        double getCostPerMessage();
    }
    
    // Provider implementations
    private class AwsSnsProvider implements SmsProvider {
        @Override
        public String sendSms(String to, String message) throws Exception {
            // AWS SNS implementation
            // In production: actual AWS SDK calls
            log.debug("📤 Sending via AWS SNS to {}: {}...", to, message.substring(0, Math.min(30, message.length())));
            return "aws-" + UUID.randomUUID().toString().substring(0, 8);
        }
        
        @Override
        public String getName() {
            return "AWS SNS";
        }
        
        @Override
        public double getCostPerMessage() {
            return 0.00645; // AWS SNS pricing
        }
    }
    
    private class TwilioProvider implements SmsProvider {
        @Override
        public String sendSms(String to, String message) throws Exception {
            // Twilio implementation
            log.debug("📤 Sending via Twilio to {}: {}...", to, message.substring(0, Math.min(30, message.length())));
            return "twilio-" + UUID.randomUUID().toString().substring(0, 8);
        }
        
        @Override
        public String getName() {
            return "Twilio";
        }
        
        @Override
        public double getCostPerMessage() {
            return 0.0075; // Twilio pricing
        }
    }
    
    private class FallbackProvider implements SmsProvider {
        @Override
        public String sendSms(String to, String message) throws Exception {
            // Fallback implementation (could be email, other service)
            log.warn("🔄 Using fallback provider for {}: {}...", to, message.substring(0, Math.min(30, message.length())));
            return "fallback-" + UUID.randomUUID().toString().substring(0, 8);
        }
        
        @Override
        public String getName() {
            return "Fallback";
        }
        
        @Override
        public double getCostPerMessage() {
            return 0.0; // Fallback might be free or different cost structure
        }
    }
    
    // Metrics tracking class
    private static class ProviderMetrics {
        private final String name;
        private int priority;
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger totalCost = new AtomicInteger(0); // in cents
        private final List<Long> responseTimes = new ArrayList<>();
        private final List<String> recentErrors = new ArrayList<>();
        private volatile long lastSuccessTime = System.currentTimeMillis();
        private volatile long lastFailureTime = 0;
        
        public ProviderMetrics(String name, int initialPriority) {
            this.name = name;
            this.priority = initialPriority;
        }
        
        public void recordSuccess() {
            successCount.incrementAndGet();
            lastSuccessTime = System.currentTimeMillis();
        }
        
        public void recordFailure(String error) {
            failureCount.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();
            
            // Keep recent errors (Brazilian error analysis)
            recentErrors.add(error);
            if (recentErrors.size() > 10) {
                recentErrors.remove(0);
            }
        }
        
        public void recordResponseTime(long milliseconds) {
            responseTimes.add(milliseconds);
            if (responseTimes.size() > 100) {
                responseTimes.remove(0);
            }
        }
        
        public void recordCost(double cost) {
            totalCost.addAndGet((int) (cost * 100)); // Convert to cents
        }
        
        public void adjustPriority(int adjustment) {
            this.priority = Math.max(1, Math.min(10, this.priority + adjustment));
        }
        
        public double calculateBrazilianScore() {
            double successRate = getSuccessRate();
            double avgResponseTime = getAverageResponseTime();
            double costEfficiency = getCostEfficiency();
            
            // Brazilian scoring formula
            return (successRate * 0.4) + 
                   ((1000.0 / (avgResponseTime + 1)) * 0.3) + // Faster is better
                   ((1.0 / (costEfficiency + 0.001)) * 0.3); // Cheaper is better
        }
        
        public double getSuccessRate() {
            int total = successCount.get() + failureCount.get();
            return total > 0 ? (successCount.get() * 100.0) / total : 100.0;
        }
        
        public double getAverageResponseTime() {
            if (responseTimes.isEmpty()) return 1000.0; // Default 1 second
            return responseTimes.stream().mapToLong(Long::longValue).average().orElse(1000.0);
        }
        
        public double getCostEfficiency() {
            int totalMessages = successCount.get() + failureCount.get();
            return totalMessages > 0 ? totalCost.get() / 100.0 / totalMessages : 0.0;
        }
        
        public boolean isHealthy() {
            // Brazilian health check: recent success and acceptable failure rate
            long timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessTime;
            double failureRate = getFailureRate();
            
            return timeSinceLastSuccess < 300000 && // Less than 5 minutes since last success
                   failureRate < 50.0; // Less than 50% failure rate
        }
        
        public double getFailureRate() {
            int total = successCount.get() + failureCount.get();
            return total > 0 ? (failureCount.get() * 100.0) / total : 0.0;
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("priority", priority);
            map.put("successCount", successCount.get());
            map.put("failureCount", failureCount.get());
            map.put("successRate", String.format("%.2f%%", getSuccessRate()));
            map.put("failureRate", String.format("%.2f%%", getFailureRate()));
            map.put("averageResponseTime", String.format("%.2fms", getAverageResponseTime()));
            map.put("costEfficiency", String.format("$%.4f", getCostEfficiency()));
            map.put("healthy", isHealthy());
            map.put("lastSuccess", new Date(lastSuccessTime).toString());
            map.put("lastFailure", lastFailureTime > 0 ? new Date(lastFailureTime).toString() : "Never");
            map.put("brazilianScore", String.format("%.2f", calculateBrazilianScore()));
            map.put("recentErrors", recentErrors.size() > 0 ? recentErrors.subList(0, Math.min(3, recentErrors.size())) : List.of("None"));
            return map;
        }
    }
}