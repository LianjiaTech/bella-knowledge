package com.ke.bella.files.configuration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.concurrent.SettableListenableFuture;

import com.ke.bella.files.protocol.FileBroadcasting;
import com.ke.bella.files.protocol.OpenAIFile;
import com.ke.bella.files.service.broadcast.BroadcastService;

public class KafkaConfigTest {
    private static final String TOPIC = "file-broadcast";

    private KafkaTemplate<String, Object> kafkaTemplate;
    private BroadcastService broadcastService;

    @Before
    public void setup() {
        KafkaConfig kafkaConfig = new KafkaConfig();
        kafkaTemplate = mock(KafkaTemplate.class);
        ReflectionTestUtils.setField(kafkaConfig, "topic", TOPIC);
        broadcastService = kafkaConfig.kafkaBroadcastService(kafkaTemplate);
    }

    @Test
    public void broadcastsWithSpaceCodeAndRunsSuccessCallback() {
        FileBroadcasting<OpenAIFile> message = message("file-1", "space-a");
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        Runnable successCallback = mock(Runnable.class);
        Runnable failCallback = mock(Runnable.class);
        when(kafkaTemplate.send(TOPIC, "space-a", message)).thenReturn(future);

        broadcastService.broadcast(message, successCallback, failCallback);
        future.set(null);

        verify(kafkaTemplate).send(TOPIC, "space-a", message);
        verify(successCallback).run();
        verify(failCallback, never()).run();
    }

    @Test
    public void broadcastsWithFileIdAndRunsFailCallbackWithoutSpaceCode() {
        FileBroadcasting<OpenAIFile> message = message("file-2", null);
        SettableListenableFuture<SendResult<String, Object>> future = new SettableListenableFuture<>();
        Runnable successCallback = mock(Runnable.class);
        Runnable failCallback = mock(Runnable.class);
        when(kafkaTemplate.send(TOPIC, "file-2", message)).thenReturn(future);

        broadcastService.broadcast(message, successCallback, failCallback);
        future.setException(new RuntimeException("send failed"));

        verify(kafkaTemplate).send(TOPIC, "file-2", message);
        verify(successCallback, never()).run();
        verify(failCallback).run();
    }

    private FileBroadcasting<OpenAIFile> message(String fileId, String spaceCode) {
        FileBroadcasting<OpenAIFile> message = new FileBroadcasting<>();
        message.setData(OpenAIFile.builder().id(fileId).spaceCode(spaceCode).build());
        return message;
    }
}
