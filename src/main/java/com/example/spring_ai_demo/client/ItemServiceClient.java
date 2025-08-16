package com.example.spring_ai_demo.client;

import com.example.spring_ai_demo.dto.ItemDTO;
import com.example.spring_ai_demo.dto.PageDTO;
import com.example.spring_ai_demo.dto.PageQuery;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "item-service")
public interface ItemServiceClient {
    
    @GetMapping("/items/page")
    PageDTO<ItemDTO> queryItemByPage(@RequestParam("pageNo") Integer pageNo,
                                   @RequestParam("pageSize") Integer pageSize,
                                   @RequestParam(value = "sortBy", required = false) String sortBy,
                                   @RequestParam(value = "isAsc", required = false) Boolean isAsc,
                                   @RequestHeader(value = "truth", required = false) String truth);
}