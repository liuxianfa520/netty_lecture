package com.chenmual.netty.l_09_aio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

import io.netty.util.CharsetUtil;

/**
 * 对于AIO模式，其是在jdk 1.7中加入的，主要原因是NIO模式代码编写非常复杂，并且容易出错。 AIO本质上还是使用的NIO的多路复用来实现的，
 * 只不过在模型上其使用的是一种事件回调的方式处理各个事件， 这种方式更加符合NIO异步模型的概念，并且在编码难易程度上比NIO要小很多。
 *
 * 在AIO中，所有的操作都是异步执行的，而每个事件都是通过一个回调函数来进行的， 这里也就是一个 {@link CompletionHandler} 对象。
 *
 * @author LiuXianfa
 * @email xianfaliu@newbanker.cn
 * @date 2020/2/28 16:29
 */
public class AioServer {


    public static void main(String[] args) throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        // 创建一个异步的ServerSocketChannel，然后绑定8080端口，并且处理其accept事件
        AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
        server.bind(new InetSocketAddress(8080));
        server.accept(server, new AcceptCompletionHandler());

        latch.await();
    }

    /**
     * 这里对于对象的传递，是通过Attachement的方式进行的，这样就可以将原始Channel传递到各个异步回调函数使用
     */
    static class AcceptCompletionHandler implements CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {
        @Override
        public void completed(AsynchronousSocketChannel channel, AsynchronousServerSocketChannel server) {
            // 在处理了一个accept事件之后，继续递归监听下一个accept事件
            server.accept(server, this);


            ByteBuffer buffer = ByteBuffer.allocate(1024);
            channel.read(buffer, buffer, new ReadCompletionHandler(channel));
        }

        @Override
        public void failed(Throwable exc, AsynchronousServerSocketChannel attachment) {
            exc.printStackTrace();
        }
    }

    static class ReadCompletionHandler implements CompletionHandler<Integer, ByteBuffer> {

        private AsynchronousSocketChannel channel;

        public ReadCompletionHandler(AsynchronousSocketChannel channel) {
            this.channel = channel;
        }

        @Override
        public void completed(Integer result, ByteBuffer readBuffer) {
            readBuffer.flip();
            byte[] body = new byte[readBuffer.remaining()];
            readBuffer.get(body);  // 读取客户端发送的数据
            try {
                System.out.println("Server receives message: " + new String(body, CharsetUtil.UTF_8));

                ByteBuffer writeBuffer = ByteBuffer.wrap(("Hello, I'm server. It's: " + new Date()).getBytes());
                // 异步的写入服务端的数据，这里也是通过一个CompletionHandler来异步的写入数据
                channel.write(writeBuffer, writeBuffer, new WriteCompletionHandler(channel));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            try {
                channel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class WriteCompletionHandler implements CompletionHandler<Integer, ByteBuffer> {
        private AsynchronousSocketChannel channel;

        public WriteCompletionHandler(AsynchronousSocketChannel channel) {
            this.channel = channel;
        }

        @Override
        public void completed(Integer result, ByteBuffer writeBuffer) {
            if (writeBuffer.hasRemaining()) {
                // 将数据写入到服务器channel中
                channel.write(writeBuffer, writeBuffer, this);
            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            try {
                channel.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }


}
