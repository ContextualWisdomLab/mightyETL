package com.xtrmetl.cdc.controller;

import com.xtrmetl.cdc.service.CdcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cdc")
public class CdcController {

    @Autowired
    private CdcService cdcService;

    @PostMapping("/start")
    public ResponseEntity<String> startCdc() {
        cdcService.start();
        return ResponseEntity.ok("CDC process started");
    }

    @PostMapping("/stop")
    public ResponseEntity<String> stopCdc() {
        cdcService.stop();
        return ResponseEntity.ok("CDC process stopped");
    }
}
