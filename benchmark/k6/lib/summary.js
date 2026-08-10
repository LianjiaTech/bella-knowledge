export function handleSummary(data) {
  const resultDir = __ENV.RESULT_DIR || '/results';
  const runId = __ENV.RUN_ID || `run-${Date.now()}`;
  return {
    stdout: consoleSummary(data, runId),
    [`${resultDir}/${runId}.json`]: JSON.stringify(data, null, 2),
  };
}

function consoleSummary(data, runId) {
  const metrics = data.metrics || {};
  const duration = (metrics.http_req_duration || {}).values || {};
  const requests = (metrics.http_reqs || {}).values || {};
  const failures = (metrics.http_req_failed || {}).values || {};
  const checks = (metrics.checks || {}).values || {};
  const operationLines = Object.keys(metrics)
    .filter((name) => name.startsWith('mixed_') && name.endsWith('_duration'))
    .sort()
    .map((name) => trendLine(name, metrics[name].values || {}));
  return [
    '',
    `benchmark: ${runId}`,
    `requests/s: ${format(requests.rate)}`,
    `latency p50/p95/p99: ${format(duration.med)} / ${format(duration['p(95)'])} / ${format(duration['p(99)'])} ms`,
    `failed requests: ${formatRate(failures.rate)}`,
    `successful checks: ${formatRate(checks.rate)}`,
    ...operationLines,
    '',
  ].join('\n');
}

function trendLine(name, values) {
  const operation = name.slice('mixed_'.length, -'_duration'.length);
  return `${operation} p50/p95/p99: ${format(values.med)} / ${format(values['p(95)'])} / ${format(values['p(99)'])} ms`;
}

function format(value) {
  return Number.isFinite(value) ? value.toFixed(2) : 'n/a';
}

function formatRate(value) {
  return Number.isFinite(value) ? `${(value * 100).toFixed(2)}%` : 'n/a';
}
