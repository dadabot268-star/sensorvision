import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { FleetHealthGauge } from './FleetHealthGauge';
import { Device, TelemetryPoint } from '../types';

const now = Date.now();
const recentTimestamp = new Date(now - 2 * 60 * 1000).toISOString();
const staleTimestamp = new Date(now - 10 * 60 * 1000).toISOString();

// Helper to create mock devices
const createMockDevice = (overrides: Partial<Device> = {}): Device => ({
  id: 'device-uuid-001',
  externalId: 'device-001',
  name: 'Test Device',
  status: 'ONLINE',
  lastSeenAt: recentTimestamp,
  ...overrides,
});

describe('FleetHealthGauge', () => {
  const renderWithRouter = (ui: JSX.Element) =>
    render(<BrowserRouter>{ui}</BrowserRouter>);

  describe('Basic Rendering', () => {
    it('should render the component', () => {
      const devices = [createMockDevice()];
      const { container } = renderWithRouter(<FleetHealthGauge devices={devices} />);
      expect(container.firstChild).toBeInTheDocument();
    });

    it('should render the Reporting Coverage header', () => {
      const devices = [createMockDevice()];
      renderWithRouter(<FleetHealthGauge devices={devices} />);
      expect(screen.getByText('Reporting Coverage')).toBeInTheDocument();
    });

    it('should render an SVG gauge with reporting label', () => {
      const devices = [createMockDevice()];
      renderWithRouter(<FleetHealthGauge devices={devices} />);
      const svg = screen.getByRole('img');
      expect(svg).toHaveAttribute('aria-label', expect.stringContaining('Reporting coverage:'));
    });
  });

  describe('Coverage Calculation', () => {
    it('should calculate reporting coverage based on lastSeenAt', () => {
      const devices = [
        createMockDevice({ lastSeenAt: recentTimestamp }),
        createMockDevice({ id: 'device-uuid-002', externalId: 'device-002', lastSeenAt: staleTimestamp }),
      ];
      renderWithRouter(<FleetHealthGauge devices={devices} />);
      expect(screen.getByText('50%')).toBeInTheDocument();
    });

    it('should use latestTelemetry timestamp as fallback', () => {
      const devices = [
        createMockDevice({ lastSeenAt: undefined }),
      ];
      const latestTelemetry: Record<string, TelemetryPoint> = {
        'device-001': { deviceId: 'device-001', timestamp: recentTimestamp },
      };
      renderWithRouter(<FleetHealthGauge devices={devices} latestTelemetry={latestTelemetry} />);
      expect(screen.getByText('100%')).toBeInTheDocument();
    });
  });

  describe('Coverage Status Labels', () => {
    it('should display "Healthy" for coverage >= 95', () => {
      renderWithRouter(<FleetHealthGauge devices={[createMockDevice()]} />);
      expect(screen.getByText('Healthy')).toBeInTheDocument();
    });

    it('should display "At Risk" for coverage 80-94', () => {
      const devices = [
        createMockDevice({ lastSeenAt: recentTimestamp }),
        createMockDevice({ id: 'device-uuid-002', externalId: 'device-002', lastSeenAt: recentTimestamp }),
        createMockDevice({ id: 'device-uuid-003', externalId: 'device-003', lastSeenAt: recentTimestamp }),
        createMockDevice({ id: 'device-uuid-004', externalId: 'device-004', lastSeenAt: recentTimestamp }),
        createMockDevice({ id: 'device-uuid-005', externalId: 'device-005', lastSeenAt: staleTimestamp }),
      ];
      renderWithRouter(<FleetHealthGauge devices={devices} />);
      expect(screen.getByText('At Risk')).toBeInTheDocument();
    });

    it('should display "Degraded" for coverage 60-79', () => {
      const devices = [
        createMockDevice({ lastSeenAt: recentTimestamp }),
        createMockDevice({ id: 'device-uuid-002', externalId: 'device-002', lastSeenAt: recentTimestamp }),
        createMockDevice({ id: 'device-uuid-003', externalId: 'device-003', lastSeenAt: recentTimestamp }),
        createMockDevice({ id: 'device-uuid-004', externalId: 'device-004', lastSeenAt: staleTimestamp }),
        createMockDevice({ id: 'device-uuid-005', externalId: 'device-005', lastSeenAt: staleTimestamp }),
      ];
      renderWithRouter(<FleetHealthGauge devices={devices} />);
      expect(screen.getByText('Degraded')).toBeInTheDocument();
    });

    it('should display "Critical" for coverage < 60', () => {
      const devices = [
        createMockDevice({ lastSeenAt: staleTimestamp }),
        createMockDevice({ id: 'device-uuid-002', externalId: 'device-002', lastSeenAt: staleTimestamp }),
      ];
      renderWithRouter(<FleetHealthGauge devices={devices} />);
      expect(screen.getByText('Critical')).toBeInTheDocument();
    });
  });

  describe('Mini Stats', () => {
    it('should display total devices, reporting count, and stale count', () => {
      const devices = [
        createMockDevice({ lastSeenAt: recentTimestamp }),
        createMockDevice({ id: 'device-uuid-002', externalId: 'device-002', lastSeenAt: staleTimestamp }),
      ];
      renderWithRouter(<FleetHealthGauge devices={devices} />);

      expect(screen.getByText('Devices')).toBeInTheDocument();
      expect(screen.getByText('Reporting')).toBeInTheDocument();
      expect(screen.getByText('Stale')).toBeInTheDocument();
    });
  });

  describe('Empty State', () => {
    it('should display 0% coverage for empty device array', () => {
      renderWithRouter(<FleetHealthGauge devices={[]} />);
      const zeroPercentElements = screen.getAllByText('0%');
      expect(zeroPercentElements.length).toBeGreaterThanOrEqual(1);
    });

    it('should display "Critical" for empty device array', () => {
      renderWithRouter(<FleetHealthGauge devices={[]} />);
      expect(screen.getByText('Critical')).toBeInTheDocument();
    });
  });

  describe('Improve Coverage Panel', () => {
    it('should show improve coverage section when stale devices exist', () => {
      const devices = [
        createMockDevice({ lastSeenAt: staleTimestamp }),
      ];
      renderWithRouter(<FleetHealthGauge devices={devices} />);
      expect(screen.getByText('Improve coverage')).toBeInTheDocument();
    });
  });
});
