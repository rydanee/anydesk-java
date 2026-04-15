package anydeskJava;

import java.awt.AWTException;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

public class SocketManager {
    Window win;
    Robot robot;
    Rectangle screenRect;

    public SocketManager(Window win) {
        this.win = win;
        this.screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        try {
            this.robot = new Robot();
        } catch (AWTException e) {}
    }

    public class Server {
        private FFmpegFrameRecorder recorder;
        private Java2DFrameConverter converter;

        public Server(String clientIp, int port) {
            converter = new Java2DFrameConverter();
            recorder = new FFmpegFrameRecorder("udp://" + clientIp + ":" + port, 1280, 720);
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mpegts");
            recorder.setFrameRate(60);
            recorder.setVideoOption("preset", "ultrafast");
            recorder.setVideoOption("tune", "zerolatency");
            recorder.setVideoBitrate(2000000);

            new Thread(() -> {
                try {
                    recorder.start();
                    while (true) {
                        BufferedImage img = robot.createScreenCapture(screenRect);
                        BufferedImage scaled = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_BGR);
                        Graphics2D g = scaled.createGraphics();
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                        g.drawImage(img, 0, 0, 1280, 720, null);
                        g.dispose();

                        recorder.record(converter.convert(scaled));
                    }
                } catch (Exception e) {
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
            grabber = new FFmpegFrameGrabber("udp://127.0.0.1:" + port + "?listen");
            grabber.setFormat("mpegts");
            grabber.setOption("fflags", "nobuffer");
            grabber.setOption("flags", "low_delay");

            new Thread(() -> {
                try {
                    grabber.start();
                    win.setIsConnected(true);
                    while (true) {
                        Frame frame = grabber.grabImage();
                        if (frame != null) {
                            BufferedImage img = converter.convert(frame);
                            win.updateImg(img);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    win.setIsConnected(false);
                }
            }).start();
        }
    }

    public Server initServer(String clientIp, int port) {
        return new Server(clientIp, port);
    }

    public Client initClient(int port) {
        return new Client(port);
    }
}