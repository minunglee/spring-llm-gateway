package io.github.minunglee.gateway.invite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 초대코드 생성·정규화·해시.
 * <p>
 * 코드는 헷갈리기 쉬운 문자(0/O, 1/I/L)를 뺀 31자 중 12자를 뽑아 {@code XXXX-XXXX-XXXX} 형태로 만든다.
 * 경우의 수가 31^12(약 8×10^17)라 무작위 대입으로 맞히는 것은 사실상 불가능하고, 입력 시도 횟수도 따로 제한한다.
 */
final class InviteCodes {

	private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

	private static final int LENGTH = 12;

	private static final SecureRandom RANDOM = new SecureRandom();

	private InviteCodes() {
	}

	static String generate() {
		StringBuilder code = new StringBuilder(LENGTH + 2);
		for (int i = 0; i < LENGTH; i++) {
			if (i > 0 && i % 4 == 0) {
				code.append('-');
			}
			code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
		}
		return code.toString();
	}

	/** 사용자가 소문자나 공백, 하이픈 없이 입력해도 같은 코드로 인식한다 */
	static String normalize(String input) {
		return input.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
	}

	static String hash(String input) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(normalize(input).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
