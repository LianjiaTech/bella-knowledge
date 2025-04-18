package com.ke.bella.files.service.broadcast;

import com.ke.bella.files.protocol.FileBroadcasting;
import org.springframework.stereotype.Component;

@Component
public class NoBroadcastService implements BroadcastService {

    @Override
    public void broadcast(FileBroadcasting<?> message,  Runnable successCallback, Runnable failCallback) {
        successCallback.run();
    }
}
