package com.ke.bella.files.db.repo;

import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.SelectLimitStep;

import com.ke.bella.files.utils.BellaContextHelper;

public interface BaseRepo {
    default void fillCreatorInfo(Operator db) {
        Long userId = BellaContextHelper.getOperatorUserId();
        String userName = BellaContextHelper.getOperatorUserName();
        if(userId != null) {
            db.setCuid(userId);
        }

        if(!StringUtils.isEmpty(userName)) {
            db.setCuName(userName);
        }
        db.setCtime(LocalDateTime.now());
        fillUpdatorInfo(db);
    }

    default void fillUpdatorInfo(Operator db) {
        Long userId = BellaContextHelper.getOperatorUserId();
        String userName = BellaContextHelper.getOperatorUserName();
        if(userId != null) {
            db.setCuid(userId);
        }

        if(!StringUtils.isEmpty(userName)) {
            db.setCuName(userName);
        }
        db.setMtime(LocalDateTime.now());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    default <T> Page<T> queryPage(DSLContext db, SelectLimitStep scs, int page, int pageSize, Class<T> clazz) {
        if(scs == null) {
            return Page.from(page, pageSize);
        }
        return Page.from(page, pageSize)
                .total(db.fetchCount(scs))
                .list(scs.limit((page - 1) * pageSize, pageSize)
                        .fetch()
                        .into(clazz));
    }
}
