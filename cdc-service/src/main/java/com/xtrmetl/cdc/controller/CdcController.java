package com.xtrmetl.cdc.controller;

import com.xtrmetl.cdc.service.CdcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.micrometer.observation.annotation.Observed;

import java.io.IOException;

@RestController
@RequestMapping("/api/cdc")
public class CdcController {

    @Autowired
    private CdcService cdcService;

    @PostMapping("/start")
    @Observed(name = "cdc.start", contextualName = "cdc-start")
    public ResponseEntity<String> startCdc() {
        cdcService.start();
        return ResponseEntity.ok("CDC process started");
    }

    @PostMapping("/stop")
    @Observed(name = "cdc.stop", contextualName = "cdc-stop")
    public ResponseEntity<String> stopCdc() throws IOException {
        cdcService.stop();
        return ResponseEntity.ok("CDC process stopped");
    }
}
