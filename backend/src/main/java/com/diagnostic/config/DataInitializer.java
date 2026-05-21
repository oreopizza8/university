package com.diagnostic.config;

import com.diagnostic.entity.DomesticUniversityCutoff;
import com.diagnostic.entity.GlobalUniversityCutoff;
import com.diagnostic.entity.MockExamCutoff;
import com.diagnostic.repository.DomesticUniversityCutoffRepository;
import com.diagnostic.repository.GlobalUniversityCutoffRepository;
import com.diagnostic.repository.MockExamCutoffRepository;
import com.opencsv.bean.CsvToBeanBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final MockExamCutoffRepository mockRepo;
    private final DomesticUniversityCutoffRepository domesticRepo;
    private final GlobalUniversityCutoffRepository globalRepo;

    @Override
    public void run(String... args) {
        loadIfEmpty(mockRepo.count(), "data/mock_exam_cutoffs.csv", MockExamCsvRow.class,
                row -> ((MockExamCsvRow) row).toEntity(), mockRepo::saveAll);
        loadIfEmpty(domesticRepo.count(), "data/domestic_universities.csv", DomesticCsvRow.class,
                row -> ((DomesticCsvRow) row).toEntity(), domesticRepo::saveAll);
        loadIfEmpty(globalRepo.count(), "data/global_universities.csv", GlobalCsvRow.class,
                row -> ((GlobalCsvRow) row).toEntity(), globalRepo::saveAll);
        log.info("Seed loaded — mock={}, domestic={}, global={}",
                mockRepo.count(), domesticRepo.count(), globalRepo.count());
    }

    private <T, E> void loadIfEmpty(long currentCount, String path, Class<T> rowType,
                                    Function<T, E> mapper, java.util.function.Consumer<List<E>> saver) {
        if (currentCount > 0) return;
        try (InputStreamReader reader = new InputStreamReader(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8)) {
            List<T> rows = new CsvToBeanBuilder<T>(reader).withType(rowType).build().parse();
            List<E> entities = rows.stream().map(mapper).toList();
            saver.accept(entities);
        } catch (Exception e) {
            log.error("Seed load failed: {}", path, e);
        }
    }

    public static class MockExamCsvRow {
        @com.opencsv.bean.CsvBindByName public Integer examYear;
        @com.opencsv.bean.CsvBindByName public Integer examMonth;
        @com.opencsv.bean.CsvBindByName public String subjectName;
        @com.opencsv.bean.CsvBindByName public Integer grade;
        @com.opencsv.bean.CsvBindByName public Integer standardScore;
        @com.opencsv.bean.CsvBindByName public Integer percentile;
        @com.opencsv.bean.CsvBindByName public Double subjectMean;
        @com.opencsv.bean.CsvBindByName public Double subjectSd;

        public MockExamCutoff toEntity() {
            return MockExamCutoff.builder()
                    .examYear(examYear).examMonth(examMonth)
                    .subjectName(subjectName).grade(grade)
                    .standardScore(standardScore).percentile(percentile)
                    .subjectMean(subjectMean).subjectSd(subjectSd)
                    .build();
        }
    }

    public static class DomesticCsvRow {
        @com.opencsv.bean.CsvBindByName public String universityName;
        @com.opencsv.bean.CsvBindByName public String departmentName;
        @com.opencsv.bean.CsvBindByName public Double ratioKo;
        @com.opencsv.bean.CsvBindByName public Double ratioMath;
        @com.opencsv.bean.CsvBindByName public Double ratioEn;
        @com.opencsv.bean.CsvBindByName public Double ratioTg;
        @com.opencsv.bean.CsvBindByName public Double cutoffPercentile70;
        // 풍부 스키마(없으면 null — 시드/구 CSV 호환)
        @com.opencsv.bean.CsvBindByName public Integer recruitCount;
        @com.opencsv.bean.CsvBindByName public Double competitionRate;
        @com.opencsv.bean.CsvBindByName public Integer additionalRank;
        @com.opencsv.bean.CsvBindByName public Double koreanCut70;
        @com.opencsv.bean.CsvBindByName public Double mathCut70;
        @com.opencsv.bean.CsvBindByName public Double tamguCut70;
        @com.opencsv.bean.CsvBindByName public String region;
        @com.opencsv.bean.CsvBindByName public String admissionGroup;
        @com.opencsv.bean.CsvBindByName public String dataSource;

        public DomesticUniversityCutoff toEntity() {
            return DomesticUniversityCutoff.builder()
                    .universityName(universityName).departmentName(departmentName)
                    .ratioKo(ratioKo).ratioMath(ratioMath).ratioEn(ratioEn).ratioTg(ratioTg)
                    .cutoffPercentile70(cutoffPercentile70)
                    .recruitCount(recruitCount).competitionRate(competitionRate).additionalRank(additionalRank)
                    .koreanCut70(koreanCut70).mathCut70(mathCut70).tamguCut70(tamguCut70)
                    .region(region).admissionGroup(admissionGroup)
                    .dataSource(dataSource)
                    .build();
        }
    }

    public static class GlobalCsvRow {
        @com.opencsv.bean.CsvBindByName public String country;
        @com.opencsv.bean.CsvBindByName public String universityName;
        @com.opencsv.bean.CsvBindByName public Integer satMath25th;
        @com.opencsv.bean.CsvBindByName public Integer satMath75th;
        @com.opencsv.bean.CsvBindByName public Integer satReading25th;
        @com.opencsv.bean.CsvBindByName public Integer satReading75th;
        @com.opencsv.bean.CsvBindByName public Double avgGpa;

        public GlobalUniversityCutoff toEntity() {
            return GlobalUniversityCutoff.builder()
                    .country(country).universityName(universityName)
                    .satMath25th(satMath25th).satMath75th(satMath75th)
                    .satReading25th(satReading25th).satReading75th(satReading75th)
                    .avgGpa(avgGpa)
                    .build();
        }
    }
}
