package com.diagnostic.service;

import com.diagnostic.dto.DiagnosticResponse;
import com.diagnostic.entity.DiagnosticEvent;
import com.diagnostic.repository.DiagnosticEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final DiagnosticEventRepository eventRepo;

    @Async("statsExecutor")
    public void record(DiagnosticResponse response) {
        try {
            int bucket = toBucket(response.getNationalPercentile());
            DiagnosticEvent evt = DiagnosticEvent.builder()
                    .mode(response.getMode())
                    .targetUniversity(response.getTargetUniversity())
                    .targetDepartment(response.getTargetDepartment())
                    .percentileBucket(bucket)
                    .signal(response.getSignal())
                    .createdAt(Instant.now())
                    .build();
            eventRepo.save(evt);
        } catch (Exception e) {
            log.warn("익명 통계 적재 실패 (무시): {}", e.getMessage());
        }
    }

    public DistributionDto distribution(String mode, String university, String department) {
        String uni = (university == null || university.isBlank()) ? null : university;
        String dept = (department == null || department.isBlank()) ? null : department;

        List<DiagnosticEventRepository.BucketCount> raw = eventRepo.histogram(mode, uni, dept);
        Map<Integer, Long> filled = new LinkedHashMap<>();
        for (int b = 0; b <= 95; b += 5) filled.put(b, 0L);
        for (DiagnosticEventRepository.BucketCount bc : raw) {
            if (bc.getBucket() == null) continue;
            filled.merge(bc.getBucket(), bc.getCnt(), Long::sum);
        }

        long total = filled.values().stream().mapToLong(Long::longValue).sum();
        List<BinDto> bins = new ArrayList<>();
        filled.forEach((b, c) -> bins.add(new BinDto(b, b + 5, c)));
        return new DistributionDto(mode, uni, dept, total, bins);
    }

    private int toBucket(Double percentile) {
        if (percentile == null) return 0;
        int v = (int) Math.floor(percentile / 5.0) * 5;
        return Math.max(0, Math.min(95, v));
    }

    public record BinDto(int from, int to, long count) {}
    public record DistributionDto(String mode, String university, String department, long total, List<BinDto> bins) {}
}
