const inviteForm = document.getElementById('invite-form');
const inviteClear = document.getElementById('invite-clear');

if (inviteForm) {
	inviteForm.addEventListener('submit', async (event) => {
		event.preventDefault();
		const error = document.getElementById('invite-error');
		error.hidden = true;
		const response = await fetch('/invite', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ code: document.getElementById('invite-code').value }),
		});
		if (response.ok) {
			location.reload();
			return;
		}
		const body = await response.json().catch(() => ({}));
		error.textContent = body.detail || '초대코드를 확인하지 못했습니다.';
		error.hidden = false;
	});
}

if (inviteClear) {
	inviteClear.addEventListener('click', async () => {
		await fetch('/invite', { method: 'DELETE' });
		location.reload();
	});
}
