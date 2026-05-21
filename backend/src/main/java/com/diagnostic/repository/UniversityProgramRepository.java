package com.diagnostic.repository;

import com.diagnostic.entity.UniversityProgram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UniversityProgramRepository extends JpaRepository<UniversityProgram, Long> {
    List<UniversityProgram> findTop20ByUniversityNameContainingOrDepartmentNameContaining(String u, String d);
}
