package com.trading.trading_service.matching.controller;



import com.trading.trading_service.matching.dto.MatchRequest;
import com.trading.trading_service.matching.dto.MatchResponse;
import com.trading.trading_service.matching.service.MatchingService;
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

