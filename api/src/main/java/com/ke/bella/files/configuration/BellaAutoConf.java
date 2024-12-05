package com.ke.bella.files.configuration;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.ke.bella.files.db.IDGenerator;
import com.ke.bella.files.db.repo.InstanceRepo;
import com.ke.infra.cloud.extension.boostrap.AppContext;
import com.ke.infra.cloud.extension.boostrap.Instance;

@Configuration
public class BellaAutoConf {
    @Autowired
    private InstanceRepo instanceRepo;

    @PostConstruct
    public void registerInstance() {
        Instance instance = AppContext.getInstance();
        String ip = instance.getIpAddress();
        Long id = instanceRepo.register(ip, instance.getPort());
        IDGenerator.setInstanceId(id);
    }
}
