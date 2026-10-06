package com.jeongjungang.testing;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 단위 테스트용 최소 HTTP 서버. 미리 넣은 응답을 순서대로 돌려주고 받은 요청을 기록한다.
 * Android 단위 테스트는 com.sun.net.httpserver를 못 써서 ServerSocket으로 만든다.
 */
public final class FakeServer implements Closeable {

    public static final class Request {
        public final String method;
        /** 인코딩된 경로+쿼리 그대로. 예: /api/v1/places?lat=... */
        public final String target;
        /** 헤더 이름은 소문자. */
        public final Map<String, String> headers;
        public final String body;

        Request(String method, String target, Map<String, String> headers, String body) {
            this.method = method;
            this.target = target;
            this.headers = headers;
            this.body = body;
        }

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private static final class Response {
        final int status;
        final String body;
        final Map<String, String> headers;

        Response(int status, String body, Map<String, String> headers) {
            this.status = status;
            this.body = body;
            this.headers = headers;
        }
    }

    private final ServerSocket socket;
    private final Deque<Response> responses = new ArrayDeque<Response>();
    private final List<Request> requests = new ArrayList<Request>();

    public FakeServer() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!socket.isClosed()) {
                    try (Socket s = socket.accept()) {
                        handle(s);
                    } catch (IOException e) {
                        // 닫으면 accept가 예외로 끝난다
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /** 예: http://127.0.0.1:12345/api/v1 */
    public String baseUrl() {
        return "http://127.0.0.1:" + socket.getLocalPort() + "/api/v1";
    }

    public FakeServer enqueue(int status, String body) {
        return enqueue(status, body, new LinkedHashMap<String, String>());
    }

    public synchronized FakeServer enqueue(int status, String body, Map<String, String> headers) {
        responses.add(new Response(status, body, headers));
        return this;
    }

    public synchronized List<Request> requests() {
        return new ArrayList<Request>(requests);
    }

    public synchronized Request last() {
        return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }

    private void handle(Socket s) throws IOException {
        InputStream in = s.getInputStream();
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) {
            return;
        }
        Map<String, String> headers = new LinkedHashMap<String, String>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
            }
        }
        int length = headers.containsKey("content-length") ? Integer.parseInt(headers.get("content-length")) : 0;
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n < 0) {
                break;
            }
            read += n;
        }
        String[] parts = requestLine.split(" ");
        Response r;
        synchronized (this) {
            requests.add(new Request(parts[0], parts[1], headers, new String(body, 0, read, StandardCharsets.UTF_8)));
            r = responses.isEmpty() ? new Response(500, "", new LinkedHashMap<String, String>()) : responses.poll();
        }
        byte[] out = r.body.getBytes(StandardCharsets.UTF_8);
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(r.status).append(" X\r\n");
        boolean noBody = r.status == 204 || r.status == 304;
        if (!noBody) {
            head.append("Content-Type: application/json; charset=utf-8\r\n")
                    .append("Content-Length: ").append(out.length).append("\r\n");
        }
        head.append("Connection: close\r\n");
        for (Map.Entry<String, String> h : r.headers.entrySet()) {
            head.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
        }
        head.append("\r\n");
        OutputStream os = s.getOutputStream();
        os.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        if (!noBody) {
            os.write(out);
        }
        os.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                buf.write(c);
            }
        }
        if (c == -1 && buf.size() == 0) {
            return null;
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
