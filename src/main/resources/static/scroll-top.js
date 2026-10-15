// 긴 페이지에서 화면 오른쪽 하단에 "위로가기" 버튼을 띄운다. 어느 정도 내려갔을 때만 보인다
(() => {
	const button = document.createElement('button');
	button.type = 'button';
	button.className = 'scroll-top';
	button.setAttribute('aria-label', '맨 위로');
	button.textContent = '↑';
	button.hidden = true;
	document.body.appendChild(button);

	const update = () => {
		button.hidden = window.scrollY < 400;
	};
	window.addEventListener('scroll', update, { passive: true });
	update();

	button.addEventListener('click', () => {
		const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
		window.scrollTo({ top: 0, behavior: reduce ? 'auto' : 'smooth' });
	});
})();
