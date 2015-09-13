package server.Game;


import java.util.HashMap;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.gson.Gson;

import Util.Packet;
import net.sf.json.JSONObject;
import server.User;
import server.Redis.RedisGameRoomClient;

/**
 * Created by un on 15. 8. 4..
 */
public class GameRoom {
	// GameRoom options -- does not change
	private int maxPlayer;
	private String title;
	private int roomNumber;
	public int status;
	int isSecret = 0;
	private String ip;
	private int port;
	int gameServer_Num;
	private ConcurrentLinkedQueue<Packet> queue = new ConcurrentLinkedQueue<>();

	// does change
	Vector<User> users;
	private int readyUser;
	int turnCounter = 0;
	int voteCounter = 0;

	// who is alive
	int mafia_num = 0;
	int citizen_num = 0;

	public HashMap<Integer, Integer> voteIndex = new HashMap<>();
	public HashMap<User, Integer> playerState;
	public static HashMap<User, String> playerRole = new HashMap<>();

	// User ENUM STATE
	public static final int ENTERED = 1;
	public static final int READY = 2;
	public static final int LOADING = 3;
	public static final int Playing = 4;
	public static final int TURN_REQUEST = 5;

	// game room state
	public static final int WAIT = 1;
	public static final int LOAD = 2;
	public static final int DAY = 3;
	public static final int VOTE_CITIZEN = 4;
	public static final int NIGHT = 5;
	public static final int VOTE_MAFIA = 6;
	public static final int OVERFLOW = 7;
	public static final int GAME_OVER = 8;
	
	//for game
	int readyforstart = 0;
	
	public GameRoom(String title, int roomNumber, int maxPlayer ,ConcurrentLinkedQueue<Packet> queue, String gameAddress, int gameServer_Num){
		this.title = title;
		this.maxPlayer = maxPlayer;
		this.users = new Vector<User>();
		this.playerState = new HashMap<>();
		this.roomNumber = roomNumber;
		this.status = WAIT;
		this.queue = queue;
		this.ip = gameAddress.split(":")[0];
		this.port = Integer.valueOf(gameAddress.split(":")[1]);
		this.gameServer_Num = gameServer_Num;

		System.out.println("게임 방 번호 " + String.valueOf(roomNumber)+" : " + title+ "이(가) 만들어 졌습니다.");
		alarmStatus();
	}

	public GameRoom(String title, int roomNumber, int maxPlayer, int isSecret ,ConcurrentLinkedQueue<Packet> queue, String gameAddress, int gameServer_Num){
		this.title = title;
		this.maxPlayer = maxPlayer;
		this.users = new Vector<User>();
		this.playerState = new HashMap<>();
		this.roomNumber = roomNumber;
		this.status = WAIT;
		this.isSecret = isSecret;
		this.queue = queue;
		this.ip = gameAddress.split(":")[0];
		this.port = Integer.valueOf(gameAddress.split(":")[1]);
		this.gameServer_Num = gameServer_Num;

		System.out.println("게임 방 번호 " + String.valueOf(roomNumber) + " : " + title + "이(가) 만들어 졌습니다.");
		alarmStatus();
	}

	public int getRoomNumber() {
		return this.roomNumber;
	}

	public int getServerNum() {
		return this.gameServer_Num;
	}

	public int joinGame(User user) {

		if (users.size() == maxPlayer) {
			System.out.println("joinGame");
			return -1;
		}

		playerState.put(user, ENTERED);
		users.add(user);

		System.out.println(String.valueOf(user.getID()) + " 님이 게임 방 번호 " + String.valueOf(roomNumber) + " 에 입장하셨습니다.");
		notyCurrentUser();
		alarmStatus();
		return this.roomNumber;
	}

	public boolean exitGame(User user) {

		playerState.remove(user);
		users.remove(user);

		System.out.println(String.valueOf(user.getID()) + " 님이 게임 방 번호 " + String.valueOf(roomNumber) + " 에서 나가셨습니다..");

		notyCurrentUser();
		alarmStatus();
		// if no user, destory room
		if (users.size() == 0) {
			GameRoomManager.getInstance().destoryRoom(this);
		}
		
		return true;
	}

