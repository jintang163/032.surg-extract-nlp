package com.surg.extract.controller;

import com.surg.extract.common.Result;
import com.surg.extract.entity.HisSyncLog;
import com.surg.extract.entity.MedicalRecordHome;
import com.surg.extract.service.HisSyncService;
import com.surg.extract.service.MedicalRecordHomeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Api(tags = "HIS同步管理")
@RestController
@RequestMapping("/api/his-sync")
@RequiredArgsConstructor
public class HisSyncController {

    private final HisSyncService hisSyncService;
    private final MedicalRecordHomeService homePageService;

    @ApiOperation("同步病案首页到HIS")
    @PostMapping("/sync/{recordId}")
    public Result<Map<String, Object>> syncToHis(@PathVariable @ApiParam("记录ID") Long recordId) {
        MedicalRecordHome home = homePageService.getByRecordId(recordId);
        if (home == null) {
            return Result.error("病案首页不存在");
        }

        boolean success = hisSyncService.syncToHis(home);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("recordId", recordId);
        result.put("hisEnabled", hisSyncService.isHisEnabled());

        if (success) {
            return Result.success(result);
        } else {
            return Result.error("同步到HIS失败");
        }
    }

    @ApiOperation("从HIS拉取数据")
    @PostMapping("/pull/{recordId}")
    public Result<Map<String, Object>> pullFromHis(
            @PathVariable @ApiParam("记录ID") Long recordId,
            @RequestParam @ApiParam("住院号") String hospitalNo) {
        boolean success = hisSyncService.syncFromHis(recordId, hospitalNo);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("recordId", recordId);
        result.put("hospitalNo", hospitalNo);

        if (success) {
            return Result.success(result);
        } else {
            return Result.error("从HIS拉取数据失败");
        }
    }

    @ApiOperation("回滚HIS同步")
    @PostMapping("/rollback/{recordId}")
    public Result<Map<String, Object>> rollbackSync(@PathVariable @ApiParam("记录ID") Long recordId) {
        boolean success = hisSyncService.rollbackSync(recordId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("recordId", recordId);

        if (success) {
            return Result.success(result);
        } else {
            return Result.error("回滚HIS同步失败");
        }
    }

    @ApiOperation("获取同步日志列表")
    @GetMapping("/logs/{recordId}")
    public Result<List<HisSyncLog>> getSyncLogs(@PathVariable @ApiParam("记录ID") Long recordId) {
        List<HisSyncLog> logs = hisSyncService.getSyncLogs(recordId);
        return Result.success(logs);
    }

    @ApiOperation("获取最新同步日志")
    @GetMapping("/logs/latest/{recordId}")
    public Result<HisSyncLog> getLatestSyncLog(@PathVariable @ApiParam("记录ID") Long recordId) {
        HisSyncLog log = hisSyncService.getLatestSyncLog(recordId);
        return Result.success(log);
    }

    @ApiOperation("获取HIS同步状态")
    @GetMapping("/status/{recordId}")
    public Result<Map<String, Object>> getSyncStatus(@PathVariable @ApiParam("记录ID") Long recordId) {
        HisSyncLog latestLog = hisSyncService.getLatestSyncLog(recordId);

        Map<String, Object> result = new HashMap<>();
        result.put("hisEnabled", hisSyncService.isHisEnabled());
        result.put("latestLog", latestLog);
        result.put("synced", latestLog != null && "SUCCESS".equals(latestLog.getSyncStatus()));

        return Result.success(result);
    }

    @ApiOperation("触发计费")
    @PostMapping("/billing/{recordId}")
    public Result<Map<String, Object>> triggerBilling(@PathVariable @ApiParam("记录ID") Long recordId) {
        MedicalRecordHome home = homePageService.getByRecordId(recordId);
        if (home == null) {
            return Result.error("病案首页不存在");
        }

        String result = hisSyncService.triggerBilling(home);

        Map<String, Object> response = new HashMap<>();
        response.put("recordId", recordId);
        response.put("result", result);

        return Result.success(response);
    }

    @ApiOperation("检查HIS服务是否启用")
    @GetMapping("/enabled")
    public Result<Map<String, Object>> checkHisEnabled() {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", hisSyncService.isHisEnabled());
        return Result.success(result);
    }
}
