package blog.syua.node.managerimpl;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import blog.syua.node.Node;
import blog.syua.node.NodeGroup;
import blog.syua.node.nodeimpl.TcpNode;
import blog.syua.utils.ThreadPoolUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpNodeGroup implements NodeGroup {

	private final Queue<TcpNode> tcpNodes;
	private final ExecutorService threadPool;
	private final ServerSocket listenSocket;
	private boolean isAvailable;

	public TcpNodeGroup(int port) throws IOException {
		tcpNodes = new LinkedList<>();
		listenSocket = new ServerSocket(port);
		isAvailable = false;
		threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}

	@Override
	public void startForwarding() {
		if (tcpNodes.isEmpty()) {
			throw new IllegalStateException("포워딩을 시작할 수 없습니다");
		}
		if (isAvailable) {
			throw new IllegalStateException("이미 포워딩이 진행중입니다");
		}
		isAvailable = true;
		new Thread(() -> {
			log.info("TcpNodeManager: startForward {}", this);
			Socket clientSocket;
			try {
				while (isAvailable && Objects.nonNull(clientSocket = listenSocket.accept())) {
					log.info("TcpNodeManager: connect new client {} {}", clientSocket.getInetAddress(),
						clientSocket.getPort());
					Socket finalClientSocket = clientSocket;
					threadPool.execute(() -> selectNode().forwardPacket(finalClientSocket));
				}
			} catch (IOException e) {
				log.error("UdpNodeManager: Error occur in startForward\n{}", Arrays.toString(e.getStackTrace()));
				throw new IllegalThreadStateException("패킷을 받을 수 없습니다");
			}

		}).start();
	}

	@Override
	public synchronized void registerNode(Node tcpNode) {
		if (!(tcpNode instanceof TcpNode)) {
			throw new IllegalArgumentException("TCP 노드가 아닙니다");
		}
		tcpNodes.offer((TcpNode)tcpNode);
		log.info("TcpNodeManager: registerNode {}", tcpNode);
	}

	@Override
	public synchronized void unRegisterNode(Node tcpNode) {
		if (!(tcpNode instanceof TcpNode)) {
			throw new IllegalArgumentException("TCP 노드가 아닙니다");
		}
		tcpNodes.remove(tcpNode);
		log.info("TcpNodeManager: unregisterNode {}", tcpNode);
		if (tcpNodes.isEmpty()) {
			isAvailable = false;
			ThreadPoolUtils.removeThreadPool(threadPool, listenSocket);
		}
	}

	private synchronized TcpNode selectNode() {
		TcpNode curNode = tcpNodes.poll();
		tcpNodes.offer(curNode);
		return curNode;
	}

}
