package com.apps4net.proxy.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import com.apps4net.proxy.services.ProxyService;

@RestController
public class GeneralController {
    private final ProxyService proxyService;

    @Autowired
    public GeneralController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @GetMapping(path = "/") 
    public String index() {
        return "index";
    }
    
    @PostMapping(path = "/api/forward")
    public ResponseEntity<String> forwardToClient(@RequestParam String clientName, @RequestBody String requestData) {
        try {
            String response = proxyService.forwardToClient(clientName, requestData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            if (e.getMessage().contains("Client not connected")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }
}
