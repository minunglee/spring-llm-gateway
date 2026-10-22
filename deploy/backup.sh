#!/usr/bin/env bash
# PostgreSQL 매일 백업 (배포 때 deploy 계정의 crontab에 등록된다: 매일 04:30 KST).
#
# - gateway: 초대코드·열람 기록처럼 다시 만들 수 없는 데이터 → 14일 보관
# - jipdon: 공공 API로 다시 모을 수 있지만 전국을 다시 채우는 데 며칠이 걸린다 → 3일 보관 (1개에 수백 MB)
#
# pg_dump -Fc(압축 형식)로 임시 파일에 받고, pg_restore --list로 읽히는지 확인한 뒤에만 이름을 바꿔 남긴다.
# 복원: docker compose -f ~/app/compose.prod.yaml exec -T postgres pg_restore -U gateway -d <db> --clean --if-exists < 파일
set -euo pipefail

BACKUP_DIR="$HOME/backups"
COMPOSE=(docker compose -f "$HOME/app/compose.prod.yaml")
STAMP=$(date +%Y%m%d-%H%M)

mkdir -p "$BACKUP_DIR"
cd "$HOME/app"

backup() {
	local db=$1 keep_days=$2
	local file="$BACKUP_DIR/$db-$STAMP.dump"
	"${COMPOSE[@]}" exec -T postgres pg_dump -U gateway -Fc "$db" > "$file.tmp"
	"${COMPOSE[@]}" exec -T postgres pg_restore --list < "$file.tmp" > /dev/null
	mv "$file.tmp" "$file"
	find "$BACKUP_DIR" -name "$db-*.dump" -mtime +"$keep_days" -delete
	echo "$(date '+%F %T') $db 백업 완료 $(du -h "$file" | cut -f1)"
}

trap 'rm -f "$BACKUP_DIR"/*.tmp' EXIT
backup gateway 14
backup jipdon 3
