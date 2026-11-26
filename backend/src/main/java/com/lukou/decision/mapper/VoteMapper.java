package com.lukou.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lukou.decision.dto.VoteStatRow;
import com.lukou.decision.entity.Vote;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VoteMapper extends BaseMapper<Vote> {

    @Select("<script>" +
            "SELECT post_id AS postId, option_id AS optionId, COUNT(*) AS cnt " +
            "FROM dv_vote WHERE post_id IN " +
            "<foreach collection='postIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY post_id, option_id" +
            "</script>")
    List<VoteStatRow> statByPostIds(@Param("postIds") List<Long> postIds);
}
