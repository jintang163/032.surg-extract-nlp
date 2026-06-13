package com.surg.extract.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Data
@Node("MedicalTerm")
public class MedicalTermNode {

    @Id
    private String id;

    @Property("term_id")
    private Long termId;

    @Property("term_code")
    private String termCode;

    @Property("name")
    private String name;

    @Property("standard_name")
    private String standardName;

    @Property("term_type")
    private String termType;

    @Property("pinyin")
    private String pinyin;

    @Property("pinyin_abbr")
    private String pinyinAbbr;

    @Property("icd_code")
    private String icdCode;

    @Property("icd_name")
    private String icdName;

    @Property("is_standard")
    private Boolean isStandard;

    @Property("enabled")
    private Boolean enabled;

    @Relationship(type = "SYNONYM_OF", direction = Relationship.Direction.OUTGOING)
    private Set<SynonymRelationship> synonyms = new HashSet<>();

    @Relationship(type = "ABBREVIATION_OF", direction = Relationship.Direction.OUTGOING)
    private Set<AbbreviationRelationship> abbreviations = new HashSet<>();

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    private Set<RelatedRelationship> relatedTerms = new HashSet<>();

    public void addSynonym(MedicalTermNode target, double confidence, String source) {
        SynonymRelationship rel = new SynonymRelationship();
        rel.setTarget(target);
        rel.setConfidence(confidence);
        rel.setSource(source);
        this.synonyms.add(rel);
    }

    public void addAbbreviation(MedicalTermNode target, double confidence, String source) {
        AbbreviationRelationship rel = new AbbreviationRelationship();
        rel.setTarget(target);
        rel.setConfidence(confidence);
        rel.setSource(source);
        this.abbreviations.add(rel);
    }

    public void addRelatedTerm(MedicalTermNode target, String relationType, double confidence) {
        RelatedRelationship rel = new RelatedRelationship();
        rel.setTarget(target);
        rel.setRelationType(relationType);
        rel.setConfidence(confidence);
        this.relatedTerms.add(rel);
    }
}
