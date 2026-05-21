package com.diagnostic.ingest;

import com.diagnostic.config.DataInitializer;
import com.diagnostic.entity.DomesticUniversityCutoff;
import com.diagnostic.entity.GlobalUniversityCutoff;
import com.diagnostic.entity.MockExamCutoff;
import com.diagnostic.entity.SusiCutoff;
import com.diagnostic.entity.UniversityProgram;
import com.diagnostic.repository.DomesticUniversityCutoffRepository;
import com.diagnostic.repository.GlobalUniversityCutoffRepository;
import com.diagnostic.repository.MockExamCutoffRepository;
import com.diagnostic.repository.SusiCutoffRepository;
import com.diagnostic.repository.UniversityProgramRepository;
import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StagingDataLoader {

    private final DomesticUniversityCutoffRepository domesticRepo;
    private final GlobalUniversityCutoffRepository globalRepo;
    private final MockExamCutoffRepository mockRepo;
    private final UniversityProgramRepository programRepo;
    private final SusiCutoffRepository susiRepo;

    @Value("${app.data-staging-dir}")
    private String stagingDirPath;

    @Async("dataIngestExecutor")
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadOnStartup() {
        Path dir = Paths.get(stagingDirPath);
        if (!Files.isDirectory(dir)) {
            log.info("Staging 디렉터리 없음 ({}). Fallback 시드만 사용합니다.", dir.toAbsolutePath());
            return;
        }
        log.info("Staging 디렉터리 적재 시작: {}", dir.toAbsolutePath());

        upsertDomestic(dir.resolve("kcue_domestic_cutoffs.csv"));
        upsertGlobal(dir.resolve("us_cds_stats.csv"));
        upsertMock(dir.resolve("mock_exam_cutoffs.csv"));
        loadPrograms(dir.resolve("university_programs.csv"));
        loadSusi(dir.resolve("kcue_susi_cutoffs.csv"));   // build_app_data.py 로 정제된 수시 컷

        log.info("Staging 적재 완료 — domestic={}, global={}, mock={}, programs={}, susi={}",
                domesticRepo.count(), globalRepo.count(), mockRepo.count(), programRepo.count(), susiRepo.count());
    }

    public void upsertDomestic(Path path) {
        readCsv(path, DataInitializer.DomesticCsvRow.class).forEach(row -> {
            DomesticUniversityCutoff entity = row.toEntity();
            // 기존 행이면 그 id로 덮어써 전체 필드를 한 번에 갱신(필드 추가 시 누락 위험 제거).
            domesticRepo.findByUniversityNameAndDepartmentName(entity.getUniversityName(), entity.getDepartmentName())
                    .ifPresent(existing -> entity.setId(existing.getId()));
            domesticRepo.save(entity);
        });
    }

    public void upsertGlobal(Path path) {
        readCsv(path, DataInitializer.GlobalCsvRow.class).forEach(row -> {
            GlobalUniversityCutoff entity = ((DataInitializer.GlobalCsvRow) row).toEntity();
            globalRepo.findByUniversityName(entity.getUniversityName())
                    .ifPresentOrElse(existing -> {
                        existing.setCountry(entity.getCountry());
                        existing.setSatMath25th(entity.getSatMath25th());
                        existing.setSatMath75th(entity.getSatMath75th());
                        existing.setSatReading25th(entity.getSatReading25th());
                        existing.setSatReading75th(entity.getSatReading75th());
                        existing.setAvgGpa(entity.getAvgGpa());
                        globalRepo.save(existing);
                    }, () -> globalRepo.save(entity));
        });
    }

    public void upsertMock(Path path) {
        readCsv(path, DataInitializer.MockExamCsvRow.class).forEach(row -> {
            MockExamCutoff entity = ((DataInitializer.MockExamCsvRow) row).toEntity();
            mockRepo.save(entity);
        });
    }

    public void loadPrograms(Path path) {
        if (programRepo.count() > 0) {
            log.info("programs 테이블에 이미 데이터 존재({}건) — skip", programRepo.count());
            return;
        }
        List<UniversityProgramRow> rows = readCsv(path, UniversityProgramRow.class);
        if (rows.isEmpty()) return;
        int batch = 1000;
        for (int i = 0; i < rows.size(); i += batch) {
            int end = Math.min(i + batch, rows.size());
            List<UniversityProgram> chunk = rows.subList(i, end).stream().map(UniversityProgramRow::toEntity).toList();
            programRepo.saveAll(chunk);
        }
        log.info("university_programs 적재 완료: {}건", rows.size());
    }

    public void loadSusi(Path path) {
        List<SusiRow> rows = readCsv(path, SusiRow.class);
        if (rows.isEmpty()) return;
        susiRepo.deleteAll();  // 전량 교체(반복 reload 시 중복 방지)
        List<SusiCutoff> entities = rows.stream().map(SusiRow::toEntity)
                .filter(e -> e.getUniversityName() != null && e.getDepartmentName() != null && e.getGrade70() != null)
                .toList();
        int batch = 1000;
        for (int i = 0; i < entities.size(); i += batch) {
            susiRepo.saveAll(entities.subList(i, Math.min(i + batch, entities.size())));
        }
        log.info("susi_cutoffs 적재 완료: {}건", entities.size());
    }

    public static class SusiRow {
        @CsvBindByName public String universityName;
        @CsvBindByName public String admissionType;
        @CsvBindByName public String departmentName;
        @CsvBindByName public String grade50;
        @CsvBindByName public String grade70;

        public SusiCutoff toEntity() {
            return SusiCutoff.builder()
                    .universityName(universityName).admissionType(admissionType).departmentName(departmentName)
                    .grade50(parse(grade50)).grade70(parse(grade70)).build();
        }
        private static Double parse(String s) {
            if (s == null || s.isBlank()) return null;
            try { return Double.parseDouble(s.trim()); } catch (Exception e) { return null; }
        }
    }

    public static class UniversityProgramRow {
        @CsvBindByName public String universityName;
        @CsvBindByName public String universityCode;
        @CsvBindByName public String universityType;
        @CsvBindByName public String region;
        @CsvBindByName public String collegeName;
        @CsvBindByName public String departmentCode;
        @CsvBindByName public String departmentName;
        @CsvBindByName public String fieldL1;
        @CsvBindByName public String fieldL2;
        @CsvBindByName public String fieldL3;
        @CsvBindByName public String degreeType;

        public UniversityProgram toEntity() {
            return UniversityProgram.builder()
                    .universityName(universityName).universityCode(universityCode)
                    .universityType(universityType).region(region)
                    .collegeName(collegeName)
                    .departmentCode(departmentCode).departmentName(departmentName)
                    .fieldL1(fieldL1).fieldL2(fieldL2).fieldL3(fieldL3)
                    .degreeType(degreeType)
                    .build();
        }
    }

    private <T> List<T> readCsv(Path path, Class<T> type) {
        if (!Files.exists(path)) {
            log.info("Staging 파일 없음 (skip): {}", path.getFileName());
            return List.of();
        }
        try (FileReader reader = new FileReader(path.toFile(), StandardCharsets.UTF_8)) {
            List<T> rows = new CsvToBeanBuilder<T>(reader).withType(type).build().parse();
            log.info("Staging {} → {}건", path.getFileName(), rows.size());
            return rows;
        } catch (Exception e) {
            log.error("Staging 파일 적재 실패 {}: {}", path.getFileName(), e.getMessage());
            return List.of();
        }
    }
}
