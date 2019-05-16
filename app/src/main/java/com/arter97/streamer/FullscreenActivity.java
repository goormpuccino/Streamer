package com.arter97.streamer;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import com.google.common.primitives.Bytes;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.spec.ECField;
import java.util.Arrays;

public class FullscreenActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final int portNumber = 5567;
    private static MediaCodec decoder = null;
    private PlayerThread mPlayer = null;

    private static ServerSocket serverSocket = null;
    private static Socket clientSocket = null;
    private static InputStream in = null;

    private static void setupDecoder(Surface decoderSurface) throws IOException {
        decoder = MediaCodec.createDecoderByType("video/avc");
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", 1080, 2280);
        decoder.configure(format, decoderSurface, null, 0);
        decoder.start();
    }

    private static void listen() throws IOException {
        serverSocket = new ServerSocket(portNumber);
        serverSocket.setReceiveBufferSize(65536);
        clientSocket = serverSocket.accept();
        clientSocket.setReceiveBufferSize(65536);
        clientSocket.setSendBufferSize(65536);
        //PrintWriter out =
          //      new PrintWriter(clientSocket.getOutputStream(), true);
        //BufferedReader in = new BufferedReader(
          //      new InputStreamReader(clientSocket.getInputStream()));

        //DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        //InputStream in = clientSocket.getInputStream();
        //BufferedInputStream in
        in = new BufferedInputStream(clientSocket.getInputStream());

        byte buf[] = new byte[40 * 1048576]; // 20 Mbps * 2
        final byte nal[] = { 0x00, 0x00, 0x00, 0x01};
        int total = 4;
        int read = 0;
        int inputIndex, outIndex;
        int i = 0;
        ByteBuffer inputBuffer;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        buf[0] = 0x00;
        buf[1] = 0x00;
        buf[2] = 0x00;
        buf[3] = 0x01;
        while ((read = in.read(buf, total, 1)) != -1) {
            total += read;
            if (buf[total - 4] != 0x00 ||
                    buf[total - 3] != 0x00 ||
                    buf[total - 2] != 0x00 ||
                    buf[total - 1] != 0x01)
                continue;

            Log.e(Streamer.APP_NAME, "Read " + total + " bytes");

            do {
                inputIndex = decoder.dequeueInputBuffer(10000);
            } while (inputIndex < 0);

            inputBuffer = decoder.getInputBuffer(inputIndex);
            inputBuffer.put(buf, 0, total);

            /*
            inputBuffer = decoder.getInputBuffers()[inputIndex];
            inputBuffer.put(buf, 0, read);
            */

            decoder.queueInputBuffer(inputIndex, 0, total, 0, 0);

            //Arrays.fill(buf, (byte)0);
            buf[0] = 0x00;
            buf[1] = 0x00;
            buf[2] = 0x00;
            buf[3] = 0x01;
            total = 4;

            outIndex = decoder.dequeueOutputBuffer(info, 10000);

            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(Streamer.APP_NAME, "INFO_OUTPUT_FORMAT_CHANGED format : " + decoder.getOutputFormat());
                    break;

                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d(Streamer.APP_NAME, "INFO_TRY_AGAIN_LATER");
                    break;

                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(Streamer.APP_NAME, "INFO_OUTPUT_BUFFERS_CHANGED");
                    break;

                default:
                    Log.d(Streamer.APP_NAME, "Rendering: " + outIndex);
                    decoder.releaseOutputBuffer(outIndex, true);
                    //decoder.flush();
                    break;
            }

            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(Streamer.APP_NAME, "BUFFER_FLAG_END_OF_STREAM");
                break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        SurfaceView sv = findViewById(R.id.surfaceView);
        sv.getHolder().addCallback(this);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mPlayer == null) {
            mPlayer = new PlayerThread(holder.getSurface());
            mPlayer.start();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mPlayer != null) {
            mPlayer.interrupt();
        }
    }

    private class PlayerThread extends Thread {
        private Surface surface;

        public PlayerThread(Surface surface) {
            this.surface = surface;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    setupDecoder(surface);
                    listen();
                } catch (Exception e) {
                    Log.e(Streamer.APP_NAME, "Exception while listening!", e);
                }

                try {
                    decoder.stop();
                    decoder.release();

                    in.close();
                    clientSocket.close();
                    serverSocket.close();
                } catch (Exception e) {
                    // Ignore
                }

                Utils.sleep(1000);
            }
        }
    }
}
