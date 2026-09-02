package com.homerun.domain.auth.repository;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class EmailVerificationStore {

    private static final DefaultRedisScript<Long> ACQUIRE_SEND_PERMIT = new DefaultRedisScript<>("""
            if redis.call('exists', KEYS[2]) == 1 then return -1 end
            local count = redis.call('incr', KEYS[1])
            if count == 1 then redis.call('pexpire', KEYS[1], ARGV[2]) end
            if count > tonumber(ARGV[1]) then return -2 end
            redis.call('set', KEYS[2], '1', 'PX', ARGV[3])
            return count
            """, Long.class);

    private static final DefaultRedisScript<Long> VERIFY_CODE = new DefaultRedisScript<>("""
            local stored = redis.call('get', KEYS[1])
            if not stored then return -1 end
            local attempts = redis.call('incr', KEYS[2])
            if attempts == 1 then
              local ttl = redis.call('pttl', KEYS[1])
              if ttl > 0 then redis.call('pexpire', KEYS[2], ttl) end
            end
            if attempts > tonumber(ARGV[2]) then
              redis.call('del', KEYS[1], KEYS[2])
              return -3
            end
            if stored ~= ARGV[1] then return -2 end
            redis.call('del', KEYS[1], KEYS[2])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> CONSUME_TOKEN = new DefaultRedisScript<>("""
            local stored = redis.call('get', KEYS[1])
            if not stored or stored ~= ARGV[1] then return 0 end
            redis.call('del', KEYS[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public EmailVerificationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public SendPermit acquireSendPermit(String emailHash, int maxSends, Duration sendWindow, Duration resendCooldown) {
        try {
            Long result = redisTemplate.execute(
                    ACQUIRE_SEND_PERMIT,
                    List.of(sendCountKey(emailHash), cooldownKey(emailHash)),
                    String.valueOf(maxSends),
                    String.valueOf(sendWindow.toMillis()),
                    String.valueOf(resendCooldown.toMillis()));
            if (result == null) {
                throw unavailable(null);
            }
            if (result == -1) {
                return SendPermit.COOLDOWN;
            }
            if (result == -2) {
                return SendPermit.LIMIT_EXCEEDED;
            }
            return SendPermit.GRANTED;
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    public void saveCode(String emailHash, String codeDigest, Duration ttl) {
        execute(() -> {
            redisTemplate.opsForValue().set(codeKey(emailHash), codeDigest, ttl);
            redisTemplate.delete(attemptsKey(emailHash));
        });
    }

    public VerificationResult verifyCode(String emailHash, String codeDigest, int maxAttempts) {
        try {
            Long result = redisTemplate.execute(
                    VERIFY_CODE,
                    List.of(codeKey(emailHash), attemptsKey(emailHash)),
                    codeDigest,
                    String.valueOf(maxAttempts));
            if (result == null || result == -1) {
                return VerificationResult.EXPIRED;
            }
            if (result == -2) {
                return VerificationResult.INVALID;
            }
            if (result == -3) {
                return VerificationResult.ATTEMPTS_EXCEEDED;
            }
            return VerificationResult.VERIFIED;
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    public void saveVerifiedToken(String tokenHash, String email, Duration ttl) {
        execute(() -> redisTemplate.opsForValue().set(verifiedKey(tokenHash), email, ttl));
    }

    public boolean consumeVerifiedToken(String tokenHash, String email) {
        try {
            Long result = redisTemplate.execute(CONSUME_TOKEN, List.of(verifiedKey(tokenHash)), email);
            return result != null && result == 1;
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    public void removePendingCode(String emailHash) {
        execute(() ->
                redisTemplate.delete(List.of(codeKey(emailHash), attemptsKey(emailHash), cooldownKey(emailHash))));
    }

    private void execute(Runnable operation) {
        try {
            operation.run();
        } catch (DataAccessException exception) {
            throw unavailable(exception);
        }
    }

    private BusinessException unavailable(Throwable cause) {
        return cause == null
                ? new BusinessException(ErrorCode.EMAIL_AUTH_UNAVAILABLE)
                : new BusinessException(ErrorCode.EMAIL_AUTH_UNAVAILABLE, cause);
    }

    private String codeKey(String hash) {
        return "auth:email:code:" + hash;
    }

    private String attemptsKey(String hash) {
        return "auth:email:attempts:" + hash;
    }

    private String cooldownKey(String hash) {
        return "auth:email:cooldown:" + hash;
    }

    private String sendCountKey(String hash) {
        return "auth:email:send-count:" + hash;
    }

    private String verifiedKey(String hash) {
        return "auth:email:verified:" + hash;
    }

    public enum SendPermit {
        GRANTED,
        COOLDOWN,
        LIMIT_EXCEEDED
    }

    public enum VerificationResult {
        VERIFIED,
        INVALID,
        EXPIRED,
        ATTEMPTS_EXCEEDED
    }
}
