package io.indcloud.controller.analytics;

import io.indcloud.service.analytics.SmsAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for SMS analytics dashboard with Brazilian-themed endpoints.
 * 
 * Brazilian Implementation 🇧🇷: "Dashboard com visualização brasileira"
 */
@RestController
@RequestMapping("/api/v1/analytics/sms")
@Tag(name = "SMS Analytics", description = "Brazilian-themed SMS analytics and dashboard endpoints")
@Slf4j
public class SmsAnalyticsController {

    private final SmsAnalyticsService smsAnalyticsService;
    
    @Autowired
    public SmsAnalyticsController(SmsAnalyticsService smsAnalyticsService) {
        this.smsAnalyticsService = smsAnalyticsService;
    }
    
    /**
     * Get comprehensive SMS analytics dashboard
     * Brazilian: "Dashboard completo com insights brasileiros"
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYTICS_VIEWER')")
    @Operation(summary = "Get SMS analytics dashboard", 
               description = "Returns comprehensive SMS analytics with Brazilian-themed visualization")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        log.info("🇧🇷 Dashboard request received - preparing Brazilian insights");
        
        Map<String, Object> dashboard = smsAnalyticsService.getDashboardData();
        
        // Add Brazilian welcome message
        dashboard.put("welcomeMessage", 
            "Bem-vindo ao Dashboard de Análises SMS com energia brasileira! 💃");
        dashboard.put("timestamp", java.time.Instant.now().toString());
        
        log.debug("🇧🇷 Dashboard data prepared: {} metrics points", dashboard.size());
        return ResponseEntity.ok(dashboard);
    }
    
    /**
     * Get real-time streaming data for live dashboard
     * Brazilian: "Dados em tempo real com fluidez brasileira"
     */
    @GetMapping("/realtime")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYTICS_VIEWER')")
    @Operation(summary = "Get real-time SMS analytics", 
               description = "Returns real-time streaming data for live dashboard updates")
    public ResponseEntity<Map<String, Object>> getRealtimeData() {
        Map<String, Object> realtimeData = smsAnalyticsService.getRealtimeData();
        
        log.debug("🇧🇷 Real-time data streamed: {} active metrics", realtimeData.size());
        return ResponseEntity.ok(realtimeData);
    }
    
    /**
     * Get provider-specific analytics
     * Brazilian: "Análises por provedor com detalhamento brasileiro"
     */
    @GetMapping("/providers/{provider}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYTICS_VIEWER')")
    @Operation(summary = "Get provider-specific analytics", 
               description = "Returns detailed analytics for a specific SMS provider")
    public ResponseEntity<Map<String, Object>> getProviderAnalytics(
            @PathVariable String provider) {
        
        Map<String, Object> dashboard = smsAnalyticsService.getDashboardData();
        Map<String, Object> providerMetrics = (Map<String, Object>) dashboard.get("providerMetrics");
        
        if (providerMetrics == null || !providerMetrics.containsKey(provider)) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = Map.of(
            "provider", provider,
            "metrics", providerMetrics.get(provider),
            "timestamp", java.time.Instant.now().toString(),
            "brazilianInsight", getProviderInsight(provider)
        );
        
        log.info("🇧🇷 Provider analytics for {}: {}", provider, response);
        return ResponseEntity.ok(response);
    }
    
    private String getProviderInsight(String provider) {
        return switch (provider.toLowerCase()) {
            case "aws_sns" -> "AWS SNS: Confiável como o Amazonas! 🌳";
            case "twilio" -> "Twilio: Comunicação com estilo brasileiro! 📞";
            case "fallback" -> "Fallback: Segurança extra brasileira! 🛡️";
            default -> "Provedor: Trabalhando com energia brasileira! 💪";
        };
    }
    
    /**
     * Get system health status
     * Brazilian: "Saúde do sistema com diagnóstico brasileiro"
     */
    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYTICS_VIEWER', 'MONITOR')")
    @Operation(summary = "Get SMS system health status", 
               description = "Returns comprehensive health check with Brazilian diagnostics")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> dashboard = smsAnalyticsService.getDashboardData();
        Map<String, Object> systemHealth = (Map<String, Object>) dashboard.get("systemHealth");
        
        // Add Brazilian health assessment
        systemHealth.put("brazilianAssessment", getBrazilianHealthAssessment(systemHealth));
        systemHealth.put("recommendedActions", getBrazilianRecommendations(systemHealth));
        
