import java.awt.AWTException;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class SocketManager {
    Window win;
    volatile Client cli = null;
    volatile Server serv = null;
    Robot robot;
    Rectangle screenRect;

    public SocketManager(Window win) {
        this.win = win;
        screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        try {
            robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    public void closeSockets() {
        if (cli != null) {
            cli.close();
        } else {
            System.out.println("cli is null");
        }
        if (serv != null) {
            serv.close();
        } else {
            System.out.println("serv is null");
        }
    }

    public void initServer(int port) {
        serv = new Server(port);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serv.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void initClient(String ip, int port) {
        cli = new Client(ip, port);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    cli.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static boolean isWayland() {
        String sessionType = System.getenv("XDG_SESSION_TYPE");
        return "wayland".equalsIgnoreCase(sessionType);
    }

    BufferedImage takeScreenshotGrim() {
        try {
            Process process = new ProcessBuilder("grim", "-").start();
            byte[] imageBytes = process.getInputStream().readAllBytes();
            process.waitFor();
            if (imageBytes.length == 0)
                return null;

            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null)
                return null;

            BufferedImage scaledImg = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
            Graphics g = scaledImg.getGraphics();
            g.drawImage(img, 0, 0, 1280, 720, null);
            g.dispose();

            return scaledImg;
        } catch (Exception e) {
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

    BufferedImage takeScreenshot() {
        BufferedImage fullImg = robot.createScreenCapture(screenRect);
        BufferedImage scaledImg = new BufferedImage(1280, 720, BufferedImage.TYPE_3BYTE_BGR);
        Graphics g = scaledImg.getGraphics();
        g.drawImage(fullImg, 0, 0, 1280, 720, null);
        g.dispose();
        return scaledImg;
    }

    public class Server {
        public int port = 9021;
        Socket s;
        ServerSocket ss;
        DataInputStream in;
        DataOutputStream out;
        private volatile boolean running = true;
        private BufferedImage previousFrame = null;
        private long lastFrameTime = 0;
        private final int TARGET_FPS = 20;
        private final long FRAME_DELAY = 1000 / TARGET_FPS;

        public Server(int port) {
            this.port = port;
        }

        public void close() {
            running = false;
            try {
                if (s != null && !s.isClosed())
                    s.close();
            } catch (Exception e) {
            }
            try {
                if (ss != null && !ss.isClosed())
                    ss.close();
            } catch (Exception e) {
            }
        }

        private boolean isDifferentEnough(BufferedImage current, BufferedImage previous) {
            if (previous == null || current.getWidth() != previous.getWidth()
                    || current.getHeight() != previous.getHeight()) {
                return true;
            }

            int changedPixels = 0;
            int sampledWidth = current.getWidth() / 10;
            int sampledHeight = current.getHeight() / 10;

            for (int y = 0; y < current.getHeight(); y += sampledHeight) {
                for (int x = 0; x < current.getWidth(); x += sampledWidth) {
                    if (current.getRGB(x, y) != previous.getRGB(x, y)) {
                        changedPixels++;
                    }
                }
            }

            return changedPixels > 2;
        }

        public void start() throws IOException {
            ss = new ServerSocket(port);
            System.out.println("server started");
            win.appendLog("Server started, waiting...");

            s = ss.accept();
            System.out.println("client connected");
            win.appendLog("Client connected.");

            in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

            out.writeInt(screenRect.width);
            out.writeInt(screenRect.height);
            out.flush();

            BufferedImage img = null;

            while (running && !s.isClosed()) {
                try {
                    long currentTime = System.currentTimeMillis();
                    long elapsed = currentTime - lastFrameTime;

                    if (elapsed < FRAME_DELAY) {
                        Thread.sleep(FRAME_DELAY - elapsed);
                    }

                    lastFrameTime = System.currentTimeMillis();

                    if (isWayland()) {
                        img = takeScreenshotGrim();
                    } else {
                        img = takeScreenshot();
                    }

                    if (img == null)
                        continue;

                    if (!isDifferentEnough(img, previousFrame)) {
                        handleInput();
                        continue;
                    }

                    previousFrame = img;

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    writeJpgOptimized(img, baos);
                    byte[] bytes = baos.toByteArray();

                    handleInput();

                    out.writeInt(bytes.length);
                    out.write(bytes);
                    out.flush();

                } catch (SocketException e) {
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
            System.out.println("server stopped");
            win.appendLog("Client disconnected.");
        }

        private void handleInput() throws IOException {
            while (in.available() >= 12) {
                int type = in.readInt();
                if (type == -1) {
                    int keycode = in.readInt();
                    boolean pressed = in.readBoolean();

                    if (pressed) {
                        win.appendLog("Key press:" + keycode);
                        robot.keyPress(keycode);
                    } else {
                        win.appendLog("Key release: " + keycode);
                        robot.keyRelease(keycode);
                    }
                } else if (type == -2) {
                    int x = in.readInt();
                    int y = in.readInt();
                    int button = in.readInt();
                    boolean pressed = in.readBoolean();

                    int buttonMask;
                    switch (button) {
                        case 1:
                            buttonMask = InputEvent.BUTTON1_DOWN_MASK;
                            break;
                        case 2:
                            buttonMask = InputEvent.BUTTON2_DOWN_MASK;
                            break;
                        case 3:
                            buttonMask = InputEvent.BUTTON3_DOWN_MASK;
                            break;
                        default:
                            buttonMask = InputEvent.BUTTON1_DOWN_MASK;
                            break;
                    }

                    if (pressed) {
                        robot.mouseMove(x, y);
                        try {
                            Thread.sleep(75);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        robot.mousePress(buttonMask);
                    } else {
                        robot.mouseRelease(buttonMask);
                    }
                } else if (type == -3) {
                    int x = in.readInt();
                    int y = in.readInt();

                    robot.mouseMove(x, y);
                }
            }
        }
    }

    public class Client {
        public String ip = "127.0.0.1";
        public int port = 9021;
        Socket cs;
        DataInputStream in;
        DataOutputStream out;
        private volatile boolean running = true;

        public Client(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        public void pressSignal(int keycode, boolean pressed) {
            try {
                if (out != null) {
                    out.writeInt(-1);
                    out.writeInt(keycode);
                    out.writeBoolean(pressed);
                    out.flush();
                }
            } catch (IOException e) {
                running = false;
            }
        }

        public void mouseClick(int x, int y, int button, boolean pressed) {
            try {
                if (out != null) {
                    out.writeInt(-2);
                    out.writeInt(x);
                    out.writeInt(y);
                    out.writeInt(button);
                    out.writeBoolean(pressed);
                    out.flush();
                }
            } catch (IOException e) {
                running = false;
            }
        }

        public void mouseMove(int x, int y) {
            try {
                if (out != null) {
                    out.writeInt(-3);
                    out.writeInt(x);
                    out.writeInt(y);
                    out.flush();
                }
            } catch (IOException e) {
                running = false;
            }
        }

        public void close() {
            running = false;
            try {
                if (cs != null && !cs.isClosed())
                    cs.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void start() {
            try {
                win.appendLog("Client started.");
                cs = new Socket(ip, port);
                in = new DataInputStream(new BufferedInputStream(cs.getInputStream()));
                out = new DataOutputStream(cs.getOutputStream());

                int serverWidth = in.readInt();
                int serverHeight = in.readInt();
                win.setRemoteScreenSize(serverWidth, serverHeight);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            win.appendLog("Connected to server.");
            while (running && !cs.isClosed()) {
                try {
                    int length = in.readInt();
                    byte[] data = new byte[length];
                    in.readFully(data);

                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                    if (img != null) {
                        win.updateImg(img);
                        win.setIsConnected(true);
                    }

                } catch (SocketException e) {
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
            System.out.println("connection reset");
            win.appendLog("Connection refused.");
            win.setIsConnected(false);
            try {
                if (in != null)
                    in.close();
            } catch (Exception e) {
            }
            try {
                if (out != null)
                    out.close();
            } catch (Exception e) {
            }
            try {
                if (cs != null)
                    cs.close();
            } catch (Exception e) {
            }
        }
    }
}
