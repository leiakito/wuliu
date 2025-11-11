package com.example.demo.report.service;

import com.example.demo.report.dto.DashboardResponse;
import java.time.LocalDate;

public interface ReportService {

    DashboardResponse dashboard(LocalDate start, LocalDate end);
}
