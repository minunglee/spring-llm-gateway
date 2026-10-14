-- 전역 일일 예산 선차감. 확인과 차감 사이에 다른 요청이 끼어들 수 없도록 한 스크립트로 처리한다.
-- KEYS[1]: 오늘 누적 비용(마이크로달러)
-- ARGV[1]: 선차감 금액, ARGV[2]: 일일 한도, ARGV[3]: 만료(초)
-- 반환: 차감 후 남은 예산, 한도를 넘으면 -1
local used = tonumber(redis.call('GET', KEYS[1]) or '0')
local amount = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
if used + amount > limit then
  return -1
end
redis.call('INCRBY', KEYS[1], amount)
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
return limit - used - amount
