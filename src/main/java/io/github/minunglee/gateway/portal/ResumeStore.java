package io.github.minunglee.gateway.portal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 비공개 폴더에서 이력서 내용(resume.json)과 PDF(resume.pdf)를 읽는다.
 * <p>
 * 공개 저장소에 개인정보를 올리지 않기 위해 파일은 서버에만 두고 컨테이너에 읽기 전용으로 마운트한다.
 * 요청마다 파일을 읽으므로 서버의 파일만 바꾸면 재배포 없이 내용이 갱신된다.
 */
@Component
@EnableConfigurationProperties(ResumeStore.Properties.class)
class ResumeStore {

	private final Path dir;

	private final JsonMapper jsonMapper;

	ResumeStore(Properties properties, JsonMapper jsonMapper) {
		this.dir = Path.of(properties.dir());
		this.jsonMapper = jsonMapper;
	}

	Optional<Resume> resume() {
		Path file = dir.resolve("resume.json");
		if (!Files.isReadable(file)) {
			return Optional.empty();
		}
		return Optional.of(jsonMapper.readValue(file.toFile(), Resume.class));
	}

	Optional<Path> pdf() {
		Path file = dir.resolve("resume.pdf");
		return Files.isReadable(file) ? Optional.of(file) : Optional.empty();
	}

	/**
	 * @param dir 비공개 폴더 경로 (resume.json, resume.pdf)
	 */
	@ConfigurationProperties("gateway.resume")
	record Properties(String dir) {
	}

}
