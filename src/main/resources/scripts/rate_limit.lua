-- IP별 사용량 제한: 시간당 토큰 버킷 + 일일 횟수를 한 번에 원자적으로 검사·차감한다.
-- KEYS[1]: 토큰 버킷 해시, KEYS[2]: 오늘 사용 횟수
-- ARGV[1]: 버킷 용량, ARGV[2]: 버킷이 빈 상태에서 가득 차는 데 걸리는 시간(ms)
-- ARGV[3]: 현재 시각(ms), ARGV[4]: 일일 한도, ARGV[5]: 일일 카운터 만료(초)
-- 반환: {허용 1/0, 이번 시간 남은 횟수, 오늘 남은 횟수, 재시도까지 ms (-1이면 내일)}
local capacity = tonumber(ARGV[1])
local refillMs = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local dailyLimit = tonumber(ARGV[4])
local rate = capacity / refillMs

local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(bucket[1]) or capacity
local ts = tonumber(bucket[2]) or now
tokens = math.min(capacity, tokens + math.max(0, now - ts) * rate)

local usedToday = tonumber(redis.call('GET', KEYS[2]) or '0')
if usedToday >= dailyLimit then
  return {0, math.floor(tokens), 0, -1}
end
if tokens < 1 then
  return {0, 0, dailyLimit - usedToday, math.ceil((1 - tokens) / rate)}
end

tokens = tokens - 1
redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))
redis.call('PEXPIRE', KEYS[1], refillMs)
redis.call('INCR', KEYS[2])
redis.call('EXPIRE', KEYS[2], tonumber(ARGV[5]))
return {1, math.floor(tokens), dailyLimit - usedToday - 1, 0}
