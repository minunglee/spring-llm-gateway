-- 등급별 일일 예산 선차감. 확인과 차감 사이에 다른 요청이 끼어들 수 없도록 한 스크립트로 처리한다.
-- KEYS[1]: 오늘 누적 금액 (정산된 실제 비용 + 아직 정산 전인 선차감액, 마이크로달러)
-- KEYS[2]: 아직 정산 전인 선차감액 합계 (진행 중인 요청들이 잡아둔 몫)
-- ARGV[1]: 선차감 금액, ARGV[2]: 일일 한도, ARGV[3]: 만료(초)
-- 반환:
--   0 이상  차감 성공, 남은 예산
--   -1      실제로 쓴 돈만으로 한도를 넘음 (기다려도 소용없음)
--   -2      진행 중인 요청들의 선차감 때문에 지금은 자리가 없음 (정산되면 자리가 생길 수 있음)
local used = tonumber(redis.call('GET', KEYS[1]) or '0')
local inflight = tonumber(redis.call('GET', KEYS[2]) or '0')
local amount = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
if used + amount > limit then
  if used - inflight + amount > limit then
    return -1
  end
  return -2
end
redis.call('INCRBY', KEYS[1], amount)
redis.call('INCRBY', KEYS[2], amount)
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3]))
return limit - used - amount
