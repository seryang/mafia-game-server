package Util;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import net.sf.json.JSONObject;

public class PacketHandler {
	SocketChannel channel;
	JSONObject json;

	public void bufferWrite(Packet packet) throws IOException{
		json = new JSONObject();
		json = packet.data;

		channel = packet.channel;

		ByteBuffer buffer = ByteBuffer.allocate(json.toString().trim().getBytes().length + 4);

		buffer.putInt(json.toString().trim().getBytes().length); // 보낼 데이터의 크기를 보냄. 4byte
		buffer.put(json.toString().getBytes());
		buffer.clear();

		channel.write(buffer);
	}

	public Packet bufferRead(Packet packet) throws IOException{
		String readData = null;
		channel = packet.channel;

		ByteBuffer buffer = ByteBuffer.allocate(4);
		channel.read(buffer);
		buffer.clear();

		int size = buffer.getInt();

		ByteBuffer readBuffer = ByteBuffer.allocate(size);
		channel.read(readBuffer);
		readBuffer.clear();
		readData = new String(readBuffer.array()).trim();
		packet.data = JSONObject.fromObject(readData);

		return packet;
	}
}
