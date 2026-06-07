package com.hypersense.boot.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.SysLog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hypersense.boot.system.model.query.LogQuery;
import com.hypersense.boot.system.model.vo.LogPageVO;
import com.hypersense.boot.system.model.vo.VisitOverviewVO;
import com.hypersense.boot.system.model.vo.VisitTrendVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 系统日志 服务接口
 *
 * @author Ray.Hao
 * @since 2.10.0
 */
public interface LogService extends IService<SysLog> {

    /**
     * 获取日志分页列表
     */
    Page<LogPageVO> getLogPage(LogQuery queryParams);


    /**
     * 获取访问趋势
     *
     * @param startDate 开始时间
     * @param endDate   结束时间
     */
    VisitTrendVO getVisitTrend(LocalDate startDate, LocalDate endDate);

    /**
     * 获取访问统计
     */
    VisitOverviewVO getVisitStats();

}
