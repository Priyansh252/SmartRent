package com.smartrent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Report history shown on the admin screen; links to the Spring Batch job execution. */
@Entity
@Table(name = "report_runs")
@Getter
@Setter
@NoArgsConstructor
public class ReportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 40)
    private ReportType reportType;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ReportStatus status = ReportStatus.RUNNING;

    @Column(name = "trigger_source", nullable = false, length = 160)
    private String triggerSource;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "batch_execution_id")
    private Long batchExecutionId;
}
