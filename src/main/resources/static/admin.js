// 관리자 대시보드 동작. 로그인 쿠키(SameSite=Strict)로 인증한다.

async function send(method, url, body) {
	const response = await fetch(url, {
		method,
		headers: body ? { 'Content-Type': 'application/json' } : {},
		body: body ? JSON.stringify(body) : undefined,
	});
	if (!response.ok) {
		throw new Error(`요청 실패 (${response.status})`);
	}
	return response.status === 204 ? null : response.json();
}

const toggle = document.getElementById('killswitch-toggle');
toggle?.addEventListener('click', async () => {
	const enable = toggle.dataset.enabled !== 'true';
	const message = enable ? '모든 AI 호출을 즉시 중단할까요?' : '긴급 차단을 해제할까요?';
	if (!confirm(message)) return;
	await send('PUT', '/admin/killswitch', { enabled: enable });
	location.reload();
});

document.getElementById('invite-create')?.addEventListener('submit', async (event) => {
	event.preventDefault();
	const label = document.getElementById('invite-label').value.trim();
	const validDays = Number(document.getElementById('invite-days').value || 30);
	const created = await send('POST', '/admin/invites', { label, validDays, dailyLimit: 100 });
	const output = document.getElementById('invite-created');
	// 취업 사이트의 포트폴리오 URL 칸에 넣을 초대 링크 (열면 코드가 자동 적용됨)
	output.textContent = `${created.label} 코드: ${created.code}
초대 링크: ${location.origin}/i/${created.code}
(이 화면을 벗어나면 다시 볼 수 없습니다)`;
	output.hidden = false;
	document.getElementById('invite-label').value = '';
	// 발급한 코드는 이 화면에서만 보이므로 새로고침 대신 목록 표만 다시 받아 바꾼다
	await refreshInviteRows();
});

async function refreshInviteRows() {
	const response = await fetch(location.href, { headers: { Accept: 'text/html' } });
	if (!response.ok) return;
	const page = new DOMParser().parseFromString(await response.text(), 'text/html');
	const fresh = page.getElementById('invite-rows');
	if (fresh) {
		document.getElementById('invite-rows').replaceWith(fresh);
	}
}

// 목록 표를 바꿔 끼워도 동작하도록 표 바깥에서 클릭을 받는다
document.addEventListener('click', async (event) => {
	const button = event.target.closest('.revoke');
	if (!button) return;
	if (!confirm(`'${button.dataset.label}' 코드를 폐기할까요? 바로 사용할 수 없게 됩니다.`)) return;
	await send('DELETE', `/admin/invites/${button.dataset.id}`);
	location.reload();
});
