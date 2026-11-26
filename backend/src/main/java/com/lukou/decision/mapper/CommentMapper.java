package com.lukou.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lukou.decision.dto.CountRow;
import com.lukou.decision.entity.Comment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface CommentMapper extends BaseMapper<Comment> {

    @Select("<script>" +
            "SELECT post_id AS postId, COUNT(*) AS cnt FROM dv_comment " +
            "WHERE deleted = 0 AND status = 1 AND post_id IN " +
            "<foreach collection='postIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY post_id" +
            "</script>")
    List<CountRow> countByPostIds(@Param("postIds") List<Long> postIds);
}
