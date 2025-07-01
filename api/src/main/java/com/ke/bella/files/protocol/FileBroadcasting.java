package com.ke.bella.files.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileBroadcasting<T> {
    private String event;
    private T data;
    private String metadata;
    private Long userId;
    private String userName;

    public void setEvent(EventType eventType) {
        this.event = eventType.getValue();
    }
}
