const form = document.getElementById('chat-form');
const message = document.getElementById('message');
const counter = document.getElementById('counter');
const submit = document.getElementById('submit');
const result = document.getElementById('result');
const error = document.getElementById('error');

// 봇 확인 위젯 (게스트에게만 있음). 토큰은 한 번만 쓸 수 있어 요청마다 새로 받는다
const turnstileBox = document.getElementById('turnstile');
let turnstileWidget = null;

window.onTurnstileLoad = () => {
	turnstileWidget = turnstile.render(turnstileBox, {
		sitekey: turnstileBox.dataset.sitekey,
		appearance: 'interaction-only',
		theme: 'auto',
		language: 'ko',
	});
};

function turnstileToken() {
	if (!turnstileBox) return undefined;
	// 체크 박스가 떠 있는데 아직 체크하지 않았으면 토큰이 없다
	return turnstileWidget === null ? '' : (turnstile.getResponse(turnstileWidget) || '');
}

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
	const token = turnstileToken();
	if (token === '') {
		error.textContent = '위의 '사람인지 확인' 체크 박스를 체크한 뒤 다시 보내주세요. 체크 박스가 없다면 잠시 후 다시 눌러주세요.';
		error.hidden = false;
		return;
	}
	submit.disabled = true;
	submit.textContent = '응답 기다리는 중...';

	try {
		const response = await fetch('/demo/chat', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ message: message.value, turnstileToken: token }),
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
		if (turnstileWidget !== null) turnstile.reset(turnstileWidget);
		refreshBudget();
	}
});

function formatWait(seconds) {
	if (seconds >= 3600) return `${Math.ceil(seconds / 3600)}시간`;
	if (seconds >= 60) return `${Math.ceil(seconds / 60)}분`;
	return `${seconds}초`;
}
