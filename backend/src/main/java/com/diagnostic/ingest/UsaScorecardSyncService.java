package com.diagnostic.ingest;

import com.diagnostic.entity.GlobalUniversityCutoff;
import com.diagnostic.repository.GlobalUniversityCutoffRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class UsaScorecardSyncService {

    private final GlobalUniversityCutoffRepository globalRepo;

    @Value("${app.scorecard.api-key}")
    private String apiKey;

    @Value("${app.scorecard.api-base}")
    private String apiBase;

    @Value("${app.scorecard.fields}")
    private String fields;

    @Value("${app.scorecard.school-ids}")
    private String schoolIdsCsv;

    private Instant lastSuccessAt;
    private String lastStatus = "NEVER_RUN";

    @Async("dataIngestExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() { sync(); }

    @Scheduled(cron = "${app.scorecard.sync-cron}")
    public void syncSeasonal() { sync(); }

    public synchronized void sync() {
        if (apiKey == null || apiKey.isBlank()) {
            lastStatus = "SKIPPED_NO_KEY";
            log.info("College Scorecard 동기화 스킵: SCORECARD_API_KEY 미설정. data-staging/us_cds_stats.csv Fallback 유지.");
            return;
        }
        if (schoolIdsCsv == null || schoolIdsCsv.isBlank()) {
            lastStatus = "SKIPPED_NO_TARGETS";
            return;
        }

        try {
            WebClient client = WebClient.builder()
                    .baseUrl(apiBase)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                    .build();
            int upserts = 0;

            for (String idRaw : schoolIdsCsv.split(",")) {
                String id = idRaw.trim();
                if (id.isEmpty()) continue;

                JsonNode root = client.get()
                        .uri(uri -> uri.path("/schools")
                                .queryParam("api_key", apiKey)
                                .queryParam("id", id)
                                .queryParam("fields", "id,school.name," + fields)
                                .build())
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(20))
                        .onErrorResume(e -> {
                            log.warn("Scorecard 호출 실패(id={}): {}", id, e.getMessage());
                            return Mono.empty();
                        })
                        .block();

                if (root == null) continue;
                JsonNode results = root.path("results");
                if (!results.isArray() || results.isEmpty()) continue;
                JsonNode school = results.get(0);

                String name = school.path("school.name").asText(null);
                if (name == null) continue;

                GlobalUniversityCutoff entity = globalRepo.findByUniversityName(name)
                        .orElseGet(() -> {
                            GlobalUniversityCutoff e = new GlobalUniversityCutoff();
                            e.setUniversityName(name);
                            e.setCountry("USA");
                            return e;
                        });

                Integer m25 = asInt(school, "latest.admissions.sat_scores.25th_percentile.math");
                Integer m75 = asInt(school, "latest.admissions.sat_scores.75th_percentile.math");
                Integer r25 = asInt(school, "latest.admissions.sat_scores.25th_percentile.critical_reading");
                Integer r75 = asInt(school, "latest.admissions.sat_scores.75th_percentile.critical_reading");

                if (m25 != null) entity.setSatMath25th(m25);
                if (m75 != null) entity.setSatMath75th(m75);
                if (r25 != null) entity.setSatReading25th(r25);
                if (r75 != null) entity.setSatReading75th(r75);
                if (entity.getAvgGpa() == null) entity.setAvgGpa(3.7);

                globalRepo.save(entity);
                upserts++;
            }

            lastSuccessAt = Instant.now();
            lastStatus = "OK_" + upserts;
            log.info("College Scorecard 동기화 완료: {}건 UPSERT", upserts);
        } catch (Exception e) {
            lastStatus = "ERROR_FALLBACK_KEPT";
            log.error("Scorecard 예외 — 캐시 유지. msg={}", e.getMessage());
        }
    }

    public SyncStatus status() { return new SyncStatus(lastStatus, lastSuccessAt); }

    private static Integer asInt(JsonNode root, String dotted) {
        JsonNode v = root.get(dotted);
        if (v == null || v.isNull()) return null;
        try { return v.asInt(); } catch (Exception e) { return null; }
    }

    public record SyncStatus(String lastStatus, Instant lastSuccessAt) {}
}
