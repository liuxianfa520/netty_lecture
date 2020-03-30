package com.chenmual.netty.l_09_nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.Set;

/**
 *
 * 在阅读zk源码时，对于ClientCnxnSocketNIO#readLength()方法的疑问：
 *
 * readLength()方法只读取了数据包总长度length，并且创建了一个length长度的byteBuffer；
 * 但没有把socket中数据读取到incomingBuffer中。这是什么原理的？
 *
 * @author LiuXianfa
 * @email xianfaliu@newbanker.cn
 * @date 2020/3/30 15:37
 */
public class Zk_ClientCnxnSocketNIOReadLengthMethodQuestion {

    private static final int server_port = 8080;

    static class Server {
        private static final Logger logger = LoggerFactory.getLogger(Server.class);

        private static ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
        private static ByteBuffer incomingBuffer = lengthBuffer;
        private static int packetLength = -1;

        public static void main(String[] args) throws IOException {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(server_port));
            serverSocketChannel.configureBlocking(false);


            Selector selector = Selector.open();


            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);


            while (true) {
                int select = selector.select();
                logger.info("存在{}个感兴趣的事件。", select);

                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey selectionKey : selectionKeys) {

                    if (selectionKey.isAcceptable()) {
                        ServerSocketChannel channel = (ServerSocketChannel) selectionKey.channel();
                        SocketChannel clientSocket = channel.accept();
                        clientSocket.configureBlocking(false);
                        clientSocket.register(selector, SelectionKey.OP_READ, clientSocket);
                    } else if (selectionKey.isReadable()) {
                        SocketChannel clientSocket = (SocketChannel) selectionKey.attachment();

                        int readCount = clientSocket.read(incomingBuffer);
                        if (readCount < 0) {
                            throw new RuntimeException("socket中读取数据长度为【" + readCount + "】，如果是-1，则说明channel has reached end-of-stream");
                        }
                        // todo:为何要加上这句话？
                        if (!incomingBuffer.hasRemaining()) {
                            incomingBuffer.flip();

                            if (incomingBuffer == lengthBuffer) {
                                // 说明读取的是数据包长度。客户端发来的数据包格式：【数据包总长度+真实数据】
                                readLength();

                            } else {
                                // 否则的话，就是读取 真实数据
                                byte[] bytes = new byte[packetLength];
                                incomingBuffer.get(bytes);
                                System.out.println("接收到client发来的数据：【" + new String(bytes) + "】");
                                reset();
                            }

                        }
                    }
                }
                selectionKeys.clear();
            }

        }

        /**
         * 一次客户端请求读取完毕之后，进行数据重置，等待下一次读取。
         */
        private static void reset() {
            packetLength = -1;
            lengthBuffer.clear();
            incomingBuffer = lengthBuffer;
        }

        private static void readLength() {
            packetLength = incomingBuffer.getInt();
            if (packetLength < 0) {
                throw new RuntimeException("客户端发来数据包总长度，不能小于0");
            }

            logger.info("client 发来数据总长度为：{}", packetLength);
            incomingBuffer = ByteBuffer.allocate(packetLength);
        }

    }

    static class Client {
        private static final Logger logger = LoggerFactory.getLogger(Client.class);

        private static Random random = new Random(25);


        public static void main(String[] args) throws IOException {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);

            socketChannel.connect(new InetSocketAddress("localhost", server_port));

            Selector selector = Selector.open();

            socketChannel.register(selector, SelectionKey.OP_CONNECT);


            // 客户端只发送数据
            while (true) {
                int select = selector.select();
                logger.info("当前有{}个关心的事件被触发。", select);
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey selectionKey : selectionKeys) {
                    if (selectionKey.isConnectable()) {

                        SocketChannel channel = (SocketChannel) selectionKey.channel();
                        channel.register(selector, SelectionKey.OP_WRITE);

                        // todo:register和下面这句有什么区别？
                        // selectionKey.interestOps(SelectionKey.OP_WRITE);

                    } else if (selectionKey.isWritable()) { // todo:可写事件，是在什么情况下被触发的？

                        SocketChannel channel = (SocketChannel) selectionKey.channel();
                        // 生成随机长度的随机字符串。作为客户端发送给服务端的【真实数据】
                        String dataMessage = getRandomString(random.nextInt(100));
                        byte[] dataMessageBytes = dataMessage.getBytes(Charset.forName("UTF-8"));

                        ByteBuffer outgoingBuffer = ByteBuffer.allocate(4 + dataMessageBytes.length);
                        outgoingBuffer.putInt(dataMessageBytes.length); // 数据长度
                        outgoingBuffer.put(dataMessageBytes); // 真实数据

                        // socket发送数据。
                        channel.write(outgoingBuffer);
                    }
                } // -- end   for selectionKeys
                selectionKeys.clear();
            }// -- end wile true
        }

        /**
         * 生成指定length的随机字符串（A-Z，a-z，0-9）
         *
         * @param length
         * @return
         */
        private static String getRandomString(int length) {
            String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            Random random = new Random();
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < length; i++) {
                int number = random.nextInt(str.length());
                sb.append(str.charAt(number));
            }
            return sb.toString();
        }


    }
}
