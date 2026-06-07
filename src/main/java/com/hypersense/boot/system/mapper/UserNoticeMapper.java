package com.hypersense.boot.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hypersense.boot.system.model.entity.UserNotice;
import com.hypersense.boot.system.model.query.NoticeQuery;
import com.hypersense.boot.system.model.vo.NoticePageVO;
import com.hypersense.boot.system.model.vo.UserNoticePageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户公告 Mapper 接口
 *
 * @author youlaitech
 * @since 2024-08-28 16:56
 */
@Mapper
public interface UserNoticeMapper extends BaseMapper<UserNotice> {
    /**
     * 分页获取我的通知公告
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return 通知公告分页列表
     */
    IPage<UserNoticePageVO> getMyNoticePage(Page<NoticePageVO> page, @Param("queryParams") NoticeQuery queryParams);
}
