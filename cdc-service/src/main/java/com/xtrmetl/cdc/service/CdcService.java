package com.xtrmetl.cdc.service;

import org.springframework.stereotype.Service;

@Service
public class CdcService {

    public String captureChanges(String changes) {
        // Simplified CDC process
        System.out.println("Captured changes: " + changes);
        return "Changes captured successfully";
    }
}
