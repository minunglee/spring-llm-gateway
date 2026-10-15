// 3. 예산 정확도
// 작은 예산에 요청 1,000건을 300명이 동시에 쏟아붓는다. 예산을 넘는 순간부터는 AI를 호출하지 않고
// 데모 응답(mode=demo)을 받아야 한다. 끝난 뒤 Redis의 사용액이 한도를 넘지 않았는지 run.sh에서 확인한다.
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const served = new Counter('llm_served');
const degraded = new Counter('budget_degraded');
const other = new Counter('other_status');

export const options = {
	scenarios: {
		burst: { executor: 'shared-iterations', vus: 300, iterations: 1000, maxDuration: '2m' },
	},
};

const TARGET = __ENV.TARGET || 'http://127.0.0.1:8090/demo/chat';
const PARAMS = { headers: { 'Content-Type': 'application/json' } };

export default function () {
	const res = http.post(TARGET, JSON.stringify({ message: 'budget burst' }), PARAMS);
	if (res.status !== 200) {
		other.add(1);
		return;
	}
	const mode = res.json('mode');
	if (mode === 'demo') {
		degraded.add(1);
	}
	else {
		served.add(1);
	}
}

export function handleSummary(data) {
	const get = (name) => (data.metrics[name] ? data.metrics[name].values.count : 0);
	const line = `\n[budget-burst] total=1000 llm_served=${get('llm_served')} demo=${get('budget_degraded')}`
		+ ` other=${get('other_status')}\n`;
	return { stdout: line, 'results/budget-burst.json': JSON.stringify(data) };
}
