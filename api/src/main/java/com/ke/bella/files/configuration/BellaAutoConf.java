package com.ke.bella.files.configuration;

import com.ke.bella.files.db.IDGenerator;
import com.ke.bella.files.db.repo.InstanceRepo;
import com.ke.bella.openapi.server.BellaServerContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class BellaAutoConf {
    @Autowired
    private InstanceRepo instanceRepo;

    @PostConstruct
    public void registerInstance() {
        String ip = BellaServerContextHolder.getIp();
        int port = BellaServerContextHolder.getPort();
        Long id = instanceRepo.register(ip, port);
        IDGenerator.setInstanceId(id);
    }
}
