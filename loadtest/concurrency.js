// 2. 느린 AI 응답에서 동시 접속 처리
// AI가 2초씩 걸린다고 가정하고 동시 사용자를 늘린다. 스레드가 응답 대기에 묶이면 처리량이 막힌다.
// 가상 스레드를 켰을 때와 껐을 때(톰캣 기본 200 스레드)를 같은 조건으로 비교한다.
import http from 'k6/http';
import { check } from 'k6';

const PEAK = Number(__ENV.PEAK || 500);

export const options = {
	scenarios: {
		ramp: {
			executor: 'ramping-vus',
			startVUs: 0,
			stages: [
				{ duration: '30s', target: PEAK },
				{ duration: '60s', target: PEAK },
				{ duration: '10s', target: 0 },
			],
			gracefulRampDown: '10s',
		},
	},
	summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

const TARGET = __ENV.TARGET || 'http://127.0.0.1:8090/demo/chat';
const PARAMS = { headers: { 'Content-Type': 'application/json' }, timeout: '30s' };

export default function () {
	const res = http.post(TARGET, JSON.stringify({ message: 'load test' }), PARAMS);
	check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
	const d = data.metrics.http_req_duration.values;
	const reqs = data.metrics.http_reqs.values;
	const failed = data.metrics.http_req_failed.values.rate;
	const line = `\n[concurrency ${__ENV.LABEL || ''}] peak=${PEAK} requests=${reqs.count} rps=${reqs.rate.toFixed(1)}`
		+ ` failed=${(failed * 100).toFixed(2)}% avg=${d.avg.toFixed(0)}ms p50=${d['p(50)'].toFixed(0)}ms`
		+ ` p95=${d['p(95)'].toFixed(0)}ms p99=${d['p(99)'].toFixed(0)}ms max=${d.max.toFixed(0)}ms\n`;
	return { stdout: line, [`results/concurrency-${__ENV.LABEL || 'run'}.json`]: JSON.stringify(data) };
}
