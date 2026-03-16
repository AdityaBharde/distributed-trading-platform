package com.trading.matching_engine_service.dto;

import com.trading.matching_engine_service.model.Trade;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchResponse {

    private List<Trade> trades;

    private int remainingQuantity;

}