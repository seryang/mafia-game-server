package server.DB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Created by un on 15. 7. 29..
 */

import net.sf.json.JSONObject;


public class DBUserClient {

	private static Connection connect;
	private static PreparedStatement preparedStatement = null;

	public static int getUserNum(String nickname){
		ResultSet rs = null;
		int usernum;

		try{
			connect = DBConnectionPoolManager.getInstance().getConnection("pool");
			preparedStatement = connect.prepareStatement("SELECT User_num FROM Users WHERE NickName = ?");
			preparedStatement.setString(1, nickname);
			rs = preparedStatement.executeQuery();

			while(rs.next()){
				usernum = rs.getInt(1);
				DBConnectionPoolManager.getInstance().freeConnection("pool", connect);
				return usernum;
			}
		}catch(SQLException e){
			DBConnectionPoolManager.getInstance().freeConnection("pool",connect);
			e.printStackTrace();
			return -1;
		}finally{
			close(connect, preparedStatement, rs);
		}
		return -1;
	}

	public static void close(Connection conn, Statement stmt, ResultSet rset){
		try{
			if(rset !=null ){
				rset.close();
				rset = null;
			}
			if(stmt != null){
				stmt.close();
				stmt = null;
			}
			if(conn != null){
				conn.close();
				conn = null;
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	public JSONObject getUser(int user_id){
		ResultSet rs = null;
		try{
			JSONObject jsonObject = new JSONObject();
			connect = DBConnectionPoolManager.getInstance().getConnection("pool");

			preparedStatement = connect.prepareStatement("SELECT Gender, NickName FROM Users WHERE User_num = (?)");
			preparedStatement.setInt(1, user_id);
			rs = preparedStatement.executeQuery();

			String gender = null, nickname = null;

			while(rs.next()){
				gender = rs.getString(1);
				nickname = rs.getString(2);

				DBConnectionPoolManager.getInstance().freeConnection("pool", connect);

				jsonObject.put("gender", gender);
				jsonObject.put("nickname", nickname);

				return jsonObject;
			}
			DBConnectionPoolManager.getInstance().freeConnection("pool", connect);
			return null;

		} catch (SQLException e) {
			DBConnectionPoolManager.getInstance().freeConnection("pool",connect);
			e.printStackTrace();
			return null;
		}finally{
			close(connect, preparedStatement, rs);
		}
	}
	
	
	/*
	 * 나중에 언제가될지 모르지만 구현해야 할 부분. 
	 */
/*
	public static ArrayList getFriends(User user){
		ResultSet rs = null;
		try{
			ArrayList friends_list = new ArrayList();

			connect = DBConnectionPoolManager.getInstance().getConnection("pool");

			preparedStatement = connect.prepareStatement("SELECT FriendList FROM friends WHERE User_num = (?)");
			preparedStatement.setInt(1, user.getID());
			rs = preparedStatement.executeQuery();

			if(rs.next()){
				if(rs.getString(1) == null)
					return null;

				String friendlist[] = rs.getString(1).split(",");

				for(String f_id : friendlist){
					friends_list.add(f_id);
				}
				DBConnectionPoolManager.getInstance().freeConnection("pool",connect);
				return friends_list;
			}

			DBConnectionPoolManager.getInstance().freeConnection("pool",connect);
			return null;
		}catch (SQLException e) {
			e.printStackTrace();
			DBConnectionPoolManager.getInstance().freeConnection("pool",connect);
			return null;
		}finally{
			close(connect, preparedStatement, rs);
		}
	}

	public static boolean addFriends(User user, String nickname) {
		ResultSet rs = null;
		try {
			if(nickname.equals(user.getNickname())){
				System.out.println("자기자신을 친구로 추가 할 수 없습니다.");
				return false;
			}

			connect = DBConnectionPoolManager.getInstance().getConnection("pool");
			System.out.println("nickName - " + nickname);
			preparedStatement = connect.prepareStatement("SELECT User_num FROM Users WHERE NickName = (?)");
			preparedStatement.setString(1, nickname);
			//            preparedStatement.setInt(1, 2);

			rs = preparedStatement.executeQuery();

			if (rs.next()) {
				int f_id = rs.getInt(1);
				ArrayList f_list = getFriends(user);
				if(f_list != null && f_list.contains(f_id)){
					System.out.println("이미 있는 친구입니다.");
				} else {
					connect.setAutoCommit(false);
					//                UPDATE myTable SET spares = IFNULL (CONCAT( spares , "string" ), "string")
					preparedStatement = connect.prepareStatement("UPDATE Friends SET FriendList = IFNULL (CONCAT( FriendList , (?) ), (?)) WHERE User_num = (?) ");
					preparedStatement.setString(1, "," + String.valueOf(f_id));
					preparedStatement.setString(2, String.valueOf(f_id));
					preparedStatement.setInt(3, user.getID());
					preparedStatement.executeUpdate();

					connect.setAutoCommit(true);
				}
				DBConnectionPoolManager.getInstance().freeConnection("pool", connect);
				return true;
			}
			return false;
		} catch (SQLException e) {
			System.out.println(e);
			DBConnectionPoolManager.getInstance().freeConnection("pool",connect);
			e.printStackTrace();
			return false;
		}finally{
			close(connect, preparedStatement, rs);
		}
	}

	public static boolean deleteFriends(User user, String nickname) {
		ResultSet rs = null;
		try {
			connect = DBConnectionPoolManager.getInstance().getConnection("pool");

			preparedStatement = connect.prepareStatement("SELECT user_num FROM user WHERE nickname = (?)");
			preparedStatement.setString(1, nickname);
			rs = preparedStatement.executeQuery();

			if(rs.next()) {
				int f_id = rs.getInt(1);

				// Transaction control
				connect.setAutoCommit(false);

				//                preparedStatement = connect.prepareStatement("DELETE FROM friends WHERE u_id = (?) AND f_id = (?)");
				//                preparedStatement.setInt(1, user.getID());
				//                preparedStatement.setInt(2, f_id);
				//                preparedStatement.executeUpdate();

				connect.setAutoCommit(true);
				DBConnectionPoolManager.getInstance().freeConnection("pool", connect);
				return true;
			}
			return false;
		} catch (SQLException e) {
			DBConnectionPoolManager.getInstance().freeConnection("pool",connect);
			return false;
		}finally{
			close(connect, preparedStatement, rs);
		}
	}*/
}
