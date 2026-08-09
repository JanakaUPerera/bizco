package com.bizco.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bizco.common.api.ApiHeaders;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void echoesProvidedCorrelationId() throws ServletException, IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(ApiHeaders.CORRELATION_ID, "request-correlation-id");

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("request-correlation-id", response.getHeader(ApiHeaders.CORRELATION_ID));
        assertEquals("request-correlation-id", request.getAttribute(ApiHeaders.CORRELATION_ID));
    }

    @Test
    void generatesCorrelationIdWhenMissing() throws ServletException, IOException {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotNull(response.getHeader(ApiHeaders.CORRELATION_ID));
        assertEquals(response.getHeader(ApiHeaders.CORRELATION_ID),
                request.getAttribute(ApiHeaders.CORRELATION_ID));
    }
}
