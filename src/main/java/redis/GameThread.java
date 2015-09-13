package redis;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.gson.Gson;

import Util.Packet;
import net.sf.json.JSONObject;
import redis.clients.jedis.Jedis;
import server.GameServer;
import server.ManualResetEvent;
import server.User;
import server.Game.GameRoom;
import server.Game.GameRoomManager;


public class GameThread extends Thread {
	public ConcurrentLinkedQueue<Packet> packetReceiveQueue;
	public ConcurrentLinkedQueue<Packet> packetSendQueue;

	public ConcurrentLinkedQueue<Packet> chatQueue;

	public HashMap<User,GameRoom> roomMapper = new HashMap<>();
	public static HashMap<Integer,User> userList = new HashMap<>();

	public ManualResetEvent eventHandler;

	private int gameServer_Num;

	public GameThread(ManualResetEvent eventHandler){
		packetReceiveQueue = new ConcurrentLinkedQueue<>();
		packetSendQueue = new ConcurrentLinkedQueue<>();
		this.eventHandler = eventHandler;
	}

	public GameThread(ConcurrentLinkedQueue<Packet> queue, ManualResetEvent eventHandler) {
		packetReceiveQueue = new ConcurrentLinkedQueue<>();
		packetSendQueue = new ConcurrentLinkedQueue<>();
		this.chatQueue = queue;
		this.eventHandler = eventHandler;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		Packet packet;
		JSONObject packetData;
		SocketChannel packetChannel;

		String requestType = null;
		int user_num;
		User user = null;

		while(true) {

			// 처리할 패킷이 없으면 공회전
			if(packetReceiveQueue.isEmpty()){
				//                System.out.println("wait one");
				try {
					eventHandler.reset();
					eventHandler.waitOne();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			// packetData 을 가져옴
			packet = packetReceiveQueue.poll();
			packetData = packet.data;
			packetChannel = packet.channel;
			System.out.println(packetData.toString());
			// packetData이 변조됐으면 버림
			if(!isPacketValid(packetData))
				continue;

			// packetData 으로 부터 requestType과 보낸 유저를 체크한다.
			requestType = packetData.getString("type").toString();



			if(requestType.equals("SERVER_INFO")){
				System.out.println("[" + requestType + "]");
				String serverNum = packetData.getString("serverNum");
				gameServer_Num = Integer.parseInt(serverNum);
				System.out.println("게임 서버 번호 : " + gameServer_Num);
			}

			if (packetData.get("user_num") == null){
				user_num = -1;
			} else {
				user_num = Integer.parseInt(packetData.getString("user_num"));
			}

			// 아직 로비로부터 정보를 받지 않은 경우 패킷 처리를 뒤로 미룬다.
			if((user = isUserValid(user_num)) == null) {
				continue;
			}

			if(requestType.equals("MAKE_ROOM")){
				System.out.println("["+requestType+"]");
				// 유저가 보낸 정보로 기존의 유저객체 복원
				Gson gson = new Gson();
				user = gson.fromJson(packetData.getString("user_gson"), User.class);

				// 게임서버 정보 입력
				user.setStatus("GAME_SERVER", gameServer_Num);
				user.alarmStatus();

				// 로비 접속유저리스트에서 삭제 해주기.
				GameServer.delUser_list("LOBBY", user.getNickname());

				// 유저 소켓을 등록시킴
				user.userSocket = packetChannel;

				// user를 게임서버에 등록
				userList.put(user.getID(), user);
				System.out.println(String.valueOf(user.getID()) + " 님이 게임 서버에 입장");

				String gameAddress = null;
				try {
					gameAddress = packetChannel.getLocalAddress().toString().split("/")[1];
				} catch (IOException e) {
					e.printStackTrace();
				}

				// 새로 room을 만들고, 유저와 방을 등록함
				int roomNum = user.makeRoom(packetData.get("title").toString(),packetData.get("maxPlayer").toString(), packetSendQueue, gameAddress, gameServer_Num);

				// room 만든게 실패
				if(roomNum == -1){
					System.out.println("Making room is failed");
					continue;
				}
				GameRoom room = GameRoomManager.getInstance().getGameRoom(roomNum);

				user.setStatus("GAME_ROOM", gameServer_Num);
				user.alarmStatus();

				roomMapper.put(user, room);
			}
			else if(requestType.equals("JOIN_ROOM")) {
				System.out.println("["+requestType+"]");

				int roomNum = Integer.parseInt(packetData.getString("room_number"));

				GameRoom room = GameRoomManager.getInstance().getGameRoom(roomNum);

				// 입장하려는 방이 없을 때
				if(room == null) {
					continue;
				}
				user.userSocket = packetChannel;

				userList.put(user.getID(), user);

				user.setStatus("GAME_ROOM", gameServer_Num);
				user.alarmStatus();
				user.joinRoom(roomNum);
				roomMapper.put(user, room);
			}
			else if(requestType.equals("READY")){
				GameRoom room = roomMapper.get(user);
				room.addReadyUser(user);
			}
			else if(requestType.equals("CANCEL_READY")){
				GameRoom room = roomMapper.get(user);
				room.subReadyUser(user);
			}
			else if(requestType.equals("READY_FOR_START_GAME")){
				GameRoom room = roomMapper.get(user);
				room.gameStart();
			}
			else if(requestType.equals("DAY")){
				GameRoom room = roomMapper.get(user);
				room.turnCounter(user);
			}
			else if(requestType.equals("VOTE_CITIZEN")){
				GameRoom room = roomMapper.get(user);
				room.turnCounter(user);
			}
			else if(requestType.equals("NIGHT")){
				GameRoom room = roomMapper.get(user);
				room.turnCounter(user);
			}
			else if(requestType.equals("VOTE_MAFIA")){
				GameRoom room = roomMapper.get(user);
				room.turnCounter(user);
			}
			else if(requestType.equals("POINT")){
				GameRoom room = roomMapper.get(user);
				room.vote(Integer.parseInt(packetData.get("user_num").toString()), Integer.parseInt(packetData.get("target_uid").toString()));
			}else if(requestType.equals("EXITGAME")){
				GameRoom room = roomMapper.get(user);
				room.exitGame(user);
				GameServer.setUser_list("LOBBY", user.getNickname());
			}else if(requestType.equals("CHAT")){
				if(packetData.get("chatType").toString().equals("NOTICE") || packetData.get("chatType").toString().equals("WHISPER")){
					System.out.println("["+requestType+"]");
					chatQueue.add(new Packet(null, packetData));
				}
				else{
					GameRoom room = roomMapper.get(user);
					System.out.println(room);
					packet.data.replace("chatType","ROOM");
					room.chat(packet.data, user);
				}
			}
			else if(requestType.equals("RETURNCHAT")){
				System.out.println("["+requestType+"]");

				JSONObject chatSendJson = new JSONObject();
				JSONObject secondJson = new JSONObject();
				secondJson.put("type", packetData.getString("chatType"));
				secondJson.put("ID", packetData.getString("nickname"));
				secondJson.put("CONTENT", packetData.getString("msg"));
				chatSendJson.put("type","CHAT");
				chatSendJson.put("CONTENT", secondJson);

				if(packetData.getString("chatType").equals("NOTICE")){
					sendAll(chatSendJson);

				} else if (packetData.get("chatType").equals("WHISPER")){
					packetSendQueue.add(new Packet(userList.get(Integer.parseInt(packetData.getString("targetNum"))).userSocket, chatSendJson));

				} else if (packetData.get("chatType").equals("WHISPERBACK")){
					secondJson.replace("ID", packetData.getString("targetId"));
					chatSendJson.put("CONTENT", secondJson);
					packetSendQueue.add(new Packet(userList.get(Integer.parseInt(packetData.getString("user_num"))).userSocket, chatSendJson));

				} else if(packetData.get("chatType").equals("WHISPER_ERROR")){
					secondJson.replace("ID", packetData.getString("targetId"));
					secondJson.replace("CONTENT", "");
					chatSendJson.replace("CONTENT", secondJson);
					packetSendQueue.add(new Packet(userList.get(Integer.parseInt(packetData.getString("targetNum"))).userSocket, chatSendJson));

				}
			}
		}
	}

	//해당 게임서버에 접속해있는 모든 유저에게 채팅메시지 전송
	private void sendAll(JSONObject chatSendJson){
		Iterator<Integer> iter = userList.keySet().iterator();
		while(iter.hasNext()){
			User chatUser = userList.get(iter.next());
			if(chatUser != null){
				packetSendQueue.add(new Packet(chatUser.userSocket, chatSendJson));
			}
		}
	}

	// packetData이 유효한 패킷인지 확인 ( 유저가 마음대로 변조한 것인지 아닌지 확인 )
	private boolean isPacketValid(JSONObject packetData) {
		return true;
	}


	// User(int u_id, String nickname, String status, int serverNum)
	private User isUserValid(int user_num) {
		if(user_num == -1)
			return null;

		User user = userList.get(user_num);

		if(user == null){
			Jedis jedis = new Jedis("192.168.0.107");

			String s = jedis.get(String.valueOf(user_num));

			JSONObject userInfo = JSONObject.fromObject(s);
			user = new User(user_num, userInfo.get("nickname").toString(), userInfo.get("status").toString(), Integer.parseInt(userInfo.get("server_num").toString()));
			jedis.close();
			return user;
		}

		return user;
	}
}
