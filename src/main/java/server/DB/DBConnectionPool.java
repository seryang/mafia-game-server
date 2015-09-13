package server.DB;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Date;
import java.util.Vector;

/**
 * Created by un on 15. 7. 30..
 */
public class DBConnectionPool {

	// number of using connection
	private int checkedOut;
	// free connection list
	private Vector<Connection> freeConnections = new Vector<Connection>();
	private int maxConn;
	// Connection 초기 개수
	private int initConn;

	// Waiting time (pool에 connection이 없을때 기다리는 최대시간)
	private int maxWait;

	// Connection Pool Name
	private String name;

	// DB Password
	private String password;

	// DB URL
	private String URL;

	// DB UserID
	private String user;

	// Constructor
	public DBConnectionPool(String name,
			String URL,
			String user,
			String password,
			int maxConn,
			int initConn,
			int waitTime) {

		this.name = name;
		this.URL = URL;
		this.user = user;
		this.password = password;
		this.maxConn = maxConn;
		this.maxWait = waitTime;

		for (int i = 0; i < initConn; i++) {
			Connection con = newConnection();
			freeConnections.addElement(con);
		}
	}

	// Connection 반납
	// @param con : 반납할 Connection
	public synchronized void freeConnection(Connection con) {
		freeConnections.addElement(con);
		checkedOut--;
		// Connection을 얻기 위해 대기하고 있는 thread에 알림
		notifyAll();
	}

	// Connection 을 얻음
	public synchronized Connection getConnection() {
		Connection con = null;
		// Connection이 Free List에 있으면 List의 처음 것을 얻음
		if (freeConnections.size() > 0) {
			con = (Connection) freeConnections.firstElement();
			freeConnections.removeElementAt(0);

			try {
				// DBMS에 의해 Connection이 close 되었으면 다시 요구
				if (con.isClosed()) {
					con = getConnection();
				}
			} // 요상한 Connection 발생하면 다시 요구
			catch (SQLException e) {
				con = getConnection();
			}
		} // Connection이 Free List에 없으면 새로 생성
		else if (maxConn == 0 || checkedOut < maxConn) {
			con = newConnection();
		}

		if (con != null) {
			checkedOut++;
		}

		return con;
	}

	// Connection을 얻음
	// @param timeout : Connection을 얻기 위한 최대 기다림 시간
	public synchronized Connection getConnection(long timeout) {
		long startTime = new Date().getTime();
		Connection con;
		while ((con = getConnection()) == null) {
			try {
				wait(timeout * maxWait);
			} catch (InterruptedException e) {}
			if ((new Date().getTime() - startTime) >= timeout) {
				// 기다림 시간 초과
				return null;
			}
		}

		return con;
	}

	// Connection 생성
	private Connection newConnection() {
		Connection con = null;
		try {
			if (user == null) {
				con = DriverManager.getConnection(URL);

			} else {
				con = DriverManager.getConnection(URL, user, password);
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
		return con;
	}
}
