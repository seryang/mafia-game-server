package server;

import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;

import Util.Packet;
import net.sf.json.JSONObject;
import server.DB.DBUserClient;
import server.Game.GameRoom;
import server.Game.GameRoomManager;
import server.Redis.RedisUserClient;

public class User implements java.io.Serializable{

	private static final long serialVersionUID = 1L;
	public static transient DBUserClient dbUserClient = new DBUserClient();
	public transient SocketChannel userSocket;

	private int user_num;
	private int server_num;
	private String nickname;
	private String status;
	private String userIP;
	private String userPort;

	public User(int user_num, String nickname,String status, int serverNum){
		this.user_num = user_num;
		this.nickname = nickname;
		this.status = status;
		this.server_num = serverNum;
	}

	public void setNickName(String nickName){
		this.nickname = nickName;
	}

	public String getUserIP() {
		return userIP;
	}

	public void setUserIP(String userIP) {
		this.userIP = userIP;
	}

	public String getUserPort() {
		return userPort;
	}

	public void setUserPort(String userPort) {
		this.userPort = userPort;
	}

	public int getID(){
		return this.user_num;
	}

	public String getNickname() {
		return nickname;
	}

	// add a new friend with his/her nickname
	// 친구 추가 후 친구목록 리프레쉬 해줘야함
/*	public void addFriend(String nickname){
		Boolean is_succeed;
		is_succeed = DBUserClient.addFriends(this, nickname);

		if(!is_succeed) {
			System.out.println("fail to add a new friend");
		}
	}
*/
	// delete a friend with his/her nickname
	// 친구 삭제 후 친구목록 리프레쉬 해줘야함
/*	public void deleteFriends(String nickname){
		Boolean is_succeed;

		is_succeed = DBUserClient.deleteFriends(this, nickname);

		if(!is_succeed) {
			System.out.println("fail to delete the friend");
		}
	}*/

	// alarm current user status to redis server
	// presence 서버에 알려야함
	public void alarmStatus(){
		RedisUserClient.setUser(this.user_num, this.status, this.nickname, this.server_num, this.userIP , this.userPort);
	}

	public void userLogout(int user_num){
		RedisUserClient.deleteUser(user_num);
	}

	// 유저 정보가져옴
	public static JSONObject getStatus(int user_id){
		JSONObject json = RedisUserClient.getUser(user_id);
		return json;
	}

	public void setStatus(String location, int server_num){
		this.status = location;
		this.server_num = server_num;
	}

	public int makeRoom(String title, String maxPlayer, ConcurrentLinkedQueue<Packet> packetSendQueue, String gameAddress, int gameServer_Num){
		if(!this.status.equals("GAME_SERVER"))
			return -1;

		GameRoomManager manager = GameRoomManager.getInstance();

		if(manager != null){
			GameRoom room = manager.makeRoom(title, maxPlayer, packetSendQueue , gameAddress, gameServer_Num);
			return room.joinGame(this); // 방이 안만들어지면, -1 잘 만들어지면 roomNumber 를 리턴
		}else {
			return -1;
		}
	}

	public int joinRoom(int roomNumber){

		if(!this.status.equals("GAME_ROOM")){
			System.out.println("users JoinRoom ~~~~~~~~");
			return -1;
		}

		GameRoomManager manager = GameRoomManager.getInstance();
		GameRoom room = manager.getGameRoom(roomNumber);

		if(room != null) {
			System.out.println("User class의 joinRoom");
			return room.joinGame(this); // 방이 안만들어지면, -1 잘 만들어지면 roomNumber 를 리턴
		}

		return -1;
	}
}