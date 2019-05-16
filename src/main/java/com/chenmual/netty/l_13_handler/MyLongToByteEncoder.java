package com.chenmual.netty.l_13_handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
//import io.netty.handler.codec.FixedLengthFrameDecoder;
//import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

public class MyLongToByteEncoder extends MessageToByteEncoder<Long> {
	@Override
	protected void encode(ChannelHandlerContext ctx,Long msg,ByteBuf out) throws Exception {
		System.out.println("encode");
		System.out.println(msg);
		out.writeLong(msg);
//		LengthFieldBasedFrameDecoder
	}
}
