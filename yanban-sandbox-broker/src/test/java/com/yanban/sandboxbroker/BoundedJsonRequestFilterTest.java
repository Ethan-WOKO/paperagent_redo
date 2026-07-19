package com.yanban.sandboxbroker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class BoundedJsonRequestFilterTest {
    @Test void rejectsChunkedOversizeBeforeControllerOrJackson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/executions");
        request.setContent(new byte[BoundedJsonRequestFilter.MAX_JSON_BYTES + 1]);
        request.removeHeader("Content-Length");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        new BoundedJsonRequestFilter().doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(413);
        verifyNoInteractions(chain);
    }
}
