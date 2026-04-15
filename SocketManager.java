import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;

public class SocketManager {
    Window win;
    Robot robot;
    Rectangle screenRect;
    private static final int MAX_PACKET_SIZE = 60000;

    public SocketManager(Window win) {
        this.win = win;
        screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        try {
            robot = new Robot();
        } catch (AWTException e) {}
    }

    public static boolean isWayland() {
        return "wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"));
    }

    BufferedImage takeScreenshot() {
        BufferedImage fullImg = robot.createScreenCapture(screenRect);
        BufferedImage scaledImg = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_BGR);
        
        Graphics2D g2d = scaledImg.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.drawImage(fullImg, 0, 0, 1280, 720, null);
        g2d.dispose();
        
        return scaledImg;
    }

    BufferedImage takeScreenshotGrim() {
        try {
            Process process = new ProcessBuilder("grim", "-").start();
            byte[] imageBytes = process.getInputStream().readAllBytes();
            process.waitFor();
            
            if (imageBytes.length == 0) return null;

            BufferedImage fullImg = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (fullImg == null) return null;

            BufferedImage scaledImg = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_BGR);
            
            Graphics2D g2d = scaledImg.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.drawImage(fullImg, 0, 0, 1280, 720, null);
            g2d.dispose();

            return scaledImg;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void writeJpgOptimized(BufferedImage img, OutputStream out) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.3f);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
            ios.flush();
        }
        writer.dispose();
    }

    public class Server {
        DatagramSocket socket;
        InetAddress clientAddr;
        int clientPort;

        public Server(int port) throws IOException {
            socket = new DatagramSocket(port);
            System.out.println("UDP Server started on port " + port);

            byte[] buf = new byte[1024];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            socket.receive(p);
            clientAddr = p.getAddress();
            clientPort = p.getPort();
            System.out.println("Client linked: " + clientAddr);

            new Thread(() -> {
                while (!socket.isClosed()) {
                    try {
                        BufferedImage img = isWayland() ? takeScreenshotGrim() : takeScreenshot();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        writeJpgOptimized(img, baos);
                        byte[] data = baos.toByteArray();

                        byte[] header = ByteBuffer.allocate(4).putInt(data.length).array();
                        socket.send(new DatagramPacket(header, header.length, clientAddr, clientPort));

                        int offset = 0;
                        while (offset < data.length) {
                            int len = Math.min(MAX_PACKET_SIZE, data.length - offset);
                            socket.send(new DatagramPacket(data, offset, len, clientAddr, clientPort));
                            offset += len;
                        }
                        Thread.sleep(2);
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }).start();
        }
    }

    public class Client {
        DatagramSocket socket;

        public Client(String ip, int port) {
            try {
                socket = new DatagramSocket();
                InetAddress addr = InetAddress.getByName(ip);
                
                byte[] hello = "hi".getBytes();
                socket.send(new DatagramPacket(hello, hello.length, addr, port));

                new Thread(() -> {
                    while (!socket.isClosed()) {
                        try {
                            byte[] header = new byte[4];
                            DatagramPacket hp = new DatagramPacket(header, header.length);
                            socket.receive(hp);
                            int totalSize = ByteBuffer.wrap(header).getInt();

                            byte[] fullImgData = new byte[totalSize];
                            int received = 0;
                            while (received < totalSize) {
                                byte[] chunkBuf = new byte[MAX_PACKET_SIZE + 200];
                                DatagramPacket cp = new DatagramPacket(chunkBuf, chunkBuf.length);
                                socket.receive(cp);
                                
                                int len = cp.getLength();
                                if (received + len <= totalSize) {
                                    System.arraycopy(cp.getData(), 0, fullImgData, received, len);
                                    received += len;
                                } else {
                                    break;
                                }
                            }

                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(fullImgData));
                            if (img != null) {
                                win.setIsConnected(true);
                                win.updateImg(img);
                            }
                        } catch (Exception e) {
                            win.setIsConnected(false);
                        }
                    }
                }).start();
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public Server initServer(int port) {
        try { return new Server(port); } catch (IOException e) { return null; }
    }

    public Client initClient(String ip, int port) {
        return new Client(ip, port);
    }
}
