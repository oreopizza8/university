package com.diagnostic.repository;

import com.diagnostic.entity.GlobalUniversityCutoff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GlobalUniversityCutoffRepository extends JpaRepository<GlobalUniversityCutoff, Long> {
    Optional<GlobalUniversityCutoff> findByUniversityName(String universityName);
    List<GlobalUniversityCutoff> findTop20ByUniversityNameContainingOrCountryContaining(String u, String c);
}
