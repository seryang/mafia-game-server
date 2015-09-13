package server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import Util.Packet;
import Util.PacketHandler;
import net.sf.json.JSONObject;
import redis.clients.jedis.Jedis;
import server.DB.DBConnectionPoolManager;
import server.Game.GameThread;
import server.Lobby.LobbyThread;

/**
 * @author Mr.Choe
 */
abstract class AbstractServer {
	protected SocketChannel server_client_SocketChannel, server_LBserver_SocketChannel, relaySocketChannel;
	protected ServerSocketChannel serverSocketChannel, relayServerSocketChannel;

	protected String hostAddr;
	protected LobbyThread lobbyThread;
	protected GameThread gameThread;

	protected PacketHandler handler = new PacketHandler();
	public ManualResetEvent eventHandler = new ManualResetEvent(false);

	abstract void initServerThread(); // lobby or game Thread Start
	abstract void connectLB() throws IOException;


	/**
	 * @throws IOException
	 */
	int count;

	public AbstractServer() throws IOException {
		init();
		connectLB();
	}

	/**
	 * @throws UnknownHostException 
	 * @throws IOException
	 * - Server Setting
	 */
	public void init() throws UnknownHostException {
		hostAddr = InetAddress.getLocalHost().toString().split("/")[1];

		// DB setting 
		DBConnectionPoolManager dbManager = DBConnectionPoolManager.getInstance();
		dbManager.init("pool", "com.mysql.jdbc.Driver", "jdbc:mysql://192.168.0.106/mapia", "devu", "123", 10, 1, 10);

		ServerThread serverThread = new ServerThread();
		serverSocketChannel = serverThread.init();
		serverThread.start();

		initServerThread();

		// Relay 서버 연결
		ChatThread ChatThread = new ChatThread();
		relayServerSocketChannel = ChatThread.init();
		ChatThread.start();
	}


	/**
	 * 서버 Thread
	 */
	class ServerThread extends Thread {
		int count;
		Selector selector;

