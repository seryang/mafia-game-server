package server.Lobby;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.gson.Gson;

import Util.Packet;
/**
 * Created by un on 15. 8. 6..
 */
import net.sf.json.JSONObject;
import redis.clients.jedis.Jedis;
import server.LobbyServer;
import server.ManualResetEvent;
import server.User;

public class LobbyThread extends Thread {
	public ConcurrentLinkedQueue<Packet> packetReceiveQueue;
	public ConcurrentLinkedQueue<Packet> packetSendQueue;
	public ConcurrentLinkedQueue<Packet> lobbyQueue;

	public ConcurrentLinkedQueue<Packet> chatQueue;
	public ConcurrentLinkedQueue<Packet> chatReceiveQueue;
	public ConcurrentLinkedQueue<Packet> chatSendQueue;

	public static HashMap<Integer, User> userList = new HashMap<>();

	public ManualResetEvent eventHandler;
	private int lobbyServer_Num;

	public LobbyThread(ManualResetEvent eventHandler){
		packetReceiveQueue = new ConcurrentLinkedQueue<>();
		packetSendQueue = new ConcurrentLinkedQueue<>();
		this.eventHandler = eventHandler;
	}

	public LobbyThread(ConcurrentLinkedQueue<Packet> lobbyQueue, ConcurrentLinkedQueue<Packet> chatQueue, ManualResetEvent eventHandler) {
		packetReceiveQueue = new ConcurrentLinkedQueue<>();
		packetSendQueue = new ConcurrentLinkedQueue<>();
		this.lobbyQueue = lobbyQueue;
		this.chatQueue = chatQueue;
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

			// packetData이 변조됐으면 버림
			if (!isPacketValid(packetData)){
				continue;
			}
			requestType = packetData.getString("type").toString(); // packetData 으로 부터 requestType과 보낸 유저를 체크한다.

			if(requestType.equals("SERVER_INFO")){
				System.out.println("[" + requestType + "]");
				String serverNum = packetData.getString("serverNum").toString();
				lobbyServer_Num = Integer.parseInt(serverNum); // GateWay에서 정해준 서버의 고유번호를 받아옴
				System.out.println("로비 서버 번호 : " + lobbyServer_Num);
			}else{

				if (packetData.get("user_num") == null){
					user_num = -1;
				} else {
					user_num = Integer.parseInt(packetData.getString("user_num").toString());
				}


				// 아직 로비로부터 정보를 받지 않은 경우 패킷 처리를 뒤로 미룬다.
				if ((user = isUserValid(user_num)) == null) {
					continue;
				}

				if (requestType.equals("LOGIN")){
					System.out.println("[" + requestType + "]");

					// 유저 소켓을 등록시킴
					user.userSocket = packetChannel;

					/*
					 * 1. 유저 객체에 Status와 내가 접속한 서버의 번호를 set하고, Redis에 <user_num, user 객체>를 등록 
					 */
					user.setStatus("LOBBY_SERVER", lobbyServer_Num);
					user.alarmStatus();
					user.setNickName(user.getNickname());

					/*
					 * 2. 유저의 Network <Address>와 <user_num>을 Redis에 등록 --- 로그아웃/비정상로그아웃 했을 때 User 처리를 위함
					 */
					LobbyServer.userAddress(packet.channel, user.getID());

					/*
					 * 3. 접속한 서버의 userList에 나의 정보를 등록
					 */
					userList.put(user.getID(), user);

					/*
					 * 4. Reids에 <LOBBY, 나의 NickName>을 등록
					 */
					LobbyServer.setUser_list("LOBBY", user.getNickname());

					System.out.println("[닉네임 : " + user.getNickname() +" / UserNUM : " +String.valueOf(user.getID()) + "] 님이 로비 서버에 입장하였습니다.");
					System.out.println("------------------------------");

					/*
					 *  로그인 성공을 유저에게 알리며, 로비에 접속한 유저의 리스트와 현재 게임방 List를 넘겨줌
					 */
					JSONObject responseData = new JSONObject();
					responseData.put("type", "LOGIN_SUCCEED");
					responseData.put("lobby_user_list", LobbyServer.getUser_list("LOBBY") /* ArrayList */);
					responseData.put("game_room_list", LobbyServer.getGame_list() /* JSON */);

					packetSendQueue.add(new Packet(packetChannel, responseData));

					// Client가 로비에게 방을 만들겠다는 request
				}else if(requestType.equals("MAKE_ROOM_FROM_CLIENT")){

					System.out.println("["+requestType+"]");
					JSONObject reponseData = new JSONObject();
					reponseData.put("type","MAKE_ROOM_FROM_LOBBY");
					reponseData.put("user_num", user_num);
					lobbyQueue.add(new Packet(null, reponseData));

					// GateWay에서 보낸 Game서버 주소를 response
				}else if(requestType.equals("GAME_SERVER_FOR_LOBBY")){
					System.out.println("["+requestType+"]");

					Gson gson = new Gson();
					String user_string = gson.toJson(user);
					packetData.put("type", "GAME_SERVER_FOR_CLIENT");
					packetData.put("user_gson", user_string);
					packetSendQueue.add(new Packet(user.userSocket, packetData));
				}else if(requestType.equals("CHAT")){
					System.out.println("["+requestType+"]");

					chatQueue.add(new Packet(null, packetData));

				}else if(requestType.equals("RETURNCHAT")){
					System.out.println("["+requestType+"]");

					JSONObject chatSendJson = new JSONObject();
					JSONObject secondJson = new JSONObject();
					secondJson.put("TYPE", packetData.getString("chatType"));
					secondJson.put("ID", packetData.getString("nickname"));
					secondJson.put("CONTENT", packetData.getString("msg"));
					chatSendJson.put("type","CHAT");
					chatSendJson.put("CONTENT", secondJson);

					if(packetData.get("chatType").equals("LOBBYCHAT") || packetData.get("chatType").equals("NOTICE")){
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
	/*			}else if(requestType.equals("ADD_FRIEND")){
					System.out.println("["+requestType+"]");
					String f_nick = packetData.get("f_nickname").toString();
					user.addFriend(f_nick);
*/
				}else if(requestType.equals("LOGOUT")){
					System.out.println("["+requestType+"]");

					// Redis에 있는 User의 모든 정보를 삭제
					LobbyServer.unusualDisconnect(user.userSocket.socket().getInetAddress(), user.userSocket.socket().getPort());

					try {
						if(user.userSocket.finishConnect()){
							user.userSocket.close();
						}
					} catch (IOException e) {
						System.out.println("user 로그아웃시 사용했던 소켓 닫을 때 생긴 Exception");
						e.printStackTrace();
					}
				}else if(requestType.equals("REFRESH")){
//					System.out.println("["+requestType+"]");
					JSONObject responseData = new JSONObject();
					responseData.put("type", "LOGIN_SUCCEED");
					responseData.put("lobby_user_list", LobbyServer.getUser_list("LOBBY"));
					responseData.put("game_room_list", LobbyServer.getGame_list() /* JSON */);

					packetSendQueue.add(new Packet(packetChannel, responseData));
				}
			}
		}
	}

	//해당 로비서버에 접속해있는 모든 유저에게 채팅메시지 전송
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
		if(user == null) {

			JSONObject jsonObject = User.dbUserClient.getUser(user_num);
			if(jsonObject == null){
				return null;
			}

			// 새로운 유저 오브젝트를 만들어서 관리
			user = new User(user_num, jsonObject.getString("nickname"), null, -1);
			return user;
		}
		return user;
	}

	public ArrayList<String> getUser_list(String type){

		Jedis jedis = new Jedis("192.168.0.107");

		Set<String> user_list = jedis.smembers(type);
		ArrayList<String> wow = new ArrayList<String>();

		Iterator<String> ite = user_list.iterator();
		while(ite.hasNext()){
			wow.add(ite.next());
		}

//		for(String go : wow){
//			System.out.println(go);
//		}
		jedis.close();

		return wow;

	}

	public void delUser_list(String type, String nickName){
		Jedis jedis = new Jedis("192.168.0.107");
		jedis.srem(type, nickName);
		jedis.close();
	}
}