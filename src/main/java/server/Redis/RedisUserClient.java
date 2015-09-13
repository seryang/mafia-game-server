package server.Redis;

import net.sf.json.JSONObject;
/**
 * Created by un on 15. 7. 27..
 */
import redis.clients.jedis.Jedis;

// 프레젠스 서버의 클라이언트

public class RedisUserClient {

	//flush all keys in redis server
	public void flush(Jedis jedis){
		jedis.flushAll();
	}

	// get user information from redis server
	public static JSONObject getUser(int user_id){
		Jedis jedis = new Jedis("192.168.0.107");

		String value = jedis.get(String.valueOf(user_id));

		if(value == null){
			jedis.close();
			return null;
		}

		JSONObject json = new JSONObject();
		json = JSONObject.fromObject(value);

		jedis.close();
		return json;
	}

	// send user information to redis server
	//    key : user_id
	//    value : json object
	//    {
	//     status : GAMESERVER or LOBBY
	//     server_num : int
	//    }
	public static void setUser(int user_num, String status, String nickname, int server_num, String ip, String port){
		Jedis jedis = new Jedis("192.168.0.107");

		JSONObject json = new JSONObject();

		json.put("status", status);
		json.put("server_num", server_num);
		json.put("ip", ip);
		json.put("port", port);
		json.put("nickname", nickname);
		jedis.set(String.valueOf(user_num), json.toString());
		jedis.close();
	}

	public static void deleteUser(int user_id){
		Jedis jedis = new Jedis("192.168.0.107");
		jedis.del(String.valueOf(user_id));
		jedis.close();
	}
}
