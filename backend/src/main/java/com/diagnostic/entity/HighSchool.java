package com.diagnostic.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "high_schools", indexes = {
        @Index(name = "idx_hs_name", columnList = "schoolName"),
        @Index(name = "idx_hs_region", columnList = "regionCode")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HighSchool {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, unique = true)
    private String schoolCode;

    @Column(length = 100)
    private String schoolName;

    @Column(length = 10)
    private String regionCode;

    @Column(length = 30)
    private String regionName;

    @Column(length = 30)
    private String foundationType;

    @Column(length = 30)
    private String schoolKind;

    /** NEIS HS_SC_NM — 고등학교구분명(일반고/특수목적고/특성화고/자율고). 수시 내신환경 보정의 핵심 축. */
    @Column(length = 30)
    private String highSchoolType;

    @Column(length = 200)
    private String roadAddress;
}
