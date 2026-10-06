package com.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/** 演示用长运行服务：监听端口（默认 18080，可用 --port=N 覆盖），回显一行标识。 */
public class HelloServer {

    public static void main(String[] args) throws Exception {
        int port = 18080;
        for (String a : args) {
            if (a.startsWith("--port=")) {
                port = Integer.parseInt(a.substring("--port=".length()));
            }
        }
        ServerSocket server = new ServerSocket(port);
        System.out.println("hello-server listening on " + port);
        while (true) {
            try (Socket socket = server.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                in.readLine(); // 读完一行即回（简单回显协议）
                out.println("hello-server-ok on " + port);
            } catch (Exception e) {
                System.err.println("conn error: " + e.getMessage());
            }
        }
    }
}
