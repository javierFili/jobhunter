package com.jobhunter.shared.web;

import com.jobhunter.shared.api.GlobalExceptionHandler;
import com.jobhunter.shared.json.Json;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Per-user API rate limits (in memory): general traffic, plus stricter limits for actions that spend money or quota
 * (AI calls, search runs). Returns RFC 9457 problem + Retry-After on 429.
 */
@Component
public class ApiRateLimitInterceptor implements HandlerInterceptor {

    private static final Pattern AI = Pattern.compile(
            "^/api/v1/(jobs/\\d+/(analyze|match|optimize-resume|cover-letters)|matches/analyze-top|resumes/uploads/\\d+/ai-parse|analytics/skills/recommendations)$");
    private static final Pattern SEARCH_RUN = Pattern.compile("^/api/v1/searches/\\d+/run$");
    private static final int MAX_TRACKED = 20_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int generalPerMinute;
    private final int aiPerMinute;
    private final int searchRunsPerMinute;

    public ApiRateLimitInterceptor(@Value("${jobhunter.rate-limit.general-per-minute:300}") int general,
                                   @Value("${jobhunter.rate-limit.ai-per-minute:20}") int ai,
                                   @Value("${jobhunter.rate-limit.search-runs-per-minute:10}") int searchRuns) {
        this.generalPerMinute = general;
        this.aiPerMinute = ai;
        this.searchRunsPerMinute = searchRuns;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String who = principal(request);
        boolean post = "POST".equalsIgnoreCase(request.getMethod());
        if (!consume(who + ":all", generalPerMinute)
                || (post && AI.matcher(path).matches() && !consume(who + ":ai", aiPerMinute))
                || (post && SEARCH_RUN.matcher(path).matches() && !consume(who + ":run", searchRunsPerMinute))) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            var pd = GlobalExceptionHandler.problem(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Demasiadas solicitudes; espera un minuto e inténtalo de nuevo");
            response.getWriter().write(Json.write(Map.of("type", pd.getType().toString(), "title", pd.getTitle(),
                    "status", 429, "detail", pd.getDetail(), "code", "RATE_LIMITED")));
            return false;
        }
        return true;
    }

    private boolean consume(String key, int perMinute) {
        if (buckets.size() > MAX_TRACKED) buckets.clear();
        return buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                .build()).tryConsume(1);
    }

    private static String principal(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) return "u" + jwt.getSubject();
        return "ip" + request.getRemoteAddr();
    }
}
