package com.ke.bella.files.configuration;

import java.io.File;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.ke.bella.files.db.IDGenerator;
import com.ke.bella.files.db.repo.InstanceRepo;
import com.ke.bella.openapi.server.BellaServerContextHolder;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class BellaAutoConf {
    @Autowired
    private InstanceRepo instanceRepo;
    @Value("${bella.file-api.file.tmp-file-dir}")
    private String tmpFileDir;

    @PostConstruct
    public void init() {
        registerInstance();
        initTmpFileDir();
    }

    public void registerInstance() {
        String ip = BellaServerContextHolder.getIp();
        int port = BellaServerContextHolder.getPort();
        Long id = instanceRepo.register(ip, port);
        IDGenerator.setInstanceId(id);
    }

    private void initTmpFileDir() {
        File tmpDir = new File(tmpFileDir);
        if(!tmpDir.exists()) {
            boolean created = tmpDir.mkdirs();
            if(created) {
                LOGGER.info("temp file dir: {} created succeeded", tmpFileDir);
            } else {
                LOGGER.warn("failed to create temp file dir: {}", tmpFileDir);
            }
        } else {
            LOGGER.info("temp file dir exist...");
        }
    }
}
