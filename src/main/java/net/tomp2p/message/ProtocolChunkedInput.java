/*
 * @(#) $CVSHeader:  $
 *
 * Copyright (C) 2011 by Netcetera AG.
 * All rights reserved.
 *
 * The copyright to the computer program(s) herein is the property of
 * Netcetera AG, Switzerland.  The program(s) may be used and/or copied
 * only with the written permission of Netcetera AG or in accordance
 * with the terms and conditions stipulated in the agreement/contract
 * under which the program(s) have been supplied.
 *
 * @(#) $Id: codetemplates.xml,v 1.5 2004/06/29 12:49:49 hagger Exp $
 */
package net.tomp2p.message;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.stream.ChunkedInput;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;

public class ProtocolChunkedInput implements ChunkedInput
{
	private final ChannelHandlerContext ctx;
	private final Queue<ChannelBuffer> queue = new ConcurrentLinkedQueue<ChannelBuffer>();
	private ChannelBuffer channelBuffer=ChannelBuffers.dynamicBuffer();
	private volatile boolean done=false;
	private final Signature signature;
	public ProtocolChunkedInput(ChannelHandlerContext ctx, PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeyException
	{
		this.ctx=ctx;
		if(privateKey!=null) {
			signature = Signature.getInstance("SHA1withDSA");
			signature.initSign(privateKey);
		}
		else {
			signature = null;
		}
	}

	@Override
	public boolean hasNextChunk() throws Exception
	{
		return !queue.isEmpty();
	}

	@Override
	public Object nextChunk() throws Exception
	{
		ChannelBuffer channelBuffer = queue.poll();
		if(channelBuffer==null) {
			return null;
		}
		if(signature != null && channelBuffer != ChannelBuffers.EMPTY_BUFFER) {
			signature.update(channelBuffer.array(), channelBuffer.arrayOffset(), channelBuffer.arrayOffset() + channelBuffer.writerIndex());
		}
		else if(signature != null && channelBuffer == ChannelBuffers.EMPTY_BUFFER) {
			byte[] signatureData = signature.sign();
			SHA1Signature decodedSignature = new SHA1Signature();
			decodedSignature.decode(signatureData);
			channelBuffer = ChannelBuffers.wrappedBuffer(decodedSignature.getNumber1().toByteArray(), decodedSignature.getNumber2().toByteArray());
		}
		return channelBuffer;
	}
	
	public int size()
	{
		return queue.size();
	}
	
	public void addMarkerForSignature()
	{
		flush(true);
		queue.add(ChannelBuffers.EMPTY_BUFFER);
		done = true;
	}

	@Override
	public boolean isEndOfInput() throws Exception
	{
		return done && !hasNextChunk();
	}

	@Override
	public void close() throws Exception
	{
		done = true;
	}
	
	public void resume() {
		ChunkedWriteHandler chunkedWriteHandler=(ChunkedWriteHandler)ctx.getPipeline().get("streamer");
		chunkedWriteHandler.resumeTransfer();
	}

	public void copyToCurrent(byte[] byteArray)
	{
		if(done) return;
		channelBuffer.writeBytes(byteArray);
	}
	
	public void copyToCurrent(int size)
	{
		if(done) return;
		channelBuffer.writeInt(size);
	}
	
	public void copyToCurrent(byte size)
	{
		if(done) return;
		channelBuffer.writeByte(size);
	}
	
	public void copyToCurrent(long long1)
	{
		if(done) return;
		channelBuffer.writeLong(long1);
	}
	
	public void copyToCurrent(short short1)
	{
		if(done) return;
		channelBuffer.writeShort(short1);
	}
	
	public void copyToCurrent(ChannelBuffer slice)
	{
		if(done) return;
		flush(false);
		queue.add(slice);
	}
	
	public void copyToCurrent(byte[] array, int offset, int length)
	{
		if(done) return;
		flush(false);
		queue.add(ChannelBuffers.wrappedBuffer(array, offset, length));
	}
	
	public void flush(boolean last)
	{
		if(channelBuffer.writerIndex() > 0)
		{
			queue.add(channelBuffer);
			if(!last) {
				channelBuffer=ChannelBuffers.dynamicBuffer();
			}
		}
		if(last)
			done = true;
	}

}
