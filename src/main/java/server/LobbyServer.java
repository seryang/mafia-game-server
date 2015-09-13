package server;

import static gateWay.GateWay.LB_SERVER_IP;
import static gateWay.GateWay.LB_SERVER_PORT;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import Util.Packet;
import Util.PacketHandler;
import net.sf.json.JSONObject;
import server.Lobby.LobbyThread;

/**
 * @author Mr.Choe & Mr.Park
 */
public class LobbyServer extends AbstractServer {

	public static ConcurrentLinkedQueue<Packet> lobbyQueue = new ConcurrentLinkedQueue<Packet>();
	public static ConcurrentLinkedQueue<Packet> chatQueue = new ConcurrentLinkedQueue<Packet>();

	public LobbyServer() throws IOException {
		super();
	}

	public static void main(String[] args) {
		try {
			new LobbyServer();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @throws IOException
	 *  - LB Server Connection & Init
	 */
	@Override
	public void connectLB() throws IOException {
		Selector lobbySelector = Selector.open();

		server_LBserver_SocketChannel = SocketChannel.open(new InetSocketAddress(LB_SERVER_IP, LB_SERVER_PORT));
		server_LBserver_SocketChannel.configureBlocking(false);
		server_LBserver_SocketChannel.register(lobbySelector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);

		System.out.println("Lobby Server ON : " + lobbySelector.isOpen());
		System.out.println("IP : " + hostAddr + " / PORT : " + serverSocketChannel.socket().getLocalPort());

		JSONObject lobbyServer_Init_Data = new JSONObject();
		lobbyServer_Init_Data.put("host_ip", hostAddr);
		lobbyServer_Init_Data.put("port", serverSocketChannel.socket().getLocalPort());
		lobbyServer_Init_Data.put("type", "LOBBY_SERVER");
		lobbyServer_Init_Data.put("chatPort", relayServerSocketChannel.socket().getLocalPort());
		lobbyServer_Init_Data.put("socket_localport", server_LBserver_SocketChannel.socket().getLocalPort());

		Packet packet = new Packet(server_LBserver_SocketChannel, lobbyServer_Init_Data);

		lobbyQueue.add(packet);

		int cnt = 0;

		while (lobbySelector.select() > 0) {
			Iterator<SelectionKey> iter = lobbySelector.selectedKeys().iterator();

			while (iter.hasNext()) {
				SelectionKey key = iter.next();
				iter.remove();

				if (key.isWritable()) {
					if (!lobbyQueue.isEmpty()) {
						if (lobbyQueue.peek().channel == null) {
							lobbyQueue.peek().channel = server_LBserver_SocketChannel;
						}
						handler.bufferWrite(lobbyQueue.poll());
					}

					if (!chatQueue.isEmpty()) {
						if (relaySocketChannel != null && cnt == 0) {
							relaySocketChannel.register(lobbySelector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
							cnt++;
						}
						if (chatQueue.peek().channel == null) {
							chatQueue.peek().channel = relaySocketChannel;
						}
						handler.bufferWrite(chatQueue.poll());
					}
				}

				if (key.isReadable()) {
					if (key.channel() == server_LBserver_SocketChannel) {
						System.out.println("Lobby : server_LBserver_SocketChannel");
						server_LBserver_SocketChannel = (SocketChannel) key.channel();
						packet = new Packet(server_LBserver_SocketChannel);
						PacketHandler handler = new PacketHandler();

						// GateWay에서 써준 데이터를 읽음
						packet = handler.bufferRead(packet);

						lobbyThread.packetReceiveQueue.add(packet);

						// 큐에 패킷 넣은 다음에 event 를 알려줌
						eventHandler.set();

					} else if (key.channel() == server_client_SocketChannel) {
						System.out.println("Lobby :server_client_SocketChannel");
						server_client_SocketChannel = (SocketChannel) key.channel();
						packet = new Packet(server_client_SocketChannel);
						PacketHandler handler = new PacketHandler();

						// 클라이언트에서 써준 데이터를 읽음
						packet = handler.bufferRead(packet);

						lobbyThread.packetReceiveQueue.add(packet);

						// 큐에 패킷 넣은 다음에 event 를 알려줌
						eventHandler.set();
					} else if (key.channel() == relaySocketChannel) {
						System.out.println("Lobby :relaySocketChannel");
						server_client_SocketChannel = (SocketChannel) key.channel();
						packet = new Packet(server_client_SocketChannel);
						PacketHandler handler = new PacketHandler();

						// 클라이언트에서 써준 데이터를 읽음
						packet = handler.bufferRead(packet);

						lobbyThread.packetReceiveQueue.add(packet);

						// 큐에 패킷 넣은 다음에 evnet 를 알려줌
						eventHandler.set();
					}
				}
			}
		}
	}

	@Override
	void initServerThread() {
		lobbyThread = new LobbyThread(lobbyQueue, chatQueue, eventHandler);
		lobbyThread.start();
	}
}