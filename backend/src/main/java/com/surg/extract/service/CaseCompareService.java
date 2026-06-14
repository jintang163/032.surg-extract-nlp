package com.surg.extract.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.json.JsonData;
import com.surg.extract.config.ElasticsearchConfig;
import com.surg.extract.dto.CaseStatsAnalysisDTO;
import com.surg.extract.dto.SimilarCaseResultDTO;
import com.surg.extract.dto.SimilarCaseSearchRequestDTO;
import com.surg.extract.entity.MedicalRecordHome;
import com.surg.extract.entity.SurgeryEntity;
import com.surg.extract.mapper.MedicalRecordHomeMapper;
import com.surg.extract.mapper.SurgeryEntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseCompareService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchConfig esConfig;
    private final SurgeryEntityMapper entityMapper;
    private final MedicalRecordHomeMapper homeMapper;

    private static final Map<String, String> NUMERIC_FIELD_UNITS = Map.of(
            "bloodLoss", "ml",
            "bloodTransfusion", "ml",
            "fluidInfusion", "ml",
            "urineOutput", "ml"
    );

    private static final Map<String, String> NUMERIC_FIELD_LABELS = Map.of(
            "bloodLoss", "失血量",
            "bloodTransfusion", "输血量",
            "fluidInfusion", "输液量",
            "urineOutput", "尿量"
    );

    private static final Map<String, String> CATEGORY_FIELD_LABELS = Map.of(
            "incisionLevel", "切口等级",
            "incisionHealing", "切口愈合",
            "surgeryLevel", "手术等级",
            "anesthesiaType", "麻醉方式"
    );

    public List<SimilarCaseResultDTO> searchSimilarCases(SimilarCaseSearchRequestDTO request) {
        if (!StringUtils.hasText(request.getSurgeryName())) {
            return Collections.emptyList();
        }

        int timeRange = request.getTimeRangeMonths() != null ? request.getTimeRangeMonths() : esConfig.similarityTimeRangeMonths;
        double minScore = request.getMinScore() != null ? request.getMinScore() : esConfig.similarityMinScore;
        int topN = request.getTopN() != null ? request.getTopN() : 10;

        LocalDateTime fromTime = LocalDateTime.now().minus(timeRange, ChronoUnit.MONTHS);

        List<Query> mustClauses = new ArrayList<>();
        List<Query> shouldClauses = new ArrayList<>();
        List<Query> filterClauses = new ArrayList<>();

        filterClauses.add(RangeQuery.of(r -> r
                .field("uploadTime")
                .gte(JsonData.of(fromTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
        )._toQuery());

        if (request.getExcludeRecordId() != null) {
            String excludeId = String.valueOf(request.getExcludeRecordId());
            filterClauses.add(BoolQuery.of(b -> b
                    .mustNot(IdsQuery.of(i -> i.values(Collections.singletonList(excludeId)))._toQuery())
            )._toQuery());
        }

        if (StringUtils.hasText(request.getDepartment())) {
            filterClauses.add(TermQuery.of(t -> t
                    .field("department")
                    .value(request.getDepartment())
            )._toQuery());
        }

        shouldClauses.add(MatchQuery.of(m -> m
                .field("surgeryName")
                .query(request.getSurgeryName())
                .boost(5.0f)
        )._toQuery());

        if (StringUtils.hasText(request.getPreopDiagnosis())) {
            shouldClauses.add(MatchQuery.of(m -> m
                    .field("preopDiagnosis")
                    .query(request.getPreopDiagnosis())
                    .boost(3.0f)
            )._toQuery());
        }
        if (StringUtils.hasText(request.getPostopDiagnosis())) {
            shouldClauses.add(MatchQuery.of(m -> m
                    .field("postopDiagnosis")
                    .query(request.getPostopDiagnosis())
                    .boost(3.0f)
            )._toQuery());
        }

        shouldClauses.add(MatchPhraseQuery.of(m -> m
                .field("surgeryName")
                .query(request.getSurgeryName())
                .boost(8.0f)
                .slop(2)
        )._toQuery());

        Query query = BoolQuery.of(b -> b
                .filter(filterClauses)
                .should(shouldClauses)
                .minimumShouldMatch("1")
        )._toQuery();

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withMaxResults(topN)
                .withMinScore((float) minScore)
                .build();

        try {
            SearchHits<com.surg.extract.es.document.SurgeryRecordDocument> searchHits =
                    elasticsearchOperations.search(nativeQuery, com.surg.extract.es.document.SurgeryRecordDocument.class);

            List<SimilarCaseResultDTO> results = new ArrayList<>();
            for (SearchHit<com.surg.extract.es.document.SurgeryRecordDocument> hit : searchHits) {
                com.surg.extract.es.document.SurgeryRecordDocument doc = hit.getContent();
                SimilarCaseResultDTO dto = new SimilarCaseResultDTO();
                dto.setRecordId(doc.getId());
                dto.setRecordNo(doc.getRecordNo());
                dto.setScore(hit.getScore());
                dto.setDepartment(doc.getDepartment());
                dto.setSurgeryName(doc.getSurgeryName());
                dto.setPreopDiagnosis(doc.getPreopDiagnosis());
                dto.setPostopDiagnosis(doc.getPostopDiagnosis());
                dto.setSurgeryLevel(doc.getSurgeryLevel());
                dto.setIncisionLevel(doc.getIncisionLevel());
                dto.setAnesthesiaType(doc.getAnesthesiaType());
                dto.setBloodLoss(doc.getBloodLoss());
                dto.setBloodTransfusion(doc.getBloodTransfusion());
                dto.setFluidInfusion(doc.getFluidInfusion());
                dto.setSurgeon(doc.getSurgeon());
                dto.setSurgeryDate(doc.getSurgeryDate());
                dto.setUploadTime(doc.getUploadTime());
                results.add(dto);
            }

            log.info("相似病例检索完成: surgeryName={}, 命中{}条", request.getSurgeryName(), results.size());
            return results;
        } catch (Exception e) {
            log.error("相似病例检索失败", e);
            return Collections.emptyList();
        }
    }

    public CaseStatsAnalysisDTO getStatsAnalysis(SimilarCaseSearchRequestDTO request) {
        CaseStatsAnalysisDTO result = new CaseStatsAnalysisDTO();

        int timeRange = request.getTimeRangeMonths() != null ? request.getTimeRangeMonths() : esConfig.similarityTimeRangeMonths;
        LocalDateTime fromTime = LocalDateTime.now().minus(timeRange, ChronoUnit.MONTHS);
        result.setTimeRangeDescription(String.format("过去%d个月", timeRange));

        List<Query> filterClauses = new ArrayList<>();
        List<Query> shouldClauses = new ArrayList<>();

        filterClauses.add(RangeQuery.of(r -> r
                .field("uploadTime")
                .gte(JsonData.of(fromTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
        )._toQuery());

        if (request.getExcludeRecordId() != null) {
            String excludeId = String.valueOf(request.getExcludeRecordId());
            filterClauses.add(BoolQuery.of(b -> b
                    .mustNot(IdsQuery.of(i -> i.values(Collections.singletonList(excludeId)))._toQuery())
            )._toQuery());
        }

        if (StringUtils.hasText(request.getDepartment())) {
            filterClauses.add(TermQuery.of(t -> t
                    .field("department")
                    .value(request.getDepartment())
            )._toQuery());
        }

        if (StringUtils.hasText(request.getSurgeryName())) {
            shouldClauses.add(MatchQuery.of(m -> m
                    .field("surgeryName")
                    .query(request.getSurgeryName())
                    .boost(5.0f)
            )._toQuery());
            shouldClauses.add(MatchPhraseQuery.of(m -> m
                    .field("surgeryName")
                    .query(request.getSurgeryName())
                    .slop(2)
            )._toQuery());
        }

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder().filter(filterClauses);
        if (!shouldClauses.isEmpty()) {
            boolBuilder.should(shouldClauses).minimumShouldMatch("1");
        }

        Map<String, Aggregation> aggs = new LinkedHashMap<>();

        aggs.put("total_count", ValueCountAggregation.of(v -> v.field("id"))._toAggregation());

        for (Map.Entry<String, String> field : NUMERIC_FIELD_LABELS.entrySet()) {
            String esField = camelToSnake(field.getKey());
            aggs.put(field.getKey() + "_stats", StatsAggregation.of(s -> s.field(esField))._toAggregation());
            aggs.put(field.getKey() + "_percentiles", PercentilesAggregation.of(p -> p
                    .field(esField)
                    .percents(Arrays.asList(25.0, 50.0, 75.0))
                    .keyed(true)
            )._toAggregation());
        }

        for (Map.Entry<String, String> field : CATEGORY_FIELD_LABELS.entrySet()) {
            String esField = camelToSnake(field.getKey());
            aggs.put(field.getKey() + "_terms", TermsAggregation.of(t -> t
                    .field(esField)
                    .size(10)
                    .minDocCount(1)
            )._toAggregation());
        }

        Query query = boolBuilder.build()._toQuery();
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withAggregations(aggs)
                .withMaxResults(0)
                .build();

        try {
            SearchHits<com.surg.extract.es.document.SurgeryRecordDocument> searchHits =
                    elasticsearchOperations.search(nativeQuery, com.surg.extract.es.document.SurgeryRecordDocument.class);

            var aggMap = searchHits.getAggregations().aggregations();

            long totalCases = 0L;
            var totalCountAgg = aggMap.get("total_count");
            if (totalCountAgg != null) {
                totalCases = totalCountAgg.getValueCount().value();
            }
            result.setTotalCases(totalCases);

            Map<String, CaseStatsAnalysisDTO.NumericFieldStats> numericStats = new LinkedHashMap<>();
            for (Map.Entry<String, String> field : NUMERIC_FIELD_LABELS.entrySet()) {
                CaseStatsAnalysisDTO.NumericFieldStats stats = new CaseStatsAnalysisDTO.NumericFieldStats();
                stats.setFieldLabel(field.getValue());
                stats.setUnit(NUMERIC_FIELD_UNITS.get(field.getKey()));

                var statsAgg = aggMap.get(field.getKey() + "_stats");
                if (statsAgg != null && statsAgg.stats() != null) {
                    StatsAggregate s = statsAgg.stats();
                    stats.setCount(s.count());
                    stats.setAvg(toBigDecimal(s.avg()));
                    stats.setMin(toBigDecimal(s.min()));
                    stats.setMax(toBigDecimal(s.max()));
                    double sumSq = s.sumOfSquares() != null ? s.sumOfSquares() : 0.0;
                    double mean = s.avg() != null ? s.avg() : 0.0;
                    long count = s.count();
                    if (count > 1) {
                        double variance = (sumSq / count) - (mean * mean);
                        stats.setStdDev(toBigDecimal(Math.sqrt(Math.max(0, variance))));
                    }
                }

                var pctAgg = aggMap.get(field.getKey() + "_percentiles");
                if (pctAgg != null && pctAgg.percentiles() != null) {
                    Map<String, Double> pcts = new HashMap<>();
                    for (var entry : pctAgg.percentiles().values().entrySet()) {
                        try {
                            pcts.put(entry.key(), entry.value().doubleValue());
                        } catch (Exception ignored) {
                        }
                    }
                    stats.setPercentile25(toBigDecimal(pcts.get("25.0")));
                    stats.setMedian(toBigDecimal(pcts.get("50.0")));
                    stats.setPercentile75(toBigDecimal(pcts.get("75.0")));

                    if (stats.getPercentile25() != null && stats.getPercentile75() != null) {
                        stats.setTypicalRange(String.format("%s - %s%s",
                                stats.getPercentile25().setScale(0, RoundingMode.HALF_UP).toPlainString(),
                                stats.getPercentile75().setScale(0, RoundingMode.HALF_UP).toPlainString(),
                                stats.getUnit() != null ? stats.getUnit() : ""));
                    }
                }

                numericStats.put(field.getKey(), stats);
            }
            result.setNumericStats(numericStats);

            Map<String, List<CaseStatsAnalysisDTO.CategoryBucket>> categoryStats = new LinkedHashMap<>();
            for (Map.Entry<String, String> field : CATEGORY_FIELD_LABELS.entrySet()) {
                List<CaseStatsAnalysisDTO.CategoryBucket> buckets = new ArrayList<>();
                var termsAgg = aggMap.get(field.getKey() + "_terms");
                if (termsAgg != null && termsAgg.terms() != null) {
                    long maxCount = 0;
                    List<TermsBucket> bs = termsAgg.terms().buckets().array();
                    for (TermsBucket b : bs) {
                        if (b.docCount() > maxCount) maxCount = b.docCount();
                    }
                    for (TermsBucket b : bs) {
                        CaseStatsAnalysisDTO.CategoryBucket bucket = new CaseStatsAnalysisDTO.CategoryBucket();
                        bucket.setValue(b.key().stringValue());
                        bucket.setCount(b.docCount());
                        bucket.setPercentage(totalCases > 0 ? (b.docCount() * 100.0 / totalCases) : 0.0);
                        bucket.setIsMostFrequent(b.docCount() == maxCount && maxCount > 0);
                        buckets.add(bucket);
                    }
                }
                categoryStats.put(field.getKey(), buckets);
            }
            result.setCategoryStats(categoryStats);

            result.setFieldComparisons(computeFieldComparisons(request, result));

            return result;
        } catch (Exception e) {
            log.error("统计分析失败", e);
            result.setTotalCases(0L);
            result.setNumericStats(Collections.emptyMap());
            result.setCategoryStats(Collections.emptyMap());
            result.setFieldComparisons(Collections.emptyMap());
            return result;
        }
    }

    private Map<String, CaseStatsAnalysisDTO.FieldComparison> computeFieldComparisons(
            SimilarCaseSearchRequestDTO request, CaseStatsAnalysisDTO stats) {

        Map<String, CaseStatsAnalysisDTO.FieldComparison> comparisons = new LinkedHashMap<>();

        Map<String, String> currentValues = new HashMap<>();
        if (request.getExcludeRecordId() != null) {
            List<SurgeryEntity> entities = entityMapper.selectByRecordId(request.getExcludeRecordId());
            for (SurgeryEntity entity : entities) {
                if (entity.getEntityType() == null) continue;
                switch (entity.getEntityType()) {
                    case "BLOOD_LOSS" -> currentValues.put("bloodLoss", entity.getEntityValue());
                    case "BLOOD_TRANSFUSION" -> currentValues.put("bloodTransfusion", entity.getEntityValue());
                    case "FLUID_INFUSION" -> currentValues.put("fluidInfusion", entity.getEntityValue());
                    case "URINE_OUTPUT" -> currentValues.put("urineOutput", entity.getEntityValue());
                    case "INCISION_LEVEL" -> currentValues.put("incisionLevel", entity.getEntityValue());
                    case "INCISION_HEALING" -> currentValues.put("incisionHealing", entity.getEntityValue());
                    case "SURGERY_LEVEL" -> currentValues.put("surgeryLevel", entity.getEntityValue());
                    case "ANESTHESIA_TYPE" -> currentValues.put("anesthesiaType", entity.getEntityValue());
                }
            }
            MedicalRecordHome home = homeMapper.selectByRecordId(request.getExcludeRecordId());
            if (home != null) {
                if (!currentValues.containsKey("bloodLoss") && home.getBloodLoss() != null)
                    currentValues.put("bloodLoss", String.valueOf(home.getBloodLoss()));
                if (!currentValues.containsKey("bloodTransfusion") && home.getBloodTransfusion() != null)
                    currentValues.put("bloodTransfusion", String.valueOf(home.getBloodTransfusion()));
                if (!currentValues.containsKey("fluidInfusion") && home.getFluidInfusion() != null)
                    currentValues.put("fluidInfusion", String.valueOf(home.getFluidInfusion()));
                if (!currentValues.containsKey("incisionLevel") && home.getIncisionLevel() != null)
                    currentValues.put("incisionLevel", home.getIncisionLevel());
                if (!currentValues.containsKey("incisionHealing") && home.getIncisionHealing() != null)
                    currentValues.put("incisionHealing", home.getIncisionHealing());
                if (!currentValues.containsKey("surgeryLevel") && home.getSurgeryLevel() != null)
                    currentValues.put("surgeryLevel", home.getSurgeryLevel());
                if (!currentValues.containsKey("anesthesiaType") && home.getAnesthesiaType() != null)
                    currentValues.put("anesthesiaType", home.getAnesthesiaType());
            }
        }

        for (Map.Entry<String, String> field : NUMERIC_FIELD_LABELS.entrySet()) {
            String key = field.getKey();
            CaseStatsAnalysisDTO.FieldComparison comp = new CaseStatsAnalysisDTO.FieldComparison();
            comp.setFieldType("NUMERIC");
            comp.setFieldLabel(field.getValue());
            comp.setUnit(NUMERIC_FIELD_UNITS.get(key));

            CaseStatsAnalysisDTO.NumericFieldStats ns = stats.getNumericStats().get(key);
            if (ns != null) {
                if (ns.getMedian() != null) {
                    comp.setTypicalValue(ns.getMedian().setScale(0, RoundingMode.HALF_UP).toPlainString()
                            + (comp.getUnit() != null ? comp.getUnit() : ""));
                } else if (ns.getAvg() != null) {
                    comp.setTypicalValue(ns.getAvg().setScale(0, RoundingMode.HALF_UP).toPlainString()
                            + (comp.getUnit() != null ? comp.getUnit() : ""));
                }
                comp.setTypicalRange(ns.getTypicalRange());
            }

            String cv = currentValues.get(key);
            if (cv != null) {
                comp.setCurrentValue(cv);
                BigDecimal cvNum = parseNumeric(cv);
                if (cvNum != null && ns != null && ns.getAvg() != null && ns.getAvg().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal deviation = cvNum.subtract(ns.getAvg());
                    BigDecimal devPct = deviation.divide(ns.getAvg(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    comp.setDeviationPercent(devPct.setScale(1, RoundingMode.HALF_UP));

                    int cmp = cvNum.compareTo(ns.getMedian() != null ? ns.getMedian() : ns.getAvg());
                    if (ns.getPercentile25() != null && ns.getPercentile75() != null
                            && cvNum.compareTo(ns.getPercentile25()) >= 0
                            && cvNum.compareTo(ns.getPercentile75()) <= 0) {
                        comp.setDeviationDirection("WITHIN_RANGE");
                        comp.setDeviationLevel("NORMAL");
                        comp.setTip("数值在历史典型区间内，属于正常范围");
                    } else if (cmp > 0) {
                        comp.setDeviationDirection("HIGHER");
                        double pct = Math.abs(devPct.doubleValue());
                        if (pct < 20) {
                            comp.setDeviationLevel("MILD");
                            comp.setTip("略高于历史平均值，请确认是否符合实际情况");
                        } else if (pct < 50) {
                            comp.setDeviationLevel("MODERATE");
                            comp.setTip("明显高于历史平均值，建议仔细核对");
                        } else {
                            comp.setDeviationLevel("SEVERE");
                            comp.setTip("远高于历史平均值，请重点关注是否存在异常");
                        }
                    } else {
                        comp.setDeviationDirection("LOWER");
                        double pct = Math.abs(devPct.doubleValue());
                        if (pct < 20) {
                            comp.setDeviationLevel("MILD");
                            comp.setTip("略低于历史平均值，请确认是否符合实际情况");
                        } else if (pct < 50) {
                            comp.setDeviationLevel("MODERATE");
                            comp.setTip("明显低于历史平均值，建议仔细核对");
                        } else {
                            comp.setDeviationLevel("SEVERE");
                            comp.setTip("远低于历史平均值，请重点关注是否遗漏");
                        }
                    }
                }
            }
            comparisons.put(key, comp);
        }

        for (Map.Entry<String, String> field : CATEGORY_FIELD_LABELS.entrySet()) {
            String key = field.getKey();
            CaseStatsAnalysisDTO.FieldComparison comp = new CaseStatsAnalysisDTO.FieldComparison();
            comp.setFieldType("CATEGORY");
            comp.setFieldLabel(field.getValue());

            List<CaseStatsAnalysisDTO.CategoryBucket> buckets = stats.getCategoryStats().get(key);
            if (!CollectionUtils.isEmpty(buckets)) {
                CaseStatsAnalysisDTO.CategoryBucket mostFreq = buckets.stream()
                        .filter(b -> Boolean.TRUE.equals(b.getIsMostFrequent()))
                        .findFirst().orElse(buckets.get(0));
                comp.setTypicalValue(mostFreq.getValue() + String.format(" (占比%.1f%%)", mostFreq.getPercentage()));
            }

            String cv = currentValues.get(key);
            if (cv != null) {
                comp.setCurrentValue(cv);
                if (!CollectionUtils.isEmpty(buckets)) {
                    Optional<CaseStatsAnalysisDTO.CategoryBucket> matched = buckets.stream()
                            .filter(b -> cv.equals(b.getValue()) || cv.contains(b.getValue()) || b.getValue().contains(cv))
                            .findFirst();
                    if (matched.isPresent()) {
                        CaseStatsAnalysisDTO.CategoryBucket m = matched.get();
                        comp.setDeviationDirection("WITHIN_RANGE");
                        comp.setDeviationLevel("NORMAL");
                        comp.setTip(String.format("该选择占历史病例的%.1f%%，属于常用值", m.getPercentage()));
                    } else {
                        comp.setDeviationDirection("DIFFERENT");
                        comp.setDeviationLevel("MILD");
                        comp.setTip("该选择在历史病例中较少见，请确认是否正确");
                    }
                }
            }
            comparisons.put(key, comp);
        }

        return comparisons;
    }

    private BigDecimal toBigDecimal(Double value) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) return null;
        return BigDecimal.valueOf(value);
    }

    private BigDecimal parseNumeric(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            String num = value.replaceAll("[^0-9.]", "");
            return StringUtils.hasText(num) ? new BigDecimal(num) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String camelToSnake(String camel) {
        return camel.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
