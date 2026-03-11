# Phase 1 Baseline Plan (Reactive Migration Program)

## Objective
Establish measurable baseline latency/error metrics before refactoring persistence and AI processing layers.

## Implemented
- Added `ApiMetricsWebFilter` to record endpoint latency and error counters.
- Grouped metrics by functional API areas:
  - `auth`, `upload`, `projects`, `jobs`, `chat`, `reports`, `dashboard`, `notifications`, `export`, `other`
- Exposed actuator metrics endpoint:
  - `GET /actuator/metrics`
- Enabled percentile baseline tracking for:
  - `codescope.http.server.latency` (`p50`, `p95`, `p99`)

## Metrics Added
- `codescope.http.server.latency`
  - Tags: `api_group`, `method`, `status`, `status_class`
- `codescope.http.server.errors`
  - Tags: `api_group`, `method`, `exception`

## Baseline Capture Commands
- List metrics:
  - `GET /actuator/metrics`
- Latency metric:
  - `GET /actuator/metrics/codescope.http.server.latency`
- Error metric:
  - `GET /actuator/metrics/codescope.http.server.errors`

## Exit Criteria for Phase 1
- Metrics visible in actuator.
- All key API groups produce latency data.
- Error counters increment on failed requests.
- Baseline values for p50/p95/p99 captured before Phase 2 changes.
