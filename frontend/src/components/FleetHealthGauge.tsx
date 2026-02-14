import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { Device, TelemetryPoint } from '../types';
import { Card, CardBody } from './ui/Card';
import { Activity, Cpu, Wifi, Zap, Info } from 'lucide-react';
import { clsx } from 'clsx';
import { formatTimeAgo } from '../utils/timeUtils';

const REPORTING_WINDOW_MINUTES = 5;

// Reporting coverage configuration
const COVERAGE_CONFIG = {
  HEALTHY: { label: 'Healthy', color: '#10b981', minPercent: 95 },
  AT_RISK: { label: 'At Risk', color: '#00d4ff', minPercent: 80 },
  DEGRADED: { label: 'Degraded', color: '#f59e0b', minPercent: 60 },
  CRITICAL: { label: 'Critical', color: '#f43f5e', minPercent: 0 },
};

type CoverageStatus = keyof typeof COVERAGE_CONFIG;

interface FleetHealthGaugeProps {
  devices: Device[];
  latestTelemetry?: Record<string, TelemetryPoint>;
  className?: string;
}

export const FleetHealthGauge = ({ devices, latestTelemetry = {}, className }: FleetHealthGaugeProps) => {
  const parseTimestamp = (value: string | undefined): number | undefined => {
    if (!value) return undefined;
    const ms = new Date(value).getTime();
    return Number.isNaN(ms) ? undefined : ms;
  };

  // Calculate reporting coverage metrics
  const fleetMetrics = useMemo(() => {
    const totalDevices = devices.length;
    const now = Date.now();
    const windowMs = REPORTING_WINDOW_MINUTES * 60 * 1000;

    const getLastSeenMs = (device: Device): number | undefined => {
      const deviceSeen = parseTimestamp(device.lastSeenAt);
      const telemetrySeen = parseTimestamp(latestTelemetry[device.externalId]?.timestamp);

      if (deviceSeen === undefined && telemetrySeen === undefined) return undefined;
      return Math.max(deviceSeen ?? 0, telemetrySeen ?? 0);
    };

    const reportingDevices = devices.filter((device) => {
      const lastSeenMs = getLastSeenMs(device);
      if (lastSeenMs === undefined) return false;
      return now - lastSeenMs <= windowMs;
    });

    const staleDevices = devices.filter((device) => {
      const lastSeenMs = getLastSeenMs(device);
      if (lastSeenMs === undefined) return true;
      return now - lastSeenMs > windowMs;
    });

    const coveragePercent = totalDevices > 0
      ? Math.round((reportingDevices.length / totalDevices) * 100)
      : 0;

    let coverageStatus: CoverageStatus = 'CRITICAL';
    for (const [status, config] of Object.entries(COVERAGE_CONFIG)) {
      if (coveragePercent >= config.minPercent) {
        coverageStatus = status as CoverageStatus;
        break;
      }
    }

    const onlineDevices = devices.filter(d => d.status === 'ONLINE').length;

    return {
      coveragePercent,
      coverageStatus,
      totalDevices,
      reportingDevices,
      staleDevices,
      onlineDevices,
    };
  }, [devices, latestTelemetry]);

  // SVG gauge calculations
  const size = 180;
  const strokeWidth = 14;
  const radius = (size - strokeWidth) / 2;
  const circumference = radius * 2 * Math.PI;
  const healthPercentage = fleetMetrics.coveragePercent / 100;
  const strokeDashoffset = circumference * (1 - healthPercentage);

  const healthConfig = COVERAGE_CONFIG[fleetMetrics.coverageStatus];

  const healthStatusClass = `health-status-${fleetMetrics.coverageStatus.toLowerCase()}`;

  return (
    <Card className={clsx('fleet-health-card', className)}>
      <CardBody>
        {/* Header */}
        <div className="flex items-center gap-2 mb-6">
          <div className="p-2 rounded-lg bg-gradient-to-br from-emerald-500/20 to-cyan-500/20 shadow-lg shadow-emerald-500/10">
            <Activity className="h-5 w-5 text-emerald-400" />
          </div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold text-primary">Reporting Coverage</h2>
            <span title="Based on last telemetry received in the last 5 minutes. Improve by restoring connectivity or increasing telemetry frequency.">
              <Info className="h-4 w-4 text-secondary cursor-help" />
            </span>
          </div>
        </div>
        <p className="text-sm text-secondary mb-4">
          {fleetMetrics.reportingDevices.length} of {fleetMetrics.totalDevices} devices reporting (last {REPORTING_WINDOW_MINUTES} min)
        </p>

        {/* Circular Gauge */}
        <div className="flex justify-center mb-6">
          <div className="relative fleet-gauge-glow">
            <svg
              width={size}
              height={size}
              className="transform -rotate-90"
              role="img"
              aria-label={`Reporting coverage: ${fleetMetrics.coveragePercent}%`}
            >
              <defs>
                <linearGradient id="fleetHealthGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" stopColor="#10b981" />
                  <stop offset="100%" stopColor="#00d4ff" />
                </linearGradient>
                <filter id="glow">
                  <feGaussianBlur stdDeviation="3" result="coloredBlur"/>
                  <feMerge>
                    <feMergeNode in="coloredBlur"/>
                    <feMergeNode in="SourceGraphic"/>
                  </feMerge>
                </filter>
              </defs>
              {/* Background circle */}
              <circle
                cx={size / 2}
                cy={size / 2}
                r={radius}
                fill="none"
                stroke="currentColor"
                strokeWidth={strokeWidth}
                className="text-hover opacity-30"
              />
              {/* Value circle */}
              <circle
                cx={size / 2}
                cy={size / 2}
                r={radius}
                fill="none"
                stroke={fleetMetrics.coveragePercent >= 80 ? 'url(#fleetHealthGradient)' : healthConfig.color}
                strokeWidth={strokeWidth}
                strokeLinecap="round"
                strokeDasharray={circumference}
                strokeDashoffset={strokeDashoffset}
                className="transition-all duration-1000 ease-out fleet-gauge-animate"
                filter="url(#glow)"
              />
            </svg>
            {/* Center content */}
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span
                className="text-5xl font-bold font-mono fleet-health-value"
                style={{
                  background: fleetMetrics.coveragePercent >= 80
                    ? 'linear-gradient(135deg, #10b981, #00d4ff)'
                    : healthConfig.color,
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent',
                  backgroundClip: 'text',
                }}
              >
                {fleetMetrics.coveragePercent}%
              </span>
              <span className={clsx(
                'text-sm font-bold uppercase tracking-widest mt-1',
                healthStatusClass
              )}>
                {healthConfig.label}
              </span>
            </div>
          </div>
        </div>

        {/* Mini Stats */}
        <div className="grid grid-cols-3 gap-3">
          <MiniStat
            icon={<Cpu className="h-4 w-4" />}
            value={fleetMetrics.totalDevices}
            label="Devices"
            iconColor="text-cyan-400"
          />
          <MiniStat
            icon={<Wifi className="h-4 w-4" />}
            value={fleetMetrics.reportingDevices.length}
            label="Reporting"
            valueClassName="text-emerald-400"
            iconColor="text-emerald-400"
          />
          <MiniStat
            icon={<Zap className="h-4 w-4" />}
            value={fleetMetrics.staleDevices.length}
            label="Stale"
            valueClassName="text-amber-400"
            iconColor="text-amber-400"
          />
        </div>

        {fleetMetrics.staleDevices.length > 0 && (
          <div className="mt-6 rounded-lg border border-default bg-hover/60 p-3">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-semibold text-primary">Improve coverage</span>
              <Link to="/devices" className="text-xs text-link hover:underline">
                View devices
              </Link>
            </div>
            <div className="space-y-1 text-xs text-secondary">
              {fleetMetrics.staleDevices.slice(0, 3).map((device) => {
                const deviceSeen = parseTimestamp(device.lastSeenAt);
                const telemetrySeen = parseTimestamp(latestTelemetry[device.externalId]?.timestamp);
                const lastSeenMs = deviceSeen === undefined && telemetrySeen === undefined
                  ? undefined
                  : Math.max(deviceSeen ?? 0, telemetrySeen ?? 0);
                const lastSeen = lastSeenMs ? new Date(lastSeenMs).toISOString() : undefined;
                return (
                  <div key={device.externalId} className="flex items-center justify-between gap-2">
                    <span className="truncate">{device.name || device.externalId}</span>
                    <span className="text-tertiary font-mono">
                      {lastSeen ? `Last seen ${formatTimeAgo(lastSeen)}` : 'Never seen'}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </CardBody>
    </Card>
  );
};

// Mini stat component
interface MiniStatProps {
  icon: React.ReactNode;
  value: number | string;
  label: string;
  valueClassName?: string;
  iconColor?: string;
}

const MiniStat = ({ icon, value, label, valueClassName, iconColor }: MiniStatProps) => (
  <div className="mini-stat-card">
    <div className={clsx('flex justify-center mb-2', iconColor || 'text-secondary')}>{icon}</div>
    <div className={clsx('text-2xl font-bold font-mono', valueClassName || 'text-primary')}>
      {value}
    </div>
    <div className="text-xs text-secondary uppercase tracking-wider mt-1">{label}</div>
  </div>
);
