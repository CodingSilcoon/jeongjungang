package com.jeongjungang.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.jeongjungang.domain.model.LatLng;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 로컬 HTTP 서버로 docs/API.md 응답 형식과 오류 처리를 확인한다.
 * Android 단위 테스트는 com.sun.net.httpserver를 못 써서 ServerSocket으로 최소한만 응답한다.
 */
public class HttpGeocodeApiTest {

    private ServerSocket server;
    private Thread acceptor;
    private HttpGeocodeApi api;
    private volatile int status;
    private volatile String body;
    private volatile String retryAfter;
    private volatile String lastRawQuery;
    private volatile String lastPath;

    @Before
    public void start() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        acceptor = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!server.isClosed()) {
                    try (Socket socket = server.accept()) {
                        handle(socket);
                    } catch (IOException e) {
                        // 서버를 닫으면 accept가 예외로 끝난다
                    }
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
        api = new HttpGeocodeApi("http://127.0.0.1:" + server.getLocalPort() + "/api/v1/");
    }

    private void handle(Socket socket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String requestLine = in.readLine();
        String header;
        while ((header = in.readLine()) != null && !header.isEmpty()) {
            // 요청 헤더는 쓰지 않는다
        }
        URI uri = URI.create(requestLine.split(" ")[1]);
        lastPath = uri.getRawPath();
        lastRawQuery = uri.getRawQuery();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String crlf = "\r\n";
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append(" X").append(crlf)
                .append("Content-Type: application/json; charset=utf-8").append(crlf)
                .append("Content-Length: ").append(bytes.length).append(crlf)
                .append("Connection: close").append(crlf);
        if (retryAfter != null) {
            head.append("Retry-After: ").append(retryAfter).append(crlf);
        }
        head.append(crlf);
        OutputStream out = socket.getOutputStream();
        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }

    @After
    public void stop() throws IOException {
        server.close();
    }

    private void respond(int status, String body) {
        this.status = status;
        this.body = body;
    }

    @Test
    public void searchParsesItemsAndSendsEncodedQueryAndSize() throws Exception {
        respond(200, "{\"success\":true,\"data\":{\"items\":["
                + "{\"type\":\"PLACE\",\"name\":\"홍대입구역 2호선\",\"address\":\"서울 마포구 양화로 160\",\"lat\":37.5572,\"lng\":126.9245},"
                + "{\"type\":\"ADDRESS\",\"name\":\"서울 마포구 와우산로 94\",\"address\":null,\"lat\":37.5489,\"lng\":126.9227}"
                + "]},\"error\":null}");

        List<GeoPlace> items = api.search("  홍대 입구 ", 5);

        assertEquals("/api/v1/geocode", lastPath);
        assertEquals("query=%ED%99%8D%EB%8C%80%20%EC%9E%85%EA%B5%AC&size=5", lastRawQuery);
        assertEquals(2, items.size());
        assertEquals(GeoPlace.Type.PLACE, items.get(0).type);
        assertEquals("홍대입구역 2호선", items.get(0).name);
        assertEquals("서울 마포구 양화로 160", items.get(0).address);
        assertEquals(new LatLng(37.5572, 126.9245), items.get(0).location);
        assertEquals(GeoPlace.Type.ADDRESS, items.get(1).type);
        assertNull(items.get(1).address);
    }

    @Test
    public void sizeIsClampedToSpecRange() throws Exception {
        respond(200, "{\"success\":true,\"data\":{\"items\":[]},\"error\":null}");
        api.search("강남", 50);
        assertTrue(lastRawQuery, lastRawQuery.endsWith("&size=10"));
        api.search("강남", 0);
        assertTrue(lastRawQuery, lastRawQuery.endsWith("&size=1"));
    }

    @Test
    public void emptyItemsIsEmptyListNotError() throws Exception {
        respond(200, "{\"success\":true,\"data\":{\"items\":[]},\"error\":null}");
        assertTrue(api.search("없는주소", 5).isEmpty());
    }

    @Test
    public void tooShortQueryIsRejectedWithoutCallingServer() {
        lastPath = null;
        try {
            api.search(" a ", 5);
            fail();
        } catch (ApiException e) {
            assertEquals("VALIDATION_FAILED", e.code);
            assertNull(lastPath);
        }
    }

    @Test
    public void reverseReadsBothAddressesAndNulls() throws Exception {
        respond(200, "{\"success\":true,\"data\":{\"address\":\"서울 마포구 상수동 331-5\",\"roadAddress\":\"서울 마포구 와우산로 94\"},\"error\":null}");
        ReverseAddress found = api.reverse(new LatLng(37.5489, 126.9227));
        assertEquals("/api/v1/geocode/reverse", lastPath);
        assertEquals("lat=37.548900&lng=126.922700", lastRawQuery);
        assertEquals("서울 마포구 와우산로 94", found.displayText());

        respond(200, "{\"success\":true,\"data\":{\"address\":null,\"roadAddress\":null},\"error\":null}");
        ReverseAddress none = api.reverse(new LatLng(37.0, 127.0));
        assertNull(none.address);
        assertNull(none.roadAddress);
        assertNull(none.displayText());
    }

    @Test
    public void rateLimitedUsesRetryAfterAndServerMessage() {
        respond(429, "{\"success\":false,\"data\":null,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많아요.\"}}");
        retryAfter = "12";
        try {
            api.search("강남", 5);
            fail();
        } catch (ApiException e) {
            assertEquals(ApiException.RATE_LIMITED, e.code);
            assertEquals(429, e.httpStatus);
            assertEquals(12, e.retryAfterSeconds);
            assertEquals("요청이 너무 많아요.", e.getMessage());
        }
    }

    @Test
    public void rateLimitedWithoutBodyStillExplainsWait() {
        respond(429, "");
        retryAfter = "30";
        try {
            api.search("강남", 5);
            fail();
        } catch (ApiException e) {
            assertEquals(ApiException.RATE_LIMITED, e.code);
            assertTrue(e.getMessage(), e.getMessage().contains("30초"));
        }
    }

    @Test
    public void serverErrorEnvelopeMessageIsPassedThrough() {
        respond(502, "{\"success\":false,\"data\":null,\"error\":{\"code\":\"UPSTREAM_ERROR\",\"message\":\"주소 검색 서비스가 잠시 불안정해요.\"}}");
        try {
            api.search("강남", 5);
            fail();
        } catch (ApiException e) {
            assertEquals("UPSTREAM_ERROR", e.code);
            assertEquals(502, e.httpStatus);
            assertEquals("주소 검색 서비스가 잠시 불안정해요.", e.getMessage());
        }
    }

    @Test
    public void nonJsonErrorGetsGenericMessage() {
        respond(500, "<html>oops</html>");
        try {
            api.search("강남", 5);
            fail();
        } catch (ApiException e) {
            assertEquals("HTTP_500", e.code);
            assertEquals(HttpGeocodeApi.MSG_SERVER, e.getMessage());
        }
    }

    @Test
    public void malformedSuccessIsBadResponse() {
        respond(200, "{\"success\":true,\"data\":{\"items\":[{\"name\":\"x\"}]},\"error\":null}");
        try {
            api.search("강남", 5);
            fail();
        } catch (ApiException e) {
            assertEquals(ApiException.BAD_RESPONSE, e.code);
            assertEquals(HttpGeocodeApi.MSG_BAD_RESPONSE, e.getMessage());
        }
    }

    @Test
    public void unreachableServerIsNetworkError() throws IOException {
        ServerSocket probe = new ServerSocket(0);
        int freePort = probe.getLocalPort();
        probe.close();
        HttpGeocodeApi offline = new HttpGeocodeApi("http://127.0.0.1:" + freePort + "/api/v1");
        try {
            offline.search("강남", 5);
            fail();
        } catch (ApiException e) {
            assertTrue(e.isNetwork());
            assertEquals(0, e.httpStatus);
            assertEquals(HttpGeocodeApi.MSG_NETWORK, e.getMessage());
        }
    }

    @Test
    public void retryAfterParsing() {
        assertEquals(0, HttpGeocodeApi.parseRetryAfter(null));
        assertEquals(7, HttpGeocodeApi.parseRetryAfter(" 7 "));
        assertEquals(0, HttpGeocodeApi.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"));
        assertEquals(0, HttpGeocodeApi.parseRetryAfter("-3"));
    }
}
