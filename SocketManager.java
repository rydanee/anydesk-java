import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class SocketManager {

    Window win;
    public SocketManager(Window win) {this.win = win;}

    Robot robot;
    Rectangle screenRect;

    public Server initServer(int port) {
            Server serv = null;
            try {
                serv = new Server(port);
            } catch (IOException e) {
            }
            return serv;
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
            InputStream is = process.getInputStream();
            BufferedImage image = ImageIO.read(is);
            process.waitFor();
            return image;
        } catch (Exception e) {
            return null;
        }
    }

    BufferedImage takeScreenshot() {
        BufferedImage img = robot.createScreenCapture(screenRect);

        return img;
    }

    public class Server {
        public int port = 9021;
        Socket s;
        ServerSocket ss;
        DataInputStream in;

        public Server(int port) throws IOException {
            ss = new ServerSocket(port);
            
            System.out.println("server started");

            s = ss.accept();

            System.out.println("client connected");

            in = new DataInputStream(
                new BufferedInputStream(s.getInputStream()));
            
            new Thread(new Runnable() {
                String m = "";
                BufferedImage img = null;

                @Override
                public void run() {
                    while (s.isConnected()) {
                        try {
                            if (isWayland()) {
                                img = takeScreenshotGrim();
                            } else img = takeScreenshot();
                            ImageIO.write(img, "PNG", s.getOutputStream());
                        } catch (IOException e) {
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

        public Client(String ip, int port) {    
            screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            try {
                robot = new Robot();
            } catch (AWTException e) {
            }

            try {
                cs = new Socket(ip, port);
                in = new DataInputStream(System.in);
                out = new DataOutputStream(cs.getOutputStream());


            } catch (IOException e) {
            }

            new Thread(new Runnable() {
                String m = "";

                @Override
                public void run() {
                    while (cs.isConnected()) {
                        win.setIsConnected(true);
                        try {
                            BufferedImage img = ImageIO.read(cs.getInputStream());
                            win.updateImg(img);
                        } catch (IOException e) {
                        }
                    }
                    try {
                        win.setIsConnected(false);
                        cs.close();
                        in.close();
                        out.close();
                    } catch (IOException e) {
                    }
                }
            }).start();
        }

    }
}
