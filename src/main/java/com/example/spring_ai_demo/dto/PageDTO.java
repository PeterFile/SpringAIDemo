package com.example.spring_ai_demo.dto;

import lombok.Data;
import java.util.List;

@Data
public class PageDTO<T> {
    private Long total;
    private Integer pages;
    private List<T> list;
}