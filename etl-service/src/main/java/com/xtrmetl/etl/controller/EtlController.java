package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.service.EtlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/etl")
public class EtlController {

    @Autowired
    private EtlService etlService;

    @PostMapping("/process")
    public ResponseEntity<String> processData(@RequestBody String jsonInput) {
        try {
            String result = etlService.processData(jsonInput);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error processing data: " + e.getMessage());
        }
    }
}
