package com.diagnostic.repository;

import com.diagnostic.entity.SusiCutoff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SusiCutoffRepository extends JpaRepository<SusiCutoff, Long> {
    List<SusiCutoff> findByUniversityNameAndDepartmentName(String universityName, String departmentName);
    List<SusiCutoff> findTop20ByUniversityNameContainingOrDepartmentNameContaining(String u, String d);
}
