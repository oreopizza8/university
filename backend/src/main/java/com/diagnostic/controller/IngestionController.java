package com.diagnostic.controller;

import com.diagnostic.ingest.KcueSyncService;
import com.diagnostic.ingest.NeisSyncService;
import com.diagnostic.ingest.StagingDataLoader;
import com.diagnostic.ingest.UsaScorecardSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final KcueSyncService kcueSyncService;
    private final NeisSyncService neisSyncService;
    private final UsaScorecardSyncService usaScorecardSyncService;
    private final StagingDataLoader stagingDataLoader;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "kcue", kcueSyncService.status(),
                "neis", neisSyncService.status(),
                "usaScorecard", usaScorecardSyncService.status()
        );
    }

    @PostMapping("/kcue/run")
    public KcueSyncService.SyncStatus runKcue() {
        kcueSyncService.sync();
        return kcueSyncService.status();
    }

    @PostMapping("/neis/run")
    public NeisSyncService.SyncStatus runNeis() {
        neisSyncService.sync();
        return neisSyncService.status();
    }

    @PostMapping("/scorecard/run")
    public UsaScorecardSyncService.SyncStatus runScorecard() {
        usaScorecardSyncService.sync();
        return usaScorecardSyncService.status();
    }

    @PostMapping("/staging/reload")
    public Map<String, Object> reloadStaging() {
        stagingDataLoader.loadOnStartup();
        return Map.of("status", "TRIGGERED");
    }
}
