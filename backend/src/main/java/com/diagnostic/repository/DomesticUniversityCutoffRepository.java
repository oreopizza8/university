package com.diagnostic.repository;

import com.diagnostic.entity.DomesticUniversityCutoff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DomesticUniversityCutoffRepository extends JpaRepository<DomesticUniversityCutoff, Long> {
    Optional<DomesticUniversityCutoff> findByUniversityNameAndDepartmentName(String universityName, String departmentName);
    List<DomesticUniversityCutoff> findTop20ByUniversityNameContainingOrDepartmentNameContaining(String u, String d);

    // 지원 포트폴리오 추천용: 내 가중 백분위 기준 안정(컷이 내 점수 이하) / 소신(컷이 내 점수 초과) 라인
    List<DomesticUniversityCutoff> findTop8ByCutoffPercentile70LessThanEqualOrderByCutoffPercentile70Desc(Double p);
    List<DomesticUniversityCutoff> findTop8ByCutoffPercentile70GreaterThanOrderByCutoffPercentile70Asc(Double p);

    // 내 라인 찾기(Bottom-Up): 70%컷이 [low, high] 범위인 전국 학과를 컷 내림차순으로. idx_uni_dept 외 percentile 레인지 스캔.
    List<DomesticUniversityCutoff> findByCutoffPercentile70BetweenOrderByCutoffPercentile70Desc(Double low, Double high);
}
