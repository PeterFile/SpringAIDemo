package com.example.spring_ai_demo.controller;


import com.example.spring_ai_demo.service.MilvusRAGService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static java.lang.Thread.sleep;

@RestController
@RequestMapping("/api/rag")
public class MilvusRAGController {
    private final MilvusRAGService ragService;

    public MilvusRAGController(MilvusRAGService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/query")
    public ResponseEntity<String> query(@RequestParam String question) throws InterruptedException {
        ragService.ingest();
        sleep(1000);
        String response = ragService.directRag(question);
        return ResponseEntity.ok().body(response);
    }
}
