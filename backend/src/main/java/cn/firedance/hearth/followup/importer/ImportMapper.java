package cn.firedance.hearth.followup.importer;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 导入用的读写。SQL 在 resources/mapper/ImportMapper.xml。
 */
@Mapper
public interface ImportMapper {

    Long findShopIdByCode(@Param("code") String code);

    /** 按姓名找老师；找不到返回 null（导入不因此失败） */
    Long findStaffIdByName(@Param("name") String name);

    Long findMemberId(@Param("mobile") String mobile, @Param("name") String name);

    int insertMember(@Param("shopId") Long shopId,
                     @Param("name") String name,
                     @Param("mobile") String mobile,
                     @Param("ownerStaffId") Long ownerStaffId,
                     @Param("firstSeen") LocalDate firstSeen);

    /** 重复导入同一天 = 覆盖：先删当天快照，再插 */
    int deleteFlagsByDate(@Param("date") LocalDate date);

    int deleteSnapshotsByDate(@Param("date") LocalDate date);

    PrevSnapshotRow findPrevSnapshot(@Param("mobile") String mobile, @Param("date") LocalDate date);

    int insertSnapshot(SnapshotDO snapshot);

    int insertFlag(@Param("snapshotId") Long snapshotId, @Param("reasonCode") String reasonCode);
}
