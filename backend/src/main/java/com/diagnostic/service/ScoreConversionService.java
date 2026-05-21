package com.diagnostic.service;

import org.springframework.stereotype.Service;

@Service
public class ScoreConversionService {

    public double koreanGradeToGpa(int grade) {
        int g = clamp(grade, 1, 9);
        double gpa = 4.0 - 0.3 * (g - 1);
        return Math.max(0.0, Math.round(gpa * 100.0) / 100.0);
    }

    public int percentileSumToSat(double koPercentile, double mathPercentile) {
        double sum = clampPercentile(koPercentile) + clampPercentile(mathPercentile);
        double sat = 400.0 + (sum / 200.0) * 1200.0;
        return (int) Math.round(sat / 10.0) * 10;
    }

    public double zScore(double rawScore, double mean, double sd) {
        if (sd <= 0) return 0.0;
        return (rawScore - mean) / sd;
    }

    public double zToPercentile(double z) {
        double pct = 100.0 * cumulativeNormal(z);
        return Math.round(pct * 100.0) / 100.0;
    }

    public double gradeToTopPercentile(int grade) {
        return switch (clamp(grade, 1, 9)) {
            case 1 -> 96.0;
            case 2 -> 89.0;
            case 3 -> 77.0;
            case 4 -> 60.0;
            case 5 -> 40.0;
            case 6 -> 23.0;
            case 7 -> 11.0;
            case 8 -> 4.0;
            default -> 1.0;
        };
    }

    private double cumulativeNormal(double z) {
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = 0.3989422804 * Math.exp(-z * z / 2.0);
        double prob = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        return z > 0 ? 1.0 - prob : prob;
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private double clampPercentile(double p) {
        return Math.max(0.0, Math.min(100.0, p));
    }
}
