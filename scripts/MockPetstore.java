import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * E2E mock 下游：模拟 petstore 三接口（GET/POST /pets、DELETE /pets/{petId}），
 * 供全链路手工验证使用（M3 E2E 步骤 1）。用法：java MockPetstore [port=18090]。
 */
public class MockPetstore {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 18090;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/pets", exchange -> {
            String method = exchange.getRequestMethod();
            byte[] body;
            int code;
            switch (method) {
                case "GET" -> {
                    body = "[{\"id\":1,\"name\":\"Rex\",\"tag\":\"dog\"},{\"id\":2,\"name\":\"Milo\",\"tag\":\"cat\"}]"
                            .getBytes(StandardCharsets.UTF_8);
                    code = 200;
                }
                case "POST" -> {
                    body = "{\"id\":7,\"name\":\"Mochi\",\"created\":true}".getBytes(StandardCharsets.UTF_8);
                    code = 201;
                }
                case "DELETE" -> {
                    body = new byte[0];
                    code = 204;
                }
                default -> {
                    body = "{\"error\":\"method not allowed\"}".getBytes(StandardCharsets.UTF_8);
                    code = 405;
                }
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
            if (code == 204) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                exchange.sendResponseHeaders(code, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        });
        server.start();
        System.out.println("MockPetstore listening on http://127.0.0.1:" + port);
    }
}
