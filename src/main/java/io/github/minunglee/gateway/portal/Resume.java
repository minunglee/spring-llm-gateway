package io.github.minunglee.gateway.portal;

import java.util.List;

/**
 * 이력서·경력기술서 내용. 개인정보가 있어 저장소(Git)가 아니라 서버의 비공개 폴더(resume.json)에서 읽는다.
 */
public record Resume(String name, String headline, List<String> summary, Contact contact, List<String> skills,
		List<Experience> experience, List<Project> projects, List<Entry> education, List<Certificate> certificates,
		List<Entry> training, List<CoverLetter> coverLetter) {

	/** 연락처 */
	public record Contact(String email, String phone) {
	}

	/** 경력 (회사, 기간, 담당 업무) */
	public record Experience(String company, String period, String role) {
	}

	/**
	 * 경력기술서의 프로젝트 하나
	 *
	 * @param org 소속 (파견처 등)
	 * @param tags 기술 스택
	 */
	public record Project(String title, String period, String org, List<String> tags, String overview,
			List<Section> sections) {
	}

	/** 프로젝트 안의 큰 묶음 (예: OSB 아키텍처 설계) */
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

	/** 학력·교육 이수 한 줄 */
	public record Entry(String period, String name) {
	}

	/** 자격증 (취득일, 이름) */
	public record Certificate(String date, String name) {
	}

	/** 자기소개서 항목 (소제목 + 문단) */
	public record CoverLetter(String title, List<String> paragraphs) {
	}

}
