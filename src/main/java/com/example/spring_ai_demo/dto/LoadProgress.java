package com.example.spring_ai_demo.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoadProgress {
    private String taskId;
    private Integer currentPage;
    private Integer totalPages;
    private Integer processedItems;
    private Integer totalItems;
    private String status; // RUNNING, COMPLETED, FAILED, PAUSED
    private LocalDateTime startTime;
    private LocalDateTime lastUpdateTime;
    private String errorMessage;
    private Integer batchSize;
    
    public LoadProgress() {
        this.startTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
        this.status = "RUNNING";
        this.currentPage = 1;
        this.processedItems = 0;
    }
}