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
| IP별 사용량 제한 (시간당 5회, 하루 20회, Redis Lua 토큰 버킷) | ✅ |
| 전역 일일 예산 ($1, 선차감 → 실제 비용 정산) | ✅ |
| 긴급 차단 스위치, 예산 소진·Redis 장애 시 데모 응답 | ✅ |
| 포트폴리오 메인 페이지 (https://minunglee.cloud) | ✅ |
| 초대코드: 회사별 발급, 서명 쿠키, 열람 기록, 등급별 모델·한도·예산 | ✅ |
| 응답 캐시 · 장애 대응 (Resilience4j) | 예정 |
| 관리자 대시보드 (본인 전용 로그인, 사용량·예산·초대코드 현황·열람 기록) | ✅ |

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

## 배포

`main`에 머지되면 GitHub Actions가 테스트 → Docker 이미지 빌드(ghcr.io) → SSH로 서버 배포 → 헬스 체크까지 자동으로 진행합니다.

| 항목 | 내용 |
| --- | --- |
| 서버 | 카페24 가상서버 (vCPU 4, RAM 2GB, Ubuntu 24.04) |
| 주소 | https://minunglee.cloud (포트폴리오) · https://minunglee.cloud/gateway (체험) |
| 구성 | Nginx → Spring Boot, PostgreSQL, Redis (`deploy/compose.prod.yaml`) |
| HTTPS | Let's Encrypt 인증서, 서버의 certbot이 자동 갱신 (갱신 후 Nginx 자동 재적용) |
| 메모리 한도 | 앱 768MB(힙 512MB), PostgreSQL 256MB, Redis 64MB, Nginx 32MB |
| 보안 | SSH 키 로그인만 허용, 배포 전용 계정(sudo 없음), 방화벽 22/80/443, fail2ban, 자동 보안 업데이트 |
| 비밀값 | DB 비밀번호와 API 키는 서버의 `.env`에만 저장 (Git, GitHub에 없음) |

Docker가 연 포트는 ufw 방화벽을 우회하기 때문에, PostgreSQL과 Redis는 외부 포트를 열지 않고 Docker 내부 네트워크로만 접근합니다.

## 비용 방어 흐름

```
요청 → IP 사용량 제한 ─초과→ 429 + Retry-After
         │
         ├─ 긴급 차단 ON ──────→ 데모 응답 (호출 안 함)
         │
         ├─ 예산 선차감 실패 ──→ 데모 응답 (호출 안 함)
         │
         └─ Claude 호출 ─성공→ 실제 비용으로 정산 (차액 반환)
                        └실패→ 선차감 전액 반환
```

| 장치 | 구현 | 이유 |
| --- | --- | --- |
| IP별 사용량 제한 | Redis Lua 토큰 버킷 + 일일 카운터 | GET→계산→SET을 앱에서 나누면 동시 요청이 같은 값을 읽고 둘 다 통과한다. Lua는 Redis에서 한 번에 실행된다 |
| 일일 예산 | 호출 전 최대 비용 선차감 → 응답 후 정산 | 응답 전에는 실제 비용을 모른다. 최대치로 먼저 잡아야 동시 요청이 몰려도 한도를 넘지 않는다 (200건 동시 테스트로 검증) |
| Redis 장애 | 호출하지 않고 데모 응답 (fail-closed) | 비용 통제를 보장할 수 없으면 호출하지 않는 쪽이 안전하다 |
| IP 위조 방지 | Nginx가 `X-Forwarded-For`를 실제 접속 IP로 덮어씀 | 클라이언트가 보낸 헤더를 이어붙이면 IP를 바꿔가며 제한을 우회할 수 있다 |
| 개인정보 | IP는 HMAC 해시로만 사용 | 원본 IP를 저장하지 않는다 |

## 설계 메모

- **비용은 마이크로달러 정수로 저장**: 단가가 "USD / 100만 토큰"이므로 `토큰 수 × 단가`가 곧 마이크로달러입니다. 부동소수점 오차가 없고, 예산이 실제보다 작게 잡히지 않도록 올림합니다.
- **LLM 호출을 인터페이스로 분리**: `LlmClient` 구현체를 가짜 응답과 실제 호출로 나눠, 개발과 부하 테스트를 API 비용 없이 진행합니다.
