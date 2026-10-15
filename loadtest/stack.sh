#!/usr/bin/env bash
# 서버에서 부하 테스트 복제본을 관리한다. 운영 스택(app)과는 이름·DB·Redis가 모두 분리되어 있다.
#   ./stack.sh up [MOCK_LATENCY] [VIRTUAL_THREADS] [GUEST_BUDGET_USD]   복제본을 새로 띄운다 (DB·Redis 초기화)
#   ./stack.sh stats                                                   메모리·CPU 사용량
#   ./stack.sh budget                                                  오늘 게스트 예산 사용액(마이크로달러)
#   ./stack.sh sql "SELECT ..."                                       테스트 DB 조회
#   ./stack.sh down                                                    복제본과 데이터를 모두 지운다
set -euo pipefail
cd "$(dirname "$0")"
# 운영과 같은 이미지 버전을 쓴다
export IMAGE_TAG=$(grep '^IMAGE_TAG=' /home/deploy/app/.env | cut -d= -f2)
COMPOSE="docker compose -f compose.loadtest.yaml"

case "${1:-}" in
	up)
		export MOCK_LATENCY="${2:-0ms}" VIRTUAL_THREADS="${3:-true}" GUEST_BUDGET_USD="${4:-1000}"
		$COMPOSE down -v >/dev/null 2>&1 || true
		$COMPOSE up -d >/dev/null 2>&1
		until curl -fs 127.0.0.1:8090/actuator/health >/dev/null 2>&1; do sleep 2; done
		echo "up: latency=$MOCK_LATENCY virtualThreads=$VIRTUAL_THREADS guestBudget=\$$GUEST_BUDGET_USD image=${IMAGE_TAG:0:7}"
		;;
	stats)
		docker stats --no-stream --format '{{.Name}} {{.MemUsage}} cpu={{.CPUPerc}}' | grep -E '^(loadtest|app)-'
		free -m | sed -n 2p
		;;
	budget)
		$COMPOSE exec -T redis redis-cli --scan --pattern 'budget:guest:*' \
			| xargs -r -I{} $COMPOSE exec -T redis redis-cli GET {}
		;;
	sql)
		# 테스트 DB에 SQL 실행 (예: 요청별 처리 시간 백분위)
		$COMPOSE exec -T postgres psql -U gateway -d gateway -At -c "$2"
		;;
	down)
		$COMPOSE down -v >/dev/null 2>&1
		echo "down"
		;;
	*)
		echo "usage: $0 up|stats|budget|down" >&2
		exit 1
		;;
esac
