package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.connector.TargetConnectorDispatcher;
import com.xtrmetl.etl.service.EtlService;
import io.micrometer.observation.annotation.Observed;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/etl")
public class EtlController {

    private final EtlService etlService;
    private final TargetConnectorDispatcher connectorDispatcher;

    public EtlController(EtlService etlService, TargetConnectorDispatcher connectorDispatcher) {
        this.etlService = etlService;
        this.connectorDispatcher = connectorDispatcher;
    }

    @PostMapping("/process")
    @Observed(name = "etl.process", contextualName = "etl-processing")
    public ResponseEntity<String> processData(@RequestBody String jsonInput) {
        try {
            String result = etlService.processData(jsonInput);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error processing data: " + e.getMessage());
        }
    }

    /**
     * Catalog of target connector scaffolds (Databricks / Snowflake / Qlik) and enable flags.
     * Primary load path remains PostgreSQL via {@code /process}.
     */
    @GetMapping("/connectors")
    @Observed(name = "etl.connectors", contextualName = "etl-connectors")
    public ResponseEntity<Map<String, Object>> connectors() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("product", "mightyETL");
        body.put("primaryLoadPath", "postgresql");
        body.put("connectors", connectorDispatcher.catalog());
        body.put("docs", "docs/connectors/");
        return ResponseEntity.ok(body);
    }
}