	public void gameStart() {
		User user;
		JSONObject gameSetJson = new JSONObject();
		readyforstart++;
		
		if(readyforstart >= users.size()){
			int user_num = users.size();
			mafia_num = 0;
			int dice;
			Random generator = new Random();

			while (mafia_num != (int) (user_num + 2) / 3) {
				dice = generator.nextInt(user_num);
				user = users.get(dice);
				if (playerRole.get(user) == null) {
					playerRole.put(user, "MAFIA");
					mafia_num++;
					alertMapiaRole(user);
					mesageToUser(user, "당신은 마피아로 선정되었습니다.");
					System.out.println(String.valueOf(user.getID()) + " 님이 마피아로 선정되었습니다.");
				}
			}
			
			for(int i = 0; i<users.size(); i++){
				user = users.get(i);
				if(playerRole.get(user)==null){
					playerRole.put(user, "시민");
					mesageToUser(user, "당신은 시민입니다.");
				}
			}
			
			for(int i = 0; i<users.size(); i++){
				user = users.get(i);
				System.out.println("usernum : " + user.getID() + "         PlayerRole : " + playerRole.get(user));
			}
			
			citizen_num = users.size() - mafia_num;

			setUsersStatus(Playing);

			gameSetJson.put("totalCitizen", String.valueOf(citizen_num));
			gameSetJson.put("totalMafia", String.valueOf(mafia_num));

			broadCast("GAME_SET", gameSetJson.toString());

			broadCast("GAME_START", "게임이 시작되었습니다.");
			System.out.println("게임이 시작되었습니다.");

			this.status = DAY;
			alarmStatus();
			readyforstart = 0;
		} 
	}

	private void setUsersStatus(int status) {
		for (User user : users)
			playerState.replace(user, status);
	}

	public void alarmStatus() {
		RedisGameRoomClient redis = new RedisGameRoomClient();
		redis.setRoom(this.roomNumber, this.title, this.maxPlayer, this.users.size(), this.status, this.ip, this.port,
				this.gameServer_Num);
	}

	// if user press ready button game room should add user as a ready user
	public boolean addReadyUser(User user) {
		if (playerState.get(user) == ENTERED) {
			playerState.remove(user);
			playerState.put(user, READY);
			readyUser++;
			broadCast("before_notify", String.valueOf(user.getNickname()) + " 님이 READY");
			if (readyUser == users.size()) {
				broadCast("GAME_PLAY_START", "");
				this.status = LOAD;
			}
		} else {
			System.out.print("somthing wrong");
			return false;
		}
		return true;
	}

	
	public boolean subReadyUser(User user) {
		if (playerState.get(user) == READY) {
			playerState.remove(user);
			playerState.put(user, ENTERED);
			readyUser--;
			broadCast("before_notify", String.valueOf(user.getNickname()) + " 님이 READY 취소");
			return true;
		} else {
			System.out.print("somthing wrong");
			return false;
		}
	}
	
	@SuppressWarnings("unchecked")
	public void chat(JSONObject packet, User talker) {
		JSONObject sendChat = new JSONObject();
		JSONObject secondChat  = new JSONObject();

		sendChat.put("type", "CHAT");
		secondChat.put("type", packet.getString("chatType"));
		secondChat.put("ID", packet.getString("nickname"));
		secondChat.put("CONTENT", packet.getString("msg"));
		sendChat.put("CONTENT", secondChat.toString());

		if (this.status == LOAD || this.status == WAIT) {
			for (User user : users) {
				queue.add(new Packet(user.userSocket, sendChat));
				System.out.println("packet");
			}
			return;
		}

		if (playerRole.get(talker) == "MAFIA") { // 말하는 사람이 마피아면
			sendChat.replace("type", "GAMECHAT");
			for (User user : users) {
				if (this.status == DAY || this.status == VOTE_CITIZEN) {
					queue.add(new Packet(user.userSocket, sendChat));
				} else if (this.status == NIGHT || this.status == VOTE_MAFIA) {
					if (playerRole.get(user) == "MAFIA")
						queue.add(new Packet(user.userSocket, sendChat));
				}
			}
		} else {
			sendChat.replace("type", "GAMECHAT");
			if (this.status == DAY || this.status == VOTE_CITIZEN) {
				for (User user : users) {
					queue.add(new Packet(user.userSocket, sendChat));
				}
			} else { // 밤, 밤투표시에는 말할 수 없음
				JSONObject cantTalk = new JSONObject();
				cantTalk.put("type", "CAN_NOT_TALK");
				queue.add(new Packet(talker.userSocket, cantTalk));
			}
		}
	}

	public void broadCast(String messgae) {
		JSONObject packet = new JSONObject();
		packet.put("type", "notify");
		packet.put("message", messgae);
		for (User user : users) {
			queue.add(new Packet(user.userSocket, packet));
		}
	}

	public void broadCast(String type, String message) {
		JSONObject packet = new JSONObject();
		packet.put("type", type);
		packet.put("message", message);
		for (User user : users) {
			queue.add(new Packet(user.userSocket, packet));
		}
	}

