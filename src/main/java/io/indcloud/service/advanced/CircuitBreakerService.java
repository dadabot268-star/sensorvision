package io.indcloud.service.advanced;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Advanced resilience service providing circuit breaker, retry, and bulkhead patterns
 * for SMS notification system and other critical operations.
 * 
 * Brazilian Implementation 🇧🇷: "Resiliência com samba no pé" - resilient with samba rhythm
 */
@Service
@Slf4j
public class CircuitBreakerService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final MeterRegistry meterRegistry;
    
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, Retry> retries = new ConcurrentHashMap<>();
    private final Map<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();
    
    // Metrics counters
    private Counter circuitBreakerSuccessCounter;
    private Counter circuitBreakerFailureCounter;
    private Counter circuitBreakerOpenCounter;
    private Counter retrySuccessCounter;
    private Counter retryFailureCounter;
    private Counter bulkheadRejectedCounter;
    
    @Autowired
    public CircuitBreakerService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Configure circuit breaker with Brazilian resilience: "Alegria na recuperação"
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // 50% failure rate opens circuit
                .slowCallRateThreshold(100) // 100% slow calls opens circuit
                .slowCallDurationThreshold(Duration.ofSeconds(5)) // Calls slower than 5s are slow
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s in open state
                .permittedNumberOfCallsInHalfOpenState(10) // Allow 10 calls in half-open
                .minimumNumberOfCalls(20) // Need 20 calls before calculating failure rate
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(50) // Last 50 calls
                .recordExceptions(Exception.class) // Record all exceptions
                .ignoreExceptions() // No exceptions to ignore
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        
        // Configure retry with exponential backoff: "Tentativa com ritmo de samba"
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(Duration.ofMillis(1000), 2.0))
                .retryExceptions(Exception.class)
                .build();
        
        this.retryRegistry = RetryRegistry.of(retryConfig);
        
        // Configure bulkhead for concurrent calls: "Limite com organização brasileira"
        BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(50) // Maximum 50 concurrent calls
                .maxWaitDuration(Duration.ofMillis(500)) // Wait up to 500ms for permission
                .build();
        
        this.bulkheadRegistry = BulkheadRegistry.of(bulkheadConfig);
        
        // Initialize metrics with Brazilian flair
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        circuitBreakerSuccessCounter = Counter.builder("resilience.circuitbreaker.success")
                .description("Circuit breaker successful calls")
                .tag("implementation", "brazilian")
                .register(meterRegistry);
        
        circuitBreakerFailureCounter = Counter.builder("resilience.circuitbreaker.failure")
                .description("Circuit breaker failed calls")
                .tag("implementation", "brazilian")
                .register(meterRegistry);
        
        circuitBreakerOpenCounter = Counter.builder("resilience.circuitbreaker.state.open")
                .description("Circuit breaker opened state transitions")
                .tag("implementation", "brazilian")
                .register(meterRegistry);
        
        retrySuccessCounter = Counter.builder("resilience.retry.success")
                .description("Retry successful calls")
                .tag("implementation", "brazilian")
                .register(meterRegistry);
        
        retryFailureCounter = Counter.builder("resilience.retry.failure")
                .description("Retry failed calls")
                .tag("implementation", "brazilian")
                .register(meterRegistry);
        
        bulkheadRejectedCounter = Counter.builder("resilience.bulkhead.rejected")
                .description("Bulkhead rejected calls")
                .tag("implementation", "brazilian")
                .register(meterRegistry);
        
        log.info("🇧🇷 Brazilian Resilience Metrics initialized with samba rhythm!");
    }
    
    /**
     * Get or create a circuit breaker for a specific service
     * Brazilian: "Criando um breaker com energia brasileira"
     */
    public CircuitBreaker getCircuitBreaker(String serviceName) {
        return circuitBreakers.computeIfAbsent(serviceName, name -> {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
            
            // Add event listeners with Brazilian celebration
            circuitBreaker.getEventPublisher()
                .onSuccess(event -> {
                    circuitBreakerSuccessCounter.increment();
                    log.debug("✅ Circuit breaker success for {}: {}", name, event.getEventType());
                })
                .onError(event -> {
                    circuitBreakerFailureCounter.increment();
                    log.warn("⚠️ Circuit breaker error for {}: {}", name, event.getThrowable().getMessage());
                })
                .onStateTransition(event -> {
                    if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                        circuitBreakerOpenCounter.increment();
                        log.warn("🔴 Circuit breaker OPEN for {} - Too many failures!", name);
                    } else if (event.getStateTransition().getToState() == CircuitBreaker.State.HALF_OPEN) {
                        log.info("🟡 Circuit breaker HALF_OPEN for {} - Testing recovery", name);
                    } else if (event.getStateTransition().getToState() == CircuitBreaker.State.CLOSED) {
                        log.info("🟢 Circuit breaker CLOSED for {} - Back to normal!", name);
                    }
                });
            
            log.info("🇧🇷 Created circuit breaker for {} with Brazilian resilience", name);
            return circuitBreaker;
        });
    }
    
    /**
     * Get or create a retry instance for a specific service
     * Brazilian: "Tentativas com persistência brasileira"
     */
    public Retry getRetry(String serviceName) {
        return retries.computeIfAbsent(serviceName, name -> {
            Retry retry = retryRegistry.retry(name);
            
            retry.getEventPublisher()
                .onRetry(event -> {
                    log.debug("🔄 Retry attempt {} for {}: {}", 
                            event.getNumberOfRetryAttempts(), name, event.getLastThrowable().getMessage());
                })
                .onSuccess(event -> {
                    retrySuccessCounter.increment();
                    log.debug("✅ Retry successful for {} after {} attempts", 
                            name, event.getNumberOfRetryAttempts());
                })
                .onError(event -> {
                    retryFailureCounter.increment();
                    log.error("❌ Retry failed for {} after {} attempts: {}", 
                            name, event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage());
                });
            
            log.info("🇧🇷 Created retry for {} with Brazilian persistence", name);
            return retry;
        });
    }
    
    /**
     * Get or create a bulkhead for a specific service
     * Brazilian: "Controle de concorrência com organização brasileira"
     */
    public Bulkhead getBulkhead(String serviceName) {
        return bulkheads.computeIfAbsent(serviceName, name -> {
            Bulkhead bulkhead = bulkheadRegistry.bulkhead(name);
            
            bulkhead.getEventPublisher()
                .onCallPermitted(event -> {
                    log.debug("✅ Bulkhead call permitted for {}", name);
                })
                .onCallRejected(event -> {
                    bulkheadRejectedCounter.increment();
                    log.warn("🚫 Bulkhead call rejected for {} - too many concurrent calls", name);
                })
                .onCallFinished(event -> {
                    log.debug("🏁 Bulkhead call finished for {}", name);
                });
            
            log.info("🇧🇷 Created bulkhead for {} with Brazilian organization", name);
            return bulkhead;
        });
    }
    
    /**
     * Execute with full resilience patterns (circuit breaker + retry + bulkhead)
     * Brazilian: "Execução com resiliência total brasileira"
     */
    public <T> T executeWithResilience(String serviceName, Supplier<T> operation) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        Retry retry = getRetry(serviceName);
        Bulkhead bulkhead = getBulkhead(serviceName);
        
        // Decorate with all resilience patterns
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, operation);
        decoratedSupplier = Retry.decorateSupplier(retry, decoratedSupplier);
        decoratedSupplier = Bulkhead.decorateSupplier(bulkhead, decoratedSupplier);
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            log.error("🇧🇷 Resilience execution failed for {}: {}", serviceName, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Execute with circuit breaker only
     * Brazilian: "Execução protegida por circuit breaker"
     */
    public <T> T executeWithCircuitBreaker(String serviceName, Supplier<T> operation) {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        Supplier<T> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, operation);
        
        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            log.error("🔴 Circuit breaker execution failed for {}: {}", serviceName, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Execute callable with full resilience
     * Brazilian: "Chamável com proteção brasileira"
     */
    public <T> T executeCallableWithResilience(String serviceName, Callable<T> callable) throws Exception {
        CircuitBreaker circuitBreaker = getCircuitBreaker(serviceName);
        Retry retry = getRetry(serviceName);
        Bulkhead bulkhead = getBulkhead(serviceName);
        
        Callable<T> decoratedCallable = CircuitBreaker.decorateCallable(circuitBreaker, callable);
        decoratedCallable = Retry.decorateCallable(retry, decoratedCallable);
        decoratedCallable = Bulkhead.decorateCallable(bulkhead, decoratedCallable);
        
        try {
            return decoratedCallable.call();
        } catch (Exception e) {
            log.error("🇧🇷 Resilience callable execution failed for {}: {}", serviceName, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Get circuit breaker metrics for monitoring
     * Brazilian: "Métricas com transparência brasileira"
     */
    public Map<String, Object> getCircuitBreakerMetrics(String serviceName) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(serviceName);
        if (circuitBreaker == null) {
            return Map.of("error", "Circuit breaker not found for service: " + serviceName);
        }
        
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        CircuitBreaker.State state = circuitBreaker.getState();
        
        return Map.of(
            "serviceName", serviceName,
            "state", state.name(),
            "failureRate", metrics.getFailureRate() + "%",
            "slowCallRate", metrics.getSlowCallRate() + "%",
            "totalCalls", metrics.getNumberOfSuccessfulCalls() + metrics.getNumberOfFailedCalls(),
            "successfulCalls", metrics.getNumberOfSuccessfulCalls(),
            "failedCalls", metrics.getNumberOfFailedCalls(),
            "notPermittedCalls", metrics.getNumberOfNotPermittedCalls(),
            "bufferedCalls", metrics.getNumberOfBufferedCalls(),
            "implementation", "brazilian_resilience"
        );
    }
    
    /**
     * Get all circuit breaker states for dashboard
     * Brazilian: "Visão geral com clareza brasileira"
     */
    public Map<String, Map<String, Object>> getAllCircuitBreakerStates() {
        Map<String, Map<String, Object>> states = new java.util.HashMap<>();
        
        circuitBreakers.forEach((name, circuitBreaker) -> {
            states.put(name, getCircuitBreakerMetrics(name));
        });
        
        return states;
    }
    
    /**
     * Reset circuit breaker for a service (for testing/recovery)
     * Brazilian: "Reinício com energia renovada brasileira"
     */
    public void resetCircuitBreaker(String serviceName) {
        CircuitBreaker circuitBreaker = circuitBreakers.get(serviceName);
        if (circuitBreaker != null) {
            circuitBreaker.reset();
            log.info("🔄 Reset circuit breaker for {} - Fresh start with Brazilian energy!", serviceName);
        }
    }
    
    /**
     * Health check for resilience system
     * Brazilian: "Saúde do sistema com vitalidade brasileira"
     */
    public Map<String, Object> healthCheck() {
        int totalCircuitBreakers = circuitBreakers.size();
        int totalRetries = retries.size();
        int totalBulkheads = bulkheads.size();
        
        long openCircuitBreakers = circuitBreakers.values().stream()
                .filter(cb -> cb.getState() == CircuitBreaker.State.OPEN)
                .count();
        
        return Map.of(
            "status", openCircuitBreakers == 0 ? "HEALTHY" : "DEGRADED",
            "totalCircuitBreakers", totalCircuitBreakers,
            "totalRetries", totalRetries,
            "totalBulkheads", totalBulkheads,
            "openCircuitBreakers", openCircuitBreakers,
            "message", openCircuitBreakers == 0 ? 
                "🇧🇷 Sistema de resiliência saudável com energia brasileira!" :
                "⚠️ " + openCircuitBreakers + " circuit breakers abertos - atenção necessária",
            "timestamp", java.time.Instant.now().toString()
        );
    }
}