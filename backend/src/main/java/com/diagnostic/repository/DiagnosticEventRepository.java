package com.diagnostic.repository;

import com.diagnostic.entity.DiagnosticEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DiagnosticEventRepository extends JpaRepository<DiagnosticEvent, Long> {

    @Query("""
        SELECT e.percentileBucket AS bucket, COUNT(e) AS cnt
        FROM DiagnosticEvent e
        WHERE e.mode = :mode
          AND (:university IS NULL OR e.targetUniversity = :university)
          AND (:department IS NULL OR e.targetDepartment = :department)
        GROUP BY e.percentileBucket
        ORDER BY e.percentileBucket
        """)
    List<BucketCount> histogram(@Param("mode") String mode,
                                @Param("university") String university,
                                @Param("department") String department);

    interface BucketCount {
        Integer getBucket();
        Long getCnt();
    }
}
