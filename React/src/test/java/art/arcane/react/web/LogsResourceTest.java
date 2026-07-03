package art.arcane.react.web;

import art.arcane.react.api.web.resource.LogsResource;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LogsResourceTest {

    private LogsResource buildResource() {
        return new LogsResource(n -> List.of("line-a", "line-b"));
    }

    @Test
    void list_with_no_limit_returns_default_lines() {
        LogsResource resource = buildResource();
        Context ctx = mock(Context.class);
        when(ctx.queryParam("limit")).thenReturn(null);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.list(ctx);
        verify(ctx).json(captor.capture());

        LogsResource.LogsResponse response = (LogsResource.LogsResponse) captor.getValue();
        assertEquals(2, response.data().length);
        assertEquals("line-a", response.data()[0]);
        assertEquals("line-b", response.data()[1]);
    }

    @Test
    void list_with_non_numeric_limit_throws_bad_request() {
        LogsResource resource = buildResource();
        Context ctx = mock(Context.class);
        when(ctx.queryParam("limit")).thenReturn("abc");

        assertThrows(BadRequestResponse.class, () -> resource.list(ctx));
    }

    @Test
    void list_with_limit_above_max_clamps_and_returns_without_throwing() {
        LogsResource resource = new LogsResource(n -> {
            if (n < 1 || n > 1000) {
                throw new IllegalArgumentException("n out of clamped range: " + n);
            }
            return List.of("line-a", "line-b");
        });
        Context ctx = mock(Context.class);
        when(ctx.queryParam("limit")).thenReturn("5000");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.list(ctx);
        verify(ctx).json(captor.capture());

        LogsResource.LogsResponse response = (LogsResource.LogsResponse) captor.getValue();
        assertEquals(2, response.data().length);
    }

    @Test
    void list_with_limit_below_min_clamps_to_one() {
        LogsResource resource = new LogsResource(n -> {
            if (n < 1) {
                throw new IllegalArgumentException("n below minimum: " + n);
            }
            return List.of("line-a");
        });
        Context ctx = mock(Context.class);
        when(ctx.queryParam("limit")).thenReturn("-5");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        resource.list(ctx);
        verify(ctx).json(captor.capture());

        LogsResource.LogsResponse response = (LogsResource.LogsResponse) captor.getValue();
        assertEquals(1, response.data().length);
    }

    @Test
    void list_with_valid_limit_passes_correct_value() {
        int[] capturedN = {-1};
        LogsResource resource = new LogsResource(n -> {
            capturedN[0] = n;
            return List.of("line-a");
        });
        Context ctx = mock(Context.class);
        when(ctx.queryParam("limit")).thenReturn("50");

        resource.list(ctx);
        assertEquals(50, capturedN[0]);
    }
}
