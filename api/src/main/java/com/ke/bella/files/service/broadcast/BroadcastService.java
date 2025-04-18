package com.ke.bella.files.service.broadcast;

import com.ke.bella.files.protocol.FileBroadcasting;

/**
 * 用于通知其他依赖文件的服务，可实现通知机制
 */
public interface BroadcastService {
    void broadcast(FileBroadcasting<?> message, Runnable successCallback, Runnable failCallback);
}
