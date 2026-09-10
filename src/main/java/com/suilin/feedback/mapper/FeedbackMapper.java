package com.suilin.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suilin.feedback.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {}
