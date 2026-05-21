package com.diagnostic.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "diagnostic_events", indexes = {
        @Index(name = "idx_event_target", columnList = "mode,targetUniversity,targetDepartment"),
        @Index(name = "idx_event_created", columnList = "createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DiagnosticEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 16)
    private String mode;

    @Column(length = 100)
    private String targetUniversity;

    @Column(length = 100)
    private String targetDepartment;

    private Integer percentileBucket;

    @Column(length = 8)
    private String signal;

    private Instant createdAt;
}
