package com.surg.extract.graph;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@Data
@RelationshipProperties
public class RelatedRelationship {

    @TargetNode
    private MedicalTermNode target;

    private String relationType;

    private double confidence;

    private String source;

    private String createdBy;

    private Long createdTime;
}
