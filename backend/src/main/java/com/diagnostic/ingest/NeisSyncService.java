package com.diagnostic.ingest;

import com.diagnostic.entity.HighSchool;
import com.diagnostic.repository.HighSchoolRepository;
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
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NeisSyncService {

    private final HighSchoolRepository highSchoolRepo;

    @Value("${app.neis.api-key}")
    private String apiKey;

    @Value("${app.neis.api-base}")
    private String apiBase;

    private Instant lastSuccessAt;
    private String lastStatus = "NEVER_RUN";

    @Async("dataIngestExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (highSchoolRepo.count() > 0) {
            lastStatus = "SKIPPED_CACHED_" + highSchoolRepo.count();
            log.info("NEIS 부팅 동기화 skip: 이미 {}건 캐시됨. 강제 갱신은 /api/ingest/neis/run", highSchoolRepo.count());
            return;
        }
        sync();
    }

    @Scheduled(cron = "${app.neis.sync-cron}")
    public void syncDaily() { sync(); }

    public synchronized void sync() {
        if (apiKey == null || apiKey.isBlank()) {
            lastStatus = "SKIPPED_NO_KEY";
            log.info("NEIS 동기화 스킵: API 키 미설정");
            return;
        }

        try {
            // 기본 256KB 버퍼로는 pSize=1000 응답(수백 KB~MB)을 못 받아 파싱이 조용히 실패한다 → 16MB로 상향
            WebClient client = WebClient.builder()
                    .baseUrl(apiBase)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                    .build();
            int pSize = 1000;
            int pIndex = 1;
            int upserts = 0;

            while (true) {
                final int idx = pIndex;
                JsonNode root = client.get()
                        .uri(uri -> uri.path("/schoolInfo")
                                .queryParam("KEY", apiKey)
                                .queryParam("Type", "json")
                                .queryParam("pIndex", idx)
                                .queryParam("pSize", pSize)
                                .queryParam("SCHUL_KND_SC_NM", "고등학교")
                                .build())
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .timeout(Duration.ofSeconds(30))
                        .onErrorResume(e -> {
                            log.warn("NEIS 호출 실패(p={}): {}", idx, e.getMessage());
                            return Mono.empty();
                        })
                        .block();

                if (root == null) break;
                JsonNode rows = root.path("schoolInfo").isArray() && root.path("schoolInfo").size() >= 2
                        ? root.path("schoolInfo").get(1).path("row")
                        : null;
                if (rows == null || !rows.isArray() || rows.isEmpty()) break;

                List<HighSchool> batch = new ArrayList<>();
                for (JsonNode r : rows) {
                    HighSchool entity = HighSchool.builder()
                            .schoolCode(text(r, "SD_SCHUL_CODE"))
                            .schoolName(text(r, "SCHUL_NM"))
                            .regionCode(text(r, "ATPT_OFCDC_SC_CODE"))
                            .regionName(text(r, "ATPT_OFCDC_SC_NM"))
                            .foundationType(text(r, "FOND_SC_NM"))
                            .schoolKind(text(r, "SCHUL_KND_SC_NM"))
                            .highSchoolType(text(r, "HS_SC_NM"))
                            .roadAddress(text(r, "ORG_RDNMA"))
                            .build();
                    if (entity.getSchoolCode() == null || entity.getSchoolName() == null) continue;
                    highSchoolRepo.findBySchoolCode(entity.getSchoolCode())
                            .ifPresentOrElse(existing -> {
                                existing.setSchoolName(entity.getSchoolName());
                                existing.setRegionCode(entity.getRegionCode());
                                existing.setRegionName(entity.getRegionName());
                                existing.setFoundationType(entity.getFoundationType());
                                existing.setSchoolKind(entity.getSchoolKind());
                                existing.setHighSchoolType(entity.getHighSchoolType());
                                existing.setRoadAddress(entity.getRoadAddress());
                                highSchoolRepo.save(existing);
                            }, () -> batch.add(entity));
                }
                if (!batch.isEmpty()) highSchoolRepo.saveAll(batch);
                upserts += rows.size();

                if (rows.size() < pSize) break;
                pIndex++;
                if (pIndex > 20) break;
            }

            lastSuccessAt = Instant.now();
            lastStatus = "OK_" + upserts;
            log.info("NEIS 동기화 완료: {}건", upserts);
        } catch (Exception e) {
            lastStatus = "ERROR_FALLBACK_KEPT";
            log.error("NEIS 동기화 예외 — 캐시 유지. msg={}", e.getMessage());
        }
    }

    public SyncStatus status() { return new SyncStatus(lastStatus, lastSuccessAt); }

    private static String text(JsonNode r, String key) {
        JsonNode v = r.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }

    public record SyncStatus(String lastStatus, Instant lastSuccessAt) {}
}
