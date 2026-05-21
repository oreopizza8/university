package com.diagnostic.controller;

import com.diagnostic.dto.DiagnosticRequest;
import com.diagnostic.dto.DiagnosticResponse;
import com.diagnostic.dto.MyLineRequest;
import com.diagnostic.dto.MyLineResponse;
import com.diagnostic.entity.DomesticUniversityCutoff;
import com.diagnostic.entity.GlobalUniversityCutoff;
import com.diagnostic.entity.HighSchool;
import com.diagnostic.entity.SusiCutoff;
import com.diagnostic.repository.DomesticUniversityCutoffRepository;
import com.diagnostic.repository.GlobalUniversityCutoffRepository;
import com.diagnostic.repository.HighSchoolRepository;
import com.diagnostic.repository.SusiCutoffRepository;
import com.diagnostic.service.DomesticDiagnosticService;
import com.diagnostic.service.GlobalDiagnosticService;
import com.diagnostic.service.MyLineService;
import com.diagnostic.service.StatisticsService;
import com.diagnostic.service.SusiDiagnosticService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/diagnostic")
@RequiredArgsConstructor
public class DiagnosticController {

    private final DomesticDiagnosticService domesticService;
    private final GlobalDiagnosticService globalService;
    private final SusiDiagnosticService susiService;
    private final MyLineService myLineService;
    private final DomesticUniversityCutoffRepository domesticRepo;
    private final GlobalUniversityCutoffRepository globalRepo;
    private final SusiCutoffRepository susiRepo;
    private final HighSchoolRepository highSchoolRepo;
    private final StatisticsService statisticsService;

    @PostMapping
    public DiagnosticResponse diagnose(@Valid @RequestBody DiagnosticRequest req) {
        DiagnosticResponse response;
        if (Boolean.TRUE.equals(req.getIsGlobal())) {
            response = globalService.diagnose(req);
        } else if ("SUSI".equalsIgnoreCase(req.getDiagnosisType())) {
            response = susiService.diagnose(req);   // 수시 학생부교과(내신) 진단
        } else {
            response = domesticService.diagnose(req);  // 정시(기본)
        }
        statisticsService.record(response);
        return response;
    }

    /** 내 라인 찾기(Bottom-Up): 내 백분위 평균 주변대 합격선 학과 매칭. */
    @PostMapping("/my-line")
    public MyLineResponse myLine(@Valid @RequestBody MyLineRequest req) {
        return myLineService.find(req);
    }

    @GetMapping("/universities/domestic")
    public List<DomesticUniversityCutoff> searchDomestic(@RequestParam(defaultValue = "") String q) {
        // 진단 가능한 입결표(70%컷 보유)만 노출 → 선택 시 "미등록" 이 나오지 않도록.
        if (q.isBlank()) return domesticRepo.findAll(PageRequest.of(0, 20)).getContent();
        return domesticRepo.findTop20ByUniversityNameContainingOrDepartmentNameContaining(q, q);
    }

    @GetMapping("/universities/global")
    public List<GlobalUniversityCutoff> searchGlobal(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return globalRepo.findAll(PageRequest.of(0, 20)).getContent();
        return globalRepo.findTop20ByUniversityNameContainingOrCountryContaining(q, q);
    }

    @GetMapping("/universities/susi")
    public List<SusiCutoff> searchSusi(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return susiRepo.findAll(PageRequest.of(0, 20)).getContent();
        return susiRepo.findTop20ByUniversityNameContainingOrDepartmentNameContaining(q, q);
    }

    @GetMapping("/high-schools")
    public List<HighSchool> searchHighSchools(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return highSchoolRepo.findAll(PageRequest.of(0, 20)).getContent();
        return highSchoolRepo.findTop20BySchoolNameContaining(q);
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
