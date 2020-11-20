
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;

public class Test {
    static String httpResponse = "[0.2.4,1.6.7]";

    static ServerSocket serverSocket = null;
    static DatagramSocket control = null, images = null;
    static HttpServer server = null;
    static byte[] bytesOfimg;

    public static void main(String[] args) {

        try {
            serverSocket = new ServerSocket(8888);

            server = HttpServer.create(new InetSocketAddress(8889), 0);
            server.createContext("/info", new MyHandler());
            server.setExecutor(null);
            server.start();

            URL url = new URL("https://raw.githubusercontent.com/imalinowski/AndroidTest/main/propeller.png");
            BufferedImage image = ImageIO.read(url);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            bytesOfimg = baos.toByteArray();
            System.out.println(image.getHeight() + " " + baos.size());

            System.out.println("run...");
            Socket client = serverSocket.accept();

            System.out.println("new client - " + client.getInetAddress());
            byte[] buffer = new byte[6];
            DatagramPacket pack = new DatagramPacket(buffer, buffer.length);

            control = new DatagramSocket(8001);
            images = new DatagramSocket(8888);

            control.setSoTimeout(3000);
            long lastData = System.currentTimeMillis();
            int angle = 0;
            while (!client.isClosed()) {
                control.receive(pack);
                System.out.print("->");
                for (byte b : buffer)
                    System.out.print((b & 0xFF) + " ");
                System.out.println();

                if (System.currentTimeMillis() - lastData > 1000 / 30) {
                    lastData = System.currentTimeMillis();
                    angle += (255 - buffer[2] & 0xFF) / 10;
                    baos.reset();
                    rotateImage(image, angle, baos);
                    images.send(new DatagramPacket(bytesOfimg, bytesOfimg.length, pack.getAddress(), client.getPort()));
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } finally {
            System.out.println("client gone");
            try {
                if (serverSocket != null) serverSocket.close();
                if (control != null) control.close();
                if (images != null) images.close();
                if (server != null) server.stop(1);

            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
        main(null);
    }

    public static void rotateImage(final BufferedImage img, float angle, ByteArrayOutputStream baos) {
        new Thread(() -> {
            BufferedImage newImage = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());
            double a, dist;
            int x, y;
            for (int i = 0; i < img.getWidth(); i++)
                for (int j = 0; j < img.getHeight(); j++) {
                    Color color = new Color(img.getRGB(i, j));
                    if (color.getAlpha() == 0 || color.getRed() != 0 || color.getGreen() != 0 || color.getBlue() != 0)
                        continue;
                    x = i - img.getWidth() / 2;
                    y = j - img.getHeight() / 2;

                    a = Math.atan2(y, x) + Math.toRadians(angle);
                    dist = Math.sqrt(x * x + y * y);
                    x = (int) (dist * Math.cos(a)) + img.getWidth() / 2;
                    y = (int) (dist * Math.sin(a)) + img.getHeight() / 2;
                    if (x > 0 && x < newImage.getWidth() && y > 0 && y < newImage.getHeight())
                        newImage.setRGB(x, y, img.getRGB(i, j));
                }
            try {
                ImageIO.write(newImage, "png", baos);
                bytesOfimg = baos.toByteArray();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }).start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = httpResponse;
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}