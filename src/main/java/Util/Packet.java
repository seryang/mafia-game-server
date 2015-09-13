package Util;


import java.nio.channels.SocketChannel;

import net.sf.json.JSONObject;

/**
 * Created by un on 15. 8. 7..
 */
public class Packet {
	public SocketChannel channel;
	public JSONObject data;

	public Packet(SocketChannel channel){
		this.channel = channel;
	}
	
	public Packet(SocketChannel channel, JSONObject data){
		this.channel = channel;
		this.data   = data;
	}
}
