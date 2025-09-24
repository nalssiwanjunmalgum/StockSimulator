package com.portfolio2025.first.refactor.phase_A.shared;

import java.util.List;

public interface CandidateMatchRepository {
    List<Long> lockSellCandidate(Long stockId, long buyPrice, int limit);
    List<Long> lockSellCandidate_NoSkip(Long stockId, long buyPrice, int limit);
    List<Long> lockBuyCandidate(Long stockId, long buyPrice, int limit);
    int rescueStaleProcessingSecondsUsingUpdatedAt(int thresholds);
}
