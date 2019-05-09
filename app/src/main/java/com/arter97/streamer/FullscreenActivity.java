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

public class FullscreenActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final int portNumber = 5567;
    private static MediaCodec decoder = null;
    private PlayerThread mPlayer = null;

    private static void setupDecoder(Surface decoderSurface) throws IOException {
        decoder = MediaCodec.createDecoderByType("video/avc");
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", 1080, 1920);
        decoder.configure(format, decoderSurface, null, 0);
        decoder.start();
    }

    private static void listen() throws IOException {
        ServerSocket serverSocket = new ServerSocket(portNumber);
        serverSocket.setReceiveBufferSize(1048576);
        Socket clientSocket = serverSocket.accept();
        clientSocket.setReceiveBufferSize(1048576);
        clientSocket.setSendBufferSize(1048576);
        //PrintWriter out =
          //      new PrintWriter(clientSocket.getOutputStream(), true);
        //BufferedReader in = new BufferedReader(
          //      new InputStreamReader(clientSocket.getInputStream()));

        //DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        //InputStream in = clientSocket.getInputStream();
        //BufferedInputStream in
        InputStream in = new BufferedInputStream(clientSocket.getInputStream(), 64*1024);

        byte buf[] = new byte[1048576];
        int total = 0;
        int read;
        int inputIndex, outIndex;
        ByteBuffer inputBuffer;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while ((read = in.read(buf, 0, 1048576)) != -1) {
            Log.e(Streamer.APP_NAME, "Read " + read + " bytes");

            inputIndex = decoder.dequeueInputBuffer(10000);
            if (inputIndex < 0)
               continue;

            inputBuffer = decoder.getInputBuffer(inputIndex);
            inputBuffer.put(buf, 0, read);

            /*
            inputBuffer = decoder.getInputBuffers()[inputIndex];
            inputBuffer.put(buf, 0, read);
            */

            Log.e(Streamer.APP_NAME, "Total: " + total);
            decoder.queueInputBuffer(inputIndex, 0, read, 0, 0);

            outIndex = decoder.dequeueOutputBuffer(info, 0);

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
                    break;
            }

            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(Streamer.APP_NAME, "BUFFER_FLAG_END_OF_STREAM");
                break;
            }
        }

        decoder.stop();
        decoder.release();

        in.close();
        //out.close();
        clientSocket.close();
        serverSocket.close();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

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

                Utils.sleep(1000);
            }
        }
    }
}
