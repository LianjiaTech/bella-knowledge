package com.ke.bella.files.db.repo;

import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.jooq.DSLContext;
import org.jooq.SelectLimitStep;

import com.ke.bella.openapi.BellaContext;

public interface BaseRepo {
    default void fillCreatorInfo(Operator db) {
        com.ke.bella.openapi.Operator operator = BellaContext.getOperatorIgnoreNull();
        if(operator != null) {
            if(operator.getUserId() != null) {
                db.setCuid(operator.getUserId());
            }

            if(!StringUtils.isEmpty(operator.getUserName())) {
                db.setCuName(operator.getUserName());
            }
            db.setCtime(LocalDateTime.now());
            fillUpdatorInfo(db);
        }
    }

    default void fillUpdatorInfo(Operator db) {
        com.ke.bella.openapi.Operator operator = BellaContext.getOperatorIgnoreNull();
        if(operator != null) {
            if(operator.getUserId() != null) {
                db.setMuid(operator.getUserId());
            }
            if(!StringUtils.isEmpty(operator.getUserName())) {
                db.setMuName(operator.getUserName());
            }
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
