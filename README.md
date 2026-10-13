# spring-llm-gateway

Claude API 앞단에서 rate limit · 일일 예산 · 캐시 · 장애 대응을 처리하는 Spring Boot 게이트웨이

공개 데모를 내 API 키로 운영하면서도 비용이 정해진 한도를 넘지 않도록 하는 것이 목표입니다.

## 현재 구현 상태

| 기능 | 상태 |
| --- | --- |
| 체험 페이지, `POST /demo/chat` | ✅ |
| 가짜 응답 모드 (비용 0원) / 실제 Claude 호출 모드 전환 | ✅ |
| 토큰 사용량 → 비용 계산 (마이크로달러 정수) | ✅ |
| 요청 기록 (PostgreSQL) | ✅ |
| Rate limit · 일일 예산 (Redis Lua) | 예정 |
| 응답 캐시 · 장애 대응 (Resilience4j) | 예정 |
| 관리자 대시보드 | 예정 |

## 기술 스택

Java 21 (가상 스레드) · Spring Boot 4 · Anthropic Java SDK · PostgreSQL 16 · Redis 7 · Flyway · Testcontainers · GitHub Actions

## 로컬 실행

필요한 것: JDK 21, Docker Desktop

```bash
./gradlew bootRun
```

`compose.yaml`의 PostgreSQL과 Redis가 자동으로 함께 뜹니다. 브라우저에서 http://localhost:8080 을 열면 됩니다.

기본값은 **가짜 응답 모드**라 API 키 없이 비용 0원으로 동작합니다. 실제 Claude를 호출하려면 `.env.example`을 `.env`로 복사한 뒤 값을 채웁니다.

```properties
GATEWAY_LLM_MODE=anthropic
ANTHROPIC_API_KEY=sk-ant-...
```

## 테스트

```bash
./gradlew test
```

통합 테스트는 Testcontainers로 실제 PostgreSQL과 Redis를 띄워서 실행합니다.

## 설계 메모

- **비용은 마이크로달러 정수로 저장**: 단가가 "USD / 100만 토큰"이므로 `토큰 수 × 단가`가 곧 마이크로달러입니다. 부동소수점 오차가 없고, 예산이 실제보다 작게 잡히지 않도록 올림합니다.
- **LLM 호출을 인터페이스로 분리**: `LlmClient` 구현체를 가짜 응답과 실제 호출로 나눠, 개발과 부하 테스트를 API 비용 없이 진행합니다.
