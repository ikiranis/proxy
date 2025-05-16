package com.apps4net.proxy.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("web")
public class GeneralController {

    @GetMapping(path = "/") 
    public String index() {
        return "index";
    }
    
}
