package com.surg.extract.es.repository;

import com.surg.extract.es.document.SurgeryRecordDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SurgeryRecordEsRepository extends ElasticsearchRepository<SurgeryRecordDocument, Long> {
}
