export const BASE_URL = __ENV.BASE_URL || 'http://api:8081';
export const AUTH_TOKEN = __ENV.AUTH_TOKEN || 'local-dev';
export const SPACE_CODE = __ENV.SPACE_CODE || 'local-dev';
export const DURATION = __ENV.BENCH_DURATION || '30s';
export const VUS = numberEnv('BENCH_VUS', 4);
export const P95_MS = numberEnv('BENCH_P95_MS', 1000);

export function numberEnv(name, fallback) {
  const value = Number(__ENV[name]);
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

export function loadOptions(overrides = {}) {
  const defaults = {
    vus: VUS,
    duration: DURATION,
    setupTimeout: '15m',
    teardownTimeout: '2m',
    discardResponseBodies: false,
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: {
      checks: ['rate>0.99'],
      http_req_failed: ['rate<0.01'],
      application_errors: ['rate<0.01'],
      http_req_duration: [`p(95)<${P95_MS}`],
    },
  };
  return Object.assign(defaults, overrides);
}
