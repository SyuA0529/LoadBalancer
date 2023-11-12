package blog.syua.node.managerimpl;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import blog.syua.node.Node;
import blog.syua.node.NodeManager;
import blog.syua.node.nodeimpl.TcpNode;
import blog.syua.utils.ThreadPoolUtils;

public class TcpNodeManager implements NodeManager {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	private final Queue<TcpNode> tcpNodes;
	private final ExecutorService threadPool;
	private final ServerSocket listenSocket;
	private boolean isAvailable;

	public TcpNodeManager(int port) throws IOException {
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
		isAvailable = true;
		new Thread(() -> {
			logger.info("TcpNodeManager: startForward {}", this);
			Socket clientSocket;
			try {
				while (isAvailable && Objects.nonNull(clientSocket = listenSocket.accept())) {
					logger.info("TcpNodeManager: connect new client {} {}", clientSocket.getInetAddress(),
						clientSocket.getPort());
					Socket finalClientSocket = clientSocket;
					threadPool.execute(() -> selectNode().forwardPacket(finalClientSocket));
				}
			} catch (IOException e) {
				logger.error("UdpNodeManager: Error occur in startForward\n{}", Arrays.toString(e.getStackTrace()));
				throw new IllegalThreadStateException("패킷을 받을 수 없습니다");
			}

		}).start();
	}

	@Override
	public Collection<Node> getNodes() {
		return new ArrayList<>(tcpNodes);
	}

	@Override
	public synchronized void registerNode(Node tcpNode) {
		if (!(tcpNode instanceof TcpNode)) {
			throw new IllegalArgumentException("TCP 노드가 아닙니다");
		}
		tcpNodes.offer((TcpNode)tcpNode);
		logger.info("TcpNodeManager: registerNode {}", tcpNode);
	}

	@Override
	public synchronized void unRegisterNode(Node tcpNode) {
		if (!(tcpNode instanceof TcpNode)) {
			throw new IllegalArgumentException("TCP 노드가 아닙니다");
		}
		tcpNodes.remove(tcpNode);
		logger.info("TcpNodeManager: unregisterNode {}", tcpNode);
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
