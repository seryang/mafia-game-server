package gateWay;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import Util.Packet;
import Util.PacketHandler;
import net.sf.json.JSONObject;

public class GateWay {

	public static final String LB_SERVER_IP = "192.168.0.118";

	public static final int LB_SERVER_PORT = 7777;
	public static final int LB_SERVER_PORT_FOR_RELAY = 5555;

	private List<LobbyServerInfo> lobbyList = new ArrayList<LobbyServerInfo>();
	private List<GameServerInfo> gameList =  new ArrayList<GameServerInfo>();
	private HashMap<String, String> Port_info = new HashMap<String, String>();

	private int lobbyRound;
	private int gameRound;

	private int lobbyNum = 1; // 로비서버 번호
	private int gameNum = 1;  // 게임서버 번호
	private int totalUserNum = 1; // 이 게임에 들어온 사람

	private Selector selector;
	private ServerSocketChannel lb_ServerSocketChannel, relay_ServerSocketChannel;
	private SocketChannel lb_SocketChannel, relay_SocketChannel;
	PacketHandler handler = new PacketHandler();

	public GateWay() throws IOException{
		lbServerReady();
	}

	public static void main (String [] args){
		try {
			new GateWay();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void lbServerReady() throws IOException {
		selector = Selector.open();	// Get Selector

		lb_ServerSocketChannel = ServerSocketChannel.open(); // 서버 소켓 채널 생성- 채널을 생성하면 이와 연관된 서버 소켓도 같이 생성됩니다
		lb_ServerSocketChannel.configureBlocking(false); // 서버소켓채널을 비블록킹으로 설정 - 반드시 해주어야 합니다
		lb_ServerSocketChannel.socket().bind(new InetSocketAddress(LB_SERVER_IP, LB_SERVER_PORT)); // 서버 소켓에 바인드, 실제 바인드는 서버소켓 채널에 하는것이 아니라 서버 소켓에 합니다.
		lb_ServerSocketChannel.register(selector, SelectionKey.OP_ACCEPT); // 서버 소켓채널이 듣고자 하는 이벤트를 셀렉터에 등록. 즉, 클라이언트가 연결 요청을 올때 이벤트가 옵니다.

		System.out.println("LB Server ON : " + selector.isOpen());
		System.out.println("_________________________________");

		relay_ServerSocketChannel = ServerSocketChannel.open();
		relay_ServerSocketChannel.configureBlocking(false);
		relay_ServerSocketChannel.socket().bind(new InetSocketAddress(LB_SERVER_IP, LB_SERVER_PORT_FOR_RELAY));
		relay_ServerSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

		System.out.println("Relay Server ON : " + selector.isOpen());
		System.out.println("_________________________________");

		while (selector.select() > 0) { // 블록킹 메소드. 이벤트가 발생할 때까지 블록킹 (셀렉터를 셀렉트 시켜 요청된 이벤트가 있는지 검사)
			Iterator<SelectionKey> iter = selector.selectedKeys().iterator(); // 선택된 채널들의 키를 얻음

			while (iter.hasNext()) {
				SelectionKey key = iter.next(); // 이벤트가 발생한 셀렉션 키를 가져옴.
				iter.remove(); // 현재 처리하는 바로 제거.
				SelectableChannel selectableChannel = key.channel(); // channel()의 현재 하고 있는 동작(읽기, 쓰기)에 대한 파악을 하기 위한 겁니다.

				if (selectableChannel instanceof ServerSocketChannel) {
					if(key.channel() == lb_ServerSocketChannel){
						lb_SocketChannel = lb_ServerSocketChannel.accept(); // LBserverSocketChannel이라면 accept()를 호출. 접속 요청을 해온 상대방 소켓과 연결될 수 있는 socketChannel 얻음.
						lb_SocketChannel.configureBlocking(false); // 얻어진 소켓은 블로킹 소켓이므로 Non-Blocking IO 상태로 설정 합니다.
						lb_SocketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE); // 소켓 채널을 Selector에 등록
					}else if(key.channel() == relay_ServerSocketChannel){
						relay_SocketChannel = relay_ServerSocketChannel.accept();
						relay_SocketChannel.configureBlocking(false);
						relay_SocketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
					}
				} else {
					if (key.isReadable()) { // READ 버퍼에 읽을 데이터가 있느냐를 판단. 이벤트가 읽을 준비가 되었다는 것이라면(즉, 데이타가 수신되었다면) True
						try{
							lb_SocketChannel = (SocketChannel) key.channel();

							String requestType = null;
							JSONObject serverList = new JSONObject();
							JSONObject serverInfo = new JSONObject();

							Packet packet = new Packet(lb_SocketChannel);

							packet = handler.bufferRead(packet);

							if(packet.data.toString().length() != 0 ){
								requestType = packet.data.get("type").toString();
							}

							if(requestType.equals("LOBBY_SERVER")){

								System.out.println("["+requestType+"]");

								LobbyServerInfo lobby = new LobbyServerInfo(packet.data.get("host_ip").toString(), packet.data.get("port").toString(), packet.data.get("socket_localport").toString());
								lobbyList.add(lobby);

								Port_info.put(packet.data.get("socket_localport").toString(), "LOBBY");

								try{

									// Relay Server에 서버 정보를 알려줌
									serverList.put("type", "SERVER");
									serverList.put("serverType", "LOBBY");
									serverList.put("serverIp", lobby.getAddress());
									serverList.put("serverPort", packet.data.get("chatPort").toString());
									serverList.put("serverNum", lobbyNum);
									handler.bufferWrite(new Packet(relay_SocketChannel, serverList));


									// Lobby Server에 Gateway에서 정해준 ServerNum을 알려줌
									serverInfo.put("type", "SERVER_INFO");
									serverInfo.put("serverNum", lobbyNum);
									handler.bufferWrite(new Packet(lb_SocketChannel, serverInfo));

									System.out.println(" Lobby 서버 번호 : " + lobbyNum);
									System.out.println(" 생성된 Lobby 서버 IP : " + lobby.getAddress() + " / PORT : " + lobby.getPort() /*+ " / LOCAL_PORT : " + lobby.getLocalport()*/);
									System.out.println(" 현재 생성 Lobby Server : " + lobbyList.size() + " 개");
									System.out.println("_________________________________");

									lobbyNum++;
								}catch(Exception e){
									e.printStackTrace();
								}
							}else if(requestType.equals("GAME_SERVER")){

								System.out.println("["+requestType+"]" );

								GameServerInfo game = new GameServerInfo(packet.data.get("host_ip").toString(), packet.data.get("port").toString(), packet.data.get("socket_localport").toString());
								gameList.add(game);

								Port_info.put(packet.data.get("socket_localport").toString(), "GAME");

								try{
									serverList.put("type", "SERVER");
									serverList.put("serverType", "GAME");
									serverList.put("serverIp", game.getAddress());
									serverList.put("serverPort", packet.data.get("chatPort").toString());
									serverList.put("serverNum", gameNum);
									handler.bufferWrite(new Packet(relay_SocketChannel, serverList));

									// Game Server에 Gateway에서 정해준 ServerNum을 알려줌
									serverInfo.put("type", "SERVER_INFO");
									serverInfo.put("serverNum", gameNum);
									handler.bufferWrite(new Packet(lb_SocketChannel, serverInfo));


									System.out.println(" Game 서버 번호 : " + gameNum);
									System.out.println(" 생성된 Game 서버 IP : " + game.getAddress() + " / PORT : " + game.getPort());
									System.out.println(" 현재 생성 Game Server : " + gameList.size() + " 개");
									System.out.println("_________________________________");

									gameNum++;
								}catch(Exception e){
									e.printStackTrace();
								}


							}else if(requestType.equals("LOGIN")){      

								System.out.println("["+requestType+"] - 누적 유저 수 : " + totalUserNum++);

								if( lobbyRound >= lobbyList.size()){ // 라운드 로빈으로 lobbyServer 분산
									lobbyRound = 0;
								}

								JSONObject jsonObject = new JSONObject();
								jsonObject.put("host_ip", lobbyList.get(lobbyRound).getAddress());
								jsonObject.put("port", lobbyList.get(lobbyRound).getPort());

								packet = new Packet(lb_SocketChannel, jsonObject);
								handler.bufferWrite(packet);

								System.out.println("- Client에게 LobbyServer 주소 알려줌 ! "+ lobbyList.get(lobbyRound).getAddress() + " / " + lobbyList.get(lobbyRound).getPort());
								System.out.println("");
								lobbyRound++;

								if(lb_SocketChannel.finishConnect()){
									lb_SocketChannel.close();
								}

							}else if(requestType.equals("MAKE_ROOM_FROM_LOBBY")){
								System.out.println("[" + requestType + "]");

								if (gameRound >= gameList.size()) { // 라운드 로빈으로 gameServer 분산
									gameRound = 0;
								}

								JSONObject jsonObject = new JSONObject();
								jsonObject.put("type", "GAME_SERVER_FOR_LOBBY");
								jsonObject.put("ip", gameList.get(gameRound).getAddress());
								jsonObject.put("port", gameList.get(gameRound).getPort());
								jsonObject.put("user_num", packet.data.get("user_num"));

								System.out.println(jsonObject.toString());
								handler.bufferWrite(new Packet(lb_SocketChannel, jsonObject));

								System.out.println("- Client에게 GameServer 주소 알려줌 ! " + gameList.get(gameRound).getAddress() + " / " + gameList.get(gameRound).getPort());
								System.out.println("");
								gameRound++;
							}
						}catch(Exception ioe){
							// 비정상 종료된 서버(Lobby,Game)의 소켓을 닫아주고, 종료된 서버를 List에서 Remove
							String socket_type = String.valueOf(lb_SocketChannel.socket().getPort());
							JSONObject json = new JSONObject();

							if(Port_info.get(socket_type) == "LOBBY"){
								json.put("locate", "LOBBY");
								for(int i = 0 ; i < lobbyList.size() ; i++){
									if( String.valueOf(lb_SocketChannel.socket().getPort()).equals(lobbyList.get(i).localPort)){
										System.out.println("[Warning] LOBBY Server Disconnect - " + lobbyList.get(i).ipAddress + " / " + lobbyList.get(i).port);
										json.put("real_port",lobbyList.get(i).port);
										lobbyList.remove(i);
									}
								}
							}else if(Port_info.get(socket_type) == "GAME"){
								json.put("locate", "GAME");
								for(int i = 0 ; i < gameList.size() ; i++){
									if( String.valueOf(lb_SocketChannel.socket().getPort()).equals(gameList.get(i).localPort)){
										System.out.println("[Warning] GAME Server Disconnect - " + gameList.get(i).ipAddress + " / " + gameList.get(i).port);
										json.put("real_port",gameList.get(i).port);
										gameList.remove(i);

									}
								}
							}

							json.put("type", "DISCONNECT");
							json.put("port", lb_SocketChannel.socket().getPort());

							handler.bufferWrite(new Packet(relay_SocketChannel, json));

							// 비정상처리된 소켓 Close
							if(lb_SocketChannel.finishConnect()){
								lb_SocketChannel.close();
							}
						}
					}
				}
			}
		}
	}

	class LobbyServerInfo{
		private String port;
		private String ipAddress;
		private String localPort;

		public LobbyServerInfo(String ipAddress, String port, String localPort){
			this.port = port;
			this.ipAddress = ipAddress;
			this.localPort = localPort;
		}

		public String getAddress(){
			return this.ipAddress;
		}
		public String getPort(){
			return this.port;
		}
		public String getLocalport(){
			return this.localPort;
		}
	}

	class GameServerInfo{
		private String port;
		private String ipAddress;
		private String localPort;

		public GameServerInfo(String ipAddress, String port, String localPort){
			this.port = port;
			this.ipAddress = ipAddress;
			this.localPort = localPort;
		}

		public String getAddress(){
			return this.ipAddress;
		}
		public String getPort(){
			return this.port;
		}
		public String getLocalport(){
			return this.localPort;
		}
	}
} 
