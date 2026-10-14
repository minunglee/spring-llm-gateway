package io.github.minunglee.gateway.portal;

import java.util.List;

/**
 * 이력서·경력기술서 내용. 개인정보가 있어 저장소(Git)가 아니라 서버의 비공개 폴더(resume.json)에서 읽는다.
 */
public record Resume(String name, String headline, List<String> summary, Contact contact, List<String> skills,
		List<Experience> experience, List<Project> projects, List<Entry> education, List<Certificate> certificates,
		List<Entry> training, List<CoverLetter> coverLetter) {

	public record Contact(String email, String phone) {
	}

	public record Experience(String company, String period, String role) {
	}

	public record Project(String title, String period, String org, List<String> tags, String overview,
			List<Section> sections) {
	}

	public record Section(String title, String overview, List<Item> items) {
	}

	/**
	 * problem이 있으면 "문제 → 해결" 형태로, 없으면 해결 내용만 보여준다.
	 *
	 * @param problemLabel problem 앞 라벨 (기본 "문제", 예: "상황")
	 */
	public record Item(String title, String problem, String problemLabel, List<String> points) {

		public String problemLabelOrDefault() {
			return problemLabel == null ? "문제" : problemLabel;
		}

	}

	public record Entry(String period, String name) {
	}

	public record Certificate(String date, String name) {
	}

	/** 자기소개서 항목 (소제목 + 문단) */
	public record CoverLetter(String title, List<String> paragraphs) {
	}

}
