package com.ke.bella.files.service;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.files.configuration.KafkaConfig;
import com.ke.bella.files.protocol.FileBroadcasting;

public class FileKafkaSerializationTest {
    @Test
    public void actualKafkaSerializerKeepsAkCodeWireName() {
        KafkaConfig config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        DefaultKafkaProducerFactory<String, Object> factory =
                (DefaultKafkaProducerFactory<String, Object>) config.producerFactory();
        FileBroadcasting<String> message = new FileBroadcasting<>();
        message.setAkCode("original-user-ak");
        message.setData("file");
        String json = new String(factory.getValueSerializerSupplier().get().serialize("files", message), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"ak_code\":\"original-user-ak\""));
        assertFalse(json.contains("akCode"));
        assertFalse(json.contains("serviceClient"));
    }
}
