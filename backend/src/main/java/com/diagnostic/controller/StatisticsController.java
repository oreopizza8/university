package com.diagnostic.controller;

import com.diagnostic.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/distribution")
    public StatisticsService.DistributionDto distribution(
            @RequestParam(defaultValue = "DOMESTIC") String mode,
            @RequestParam(required = false) String university,
            @RequestParam(required = false) String department) {
        return statisticsService.distribution(mode, university, department);
    }
}
