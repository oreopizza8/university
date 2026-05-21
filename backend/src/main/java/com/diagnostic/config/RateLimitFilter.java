package com.diagnostic.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${app.ratelimit.per-minute}")
    private int perMinute;

    @Value("${app.ratelimit.per-hour}")
    private int perHour;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/api/diagnostic") && "POST".equalsIgnoreCase(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String ip = resolveClientIp(req);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());

        if (bucket.tryConsume(1)) {
            res.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            chain.doFilter(req, res);
            return;
        }

        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("Retry-After", "60");
        res.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"진단 요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요.\"}");
    }

    private Bucket newBucket() {
        Bandwidth minute = Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build();
        Bandwidth hour = Bandwidth.builder().capacity(perHour).refillGreedy(perHour, Duration.ofHours(1)).build();
        return Bucket.builder().addLimit(minute).addLimit(hour).build();
    }

    private String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return req.getRemoteAddr();
    }
}
