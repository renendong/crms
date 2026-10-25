package com.company.crms.revenue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.crms.revenue.entity.RevenueRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface RevenueRecordMapper extends BaseMapper<RevenueRecord> {

    /**
     * 时点平衡公式左值：截至 date（含）的含税收入合计，可排除指定记录（编辑场景）。
     */
    @Select("""
        <script>
        SELECT IFNULL(SUM(amount), 0) FROM revenue_record
         WHERE contract_id = #{contractId} AND is_deleted = 0
           AND recognize_date &lt;= #{date}
         <if test="excludeId != null">AND id &lt;&gt; #{excludeId}</if>
        </script>
        """)
    BigDecimal sumAmountUpTo(@Param("contractId") Long contractId,
                             @Param("date") LocalDate date,
                             @Param("excludeId") Long excludeId);

    /**
     * 合同级收入合计，可排除指定记录（编辑场景）。
     */
    @Select("""
        <script>
        SELECT IFNULL(SUM(amount), 0) FROM revenue_record
         WHERE contract_id = #{contractId} AND is_deleted = 0
         <if test="excludeId != null">AND id &lt;&gt; #{excludeId}</if>
        </script>
        """)
    BigDecimal sumAmountByContract(@Param("contractId") Long contractId,
                                   @Param("excludeId") Long excludeId);
}
