package io.github.minunglee.gateway.chat;

class BotCheckFailedException extends RuntimeException {

	BotCheckFailedException() {
		super("자동화된 요청으로 판단되었습니다. 페이지를 새로고침한 뒤 다시 시도해주세요.");
	}

}
