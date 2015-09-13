package view;

import static gateWay.GateWay.LB_SERVER_IP;
import static gateWay.GateWay.LB_SERVER_PORT;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import Util.Packet;
import Util.PacketHandler;
import net.sf.json.JSONObject;

/**
 * 
 * @author Mr.Choe
 *
 */
public class Dummy2 {
	private SocketChannel lbSocketChannel;
	private static SocketChannel lobbySocketChannel;
	private JSONObject json = new JSONObject();
	private JSONObject json1 = new JSONObject();
	static int i = 0;


	public static void main(String[] args) {
		Dummy2 client = new Dummy2();
		try {
			while(i < 1){
				++i;
				client.lobbyConnect();

				System.out.println("");
				System.out.println(String.valueOf(i) + "___________________");
				client.sendNotice();
				client.sendNotice();
				client.sendNotice();
				client.sendWhisper();
				client.sendWhisper();
				client.sendWhisper();
			}
			while(true){
				PacketHandler chatHandler = new PacketHandler();
				Packet receivePacket;
				receivePacket = chatHandler.bufferRead(new Packet(lobbySocketChannel));
				System.out.println("receive : " + receivePacket.data);
			}

		} catch (Exception e){
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	// 로비에 Connection
	public void lobbyConnect() throws IOException, InterruptedException {

		/*
		 *  Client가 LB Server에 접속
		 */
		lbSocketChannel = SocketChannel.open(new InetSocketAddress(LB_SERVER_IP, LB_SERVER_PORT));
		System.out.println("User가 LB로 접속할 때의 정보 : " + lbSocketChannel.socket());

		PacketHandler handler = new PacketHandler();

		json.put("type","LOGIN");

		handler.bufferWrite(new Packet(lbSocketChannel,json));

		/*
		 *  LB Server에서 LobbyServer의 IP와 Port 받아오는 부분
		 */
		Packet packet = handler.bufferRead(new Packet(lbSocketChannel));
		//-------------------------------------------------------------------로비 연결상태 ---------------------------------

		lobbySocketChannel = SocketChannel.open(new InetSocketAddress ( packet.data.get("host_ip").toString() ,
				Integer.parseInt( packet.data.get("port").toString()) ));

		System.out.println("User가 LobbyServer로 접속할 때의 정보 : " + lobbySocketChannel.socket());

		// Lobby 서버에게 내가 로그인 함을 알림.
		json1.put("type","LOGIN");
		json1.put("user_num", String.valueOf(i));

		handler.bufferWrite(new Packet(lobbySocketChannel, json1));


		packet = new Packet(lobbySocketChannel);
		handler = new PacketHandler();
		packet = handler.bufferRead(packet);
	}

	public void sendNotice() throws IOException, InterruptedException{
		PacketHandler chatHandler = new PacketHandler();
		Packet receivePacket;

		JSONObject chatJson = new JSONObject();
		chatJson.put("nickname", "user2");
		chatJson.put("user_num", String.valueOf(i%7));
		chatJson.put("type", "CHAT");
		chatJson.put("chatType", "LOBBYCHAT");
		chatJson.put("msg", "로비로비로비!!!!!!!!!!!!!!!");

		chatHandler.bufferWrite(new Packet(lobbySocketChannel, chatJson));

		receivePacket = chatHandler.bufferRead(new Packet(lobbySocketChannel));
		System.out.println("receive : " + receivePacket.data);
	}

	public void sendWhisper() throws IOException, InterruptedException{
		PacketHandler chatHandler = new PacketHandler();
		Packet receivePacket;

		JSONObject chatJson = new JSONObject();

		chatJson.put("nickname", "user2");
		chatJson.put("user_num", String.valueOf(i%7));
		chatJson.put("type", "CHAT");
		chatJson.put("chatType", "WHISPER");
		chatJson.put("targetId", "user100");
		chatJson.put("targetNum", "100");
		chatJson.put("msg", "로비에서귓속말귓속말귓속말!!!!!!!!!!!!!!!!!");

		chatHandler.bufferWrite(new Packet(lobbySocketChannel, chatJson));
		receivePacket = chatHandler.bufferRead(new Packet(lobbySocketChannel));
		System.out.println("receive : " + receivePacket.data);
	}
}