        log.info("🇧🇷 System health check: {}", systemHealth.get("brazilianStatus"));
        return ResponseEntity.ok(systemHealth);
    }
    
    private String getBrazilianHealthAssessment(Map<String, Object> systemHealth) {
        String status = (String) systemHealth.get("status");
        
        return switch (status) {
            case "HEALTHY" -> "🎉 Sistema saudável! Continue com a energia brasileira!";
            case "DEGRADED" -> "⚠️ Sistema precisa de atenção. Vamos melhorar juntos!";
            case "CRITICAL" -> "🚨 Sistema crítico! Ação imediata necessária!";
            default -> "🔍 Sistema em análise. Mantenha a calma brasileira!";
        };
    }
    
    private String getBrazilianRecommendations(Map<String, Object> systemHealth) {
        String status = (String) systemHealth.get("status");
        
        return switch (status) {
            case "HEALTHY" -> "• Continue monitorando com precisão brasileira\n" +
                             "• Explore otimizações com criatividade brasileira\n" +
                             "• Celebre o sucesso com alegria brasileira!";
            case "DEGRADED" -> "• Revise configurações com cuidado brasileiro\n" +
                              "• Verifique provedores com atenção brasileira\n" +
                              "• Implemente melhorias com eficiência brasileira";
            case "CRITICAL" -> "• Priorize correções com urgência brasileira\n" +
                              "• Acione suporte com clareza brasileira\n" +
                              "• Documente problemas com precisão brasileira";
            default -> "• Mantenha monitoramento constante\n" +
                      "• Documente observações detalhadamente\n" +
                      "• Procure padrões com insight brasileiro";
        };
    }
    
    /**
     * Get predictive analytics and forecasts
     * Brazilian: "Previsões com intuição brasileira"
     */
    @GetMapping("/predictions")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYTICS_VIEWER')")
    @Operation(summary = "Get predictive analytics", 
               description = "Returns predictions and forecasts with Brazilian intuition")
    public ResponseEntity<Map<String, Object>> getPredictions() {
        Map<String, Object> dashboard = smsAnalyticsService.getDashboardData();
        Map<String, Object> predictions = (Map<String, Object>) dashboard.get("predictions");
        
        // Add Brazilian forecasting wisdom
        predictions.put("brazilianForecast", getBrazilianForecast());
        predictions.put("culturalInsight", "No Brasil, preparamos para o carnaval com antecedência! 🎭");
        
        log.info("🇧🇷 Predictive analytics generated: {} predictions", predictions.size());
        return ResponseEntity.ok(predictions);
    }
    
    private String getBrazilianForecast() {
        return "Baseado em padrões brasileiros de comunicação:\n" +
               "• Picos durante horários de almoço (12:00-14:00)\n" +
               "• Aumento nas sextas-feiras e vésperas de feriados\n" +
               "• Comunicação mais ativa durante eventos culturais\n" +
               "• Prepare-se para volume carnavalesco em épocas festivas!";
    }
    
    /**
     * Get Brazilian-themed visualization configuration
     * Brazilian: "Configuração visual com estilo brasileiro"
     */
    @GetMapping("/visualization/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYTICS_VIEWER')")
    @Operation(summary = "Get visualization configuration", 
               description = "Returns Brazilian-themed visualization configuration for dashboards")
    public ResponseEntity<Map<String, Object>> getVisualizationConfig() {
        Map<String, Object> dashboard = smsAnalyticsService.getDashboardData();
        Map<String, Object> theme = (Map<String, Object>) dashboard.get("theme");
        
        // Enhanced Brazilian visualization config
        Map<String, Object> config = Map.of(
            "theme", theme,
            "charts", getBrazilianChartConfig(),
            "colors", getBrazilianColorPalette(),
            "typography", Map.of(
                "fontFamily", "'Roboto', 'Arial', sans-serif",
                "headingFont", "'Montserrat', 'Arial', sans-serif",
                "brazilianAccent", "Use icons de carnaval para highlights"
            ),
            "animations", Map.of(
                "enabled", true,
                "style", "samba_rhythm",
                "duration", "0.5s"
            ),
            "culturalElements", Arrays.asList(
                "Carnaval confetti effects on success",
                "Samba progress bars",
                "Amazon forest green success indicators",
                "Sunset orange warning colors",
                "Ocean blue information panels"
            )
        );
        
        log.info("🇧🇷 Visualization config prepared with Brazilian style");
        return ResponseEntity.ok(config);
    }
    
    private Map<String, Object> getBrazilianChartConfig() {
        return Map.of(
            "successRateChart", Map.of(
                "type", "line",
                "color", "#2ECC71",
                "animation", "wave",
                "brazilianStyle", "Amazon river flow"
            ),
            "volumeChart", Map.of(
                "type", "bar",
                "color", "#3498DB",
                "animation", "bounce",
                "brazilianStyle", "Carnaval crowd levels"
            ),
            "costChart", Map.of(
                "type", "area",
                "color", "#9B59B6",
                "animation", "fade",
                "brazilianStyle", "Sunset over mountains"
            ),
            "providerChart", Map.of(
                "type", "pie",
                "colors", Arrays.asList("#2ECC71", "#F1C40F", "#E74C3C", "#9B59B6"),
                "animation", "rotate",
                "brazilianStyle", "Carnaval costume colors"
            )
        );
    }
    
    private Map<String, String> getBrazilianColorPalette() {
        return Map.of(
            "amazonGreen", "#27AE60",
            "carnavalYellow", "#F1C40F",
            "sunsetOrange", "#E67E22",
            "oceanBlue", "#3498DB",
            "passionRed", "#E74C3C",
            "royalPurple", "#9B59B6",
            "cloudWhite", "#ECF0F1",
            "nightBlack", "#2C3E50",
            "earthBrown", "#A84300",
            "skyLightBlue", "#85C1E9"
        );
    }
    
    /**
     * Health check endpoint for load balancers
     * Brazilian: "Verificação de saúde com vitalidade brasileira"
     */
    @GetMapping("/health/check")
    @Operation(summary = "Health check endpoint", 
               description = "Simple health check for load balancers and monitoring")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = Map.of(
            "status", "UP",
            "service", "sms-analytics",
            "implementation", "brazilian",
            "timestamp", java.time.Instant.now().toString(),
            "message", "🇧🇷 Serviço de análises SMS funcionando com energia brasileira!",
            "version", "1.0.0-brazilian"
        );
        
        return ResponseEntity.ok(response);
    }
}