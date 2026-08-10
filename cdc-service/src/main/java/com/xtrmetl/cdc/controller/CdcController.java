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
    private final XtrmetlProperties properties;
    private final CdcSourceRegistry sourceRegistry;
    private final CdcTargetRegistry targetRegistry;
    private final CdcSourceFactory sourceFactory;
    private final ReplicationSlotProbe replicationSlotProbe;

    public CdcController(
            CdcService cdcService,
            XtrmetlProperties properties,
            CdcSourceRegistry sourceRegistry,
            CdcTargetRegistry targetRegistry,
            CdcSourceFactory sourceFactory,
            ReplicationSlotProbe replicationSlotProbe
    ) {
        this.cdcService = cdcService;
        this.properties = properties;
        this.sourceRegistry = sourceRegistry;
        this.targetRegistry = targetRegistry;
        this.sourceFactory = sourceFactory;
        this.replicationSlotProbe = replicationSlotProbe;
    }

    @GetMapping("/status")
    @Observed(name = "cdc.status", contextualName = "cdc-status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>(cdcService.getStatus());
        body.put("replicaEnabled", properties.getReplica().isEnabled());
        body.put("replicaDdlEnabled", properties.getReplica().isDdlEnabled());
        body.put("replicaTopicPattern", properties.getReplica().getTopicPattern());
        body.put("replicaTables", properties.getReplica().getTables());
        body.put("replicationSlot", replicationSlotProbe.probeConfiguredSlot());
        body.put("configuredSources", sourceFactory.describeConfigured(
                properties.getCdc().getSources().stream()
                        .map(s -> new CdcSourceFactory.SourceSpec(s.getId(), s.getType(), s.isEnabled()))
                        .collect(Collectors.toList())
        ));
        body.put("registeredSources", sourceRegistry.all().stream()
                .map(this::sourceEntry)
                .collect(Collectors.toList()));
        body.put("registeredTargets", targetRegistry.all().stream()
                .map(target -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", target.id());
                    entry.put("displayName", target.displayName());
                    entry.put("scaffoldOnly", target.scaffoldOnly());
                    return entry;
                })
                .collect(Collectors.toList()));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/sources")
    @Observed(name = "cdc.sources", contextualName = "cdc-sources")
    public ResponseEntity<List<Map<String, Object>>> sources() {
        return ResponseEntity.ok(sourceRegistry.all().stream()
                .map(this::sourceEntry)
                .collect(Collectors.toList()));
    }

    @GetMapping("/targets")
    @Observed(name = "cdc.targets", contextualName = "cdc-targets")
    public ResponseEntity<List<Map<String, Object>>> targets() {
        List<Map<String, Object>> body = targetRegistry.all().stream()
                .map(target -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", target.id());
                    entry.put("displayName", target.displayName());
                    entry.put("scaffoldOnly", target.scaffoldOnly());
                    return entry;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> sourceEntry(com.xtrmetl.cdc.spi.CdcSourceConnector source) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", source.id());
        entry.put("displayName", source.displayName());
        entry.put("engine", source.capabilities().engine());
        entry.put("databases", source.capabilities().databases());
        entry.put("scaffoldOnly", source.capabilities().scaffoldOnly());
        return entry;
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
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("CDC process could not be stopped");
        }
    }
}
