package com.ke.bella.files;

import com.ke.bella.files.db.repo.InstanceRepo;
import com.ke.bella.openapi.server.BellaServerContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

@Component
public class ShutdownListener implements ApplicationListener<ApplicationEvent> {

    @Autowired
    InstanceRepo repo;

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if(event instanceof ContextClosedEvent) {
            String ip = BellaServerContextHolder.getIp();
            int port = BellaServerContextHolder.getPort();
            repo.unregister(ip, port);
        }
    }
}
