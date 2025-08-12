package com.example.spring_ai_demo.controller;

import com.example.spring_ai_demo.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer")
public class CustomerServiceController {

    private final CustomerService customerService;

    public CustomerServiceController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/chat")
    public ResponseEntity<String> chat(@RequestParam("sessionId") String sessionId,
                                       @RequestParam("message") String message) {
        String reply = customerService.chat(sessionId, message);
        return ResponseEntity.ok(reply);
    }
}