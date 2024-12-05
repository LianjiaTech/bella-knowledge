package com.ke.bella.files.db.repo;

import java.time.LocalDateTime;

import org.springframework.util.StringUtils;

import com.ke.bella.files.BellaContext;

public interface BaseRepo {
    default void fillCreatorInfo(Operator db) {
        com.ke.bella.files.api.Operator operator = BellaContext.getOperator();
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
        com.ke.bella.files.api.Operator operator = BellaContext.getOperator();
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
}
