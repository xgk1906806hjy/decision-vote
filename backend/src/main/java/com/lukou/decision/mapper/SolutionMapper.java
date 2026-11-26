package com.lukou.decision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lukou.decision.entity.Solution;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface SolutionMapper extends BaseMapper<Solution> {

    @Select("SELECT solution_id, COUNT(*) AS cnt FROM dv_solution_like WHERE solution_id IN "
            + "(SELECT id FROM dv_solution WHERE post_id = #{postId} AND status = 1) GROUP BY solution_id")
    List<Map<String, Object>> countLikesByPost(@Param("postId") Long postId);

    @Update("UPDATE dv_solution SET like_count = like_count + 1 WHERE id = #{id}")
    int incrementLike(@Param("id") Long id);

    @Update("UPDATE dv_solution SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{id}")
    int decrementLike(@Param("id") Long id);
}
