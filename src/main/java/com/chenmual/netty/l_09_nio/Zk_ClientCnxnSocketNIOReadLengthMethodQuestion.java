package com.chenmual.netty.l_09_nio;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Random;
import java.util.Set;

/**
 * <pre>
 * 在阅读zk源码时，对于ClientCnxnSocketNIO#readLength()方法的疑问：
 *
 * readLength()方法只读取了数据包总长度length，并且创建了一个length长度的byteBuffer；
 * 但没有把socket中数据读取到incomingBuffer中。这是什么原理的？
 *
 * </pre>
 *
 * @author LiuXianfa
 * @email xianfaliu@newbanker.cn
 * @date 2020/3/30 15:37
 */
public class Zk_ClientCnxnSocketNIOReadLengthMethodQuestion {

    private static final int server_port = 8080;

    static class Server {
        private static ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
        private static ByteBuffer incomingBuffer = lengthBuffer;
        private static int packetLength = -1;

        public static void main(String[] args) throws IOException {
            Selector selector = Selector.open();
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            // 注意：服务端暴露端口，是使用serverSocket#bind方法。
            ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.bind(new InetSocketAddress(server_port));

            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);


            while (true) {
                selector.select(1000); // select是阻塞的。设置一个超时时间，超时之后仍然没有接收关心的事件，就不再等待。
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
                        // 如果上面sock.read(incomingBuffer)读完一段数据后，byteBuffer没有剩余空间了:
                        if (!incomingBuffer.hasRemaining()) {
                            incomingBuffer.flip();

                            if (incomingBuffer == lengthBuffer) {
                                // 相等，则说明读取的是数据包长度。客户端发来的数据包格式：【数据包总长度+真实数据】
                                readLength();
                                // todo:  【对于readLength()方法的疑问】：readLength()只是把数据包长度读出来，并根据长度分配一个用于接收真实数据的buffer,
                                // todo:    但是读取真实数据，需要等到下一次selector.select()了。nio还能这样玩？

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
         * 一次客户端请求读取完毕之后，进行数据重置，等待下一条消息的读取。
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

            System.out.println("client 发来数据总长度为：" + packetLength);
            incomingBuffer = ByteBuffer.allocate(packetLength);
        }

    }

    static class Client {

        private static Random random = new Random(25);


        public static void main(String[] args) throws IOException {
            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);

            socketChannel.connect(new InetSocketAddress("127.0.0.1", server_port));

            Selector selector = Selector.open();

            socketChannel.register(selector, SelectionKey.OP_CONNECT);


            // 客户端只发送数据
            while (true) {
                selector.select(1000);
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                for (SelectionKey selectionKey : selectionKeys) {
                    if (selectionKey.isConnectable()) {
                        SocketChannel client = (SocketChannel) selectionKey.channel();
                        if (client.isConnectionPending()) {
                            // 等待连接真正建立好
                            client.finishConnect();

                            new Thread(() -> {
                                while (true) {
                                    writeMessageToServer(selectionKey);
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }).start();

                        }


                    }
                } // -- end   for selectionKeys
                selectionKeys.clear();
            }// -- end wile true
        }

        private static void writeMessageToServer(SelectionKey selectionKey) {
            SocketChannel channel = (SocketChannel) selectionKey.channel();
            // 生成随机长度的随机字符串。作为客户端发送给服务端的【真实数据】
            String dataMessage = getRandomString(random.nextInt(100));
            System.out.println("client send : " + dataMessage);
            byte[] dataMessageBytes = dataMessage.getBytes(Charset.forName("UTF-8"));

            ByteBuffer outgoingBuffer = ByteBuffer.allocate(4 + dataMessageBytes.length);
            outgoingBuffer.putInt(dataMessageBytes.length); // 数据长度
            outgoingBuffer.put(dataMessageBytes); // 真实数据
            // 注意：这里把数据put到byteBuffer中后，需要flip一下：否则客户端不会发送任何数据。
            outgoingBuffer.flip();

            try {
                // socket发送数据。
                channel.write(outgoingBuffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
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
