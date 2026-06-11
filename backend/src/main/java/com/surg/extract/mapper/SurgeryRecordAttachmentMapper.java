package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.surg.extract.entity.SurgeryRecordAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SurgeryRecordAttachmentMapper extends BaseMapper<SurgeryRecordAttachment> {

    List<SurgeryRecordAttachment> selectByRecordId(@Param("recordId") Long recordId);

    List<SurgeryRecordAttachment> selectByRecordIdAndType(
            @Param("recordId") Long recordId,
            @Param("attachmentType") String attachmentType
    );

    Integer updateStatus(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("message") String message
    );

    Integer updateExtractedText(
            @Param("id") Long id,
            @Param("extractedText") String extractedText
    );
}
