// 1. 게이트웨이 자체 처리 시간
// AI 응답 대기를 0으로 두고, 사용량 제한(Redis Lua) + 예산 선차감·정산 + 요청 기록(PostgreSQL)에 드는 시간만 잰다.
import http from 'k6/http';
import { check } from 'k6';

export const options = {
	scenarios: {
		steady: { executor: 'constant-vus', vus: Number(__ENV.VUS || 50), duration: __ENV.DURATION || '60s' },
	},
	summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
	thresholds: { http_req_failed: ['rate<0.01'] },
};

const TARGET = __ENV.TARGET || 'http://127.0.0.1:8090/demo/chat';
const PARAMS = { headers: { 'Content-Type': 'application/json' } };

export default function () {
	const res = http.post(TARGET, JSON.stringify({ message: 'load test' }), PARAMS);
	check(res, { 'status 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
	return { stdout: summary('overhead', data), [`results/overhead-${__ENV.LABEL || 'run'}.json`]: JSON.stringify(data) };
}

function summary(name, data) {
	const d = data.metrics.http_req_duration.values;
	const reqs = data.metrics.http_reqs.values;
	const failed = data.metrics.http_req_failed.values.rate;
	return `\n[${name}] requests=${reqs.count} rps=${reqs.rate.toFixed(1)} failed=${(failed * 100).toFixed(2)}%`
		+ ` avg=${d.avg.toFixed(1)}ms p50=${d['p(50)'].toFixed(1)}ms p95=${d['p(95)'].toFixed(1)}ms`
		+ ` p99=${d['p(99)'].toFixed(1)}ms max=${d.max.toFixed(1)}ms\n`;
}
