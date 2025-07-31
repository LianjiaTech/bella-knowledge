package com.ke.bella.files.db.repo;

import static com.ke.bella.files.db.tables.Tag.TAG;

import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.ke.bella.files.db.tables.pojos.TagDB;
import com.ke.bella.files.db.tables.records.TagRecord;
import com.ke.bella.files.protocol.TagOps;
import com.ke.bella.files.utils.BellaContextHelper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TagRepo implements BaseRepo {

    @Resource
    private DSLContext db;

    public TagDB addTag(TagOps.TagOp op) {
        TagRecord insertRec = TAG.newRecord();

        String name = op.getName();
        insertRec.setSpaceCode(BellaContextHelper.getOperateSpaceCode());
        insertRec.setName(name);

        fillCreatorInfo(insertRec);

        TagRecord updateRec = TAG.newRecord();
        updateRec.setStatus(0);
        fillUpdatorInfo(updateRec);

        db.insertInto(TAG)
                .set(insertRec)
                .onDuplicateKeyUpdate()
                .set(updateRec)
                .execute();

        return db.selectFrom(TAG)
                .where(TAG.SPACE_CODE.eq(BellaContextHelper.getOperateSpaceCode()))
                .and(TAG.NAME.eq(name))
                .and(TAG.STATUS.eq(0))
                .fetchOneInto(TagDB.class);
    }

    public TagDB getTag(TagOps.TagOp op) {
        return getTag(op, 0);
    }

    public TagDB getTag(TagOps.TagOp op, Integer status) {
        return db.selectFrom(TAG)
                .where(TAG.SPACE_CODE.eq(BellaContextHelper.getOperateSpaceCode()))
                .and(TAG.NAME.eq(op.getName()))
                .and(TAG.STATUS.eq(status))
                .fetchOneInto(TagDB.class);
    }

    public void updateTag(TagOps.TagOp op) {
        TagRecord rec = TAG.newRecord();

        if(op.getName() != null) {
            rec.set(TAG.NAME, op.getName());
        }

        fillUpdatorInfo(rec);

        int execute = db.update(TAG)
                .set(rec)
                .where(TAG.SPACE_CODE.eq(BellaContextHelper.getOperateSpaceCode()))
                .and(TAG.STATUS.eq(0))
                .execute();

        Assert.isTrue(execute == 1, "tag update failed");
    }

    public void deleteTag(TagOps.TagOp op) {
        TagRecord rec = TAG.newRecord();
        rec.set(TAG.STATUS, -1);
        fillUpdatorInfo(rec);

        int execute = db.update(TAG)
                .set(rec)
                .where(TAG.SPACE_CODE.eq(BellaContextHelper.getOperateSpaceCode()))
                .and(TAG.STATUS.eq(0))
                .execute();

        Assert.isTrue(execute == 1, "tag delete failed");
    }

    public Page<TagDB> pageTag(TagOps.TagPage page) {
        SelectConditionStep<TagRecord> sql = db.selectFrom(TAG)
                .where(StringUtils.isEmpty(page.getName()) ? DSL.noCondition()
                        : TAG.NAME.like("%" + DSL.escape(page.getName(), '\\') + "%"))
                .and(TAG.SPACE_CODE.eq(BellaContextHelper.getOperateSpaceCode()))
                .and(TAG.STATUS.eq(0));

        String orderBy = StringUtils.isEmpty(page.getOrderBy()) ? "ctime" : page.getOrderBy().toLowerCase();
        boolean isAsc = "asc".equals(page.getOrder());

        sql.orderBy(isAsc ? DSL.field(orderBy).asc() : DSL.field(orderBy).desc());

        return queryPage(db, sql, page.getPage(), page.getPageSize(), TagDB.class);
    }

    public List<TagDB> listTag(TagOps.TagPage page) {
        SelectConditionStep<TagRecord> sql = db.selectFrom(TAG)
                .where(TAG.SPACE_CODE.eq(BellaContextHelper.getOperateSpaceCode()))
                .and(StringUtils.isEmpty(page.getName()) ? DSL.noCondition()
                        : TAG.NAME.like("%" + DSL.escape(page.getName(), '\\') + "%"))
                .and(TAG.STATUS.eq(0));

        String orderBy = StringUtils.isEmpty(page.getOrderBy()) ? "ctime" : page.getOrderBy().toLowerCase();
        boolean isAsc = "asc".equals(page.getOrder());

        return sql.orderBy(isAsc ? DSL.field(orderBy).asc() : DSL.field(orderBy).desc())
                .fetch().into(TagDB.class);
    }
}
