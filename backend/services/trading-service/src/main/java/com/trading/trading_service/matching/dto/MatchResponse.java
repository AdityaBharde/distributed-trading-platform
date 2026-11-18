package com.trading.trading_service.matching.dto;

import com.trading.trading_service.matching.model.Trade;
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