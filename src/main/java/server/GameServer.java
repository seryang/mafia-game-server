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
import server.Game.GameThread;

/**
 * @author Mr.Choe
 */
public class GameServer extends AbstractServer {
	public static ConcurrentLinkedQueue<Packet> chatQueue = new ConcurrentLinkedQueue<>();
	public static ConcurrentLinkedQueue<Packet> gameQueue = new ConcurrentLinkedQueue<Packet>();

	public GameServer() throws IOException {
		super();
	}

	public static void main(String[] args) {
		try {
			new GameServer();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @throws IOException
	 * - LB Server Connection
	 */
	@Override
	public void connectLB() throws IOException {
		Selector gameSelector = Selector.open();

		server_LBserver_SocketChannel = SocketChannel.open(new InetSocketAddress(LB_SERVER_IP, LB_SERVER_PORT));
		server_LBserver_SocketChannel.configureBlocking(false);
		server_LBserver_SocketChannel.register(gameSelector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);

		System.out.println("Game Server ON : " + gameSelector.isOpen());
		System.out.println("IP : " + hostAddr + " / PORT : " + serverSocketChannel.socket().getLocalPort());

		JSONObject gameServer_Init_Data = new JSONObject();
		gameServer_Init_Data.put("host_ip", hostAddr);
		gameServer_Init_Data.put("port", serverSocketChannel.socket().getLocalPort());
		gameServer_Init_Data.put("type", "GAME_SERVER");
		gameServer_Init_Data.put("chatPort", relayServerSocketChannel.socket().getLocalPort());
		gameServer_Init_Data.put("socket_localport",server_LBserver_SocketChannel.socket().getLocalPort());

		Packet packet = new Packet(server_LBserver_SocketChannel, gameServer_Init_Data);

		gameQueue.add(packet);

		int cnt = 0;

		while(gameSelector.select() > 0 ){
			Iterator<SelectionKey> iter = gameSelector.selectedKeys().iterator();

			while(iter.hasNext()){
				SelectionKey key = iter.next();
				iter.remove();

				if(key.isWritable()){
					if(!gameQueue.isEmpty()){
						handler = new PacketHandler();
						handler.bufferWrite(gameQueue.poll());
					}

					if(!chatQueue.isEmpty()){
						if(relaySocketChannel != null && cnt == 0){
							relaySocketChannel.register(gameSelector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
							cnt++;
						}
						if(chatQueue.peek().channel == null){
							chatQueue.peek().channel = relaySocketChannel;
						}
						handler.bufferWrite(chatQueue.poll());
					}
				}

				if (key.isReadable()) {
					if(key.channel() == server_LBserver_SocketChannel){
						System.out.println("Game : channel is server_LBserver_SocketChannel");
						server_LBserver_SocketChannel = (SocketChannel) key.channel();
						packet = new Packet(server_LBserver_SocketChannel);
						PacketHandler handler = new PacketHandler();

						// GateWay에서 써준 데이터를 읽음
						packet = handler.bufferRead(packet);

						gameThread.packetReceiveQueue.add(packet);

						// 큐에 패킷 넣은 다음에 event 를 알려줌
						eventHandler.set();

					}else if(key.channel() == server_client_SocketChannel){
						System.out.println("Game : channel is server_client_SocketChannel");
						server_client_SocketChannel = (SocketChannel) key.channel();
						packet = new Packet(server_client_SocketChannel);
						PacketHandler handler = new PacketHandler();

						// 클라이언트에서 써준 데이터를 읽음
						packet = handler.bufferRead(packet);

						gameThread.packetReceiveQueue.add(packet);
						// 큐에 패킷 넣은 다음에 event 를 알려줌
						eventHandler.set();
					} else if (key.channel() == relaySocketChannel) {
						System.out.println("Game :relaySocketChannel");
						server_client_SocketChannel = (SocketChannel) key.channel();
						packet = new Packet(server_client_SocketChannel);
						PacketHandler handler = new PacketHandler();

						// 클라이언트에서 써준 데이터를 읽음
						packet = handler.bufferRead(packet);

						gameThread.packetReceiveQueue.add(packet);
						// 큐에 패킷 넣은 다음에 event 를 알려줌
						eventHandler.set();
					}
				}
			}
		} 
	}

	@Override
	void initServerThread() {
		gameThread = new GameThread(chatQueue, eventHandler);
		gameThread.start();
	}
}