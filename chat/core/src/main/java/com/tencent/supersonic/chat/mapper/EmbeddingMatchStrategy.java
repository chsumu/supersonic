package com.tencent.supersonic.chat.mapper;

import com.tencent.supersonic.chat.api.pojo.request.QueryReq;
import com.tencent.supersonic.chat.config.OptimizationConfig;
import com.tencent.supersonic.common.pojo.Constants;
import com.tencent.supersonic.common.util.embedding.EmbeddingUtils;
import com.tencent.supersonic.common.util.embedding.Retrieval;
import com.tencent.supersonic.common.util.embedding.RetrieveQuery;
import com.tencent.supersonic.common.util.embedding.RetrieveQueryResult;
import com.tencent.supersonic.knowledge.dictionary.EmbeddingResult;
import com.tencent.supersonic.semantic.model.domain.listener.MetaEmbeddingListener;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * match strategy implement
 */
@Service
@Slf4j
public class EmbeddingMatchStrategy extends BaseMatchStrategy<EmbeddingResult> {

    @Autowired
    private OptimizationConfig optimizationConfig;
    @Autowired
    private EmbeddingUtils embeddingUtils;

    @Override
    public boolean needDelete(EmbeddingResult oneRoundResult, EmbeddingResult existResult) {
        return getMapKey(oneRoundResult).equals(getMapKey(existResult))
                && existResult.getDistance() > oneRoundResult.getDistance();
    }

    @Override
    public String getMapKey(EmbeddingResult a) {
        return a.getName() + Constants.UNDERLINE + a.getId();
    }

    public void detectByStep(QueryReq queryReq, Set<EmbeddingResult> existResults, Set<Long> detectModelIds,
            Integer startIndex, Integer index, int offset) {
        String detectSegment = queryReq.getQueryText().substring(startIndex, index);
        // step1. build query params
        if (StringUtils.isBlank(detectSegment)
                || detectSegment.length() <= optimizationConfig.getEmbeddingMapperWordMin()) {
            return;
        }
        int embeddingNumber = optimizationConfig.getEmbeddingMapperNumber();
        Double distance = optimizationConfig.getEmbeddingMapperDistanceThreshold();
        Map<String, String> filterCondition = null;

        // if only one modelId, add to filterCondition
        if (CollectionUtils.isNotEmpty(detectModelIds) && detectModelIds.size() == 1) {
            filterCondition = new HashMap<>();
            filterCondition.put("modelId", detectModelIds.stream().findFirst().get().toString());
        }

        RetrieveQuery retrieveQuery = RetrieveQuery.builder()
                .queryTextsList(Collections.singletonList(detectSegment))
                .filterCondition(filterCondition)
                .queryEmbeddings(null)
                .build();
        // step2. retrieveQuery by detectSegment
        List<RetrieveQueryResult> retrieveQueryResults = embeddingUtils.retrieveQuery(
                MetaEmbeddingListener.COLLECTION_NAME, retrieveQuery, embeddingNumber);

        if (CollectionUtils.isEmpty(retrieveQueryResults)) {
            return;
        }
        // step3. build EmbeddingResults. filter by modelId
        List<EmbeddingResult> collect = retrieveQueryResults.stream()
                .map(retrieveQueryResult -> {
                    List<Retrieval> retrievals = retrieveQueryResult.getRetrieval();
                    if (CollectionUtils.isNotEmpty(retrievals)) {
                        retrievals.removeIf(retrieval -> retrieval.getDistance() > distance.doubleValue());
                        if (CollectionUtils.isNotEmpty(detectModelIds)) {
                            retrievals.removeIf(retrieval -> {
                                String modelIdStr = retrieval.getMetadata().get("modelId");
                                if (StringUtils.isBlank(modelIdStr)) {
                                    return true;
                                }
                                return detectModelIds.contains(Long.parseLong(modelIdStr));
                            });
                        }
                    }
                    return retrieveQueryResult;
                })
                .filter(retrieveQueryResult -> CollectionUtils.isNotEmpty(retrieveQueryResult.getRetrieval()))
                .flatMap(retrieveQueryResult -> retrieveQueryResult.getRetrieval().stream()
                        .map(retrieval -> {
                            EmbeddingResult embeddingResult = new EmbeddingResult();
                            BeanUtils.copyProperties(retrieval, embeddingResult);
                            embeddingResult.setDetectWord(detectSegment);
                            embeddingResult.setName(retrieval.getQuery());
                            return embeddingResult;
                        }))
                .collect(Collectors.toList());

        // step4. select mapResul in one round
        int roundNumber = optimizationConfig.getEmbeddingMapperRoundNumber();
        List<EmbeddingResult> oneRoundResults = collect.stream()
                .sorted(Comparator.comparingDouble(EmbeddingResult::getDistance))
                .limit(roundNumber)
                .collect(Collectors.toList());
        selectResultInOneRound(existResults, oneRoundResults);
    }
}