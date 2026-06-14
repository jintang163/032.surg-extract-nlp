package com.surg.extract.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class FeedbackDashboardDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private FeedbackOverviewDTO overview;

    private List<FeedbackTrendDTO> feedbackTrend;

    private List<EntityFeedbackStatsDTO> entityTypeStats;

    private List<CorrectionTypeStatsDTO> correctionTypeStats;

    private List<DoctorFeedbackStatsDTO> topDoctors;

    private List<ModelTrainLogDTO> recentTrainLogs;
}
