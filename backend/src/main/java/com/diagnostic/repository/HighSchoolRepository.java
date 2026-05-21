package com.diagnostic.repository;

import com.diagnostic.entity.HighSchool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HighSchoolRepository extends JpaRepository<HighSchool, Long> {
    Optional<HighSchool> findBySchoolCode(String schoolCode);
    List<HighSchool> findTop20BySchoolNameContaining(String q);
}
