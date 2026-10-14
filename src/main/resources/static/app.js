const form = document.getElementById('chat-form');
const message = document.getElementById('message');
const counter = document.getElementById('counter');
const submit = document.getElementById('submit');
const result = document.getElementById('result');
const error = document.getElementById('error');

async function refreshBudget() {
	try {
		const response = await fetch('/demo/budget');
		const body = await response.json();
		document.getElementById('budget-text').textContent = `${body.used} / ${body.limit}`;
		document.getElementById('budget-bar').style.width = `${body.usedPercent}%`;
	} catch (e) {
		document.getElementById('budget-text').textContent = '확인 불가';
	}
}

refreshBudget();

message.addEventListener('input', () => {
	counter.textContent = `${message.value.length.toLocaleString()} / 2,000`;
});

form.addEventListener('submit', async (event) => {
	event.preventDefault();
	error.hidden = true;
	submit.disabled = true;
	submit.textContent = '응답 기다리는 중...';

	try {
		const response = await fetch('/demo/chat', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ message: message.value }),
		});
		const body = await response.json();
		if (!response.ok) {
			const retry = body.retryAfterSeconds ? ` (${formatWait(body.retryAfterSeconds)} 후 가능)` : '';
			throw new Error((body.detail || '요청을 처리하지 못했습니다.') + retry);
		}
		document.getElementById('answer').textContent = body.answer;
		document.getElementById('input-tokens').textContent = body.inputTokens.toLocaleString();
		document.getElementById('output-tokens').textContent = body.outputTokens.toLocaleString();
		document.getElementById('cost').textContent = body.cost;
		document.getElementById('latency').textContent = `${body.latencyMs.toLocaleString()} ms`;
		document.getElementById('remaining').textContent =
			`남은 횟수: 이번 시간 ${body.remainingThisHour}회 · 오늘 ${body.remainingToday}회`;
		result.classList.toggle('degraded', body.mode === 'demo');
		result.hidden = false;
	} catch (e) {
		error.textContent = e.message;
		error.hidden = false;
	} finally {
		submit.disabled = false;
		submit.textContent = '보내기';
		refreshBudget();
	}
});

function formatWait(seconds) {
	if (seconds >= 3600) return `${Math.ceil(seconds / 3600)}시간`;
	if (seconds >= 60) return `${Math.ceil(seconds / 60)}분`;
	return `${seconds}초`;
}
