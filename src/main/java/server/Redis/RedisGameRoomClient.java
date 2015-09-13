package server.Redis;

import net.sf.json.JSONObject;
import redis.clients.jedis.Jedis;

// 프레젠스 서버의 클라이언트
public class RedisGameRoomClient {

	//flush all keys in redis server
	public void flush(Jedis jedis){
		jedis.flushAll();
	}

	// get user information from redis server
	public JSONObject getRoom(int room_id){

		Jedis jedis = new Jedis("192.168.0.107");

		String value = jedis.get(String.valueOf(room_id));
		if(value == null){
			jedis.close();
			return null;
		}

		JSONObject json = JSONObject.fromObject(value);

		jedis.close();

		return json;
	}

	// send user information to redis server
	//    key : serverNum_roomNum
	//    value : json object
	//    {
	//         current_user Num : 5
	//         max_user Num : 10
	//     status : WAIT, or PLAY
	//     server_num : String
	//         title : String
	//         is_secret : 0 // 비밀방아님, 1 비밀방
	//    }

	public void setRoom(int roomNum, String title, int maxPlayer, int currentPlayer, int status, String ip, int port, int gameServer_Num){

		Jedis jedis = new Jedis("192.168.0.107");

		JSONObject json = new JSONObject();
		json.put("status", status);
		json.put("server_num",gameServer_Num);
		json.put("title", title);
		json.put("roomNum", roomNum);
		json.put("maxPlayer", maxPlayer);
		json.put("currentPlayer", currentPlayer);
		json.put("ip", ip);
		json.put("port", port);

		jedis.set(gameServer_Num+"-"+roomNum, json.toString());
		jedis.close();
	}
}

