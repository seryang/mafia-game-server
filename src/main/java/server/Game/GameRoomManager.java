package server.Game;


import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.gson.Gson;

import Util.Packet;
import redis.clients.jedis.Jedis;

/**
 * Created by un on 15. 8. 4..
 */
public class GameRoomManager {
	// singleton
	private static GameRoomManager instance;
	private static HashMap<Integer, GameRoom> rooms = new HashMap<Integer,GameRoom>();
	private static int roomCounter;

	private GameRoomManager(){}

	// get instance
	// usage : GameRoomManager.getInstance()
	public static synchronized GameRoomManager getInstance(){
		if(instance != null)
			return instance;
		instance = new GameRoomManager();
		return instance;
	}

	public synchronized GameRoom makeRoom(String title, String maxPlayer, ConcurrentLinkedQueue<Packet> packetSendQueue, String gameAddress, int gameServer_Num){
		roomCounter++;
		GameRoom newRoom = new GameRoom(title, roomCounter, Integer.parseInt(maxPlayer), packetSendQueue, gameAddress, gameServer_Num);
		rooms.put(roomCounter, newRoom);

		// Redis에 Game이라는 Key값에 들어갈 방 리스트 
		setGame_list(roomCounter, gameServer_Num);
		return newRoom;
	}

	public synchronized boolean destoryRoom(GameRoom target){

		delGame_list(target.getServerNum(), target.getRoomNumber());
		rooms.remove(target);
		return true;
	}

	public String roomList(){
		Gson gson = new Gson();
		String room_list = gson.toJson(rooms);
		return room_list;
	}

	public synchronized GameRoom getGameRoom(int roomNumber){
		return rooms.get(roomNumber);
	}

	public static void setGame_list(int roomNum, int gameServer_Num){
		Jedis jedis = new Jedis("192.168.0.107");

		jedis.sadd("GAME", gameServer_Num+"-"+roomNum);
		jedis.close();
	}

	public static void delGame_list(int serverNum, int roomNum){
		Jedis jedis = new Jedis("192.168.0.107");
		
		System.out.println("지우려는 서버 Num : " + serverNum);
		System.out.println("지우려는 룸 Num : " + roomNum);
		System.out.println("도합 : " + serverNum+"-"+roomNum);
		
		Long i = jedis.del(String.valueOf(serverNum+"-"+roomNum));
		if(i == 1){
			System.out.println("지워짐");
		}else{
			System.out.println("안지워졌어");
		}
		
		// GAME 
		jedis.srem("GAME", serverNum+"-"+roomNum);
	
		jedis.close();
	}
}
