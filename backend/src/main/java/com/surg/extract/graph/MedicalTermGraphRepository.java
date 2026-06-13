package com.surg.extract.graph;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalTermGraphRepository extends Neo4jRepository<MedicalTermNode, String> {

    Optional<MedicalTermNode> findByTermId(Long termId);

    Optional<MedicalTermNode> findByName(String name);

    List<MedicalTermNode> findByTermType(String termType);

    List<MedicalTermNode> findByStandardName(String standardName);

    @Query("MATCH (n:MedicalTerm) WHERE n.name CONTAINS $keyword OR n.pinyin CONTAINS $keyword OR n.pinyin_abbr CONTAINS $keyword RETURN n")
    List<MedicalTermNode> searchByKeyword(@Param("keyword") String keyword);

    @Query("MATCH (n:MedicalTerm)-[r:SYNONYM_OF*1..3]-(m:MedicalTerm) " +
            "WHERE n.name = $name AND n.enabled = true AND m.enabled = true " +
            "RETURN DISTINCT m, r")
    List<MedicalTermNode> findSynonymsByName(@Param("name") String name);

    @Query("MATCH path = (start:MedicalTerm {name: $startName})-[*1..3]-(end:MedicalTerm {name: $endName}) " +
            "WHERE ALL(n IN nodes(path) WHERE n.enabled = true) " +
            "RETURN path ORDER BY length(path) ASC LIMIT 5")
    List<Object> findShortestPath(@Param("startName") String startName, @Param("endName") String endName);

    @Query("MATCH (n:MedicalTerm) " +
            "WHERE n.enabled = true " +
            "AND (n.name = $text OR n.pinyin = $pinyin OR n.pinyin_abbr = $pinyinAbbr " +
            "OR n.standard_name = $text) " +
            "RETURN n LIMIT 10")
    List<MedicalTermNode> findExactMatches(@Param("text") String text,
                                           @Param("pinyin") String pinyin,
                                           @Param("pinyinAbbr") String pinyinAbbr);

    @Query("MATCH (n:MedicalTerm)-[r:SYNONYM_OF]-(m:MedicalTerm) " +
            "WHERE n.term_id = $termId AND n.enabled = true AND m.enabled = true " +
            "RETURN DISTINCT m " +
            "ORDER BY r.confidence DESC " +
            "LIMIT $limit")
    List<MedicalTermNode> findSynonymsByTermId(@Param("termId") Long termId, @Param("limit") Integer limit);

    @Query("MATCH (n:MedicalTerm) WHERE n.is_standard = true AND n.enabled = true RETURN COUNT(n)")
    long countStandardTerms();

    @Query("MATCH ()-[r:SYNONYM_OF]->() RETURN COUNT(r)")
    long countSynonymRelationships();

    @Query("MATCH (n:MedicalTerm) DETACH DELETE n")
    void deleteAllNodes();
}
