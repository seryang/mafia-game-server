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
public class Dummy {
	private SocketChannel lbSocketChannel;
	private static SocketChannel lobbySocketChannel;
	private static SocketChannel gameSocketChannel;
	private JSONObject json = new JSONObject();
	private JSONObject json1 = new JSONObject();
	static int i = 0;


	public static void main(String[] args) {
		Dummy client = new Dummy();
		try {
			while(i < 1){
				++i;
				client.lobbyConnect();
				client.makeRoom();


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
				receivePacket = chatHandler.bufferRead(new Packet(gameSocketChannel));
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


	public void makeRoom() throws IOException {
		PacketHandler handler = new PacketHandler();

		JSONObject makeRoomJson = new JSONObject();

		makeRoomJson.put("type","MAKE_ROOM_FROM_CLIENT");
		makeRoomJson.put("user_num", String.valueOf(i));

		handler.bufferWrite(new Packet(lobbySocketChannel, makeRoomJson));

		Packet packet = handler.bufferRead(new Packet(lobbySocketChannel));
		System.out.println("받아온 게임 서버 주소 : " + packet.data);
		System.out.println("받아온 유저정보 : " + packet.data.get("user_gson"));

		gameSocketChannel = SocketChannel.open(new InetSocketAddress(packet.data.get("ip").toString(),Integer.parseInt(packet.data.get("port").toString())));

		if(gameSocketChannel.isConnected()){
			lobbySocketChannel.close();
			System.out.println("User와 LobbyServer의 Connection 정보 : " + lobbySocketChannel.socket());
			System.out.println("User와 GameServer의 Connection 정보 : " + gameSocketChannel.socket());
		}


		JSONObject loginPacket0= new JSONObject();

		loginPacket0.put("type", "GAME_LOGIN");
		loginPacket0.put("user_num", "1");

		handler.bufferWrite(new Packet(gameSocketChannel, loginPacket0));
		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);



	}

	public void sendWhisper() throws IOException, InterruptedException{
		PacketHandler chatHandler = new PacketHandler();
		Packet receivePacket;

		JSONObject chatJson = new JSONObject();

		chatJson.put("nickname", "user1");
		chatJson.put("user_num", String.valueOf(i%7));
		chatJson.put("type", "CHAT");
		chatJson.put("chatType", "WHISPER");
		chatJson.put("targetId", "user200");
		chatJson.put("targetNum", "200");
		chatJson.put("msg", "게임에서귓속말귓속말귓속말!!!!!!!!!!!!!!!!!");

		chatHandler.bufferWrite(new Packet(gameSocketChannel, chatJson));
		receivePacket = chatHandler.bufferRead(new Packet(gameSocketChannel));
		System.out.println("receive : " + receivePacket.data);
	}

	public void sendNotice() throws IOException, InterruptedException{
		PacketHandler chatHandler = new PacketHandler();
		Packet receivePacket;

		JSONObject chatJson = new JSONObject();
		chatJson.put("nickname", "user1");
		chatJson.put("user_num", String.valueOf(i%7));
		chatJson.put("type", "CHAT");
		chatJson.put("chatType", "NOTICE");
		chatJson.put("msg", "게임게임게임!!!!!!!!!!!!!!!");

		chatHandler.bufferWrite(new Packet(gameSocketChannel, chatJson));

		receivePacket = chatHandler.bufferRead(new Packet(gameSocketChannel));
		System.out.println("receive : " + receivePacket.data);
	}


	public static void gameLogic() throws IOException {

		PacketHandler handler = new PacketHandler();
		Packet packet;

		gameSocketChannel = SocketChannel.open(new InetSocketAddress ("192.168.0.84",49646));

		JSONObject loginPacket0= new JSONObject();

		loginPacket0.put("type", "GAME_LOGIN");
		loginPacket0.put("user_num", "1");

		handler.bufferWrite(new Packet(gameSocketChannel, loginPacket0));
		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		JSONObject loginPacket = new JSONObject();

		loginPacket.put("type", "GAME_LOGIN");
		loginPacket.put("user_num", "2");

		handler.bufferWrite(new Packet(gameSocketChannel, loginPacket));
		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		JSONObject makeRoomPacket = new JSONObject();

		makeRoomPacket.put("type", "MAKE_ROOM");
		makeRoomPacket.put("title", "hahahah");
		makeRoomPacket.put("user_num", "2");

		handler.bufferWrite(new Packet(gameSocketChannel, makeRoomPacket));
		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);


		JSONObject joinRoomPacket = new JSONObject();

		joinRoomPacket.put("type", "JOIN_ROOM");
		joinRoomPacket.put("user_num", "1");
		joinRoomPacket.put("room_number", "1");

		handler.bufferWrite(new Packet(gameSocketChannel, joinRoomPacket));
		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		JSONObject gameReadyPacket = new JSONObject();

		gameReadyPacket.put("type", "READY");
		gameReadyPacket.put("user_num", "1");

		handler.bufferWrite(new Packet(gameSocketChannel, gameReadyPacket));
		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		gameReadyPacket = new JSONObject();

		gameReadyPacket.put("type", "READY");
		gameReadyPacket.put("user_num", "2");

		handler.bufferWrite(new Packet(gameSocketChannel, gameReadyPacket));
		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);


		// day chat test
		JSONObject chatPacket = new JSONObject();

		chatPacket.put("type", "CHAT");
		chatPacket.put("user_num", "1");
		chatPacket.put("message", "gggggg");

		handler.bufferWrite(new Packet(gameSocketChannel, chatPacket));
		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		// turn change test
		JSONObject time_change = new JSONObject();

		time_change.put("type", "VOTE_CITIZEN");
		time_change.put("user_num", "1");

		handler.bufferWrite(new Packet(gameSocketChannel, time_change));

		time_change = new JSONObject();

		time_change.put("type", "VOTE_CITIZEN");
		time_change.put("user_num", "2");

		handler.bufferWrite(new Packet(gameSocketChannel, time_change));

		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		// vote citizen chat test
		chatPacket = new JSONObject();

		chatPacket.put("type", "CHAT");
		chatPacket.put("user_num", "1");
		chatPacket.put("message", "gggggg");

		handler.bufferWrite(new Packet(gameSocketChannel, chatPacket));
		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		// turn change test
		time_change = new JSONObject();

		time_change.put("type", "NIGHT");
		time_change.put("user_num", "1");

		handler.bufferWrite(new Packet(gameSocketChannel, time_change));

		time_change = new JSONObject();

		time_change.put("type", "NIGHT");
		time_change.put("user_num", "2");

		handler.bufferWrite(new Packet(gameSocketChannel, time_change));

		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		// NIGHT chat test
		chatPacket = new JSONObject();

		chatPacket.put("type", "CHAT");
		chatPacket.put("user_num", "1");
		chatPacket.put("message", "gggggg");

		handler.bufferWrite(new Packet(gameSocketChannel, chatPacket));
		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		packet = handler.bufferRead(new Packet(gameSocketChannel));
		System.out.println(packet.data);

		//
		////
		//
		//      packet = handler.bufferRead(new Packet(gameSocketChannel));
		//      System.out.println(packet.data);
		//


		//      System.out.println("받아온 게임 서버 주소 : " + packet.data);
		//      System.out.println("받아온 유저정보 : " + packet.data.get("user_gson"));

		//
		//      if(gameSocketChannel.isConnected()){
		//         lobbySocketChannel.close();
		////         System.out.println("User와 LobbyServer의 Connection 정보 : " + lobbySocketChannel.socket());
		////         System.out.println("User와 GameServer의 Connection 정보 : " + gameSocketChannel.socket());
		//      }
	}
}