package com.trading.matching_engine_service.controller;



import com.trading.matching_engine_service.dto.MatchRequest;
import com.trading.matching_engine_service.dto.MatchResponse;
import com.trading.matching_engine_service.service.MatchingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/match")
public class MatchingController {
    private final MatchingService matchingService;
    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping
    public MatchResponse matchOrder(@RequestBody MatchRequest matchRequest){
        return matchingService.processOrder(matchRequest);
    }
}

