package com.chenmual.netty.l_09_aio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

/**
 * @author LiuXianfa
 * @email xianfaliu@newbanker.cn
 * @date 2020/2/28 16:33
 */
public class AioClient {

    /**
     * 客户端的处理方式与服务端基本类似，首先是连接服务器。 连接完成后，通过ConnectionCompletionHandler进行后续处理，
     * 这里首先是异步往服务器写入数据，写入完成后监听服务器的数据响应，最后读取服务器数据并打印。
     *
     * @param args
     * @throws IOException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        // 创建一个客户端AsynchronousSocketChannel
        AsynchronousSocketChannel clientChannel = AsynchronousSocketChannel.open();
        // 异步连接服务器，并且将连接后的处理交由ConnectCompletionHandler进行
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8080);
        clientChannel.connect(address, clientChannel, new ConnectCompletionHandler(latch));

        latch.await();
    }

    static class ConnectCompletionHandler implements CompletionHandler<Void, AsynchronousSocketChannel> {
        private CountDownLatch latch;

        public ConnectCompletionHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void completed(Void result, AsynchronousSocketChannel channel) {
            ByteBuffer buffer = ByteBuffer.wrap("Hello, I'm client. ".getBytes());
            // 连接完成后，往Channel中写入数据，以发送给服务器
            channel.write(buffer, buffer, new AioClient.WriteCompletionHandler(channel, latch));
        }

        @Override
        public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
            // ignore
        }
    }


    static class WriteCompletionHandler implements CompletionHandler<Integer, ByteBuffer> {
        private AsynchronousSocketChannel channel;
        private CountDownLatch latch;

        public WriteCompletionHandler(AsynchronousSocketChannel channel, CountDownLatch latch) {
            this.channel = channel;
            this.latch = latch;
        }

        @Override
        public void completed(Integer result, ByteBuffer buffer) {
            if (buffer.hasRemaining()) {
                channel.write(buffer, buffer, this);
            } else {
                ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                // 写入完成后，这里异步监听服务器的响应数据
                channel.read(readBuffer, readBuffer, new AioClient.ReadCompletionHandler(channel, latch));
            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            try {
                channel.close();
                latch.countDown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    static class ReadCompletionHandler implements CompletionHandler<Integer, ByteBuffer> {
        private AsynchronousSocketChannel channel;
        private CountDownLatch latch;

        public ReadCompletionHandler(AsynchronousSocketChannel channel, CountDownLatch latch) {
            this.channel = channel;
            this.latch = latch;
        }

        @Override
        public void completed(Integer result, ByteBuffer readBuffer) {
            readBuffer.flip();
            byte[] bytes = new byte[readBuffer.remaining()];
            readBuffer.get(bytes);  // 读取服务器响应的数据，并且进行处理
            try {
                System.out.println("Client receives message: " + new String(bytes, StandardCharsets.UTF_8));
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            try {
                channel.close();
                latch.countDown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}






