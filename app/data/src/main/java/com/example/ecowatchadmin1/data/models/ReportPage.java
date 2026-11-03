package com.example.ecowatchadmin1.data.models;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/** Paged report-history response for one reporter. */
public class ReportPage implements Serializable {
    private List<Report> reports = Collections.emptyList();
    private int page = 1;
    private int limit;
    private int total;
    private int totalPages;

    public List<Report> getReports() {
        return reports == null ? Collections.emptyList() : reports;
    }

    public void setReports(List<Report> reports) {
        this.reports = reports;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getTotal() {
        return Math.max(0, total);
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getTotalPages() {
        return Math.max(0, totalPages);
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean hasMore() {
        return page < totalPages;
    }
}
