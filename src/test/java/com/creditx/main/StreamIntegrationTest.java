package com.creditx.main;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = StreamIntegrationTest.class)
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class})
@Import(TestChannelBinderConfiguration.class)
@ActiveProfiles("test")
class StreamIntegrationTest {

  @Autowired
  private StreamBridge streamBridge;

  @Autowired
  private OutputDestination outputDestination;

  @Test
  void testPublishAndConsume() {
    String bindingName = "test-out-0";
    String payload = "hello-stream";
    String key = "test-key";

    Message<String> message = MessageBuilder.withPayload(payload).setHeader("key", key).build();

    streamBridge.send(bindingName, message);

    Message<byte[]> received = outputDestination.receive(1000, "test-topic");

    assertThat(received).isNotNull();
    assertThat(new String(received.getPayload())).isEqualTo(payload);
    assertThat(received.getHeaders().get("key")).isEqualTo(key);
  }
}