	public void mesageToUser(User user, String message) {
		JSONObject data = new JSONObject();
		data.put("type", "notify");
		data.put("message", message);
		queue.add(new Packet(user.userSocket, data));
	}
	
	public void notifyToUser(User user, String type) {
		JSONObject data = new JSONObject();
		data.put("type", type);
		queue.add(new Packet(user.userSocket, data));
	}

	public void alertMapiaRole(User user) {
		JSONObject data = new JSONObject();
		data.put("type", "ALERT_MAPIA");
		queue.add(new Packet(user.userSocket, data));
	}

	public void turnCounter(User user) {
		if (playerState.get(user) != Playing)
			return;

		playerState.replace(user, TURN_REQUEST);
		turnCounter++;
		System.out.println("turn count");
		if (turnCounter == users.size()) {
			System.out.println("turn change");
			turnCounter = 0;
			turnChange();
			setUsersStatus(Playing);
		}
	}

	public void notyCurrentUser() {
		Gson gson = new Gson();
		String user_List = gson.toJson(users);
		System.out.println("게임방 유저 리스트 : " + user_List);
		broadCast("users_now", user_List);
	}

	public void vote(int user_num, int target_uid) {
		System.out.println("vote : " + user_num + "   /   " + target_uid);
		voteCounter++;
		if (target_uid == -1) {
			target_uid = user_num;
		}

		if (voteIndex.get(target_uid) == null) {
			voteIndex.put(target_uid, 1);
		} else {
			voteIndex.replace(target_uid, voteIndex.get(target_uid) + 1);
		}

		// 전원 투표시
		if (voteCounter == users.size()) {
			int max = -1, sencond_max = -1, index = -1, value;
			for (User user : users) {
				if(voteIndex.get(user.getID()) != null){
					value = voteIndex.get(user.getID()); // 각 user별 얻은 표를 가져와서
					if (value >= max) { // 이전 최대값과 비교하여 같거나크면
						sencond_max = max; // 최대값교체
						max = value;
						index = user.getID(); // 유저의 아이디를 인덱스로가져온다?
					}
				}
			}

			if (max == sencond_max) {
				broadCast("RE_VOTE", "");
				if (this.status == VOTE_CITIZEN) {
					broadCast("VOTE_CITIZEN_RES", "");
					turnChange();
					return;
				} else if (this.status == VOTE_MAFIA) {
					broadCast("VOTE_MAFIA_RES", "");
					turnChange();
					return;
				}
			} else {
				User target = null;
				for (int i = 0; i < users.size(); i++) {
					if (users.get(i).getID() == index) {
						target = users.get(i);
					}
				}
				
				if (this.status == VOTE_CITIZEN) {
					broadCast("VOTE_CITIZEN_FINISHED", "");
					if (playerRole.get(target).equals("MAFIA")) {
						broadCast("KILLED_MAFIA", String.valueOf(target.getNickname()));
						notifyToUser(target, "URDEAD");
						mafia_num--;
					} else {
						broadCast("KILLED_CITIZEN", String.valueOf(target.getNickname()));
						notifyToUser(target, "URDEAD");
						citizen_num--;
					}
				} else if (this.status == VOTE_MAFIA) {
					broadCast("VOTE_MAFIA_FINISHED", "");
					if (playerRole.get(target).equals("MAFIA")) {
						broadCast("KILLED", String.valueOf(target.getNickname()));
						notifyToUser(target, "URDEAD");
						mafia_num--;
					} else {
						broadCast("KILLED", String.valueOf(target.getNickname()));
						notifyToUser(target, "URDEAD");
						citizen_num--;
					}
				}
				if (mafia_num == 0) {
					broadCast("GAME_OVER", "시민");
					this.status = GAME_OVER;
				} else if (mafia_num > citizen_num) {
					broadCast("GAME_OVER", "마피아");
					this.status = GAME_OVER;
				}
			}
		}
	}

	public void turnChange() {

		this.status++;
		if (this.status == OVERFLOW) {
			this.status = DAY;
		}

		switch (this.status) {
		case 3:
			broadCast("DAY_RES", "");
			break;
		case 4:
			broadCast("VOTE_CITIZEN_RES", "");
			voteCounter = 0;
			voteIndex = new HashMap<>();
			break;
		case 5:
			broadCast("NIGHT_RES", "");
			break;
		case 6:
			broadCast("VOTE_MAFIA_RES", "");
			voteCounter = 0;
			voteIndex = new HashMap<>();
			break;
		}
	}
}
