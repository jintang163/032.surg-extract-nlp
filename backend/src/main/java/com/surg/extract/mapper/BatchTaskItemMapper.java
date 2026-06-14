package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.surg.extract.dto.BatchTaskItemDTO;
import com.surg.extract.entity.BatchTaskItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BatchTaskItemMapper extends BaseMapper<BatchTaskItem> {

    IPage<BatchTaskItemDTO> selectItemPage(
            Page<BatchTaskItemDTO> page,
            @Param("taskId") Long taskId,
            @Param("status") String status
    );

    List<BatchTaskItem> selectPendingItems(@Param("taskId") Long taskId, @Param("limit") Integer limit);

    List<BatchTaskItem> selectFailedItems(@Param("taskId") Long taskId);

    List<Long> selectSuccessRecordIds(@Param("taskId") Long taskId);
}
