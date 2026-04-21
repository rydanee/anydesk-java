import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.awt.AWTException;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class SocketManager {
    Window win;
    public SocketManager(Window win) {
        this.win = win;
        screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            try {
                robot = new Robot();
            } catch (AWTException e) {
        }
    }

    Robot robot;
    Rectangle screenRect;

    public Server initServer(int port) {
            try {
                return new Server(port);
            } catch (IOException e) { System.out.println("system init failed"); return null; }
        }

    public Client initClient(String ip, int port) {
        Client cl = new Client(ip, port);
        return cl;
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
            if (imageBytes.length == 0) return null;

            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null) return null;

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
        param.setCompressionQuality(0.6f);
        
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
            ios.flush();
        }
        writer.dispose();
        
    }


    BufferedImage takeScreenshot() {
        BufferedImage fullImg = robot.createScreenCapture(screenRect);

        BufferedImage scaledImg = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);

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

        public Server(int port) throws IOException {
            ss = new ServerSocket(port);
            
            System.out.println("server started");

            s = ss.accept();

            System.out.println("client connected");

            in = new DataInputStream(
                new BufferedInputStream(s.getInputStream()));

            out = new DataOutputStream(
                new BufferedOutputStream(s.getOutputStream())
            );
            
            new Thread(new Runnable() {
                BufferedImage img = null;

                @Override
                public void run() {
                    while (s.isConnected()) {
                        try {
                            if (isWayland()) {
                                img = takeScreenshotGrim();
                            } else img = takeScreenshot();
                            
                            if (img == null) continue;

                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            writeJpgOptimized(img, baos);

                            byte[] bytes = baos.toByteArray();

                            if (in.available() >= 12) {
                                int type = in.readInt();
                                if (type == -1) {
                                    int keycode = in.readInt();
                                    boolean pressed = in.readBoolean();

                                    if (pressed) {
                                        robot.keyPress(keycode);
                                    } else {
                                        robot.keyRelease(keycode);
                                    }
                                }
                            }

                            out.writeInt(bytes.length); 

                            out.write(bytes);
                            out.flush();

                            Thread.sleep(30);
                        } catch (Exception e) {
                        }
                    }
                    try {
                        s.close();
                        in.close();
                    } catch (IOException e) {
                    }
                }
            }).start();
        }
    }

    public class Client {
        public String ip = "127.0.0.1";
        public int port = 9021;
        Socket cs;
        public boolean stop = false;
        DataInputStream in;
        DataOutputStream out;

        public void pressSignal(int keycode, boolean pressed) {
            try {
                out.writeInt(-1);
                out.writeInt(keycode);
                out.writeBoolean(pressed);
                out.flush();
            } catch (IOException e) {}
        }

        public Client(String ip, int port) {    

            try {
                cs = new Socket(ip, port);
                in = new DataInputStream(new BufferedInputStream(cs.getInputStream()));
                out = new DataOutputStream(cs.getOutputStream());


            } catch (IOException e) {
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (cs.isConnected()) {
                        win.setIsConnected(true);
                        try {
                            int length = in.readInt(); 
                            byte[] data = new byte[length];
                            in.readFully(data);

                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                            if(img != null) {
                                img.getGraphics().dispose(); 
                                win.updateImg(img);
                                win.setIsConnected(true);
                            }


                        } catch (Exception e) {}
                    }
                    try {
                        win.setIsConnected(false);
                        cs.close();
                        in.close();
                        out.close();
                    } catch (IOException e) {}
                }
            }).start();
        }

    }
}
