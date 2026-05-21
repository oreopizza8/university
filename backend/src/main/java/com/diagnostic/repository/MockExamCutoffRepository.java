package com.diagnostic.repository;

import com.diagnostic.entity.MockExamCutoff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MockExamCutoffRepository extends JpaRepository<MockExamCutoff, Long> {
    Optional<MockExamCutoff> findFirstBySubjectNameAndGradeOrderByExamYearDescExamMonthDesc(String subjectName, Integer grade);
}
