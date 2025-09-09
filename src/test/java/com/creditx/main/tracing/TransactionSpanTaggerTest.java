package com.creditx.main.tracing;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionSpanTaggerTest {

  @Mock
  private Tracer tracer;

  @Mock
  private Span span;

  @InjectMocks
  private TransactionSpanTagger tagger;

  @Test
  void shouldTagWhenSpanPresent() {
    when(tracer.currentSpan()).thenReturn(span);
    tagger.tagTransactionId(42L);
    verify(span, times(1)).tag("transactionId", "42");
  }

  @Test
  void shouldNoOpWhenNoSpan() {
    when(tracer.currentSpan()).thenReturn(null);
    tagger.tagTransactionId(99L); // Should not throw
    // No interactions with span to verify
  }
}
