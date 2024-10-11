package com.xtrmetl.cdc.controller;

import com.xtrmetl.cdc.service.CdcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cdc")
public class CdcController {

    @Autowired
    private CdcService cdcService;

    @PostMapping("/capture")
    public ResponseEntity<String> captureChanges(@RequestBody String changes) {
        try {
            String result = cdcService.captureChanges(changes);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error capturing changes: " + e.getMessage());
        }
    }
}
