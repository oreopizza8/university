package com.diagnostic.ingest;

import com.diagnostic.entity.DomesticUniversityCutoff;
import com.diagnostic.repository.DomesticUniversityCutoffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KcueSyncService {

    private final DomesticUniversityCutoffRepository domesticRepo;

    @Value("${app.kcue.api-key}")
    private String apiKey;

    @Value("${app.kcue.api-base}")
    private String apiBase;

    private Instant lastSuccessAt;
    private String lastStatus = "NEVER_RUN";

    @Async("dataIngestExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        sync();
    }

    @Scheduled(cron = "${app.kcue.sync-cron}")
    public void syncDaily() {
        sync();
    }

    public synchronized void sync() {
        if (apiKey == null || apiKey.isBlank()) {
            lastStatus = "SKIPPED_NO_KEY";
            log.info("KCUE 동기화 스킵: API 키 미설정. 환경변수 KCUE_API_KEY 를 설정하세요.");
            return;
        }

        try {
            WebClient client = WebClient.builder()
                    .baseUrl(apiBase)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                    .build();

            List<Map<String, Object>> rows = client.get()
                    .uri(uri -> uri.path("/univCutoff")
                            .queryParam("serviceKey", apiKey)
                            .queryParam("type", "json")
                            .queryParam("numOfRows", 5000)
                            .build())
                    .retrieve()
                    .bodyToMono(KcueResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .map(r -> Optional.ofNullable(r).map(KcueResponse::items).orElse(List.of()))
                    .onErrorResume(e -> {
                        log.warn("KCUE API 호출 실패 — 캐시 유지. cause={}", e.getMessage());
                        return Mono.just(List.of());
                    })
                    .block();

            if (rows == null || rows.isEmpty()) {
                lastStatus = "FALLBACK_KEPT";
                log.info("KCUE 응답 없음 — Fallback 캐시 유지");
                return;
            }

            int upserts = 0;
            for (Map<String, Object> row : rows) {
                String uni = asString(row.get("universityName"));
                String dept = asString(row.get("departmentName"));
                if (uni == null || dept == null) continue;

                DomesticUniversityCutoff entity = domesticRepo
                        .findByUniversityNameAndDepartmentName(uni, dept)
                        .orElseGet(() -> {
                            DomesticUniversityCutoff e = new DomesticUniversityCutoff();
                            e.setUniversityName(uni);
                            e.setDepartmentName(dept);
                            return e;
                        });
                entity.setRatioKo(asDouble(row.getOrDefault("ratioKo", entity.getRatioKo())));
                entity.setRatioMath(asDouble(row.getOrDefault("ratioMath", entity.getRatioMath())));
                entity.setRatioEn(asDouble(row.getOrDefault("ratioEn", entity.getRatioEn())));
                entity.setRatioTg(asDouble(row.getOrDefault("ratioTg", entity.getRatioTg())));
                entity.setCutoffPercentile70(asDouble(row.getOrDefault("cutoffPercentile70", entity.getCutoffPercentile70())));
                domesticRepo.save(entity);
                upserts++;
            }
            lastSuccessAt = Instant.now();
            lastStatus = "OK_" + upserts;
            log.info("KCUE 동기화 완료: {}건 UPSERT", upserts);
        } catch (Exception e) {
            lastStatus = "ERROR_FALLBACK_KEPT";
            log.error("KCUE 동기화 예외 — 캐시 유지. msg={}", e.getMessage());
        }
    }

    public SyncStatus status() {
        return new SyncStatus(lastStatus, lastSuccessAt);
    }

    private static String asString(Object v) { return v == null ? null : v.toString(); }
    private static Double asDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    public record KcueResponse(List<Map<String, Object>> items) {}
    public record SyncStatus(String lastStatus, Instant lastSuccessAt) {}
}