		public ServerSocketChannel init() {
			try {
				selector = Selector.open();
				serverSocketChannel = ServerSocketChannel.open();
				InetSocketAddress hostAddress = new InetSocketAddress(0);
				serverSocketChannel.bind(hostAddress);
				serverSocketChannel.configureBlocking(false);
				serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
				return serverSocketChannel;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		public void run(){
			try {
				while (selector.select() > 0) {
					Iterator<SelectionKey> iter = selector.selectedKeys().iterator();

					while (iter.hasNext()) {
						SelectionKey key = iter.next();
						iter.remove();

						SelectableChannel selectableChannel = key.channel();

						if (selectableChannel instanceof ServerSocketChannel) {
							if(key.channel() == serverSocketChannel){
								server_client_SocketChannel = serverSocketChannel.accept();
								server_client_SocketChannel.configureBlocking(false);
								server_client_SocketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
								count++;

								System.out.println("@________________________________");
								System.out.println("- 유저 입장 : " + server_client_SocketChannel.socket().getRemoteSocketAddress());
								System.out.println(" 유저 IP : " + server_client_SocketChannel.socket().getInetAddress());
								System.out.println(" 유저 port : " + server_client_SocketChannel.socket().getPort());
								System.out.println(" 현재 서버 접속 인원 : " + count);
								System.out.println("________________________________@");
							}
						} else {
							if(key.isReadable()){
								try{

									if(lobbyThread != null && lobbyThread.isAlive()){
										Packet packet = handler.bufferRead(new Packet(  (SocketChannel) key.channel())  );
										lobbyThread.packetReceiveQueue.add(packet);
										eventHandler.set();

									}else if(gameThread !=null && gameThread.isAlive()){
										Packet packet = handler.bufferRead(new Packet(  (SocketChannel) key.channel())  );
										gameThread.packetReceiveQueue.add(packet);
										eventHandler.set();
									}
								}catch(Exception e){
									//	e.printStackTrace();

									SocketChannel channel = (SocketChannel) key.channel();
									key.cancel();

									System.out.println("나간 놈 --- " + channel.socket().getInetAddress() + " / " + channel.socket().getPort());
									unusualDisconnect(channel.socket().getInetAddress(), channel.socket().getPort());
									--count;
									System.out.println(" 선수 아웃 - 현재 접속 인원 : " + count);
									System.out.println("___________________________");
									System.out.println("");
								}
							}
						}
					}

					while(lobbyThread != null && lobbyThread.isAlive() && !lobbyThread.packetSendQueue.isEmpty()){
						handler.bufferWrite(lobbyThread.packetSendQueue.poll());
					}

					while(gameThread != null && gameThread.isAlive() && !gameThread.packetSendQueue.isEmpty()){
						System.out.println(gameThread.packetSendQueue.peek().data);
						handler.bufferWrite(gameThread.packetSendQueue.poll());
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	/*
	 * User가 최초 접속 시, 자신의 Network Address(IP:PORT)를 Key로 하고, 자신의 user_num을 value로 입력
	 */
	public static void userAddress(SocketChannel address, int user_num){
		Jedis jedis = new Jedis("192.168.0.107");
		jedis.set(String.valueOf(address.socket().getInetAddress()).split("/")[1]+":"+String.valueOf(address.socket().getPort()), String.valueOf(user_num));
		jedis.close();
	}

	/*
	 * User의 locate에 따라 자신의 닉네임을 Redis에 저장
	 */
	public static void setUser_list(String locate, String nickName){
		Jedis jedis = new Jedis("192.168.0.107");
		jedis.sadd(locate, nickName);
		jedis.close();
	}

	/*
	 * - 비정상 종료시 (비정상 종료된 사용자의 IP와 PORT는 알 수 있음)
	 * 1) 비정상 사용자의 IP와 PORT를 이용하여 Redis에서 userAddress 키값을 이용해 user_num을 구하고,
	 * 2) 그 유저의 번호를 키값으로 user(객체)의 정보를 받아오고,
	 * 3) user 정보 중, status (LOBBY or GAME)인지 확인 후
	 *    3-1) LOBBY or GAME 서버의 list에서 자신을 지우고,
	 *    3-2) Redis에서 <LOBBY or GAME>를 키값에 있는 user의 닉네임을 지워준다.
	 * 4) Redis에서 User_num을 키값으로, 유저 객체를 지워주고, 
	 * 5) 마지막으로 userAddress를 키값으로 한 user_num을 지워준다.
	 */
	public static void unusualDisconnect(InetAddress inetAddress, int port) {
		Jedis jedis = new Jedis("192.168.0.107");

		String add = String.valueOf(inetAddress).split("/")[1]+":"+port;
		System.out.println("나가려는 유저의 Address : " + add);

		String user_num = jedis.get(add);

		System.out.println("나가려는 유저의 넘버 : " + user_num);

		if(user_num == null)
		{
			jedis.close();
			return;
		}

		String user_info = jedis.get(user_num);

		if(user_info == null)
		{
			jedis.del(add);
			jedis.close();
			return;
		}
		JSONObject json = JSONObject.fromObject(user_info);

		// Lobby나 Game안에 있는 List에서 정보 삭제 lobbyList or GameList
		if(json.get("status").toString().equals("LOBBY_SERVER")){
			LobbyThread.userList.remove(user_num);

			// - 로비면 로비의 유저리스트에서 삭제              <LOBBY, 유저닉네임>
			delUser_list("LOBBY", json.get("nickname").toString());

		}else if(json.get("status").toString().equals("GAME_SERVER")){
			GameThread.userList.remove(user_num);
		}

		// Redis에서 <유저 Num, 유저 객체> 삭제
//		jedis.del(user_num);

		// <user Address, user_num> 데이터 삭제
//		jedis.del(add);
		
		jedis.close();
	}

	public static ArrayList<String> getUser_list(String type){
		Jedis jedis = new Jedis("192.168.0.107");

		Set<String> user_list = jedis.smembers(type);

		ArrayList<String> wow = new ArrayList<String>();

		Iterator<String> ite = user_list.iterator();
		while(ite.hasNext()){
			wow.add(ite.next());
		}

//		for(String go : wow){
//			System.out.print(go + " / ");
//		}
//		System.out.println("");
		jedis.close();

		return wow;

	}

	public static void delUser_list(String type, String nickName){
		Jedis jedis = new Jedis("192.168.0.107");
		jedis.srem(type, nickName);
		jedis.close();
	}

	public static JSONObject getGame_list(){
		Jedis jedis = new Jedis("192.168.0.107");

		// 1) "GAME"을 Key로 갖는 현재 방 Num를 Redis에서 가져온다. 
		Set<String> game_list = jedis.smembers("GAME");

		JSONObject gameRoomList = new JSONObject();

		Iterator<String> ite = game_list.iterator();
		while(ite.hasNext()){
			String key = ite.next();

			// 2) 방Num을 이용해 방 정보를 받아 json에 넣음
			gameRoomList.put(key, jedis.get(key));
		}

		jedis.close();

		return gameRoomList;
	} 

	class ChatThread extends Thread {
		private ServerSocketChannel relayServerSocketChannel;
		private Selector chatselector;

		public ServerSocketChannel init() {
			try {
				chatselector = Selector.open();
				relayServerSocketChannel = ServerSocketChannel.open();
				InetSocketAddress hostAddress = new InetSocketAddress(0);
				relayServerSocketChannel.bind(hostAddress);
				relayServerSocketChannel.configureBlocking(false);
				int ops = relayServerSocketChannel.validOps();
				relayServerSocketChannel.register(chatselector, ops, null);
				return relayServerSocketChannel;

			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		public void run() {
			try {
				while (chatselector.select() > 0) {
					Iterator<SelectionKey> iter = chatselector.selectedKeys().iterator();

					while (iter.hasNext()) {
						SelectionKey ky = iter.next();
						iter.remove();
						SelectableChannel selectableChannel = ky.channel();

						try {
							if (selectableChannel instanceof ServerSocketChannel) {
								if (ky.channel() == relayServerSocketChannel) {
									relaySocketChannel = relayServerSocketChannel.accept();
									relaySocketChannel.configureBlocking(false);
									relaySocketChannel.register(chatselector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	} 
}
