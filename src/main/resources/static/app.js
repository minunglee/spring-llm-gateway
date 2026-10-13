const form = document.getElementById('chat-form');
const message = document.getElementById('message');
const counter = document.getElementById('counter');
const submit = document.getElementById('submit');
const result = document.getElementById('result');
const error = document.getElementById('error');

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
			throw new Error(body.detail || '요청을 처리하지 못했습니다.');
		}
		document.getElementById('answer').textContent = body.answer;
		document.getElementById('input-tokens').textContent = body.inputTokens.toLocaleString();
		document.getElementById('output-tokens').textContent = body.outputTokens.toLocaleString();
		document.getElementById('cost').textContent = body.cost;
		document.getElementById('latency').textContent = `${body.latencyMs.toLocaleString()} ms`;
		result.hidden = false;
	} catch (e) {
		error.textContent = e.message;
		error.hidden = false;
	} finally {
		submit.disabled = false;
		submit.textContent = '보내기';
	}
});
