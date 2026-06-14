package com.surg.extract.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.surg.extract.dto.BatchTaskDTO;
import com.surg.extract.entity.BatchTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface BatchTaskMapper extends BaseMapper<BatchTask> {

    IPage<BatchTaskDTO> selectTaskPage(
            Page<BatchTaskDTO> page,
            @Param("status") String status,
            @Param("department") String department,
            @Param("createdBy") Long createdBy,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    Integer countPendingItems(@Param("taskId") Long taskId);

    Integer countSuccessItems(@Param("taskId") Long taskId);

    Integer countFailedItems(@Param("taskId") Long taskId);

    Integer updateTaskProgress(@Param("taskId") Long taskId);
}
