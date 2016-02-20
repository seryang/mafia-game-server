package relay;

import static gateWay.GateWay.LB_SERVER_IP;
import static gateWay.GateWay.LB_SERVER_PORT_FOR_RELAY;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import Util.Packet;
import Util.PacketHandler;
import net.sf.json.JSONObject;
import server.DB.DBConnectionPoolManager;
import server.DB.DBUserClient;
import server.Redis.RedisUserClient;

public class RelayServer {
	private Selector selector;

	SocketChannel lobbySocketChannel;
	SocketChannel gameSocketChannel;
	SocketChannel lbSocketChannel;

	private HashMap <Integer, SocketChannel> lobby = new HashMap<Integer, SocketChannel>(); // 로비 리스트 관리
	private HashMap <Integer, SocketChannel> game = new HashMap<Integer, SocketChannel>(); // 게임 리스트 관리

	public ConcurrentLinkedQueue<Packet> relayChatQueue;
	PacketHandler handler = new PacketHandler();

	public static void main(String[] args) {
		RelayServer relay = new RelayServer();
		relay.initServer();
		relay.startServer();
	}

	/*
	 * RelayServer가 켜지자 마자 LoadBalancing Server와 소켓채널을 연결한다.
	 */
	public void initServer() {
		try {
			selector = Selector.open();

			lbSocketChannel = SocketChannel.open(new InetSocketAddress(LB_SERVER_IP, LB_SERVER_PORT_FOR_RELAY));
			lbSocketChannel.configureBlocking(false);
			lbSocketChannel.register(selector, SelectionKey.OP_READ);

			DBConnectionPoolManager dbManager = DBConnectionPoolManager.getInstance();
			dbManager.init("pool", "com.mysql.jdbc.Driver", "jdbc:mysql://192.168.0.106/mapia", "devu", "123", 20, 3, 10);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * LB서버와 연결한 후 RelayServer 역할 시작
	 */
	public void startServer() {
		System.out.println("Server is started");
		try {
			while (selector.select() > 0) {
				Iterator<SelectionKey> it = selector.selectedKeys().iterator();

				while (it.hasNext()) {
					SelectionKey key = it.next();
					it.remove(); // 처리한 이벤트는 삭제

					if (key.isReadable() && key.channel() == lbSocketChannel) { // lb서버에서 로비/게임서버정보가 들어온 경우
						serverConnect(key);

					} else if (key.isReadable()) { // 로비에서 로비채팅 혹은 귓속말 채팅이 들어온 경우
						try{
							read(key);
						}
						catch(IOException e){
							key.cancel();
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * 서버 연결 수행
	 */
	private void serverConnect(SelectionKey key) throws IOException{
		SocketChannel sc = (SocketChannel) key.channel();
		Packet packet;
		JSONObject Json = null;
		String type = null, serverType, serverIp;
		int serverPort, serverNum;

		packet = handler.bufferRead(new Packet(sc));
		Json = packet.data;

		type = Json.get("type").toString();

		if (type.equals("SERVER")) {
			serverType = Json.get("serverType").toString();
			serverIp = Json.get("serverIp").toString();
			serverPort = Integer.parseInt(Json.get("serverPort").toString());
			serverNum = Integer.parseInt(Json.get("serverNum").toString());

			if (serverType.equals("LOBBY")) { // 로비 서버 등록인 경우
				lobbySocketChannel = SocketChannel.open(new InetSocketAddress(serverIp, serverPort));
				lobbySocketChannel.configureBlocking(false);
				lobbySocketChannel.register(selector, SelectionKey.OP_READ);
				addLobby(serverNum, lobbySocketChannel);
				System.out.println("로비서버 번호 : " + serverNum + " 번");
			} else if (serverType.equals("GAME")) { // 게임 서버 등록인 경우
				gameSocketChannel = SocketChannel.open(new InetSocketAddress(serverIp, serverPort));
				gameSocketChannel.configureBlocking(false);
				gameSocketChannel.register(selector, SelectionKey.OP_READ);
				addGame(serverNum, gameSocketChannel);
				System.out.println("게임서버 번호 : " + serverNum + " 번");
			}
		}else if(type.equals("DISCONNECT")){

			String locate = Json.get("locate").toString();
			int port = (int) Json.get("port");

			String realport = Json.getString("real_port");

			int iter_key = 0;

			if(locate.equals("LOBBY")){
				Iterator<Integer> ite = lobby.keySet().iterator();
				while(ite.hasNext()){
					iter_key = ite.next();
					SocketChannel cha = lobby.get(iter_key);
					if( cha.socket().getLocalPort() == (port+1)){
						System.out.println("[Warning] Lobby Server : " + iter_key + " 번 Out!! - Port : " + realport + " remove ! ");
						lobby.remove(iter_key);
						System.out.println();
						cha.socket().close();
						break;
					}
				}
			}else{
				Iterator<Integer> ite = game.keySet().iterator();
				while(ite.hasNext()){
					iter_key = ite.next();
					SocketChannel cha = game.get(iter_key);
					if( cha.socket().getLocalPort() == (port+1)){
						System.out.println("[Warning] game Server Port :  " + iter_key + " 번 Out!! - Port : " + realport + " remove ! ");
						game.remove(iter_key);
						System.out.println();
						cha.socket().close();
						break;
					}
				}
			}
		}
	}

	/*
	 * 채팅 수행
	 */
	@SuppressWarnings("unchecked")
	private void read(SelectionKey key) throws IOException {
		SocketChannel sc = (SocketChannel) key.channel();
		Packet chatPacket;
		JSONObject chatJson = null, secretChatJson, secretSenderChatJson;
		String chatType, type = null;

		chatPacket = handler.bufferRead(new Packet(sc));
		chatJson = chatPacket.data;
		type = chatJson.get("type").toString();

		if (type.equals("CHAT")) { // 채팅인경우 채팅 종류에 맞게 역할 수행
			chatType = chatJson.getString("chatType");
			chatJson.replace("type","RETURNCHAT");    //채팅메시지를 돌려줄때는 type을 RETURNCHAT으로 바꿔준다. 

			if (chatType.equals("LOBBYCHAT")) { // 로비채팅
				lobby_broadcast(chatJson); // 모든 로비서버에게 브로드캐스트

			}else if (chatType.equals("NOTICE")) { // 공지
				game_notice_broadcast(chatJson); //모든 게임서버에게 브로드캐스트
				lobby_broadcast(chatJson);// 모든 로비서버에게 브로드캐스트

			}else if (chatType.equals("WHISPER")) { // 귓속말
				try{
					System.out.println("targetId : " + chatJson.getString("targetId"));

					int targetNum = DBUserClient.getUserNum(chatJson.getString("targetId"));    //DB에서 상대방 usernum 찾아옴//
					System.out.println("targetNum : " + targetNum);
					chatJson.put("targetNum", String.valueOf(targetNum));
					secretChatJson = RedisUserClient.getUser(targetNum);
					//					secretChatJson = RedisUserClient.getUser(Integer.parseInt(chatJson.getString("targetNum"))); // 받을사람 ID로 redis 검색해서 위치 정보를 json으로 가져옴
					secret_chat(secretChatJson.getString("status"), Integer.parseInt(secretChatJson.getString("server_num")), chatJson);  //수신인에게

					secretSenderChatJson = RedisUserClient.getUser(Integer.parseInt(chatJson.getString("user_num")));
					chatJson.replace("chatType", "WHISPERBACK");
					System.out.println( secretSenderChatJson.getString("status")+ "     /     " + Integer.parseInt(secretSenderChatJson.getString("server_num")));
					secret_chat(secretSenderChatJson.getString("status"), Integer.parseInt(secretSenderChatJson.getString("server_num")), chatJson); //발신인에게


				}catch(Exception e){  //받을 사람이 redis 에서 검색되지 않는 경우 귓속말 에러 메시지를 보낸사람에게 보내줌
					secretChatJson = RedisUserClient.getUser(Integer.parseInt(chatJson.getString("user_num")));
					chatJson.replace("chatType", "WHISPER_ERROR");
					chatJson.put("targetNum", chatJson.get("user_num"));
					secret_chat(secretChatJson.getString("status"), Integer.parseInt(secretChatJson.getString("server_num")), chatJson);
				}
			}
		}
	}

	/*
	 * 귓속말 대상이 위치한 서버로 전달
	 */
	private void secret_chat(String serverType, int serverNum, JSONObject secretJson) throws IOException {
		if (serverType.equals("LOBBY_SERVER")) {
			SocketChannel sc = (SocketChannel) lobby.get(serverNum);
			if (sc != null) {
				handler.bufferWrite(new Packet(sc, secretJson));
			}
		} else if (serverType.equals("GAME_ROOM")) {
			SocketChannel sc = (SocketChannel) game.get(serverNum);
			if (sc != null) {
				handler.bufferWrite(new Packet(sc, secretJson));
			}
		}
	}

	/*
	 * 로비 채팅/공지인 경우는 로비 서버 전체로 다시 뿌려준다.
	 */
	private void lobby_broadcast(JSONObject lobbyChatJson) throws IOException {
		Iterator<Integer> iter = lobby.keySet().iterator();
		while (iter.hasNext()) {
			SocketChannel sc = lobby.get(iter.next());
			if (sc != null) {
				handler.bufferWrite(new Packet(sc, lobbyChatJson));
			}
		}
	}

	/*
	 * 공지인 경우는 게임서버 전체로 뿌려준다.
	 */
	private void game_notice_broadcast(JSONObject gameNoticeJson) throws IOException {
		Iterator<Integer> iter = game.keySet().iterator();
		while (iter.hasNext()) {
			SocketChannel sc = game.get(iter.next());
			if (sc != null) {
				handler.bufferWrite(new Packet(sc, gameNoticeJson));
			}
		}
	}

	/*
	 * 새로운 로비 연결되면 로비 해시맵에 로비 추가
	 */
	private void addLobby(int index, SocketChannel sc) {
		lobby.put(index, sc);
	}

	/*
	 * 새로운 게임서버 연결되면 게임서버 해시맵에 추가
	 */
	private void addGame(int index, SocketChannel sc) {
		game.put(index, sc);
	}
}