package com.tencent.supersonic.headless.chat.parser.llm;

import com.google.common.collect.Lists;
import com.tencent.supersonic.common.pojo.Text2SQLExemplar;
import com.tencent.supersonic.common.service.ExemplarService;
import com.tencent.supersonic.headless.chat.parser.ParserConfig;
import com.tencent.supersonic.headless.chat.query.llm.s2sql.LLMReq;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.tencent.supersonic.headless.chat.parser.ParserConfig.PARSER_EXEMPLAR_RECALL_NUMBER;
import static com.tencent.supersonic.headless.chat.parser.ParserConfig.PARSER_FEW_SHOT_NUMBER;
import static com.tencent.supersonic.headless.chat.parser.ParserConfig.PARSER_SELF_CONSISTENCY_NUMBER;

@Component
@Slf4j
public class PromptHelper {

    @Autowired
    private ParserConfig parserConfig;

    @Autowired
    private ExemplarService exemplarService;

    public List<List<Text2SQLExemplar>> getFewShotExemplars(LLMReq llmReq) {
        int exemplarRecallNumber = Integer.valueOf(parserConfig.getParameterValue(PARSER_EXEMPLAR_RECALL_NUMBER));
        int fewShotNumber = Integer.valueOf(parserConfig.getParameterValue(PARSER_FEW_SHOT_NUMBER));
        int selfConsistencyNumber = Integer.valueOf(parserConfig.getParameterValue(PARSER_SELF_CONSISTENCY_NUMBER));

        List<Text2SQLExemplar> exemplars = Lists.newArrayList();
        llmReq.getDynamicExemplars().stream().forEach(e -> {
            exemplars.add(e);
        });

        int recallSize = exemplarRecallNumber - llmReq.getDynamicExemplars().size();
        if (recallSize > 0) {
            exemplars.addAll(exemplarService.recallExemplars(llmReq.getQueryText(), recallSize));
        }

        List<List<Text2SQLExemplar>> results = new ArrayList<>();
        // use random collection of exemplars for each self-consistency inference
        for (int i = 0; i < selfConsistencyNumber; i++) {
            List<Text2SQLExemplar> shuffledList = exemplars.stream()
                    .sorted(Comparator.comparingDouble(Text2SQLExemplar::getScore).reversed())
                    .limit(fewShotNumber)
                    .collect(Collectors.toList());
            Collections.shuffle(shuffledList);
            results.add(shuffledList);
        }

        return results;
    }

    public String buildSideInformation(LLMReq llmReq) {
        List<LLMReq.ElementValue> linkedValues = llmReq.getLinking();
        String currentDate = llmReq.getCurrentDate();
        String priorExts = llmReq.getPriorExts();

        List<String> priorLinkingList = new ArrayList<>();
        for (LLMReq.ElementValue value : linkedValues) {
            String fieldName = value.getFieldName();
            String fieldValue = value.getFieldValue();
            priorLinkingList.add("‘" + fieldValue + "‘是一个‘" + fieldName + "‘");
        }
        String currentDataStr = "当前的日期是" + currentDate;
        String linkingListStr = String.join("，", priorLinkingList);
        String termStr = buildTermStr(llmReq);
        return String.format("%s;%s;%s;%s", linkingListStr, currentDataStr, termStr, priorExts);
    }

    public String buildSchemaStr(LLMReq llmReq) {
        String tableStr = llmReq.getSchema().getDataSetName();
        StringBuilder metricStr = new StringBuilder();
        StringBuilder dimensionStr = new StringBuilder();

        llmReq.getSchema().getMetrics().stream().forEach(
                metric -> {
                    metricStr.append("<");
                    metricStr.append(metric.getName());
                    if (!CollectionUtils.isEmpty(metric.getAlias())) {
                        StringBuilder alias = new StringBuilder();
                        metric.getAlias().stream().forEach(a -> alias.append(a + ","));
                        metricStr.append(" ALIAS '" + alias + "'");
                    }
                    if (StringUtils.isNotEmpty(metric.getDescription())) {
                        metricStr.append(" COMMENT '" + metric.getDescription() + "'");
                    }
                    if (StringUtils.isNotEmpty(metric.getDefaultAgg())) {
                        metricStr.append(" AGGREGATE '" + metric.getDefaultAgg().toUpperCase() + "'");
                    }
                    metricStr.append(">,");
                }
        );

        llmReq.getSchema().getDimensions().stream().forEach(
                dimension -> {
                    dimensionStr.append("<");
                    dimensionStr.append(dimension.getName());
                    if (!CollectionUtils.isEmpty(dimension.getAlias())) {
                        StringBuilder alias = new StringBuilder();
                        dimension.getAlias().stream().forEach(a -> alias.append(a + ","));
                        metricStr.append(" ALIAS '" + alias + "'");
                    }
                    if (StringUtils.isNotEmpty(dimension.getDescription())) {
                        dimensionStr.append(" COMMENT '" + dimension.getDescription() + "'");
                    }
                    dimensionStr.append(">,");
                }
        );

        String template = "Table: %s, Metrics: [%s], Dimensions: [%s]";


        return String.format(template, tableStr, metricStr, dimensionStr);
    }

    private String buildTermStr(LLMReq llmReq) {
        List<LLMReq.Term> terms = llmReq.getSchema().getTerms();
        StringBuilder termsDesc = new StringBuilder();
        if (!CollectionUtils.isEmpty(terms)) {
            termsDesc.append("相关业务术语：");
            for (int idx = 0; idx < terms.size(); idx++) {
                LLMReq.Term term = terms.get(idx);
                String name = term.getName();
                String description = term.getDescription();
                List<String> alias = term.getAlias();
                String descPart = StringUtils.isBlank(description) ? "" : String.format("，它的涵义是<%s>", description);
                String aliasPart = CollectionUtils.isEmpty(alias) ? "" : String.format("，类似表达还有<%s>", alias);
                termsDesc.append(String.format("%d.<%s>是业务术语%s%s；", idx + 1, name, descPart, aliasPart));
            }
            if (termsDesc.length() > 0) {
                termsDesc.setLength(termsDesc.length() - 1);
            }
        }

        return termsDesc.toString();
    }

}
