-- 선차감 정산. 실제 비용으로 누적 금액을 고치고, 진행 중 합계에서 이 요청 몫을 뺀다 (두 값을 함께 바꾼다).
-- 실패한 호출은 실제 비용 0으로 정산하면 선차감액 전액이 돌아간다.
-- KEYS[1]: 오늘 누적 금액, KEYS[2]: 진행 중 선차감 합계
-- ARGV[1]: 선차감했던 금액, ARGV[2]: 실제 비용
local reserved = tonumber(ARGV[1])
local actual = tonumber(ARGV[2])
redis.call('INCRBY', KEYS[1], actual - reserved)
redis.call('DECRBY', KEYS[2], reserved)
return 1
