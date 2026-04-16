package anydeskJava;

import java.awt.AWTException;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

public class SocketManager {
    Window win;
    Robot robot;
    Rectangle screenRect;

    static {
        FFmpegLogCallback.set();
    }

    public SocketManager(Window win) {
        this.win = win;
        this.screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        try {
            this.robot = new Robot();
        } catch (AWTException e) {}
    }

    public static boolean isWayland() {
        return "wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"));
    }

    private BufferedImage takeScreenshotWayland() {
        try {
            Process process = new ProcessBuilder("grim", "-").start();
            byte[] imageBytes = process.getInputStream().readAllBytes();
            process.waitFor();
            if (imageBytes.length == 0) {
                System.out.println("screenshot stuff failed");
                return null;
            }
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (Exception e) { 
            return null; 
        }
    }

    public class Server {
        private FFmpegFrameRecorder recorder;
        private Java2DFrameConverter converter;

        public Server(String clientIp, int port) {
            converter = new Java2DFrameConverter();
            recorder = new FFmpegFrameRecorder("tcp://" + clientIp + ":" + port, 1280, 720);
            
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mpegts");
            recorder.setFrameRate(30);
            recorder.setGopSize(15);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setVideoBitrate(1500000);
            
            recorder.setVideoOption("preset", "ultrafast");
            recorder.setVideoOption("tune", "zerolatency");
            recorder.setVideoOption("bf", "0");
            recorder.setOption("flush_packets", "1");
            
            new Thread(() -> {
                try {
                    Thread.sleep(200);
                    recorder.start();
                    System.out.println("Server started");
                    
                    int frameCount = 0;
                    while (true) {
                        BufferedImage img = isWayland() ? takeScreenshotWayland() : robot.createScreenCapture(screenRect);
                        if (img != null) {
                            BufferedImage scaled = new BufferedImage(1280, 720, BufferedImage.TYPE_3BYTE_BGR);
                            Graphics2D g = scaled.createGraphics();
                            g.drawImage(img, 0, 0, 1280, 720, null);
                            g.dispose();
                            
                            Frame frame = converter.convert(scaled);
                            if (frame != null) {
                                recorder.record(frame);
                                frameCount++;
                                if (frameCount % 30 == 0) System.out.println("Sent frames: " + frameCount);
                            }
                        }
                    }
                } catch (Exception e) { 
                    System.err.println("Server error: " + e.getMessage());
                    e.printStackTrace(); 
                }
            }).start();
        }
    }

    public class Client {
        private FFmpegFrameGrabber grabber;
        private Java2DFrameConverter converter;

        public Client(int port) {
            converter = new Java2DFrameConverter();
            grabber = new FFmpegFrameGrabber("tcp://:" + port + "?listen=1");
            grabber.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            grabber.setFormat("mpegts");
            grabber.setOption("fflags", "nobuffer");
            grabber.setOption("probesize", "3276800");
            grabber.setOption("analyzeduration", "1");
            grabber.setOption("threads", "1");

            new Thread(() -> {
                try {
                    System.out.println("Client listening on port " + port);
                    grabber.start();
                    System.out.println("Grabber started successfully");
                    Thread.sleep(100);
                    win.setIsConnected(true);
                    
                    int receivedCount = 0;
                    while (true) {
                        Frame frame = grabber.grabImage();
                        if (frame != null) {
                            receivedCount++;
                            if (receivedCount % 30 == 0) System.out.println("Received frames: " + receivedCount);
                            
                            BufferedImage img = converter.convert(frame);
                            if (img != null) {
                                java.awt.EventQueue.invokeLater(() -> win.updateImg(img));
                            } else {
                                System.out.println("Converter returned null for frame");
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Client error: " + e.getMessage());
                    e.printStackTrace();
                    win.setIsConnected(false);
                } finally {
                    try { grabber.stop(); } catch (Exception ex) {}
                }
            }).start();
        }
    }

    public void initServer(String clientIp, int port) { new Server(clientIp, port); }
    public void initClient(int port) { new Client(port); }
}