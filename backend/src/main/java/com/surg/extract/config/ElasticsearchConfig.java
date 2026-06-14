package com.surg.extract.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.surg.extract.es")
public class ElasticsearchConfig {

    @Value("${elasticsearch.index.surgery-record:surgery_records}")
    public String surgeryRecordIndex;

    @Value("${elasticsearch.similarity.min-score:0.5}")
    public double similarityMinScore;

    @Value("${elasticsearch.similarity.time-range-months:6}")
    public int similarityTimeRangeMonths;
}
