package com.xtrmetl.cdc.controller;

import com.xtrmetl.cdc.config.XtrmetlProperties;
import com.xtrmetl.cdc.service.CdcService;
import com.xtrmetl.cdc.service.ReplicationSlotProbe;
import com.xtrmetl.cdc.spi.CdcSourceFactory;
import com.xtrmetl.cdc.spi.CdcSourceRegistry;
import com.xtrmetl.cdc.spi.CdcTargetRegistry;
import io.micrometer.observation.annotation.Observed;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cdc")
public class CdcController {

    private final CdcService cdcService;
    private final XtrmetlProperties xtrmetlProperties;
    private final CdcSourceRegistry sourceRegistry;
    private final CdcTargetRegistry targetRegistry;
    private final CdcSourceFactory sourceFactory;
    private final ReplicationSlotProbe replicationSlotProbe;

    public CdcController(
            CdcService cdcService,
            XtrmetlProperties xtrmetlProperties,
            CdcSourceRegistry sourceRegistry,
            CdcTargetRegistry targetRegistry,
            CdcSourceFactory sourceFactory,
            ReplicationSlotProbe replicationSlotProbe
    ) {
        this.cdcService = cdcService;
        this.xtrmetlProperties = xtrmetlProperties;
        this.sourceRegistry = sourceRegistry;
        this.targetRegistry = targetRegistry;
        this.sourceFactory = sourceFactory;
        this.replicationSlotProbe = replicationSlotProbe;
    }

    @GetMapping("/status")
    @Observed(name = "cdc.status", contextualName = "cdc-status")
    public ResponseEntity<Map<String, Object>> cdcStatus() {
        Map<String, Object> statusBody = new LinkedHashMap<>(cdcService.getStatus());
        statusBody.put("replicaEnabled", xtrmetlProperties.getReplica().isEnabled());
        statusBody.put("replicaDdlEnabled", xtrmetlProperties.getReplica().isDdlEnabled());
        statusBody.put("replicaTopicPattern", xtrmetlProperties.getReplica().getTopicPattern());
        statusBody.put("replicaTables", xtrmetlProperties.getReplica().getTables());
        statusBody.put("replicationSlot", replicationSlotProbe.probeConfiguredSlot());
        statusBody.put("configuredSources", sourceFactory.describeConfigured(
                xtrmetlProperties.getCdc().getSources().stream()
                        .map(sourceConfiguration -> new CdcSourceFactory.SourceSpec(
                                sourceConfiguration.getSourceId(),
                                sourceConfiguration.getSourceType(),
                                sourceConfiguration.isEnabled()
                        ))
                        .collect(Collectors.toList())
        ));
        statusBody.put("registeredSources", sourceRegistry.all().stream()
                .map(this::sourceRegistryEntry)
                .collect(Collectors.toList()));
        statusBody.put("registeredTargets", targetRegistry.all().stream()
                .map(targetConnector -> {
                    Map<String, Object> targetEntry = new LinkedHashMap<>();
                    targetEntry.put("id", targetConnector.id());
                    targetEntry.put("displayName", targetConnector.displayName());
                    targetEntry.put("scaffoldOnly", targetConnector.scaffoldOnly());
                    return targetEntry;
                })
                .collect(Collectors.toList()));
        return ResponseEntity.ok(statusBody);
    }

    @GetMapping("/sources")
    @Observed(name = "cdc.sources", contextualName = "cdc-sources")
    public ResponseEntity<List<Map<String, Object>>> cdcSources() {
        return ResponseEntity.ok(sourceRegistry.all().stream()
                .map(this::sourceRegistryEntry)
                .collect(Collectors.toList()));
    }

    @GetMapping("/targets")
    @Observed(name = "cdc.targets", contextualName = "cdc-targets")
    public ResponseEntity<List<Map<String, Object>>> cdcTargets() {
        List<Map<String, Object>> targetEntries = targetRegistry.all().stream()
                .map(targetConnector -> {
                    Map<String, Object> targetEntry = new LinkedHashMap<>();
                    targetEntry.put("id", targetConnector.id());
                    targetEntry.put("displayName", targetConnector.displayName());
                    targetEntry.put("scaffoldOnly", targetConnector.scaffoldOnly());
                    return targetEntry;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(targetEntries);
    }

    private Map<String, Object> sourceRegistryEntry(
            com.xtrmetl.cdc.spi.CdcSourceConnector sourceConnector
    ) {
        Map<String, Object> sourceEntry = new LinkedHashMap<>();
        sourceEntry.put("id", sourceConnector.id());
        sourceEntry.put("displayName", sourceConnector.displayName());
        sourceEntry.put("engine", sourceConnector.capabilities().engine());
        sourceEntry.put("databases", sourceConnector.capabilities().databases());
        sourceEntry.put("scaffoldOnly", sourceConnector.capabilities().scaffoldOnly());
        return sourceEntry;
    }

    @PostMapping("/start")
    @Observed(name = "cdc.start", contextualName = "cdc-start")
    public ResponseEntity<String> startCdc() {
        cdcService.start();
        return ResponseEntity.ok("CDC process started");
    }

    @PostMapping("/stop")
    @Observed(name = "cdc.stop", contextualName = "cdc-stop")
    public ResponseEntity<String> stopCdc() {
        try {
            cdcService.stop();
            return ResponseEntity.ok("CDC process stopped");
        } catch (IOException stopFailure) {
            return ResponseEntity.internalServerError().body(
                    "Error stopping CDC process: " + stopFailure.getMessage()
            );
        }
    }
}
