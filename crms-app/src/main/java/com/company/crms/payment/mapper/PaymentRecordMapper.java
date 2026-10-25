package com.company.crms.payment.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.company.crms.payment.entity.PaymentRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecord> {

    @Select("SELECT * FROM payment_record WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    PaymentRecord selectForUpdate(@Param("id") Long id);

    @Select("""
        <script>
        SELECT * FROM payment_record WHERE is_deleted = 0
          <choose>
            <when test="ew != null and ew.nonEmptyOfWhere">AND ${ew.sqlSegment}</when>
            <when test="ew != null and ew.sqlSegment != null and ew.sqlSegment != ''">${ew.sqlSegment}</when>
          </choose>
        </script>
        """)
    IPage<PaymentRecord> selectPageRecords(IPage<PaymentRecord> page,
                                           @Param(Constants.WRAPPER) Wrapper<PaymentRecord> wrapper);

    @Delete("DELETE FROM payment_record WHERE id = #{id}")
    int physicalDelete(@Param("id") Long id);

    /**
     * 收入确认时点平衡公式用：arrival_date ≤ date 的有效回款合计。
     * 排除已红冲原单（REVERSED）与红字冲销单（RED），保证净额口径。
     */
    @Select("""
        SELECT IFNULL(SUM(amount), 0) FROM payment_record
         WHERE contract_id = #{contractId} AND arrival_date <= #{date}
           AND status = 'NORMAL' AND is_deleted = 0
        """)
    BigDecimal sumEffectiveAmountUpTo(@Param("contractId") Long contractId,
                                      @Param("date") LocalDate date);
}
